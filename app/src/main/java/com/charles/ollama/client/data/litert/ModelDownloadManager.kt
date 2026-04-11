package com.charles.ollama.client.data.litert

import android.content.Context
import android.os.StatFs
import android.util.Log
import com.charles.ollama.client.data.database.dao.InstalledLitertModelDao
import com.charles.ollama.client.data.database.entity.InstalledLitertModelEntity
import com.charles.ollama.client.data.repository.PullProgress
import com.charles.ollama.client.di.LiteRtDownloadClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    @LiteRtDownloadClient private val okHttpClient: OkHttpClient,
    private val installedDao: InstalledLitertModelDao,
    private val preferences: LitertPreferences
) {

    private fun modelsRoot(): File =
        File(context.filesDir, "litert_models").also { it.mkdirs() }

    fun localFileForCatalog(entry: LocalModelCatalogEntry): File =
        File(File(modelsRoot(), entry.id), entry.fileName)

    suspend fun isInstalled(entry: LocalModelCatalogEntry): Boolean =
        withContext(Dispatchers.IO) {
            installedDao.getById(entry.id) != null && localFileForCatalog(entry).exists()
        }

    suspend fun deleteInstalled(catalogId: String) = withContext(Dispatchers.IO) {
        installedDao.getById(catalogId)?.let { entity ->
            runCatching { File(entity.localFilePath).delete() }
        }
        installedDao.deleteById(catalogId)
    }

    /**
     * Downloads the `.litertlm` file and registers it in [InstalledLitertModelDao].
     *
     * Supports HTTP `Range` resumption: if a partial `.part` file exists from a
     * previous attempt, the download resumes from that byte offset instead of
     * restarting from scratch. Falls back to a full download if the server
     * rejects the range request.
     *
     * [onProgress] receives `(bytesDone, totalBytes)`.
     */
    suspend fun download(
        entry: LocalModelCatalogEntry,
        onProgress: (downloaded: Long, total: Long) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        val targetDir = File(modelsRoot(), entry.id)
        targetDir.mkdirs()
        val partFile = File(targetDir, entry.fileName + ".part")
        val finalFile = File(targetDir, entry.fileName)

        runCatching {
            if (finalFile.exists()) {
                // Already fully downloaded — nothing to do.
                installedDao.insert(
                    InstalledLitertModelEntity(
                        catalogId = entry.id,
                        localFilePath = finalFile.absolutePath,
                        expectedBytes = finalFile.length(),
                        installedAt = System.currentTimeMillis()
                    )
                )
                return@runCatching finalFile.absolutePath
            }

            ensureFreeSpace(targetDir, entry.approximateSizeBytes, partFile.length())

            val startOffset = if (partFile.exists()) partFile.length() else 0L

            val requestBuilder = Request.Builder().url(entry.downloadUrl)
            preferences.getHuggingFaceToken()?.let { token ->
                requestBuilder.header("Authorization", "Bearer $token")
            }
            if (startOffset > 0) {
                requestBuilder.header("Range", "bytes=$startOffset-")
                Log.i(TAG, "Resuming ${entry.id} at offset $startOffset")
            }

            okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful && response.code != 206) {
                    // 401/403 usually means the repo is gated and we need a HF token.
                    val hint = when (response.code) {
                        401, 403 -> " (gated repo — set a Hugging Face token in Settings)"
                        416 -> " (range not satisfiable — delete and retry)"
                        else -> ""
                    }
                    error("HTTP ${response.code}: ${response.message}$hint")
                }

                val body = response.body ?: error("Empty response body")
                val serverIsResuming = response.code == 206 && startOffset > 0
                if (startOffset > 0 && !serverIsResuming) {
                    // Server ignored the Range header — restart from scratch.
                    Log.w(TAG, "Server ignored Range header, restarting ${entry.id} from 0")
                    partFile.delete()
                }

                val remaining = body.contentLength().takeIf { it >= 0 } ?: -1L
                val total = when {
                    remaining >= 0 && serverIsResuming -> startOffset + remaining
                    remaining >= 0 -> remaining
                    else -> entry.approximateSizeBytes
                }

                var done = if (serverIsResuming) startOffset else 0L
                onProgress(done, total)

                body.byteStream().use { input ->
                    RandomAccessFile(partFile, "rw").use { raf ->
                        if (serverIsResuming) raf.seek(startOffset) else raf.setLength(0)
                        val buffer = ByteArray(256 * 1024)
                        while (true) {
                            val n = input.read(buffer)
                            if (n <= 0) break
                            raf.write(buffer, 0, n)
                            done += n
                            onProgress(done, total)
                        }
                    }
                }
            }

            if (!partFile.renameTo(finalFile)) {
                // Fall back to copy+delete if rename fails (e.g. cross-filesystem).
                partFile.inputStream().use { input ->
                    finalFile.outputStream().use { output -> input.copyTo(output) }
                }
                partFile.delete()
            }

            installedDao.insert(
                InstalledLitertModelEntity(
                    catalogId = entry.id,
                    localFilePath = finalFile.absolutePath,
                    expectedBytes = finalFile.length(),
                    installedAt = System.currentTimeMillis()
                )
            )
            finalFile.absolutePath
        }
    }

    /** Emits [PullProgress] updates for UI (Ollama pull-compatible shape). */
    fun downloadAsFlow(entry: LocalModelCatalogEntry): Flow<PullProgress> = callbackFlow {
        val job = launch(Dispatchers.IO) {
            val result = runCatching {
                download(entry) { done, total ->
                    trySend(PullProgress(status = "downloading", completed = done, total = total))
                }.getOrThrow()
            }
            result.onSuccess {
                trySend(
                    PullProgress(
                        status = "success: download complete",
                        completed = 1L,
                        total = 1L
                    )
                )
                close()
            }
            result.onFailure { close(it) }
        }
        awaitClose { job.cancel() }
    }

    /**
     * Throws if [targetDir]'s filesystem does not have enough free space to
     * hold the model, counting any [alreadyDownloaded] bytes already on disk.
     * We require `expected - already + 256 MB` headroom.
     */
    private fun ensureFreeSpace(targetDir: File, expected: Long, alreadyDownloaded: Long) {
        val stat = try {
            StatFs(targetDir.absolutePath)
        } catch (e: Exception) {
            Log.w(TAG, "StatFs failed for ${targetDir.absolutePath}", e)
            return
        }
        val free = stat.availableBytes
        val needed = (expected - alreadyDownloaded).coerceAtLeast(0L) + HEADROOM_BYTES
        if (free < needed) {
            throw IOException(
                "Not enough free space: need ~${needed / 1_000_000} MB, have ${free / 1_000_000} MB."
            )
        }
    }

    companion object {
        private const val TAG = "ModelDownloadManager"
        private const val HEADROOM_BYTES = 256L * 1024L * 1024L
    }
}
