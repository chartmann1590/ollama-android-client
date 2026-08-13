package com.charles.ollama.client.data.repository

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.os.Build
import com.charles.ollama.client.BuildConfig
import com.charles.ollama.client.data.api.GitHubApiService
import com.charles.ollama.client.data.api.dto.*
import com.charles.ollama.client.data.preferences.BugReportStorage
import com.charles.ollama.client.data.preferences.LocalBugReport
import com.charles.ollama.client.data.database.dao.InstalledLitertModelDao
import com.charles.ollama.client.data.database.dao.ServerConfigDao
import com.charles.ollama.client.data.api.OllamaApiFactory
import com.charles.ollama.client.data.litert.LocalModelCatalog
import com.charles.ollama.client.data.litert.ServerBackend
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GitHubFeedbackRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiService: GitHubApiService,
    private val storage: BugReportStorage,
    private val installedLitertModelDao: InstalledLitertModelDao,
    private val serverConfigDao: ServerConfigDao,
    private val apiFactory: OllamaApiFactory
) {
    fun getLocalBugReports(): List<LocalBugReport> {
        return storage.getBugReports()
    }

    suspend fun submitIssue(
        title: String,
        description: String,
        name: String,
        email: String,
        screenshotUri: Uri?,
        includeDiagnostics: Boolean
    ): LocalBugReport = withContext(Dispatchers.IO) {
        var finalBody = description
        
        if (name.isNotBlank() || email.isNotBlank()) {
            finalBody += "\n\n### Reporter Details\n"
            if (name.isNotBlank()) finalBody += "- **Name:** $name\n"
            if (email.isNotBlank()) finalBody += "- **Email:** $email\n"
        }

        if (screenshotUri != null) {
            val filename = "screenshot_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.png"
            try {
                val imageUrl = uploadScreenshot(screenshotUri, filename)
                finalBody += "\n\n![Screenshot]($imageUrl)"
            } catch (e: Exception) {
                finalBody += "\n\n*(Failed to upload screenshot: ${e.localizedMessage})*"
            }
        }

        if (includeDiagnostics) {
            finalBody += gatherDiagnostics(context)
            finalBody += gatherModelsInfo()
        }

        val response = apiService.createIssue(
            CreateIssueRequest(title = title, body = finalBody)
        )

        val localReport = LocalBugReport(
            issueNumber = response.number,
            title = response.title,
            status = response.state,
            createdAt = response.createdAt,
            htmlUrl = response.htmlUrl
        )

        storage.addOrUpdateBugReport(localReport)
        localReport
    }

    suspend fun refreshIssueStatus(issueNumber: Int): LocalBugReport = withContext(Dispatchers.IO) {
        val response = apiService.getIssue(number = issueNumber)
        val localReport = LocalBugReport(
            issueNumber = response.number,
            title = response.title,
            status = response.state,
            createdAt = response.createdAt,
            htmlUrl = response.htmlUrl
        )
        storage.addOrUpdateBugReport(localReport)
        localReport
    }

    suspend fun getComments(issueNumber: Int): List<GitHubCommentResponse> = withContext(Dispatchers.IO) {
        apiService.getComments(number = issueNumber)
    }

    suspend fun postComment(
        issueNumber: Int,
        commentText: String,
        screenshotUri: Uri?
    ): GitHubCommentResponse = withContext(Dispatchers.IO) {
        var finalBody = "**[User Reply from App]**\n\n$commentText"

        if (screenshotUri != null) {
            val filename = "screenshot_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.png"
            try {
                val imageUrl = uploadScreenshot(screenshotUri, filename)
                finalBody += "\n\n![Screenshot]($imageUrl)"
            } catch (e: Exception) {
                finalBody += "\n\n*(Failed to upload screenshot: ${e.localizedMessage})*"
            }
        }

        apiService.postComment(
            number = issueNumber,
            request = PostCommentRequest(body = finalBody)
        )
    }

    private suspend fun uploadScreenshot(uri: Uri, filename: String): String {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw Exception("Could not read screenshot data")
        val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        
        val response = apiService.uploadAsset(
            UploadContentRequest(filename = filename, contentBase64 = base64)
        )
        return response.content.downloadUrl
    }

    private fun gatherDiagnostics(context: Context): String {
        val brand = Build.BRAND
        val model = Build.MODEL
        val manufacturer = Build.MANUFACTURER
        val release = Build.VERSION.RELEASE
        val sdk = Build.VERSION.SDK_INT
        val versionName = BuildConfig.VERSION_NAME
        val versionCode = BuildConfig.VERSION_CODE
        val locale = Locale.getDefault().toString()

        val filesDir = context.filesDir
        val freeSpace = formatBytes(filesDir.freeSpace)
        val totalSpace = formatBytes(filesDir.totalSpace)

        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager.getMemoryInfo(memInfo)
        val freeMem = formatBytes(memInfo.availMem)
        val totalMem = formatBytes(memInfo.totalMem)

        return """
            
            ---
            ### Device & App Diagnostics
            - **App Version:** $versionName ($versionCode)
            - **Android Version:** $release (API $sdk)
            - **Device:** $manufacturer $brand $model
            - **Locale:** $locale
            - **Storage (Free/Total):** $freeSpace / $totalSpace
            - **RAM (Available/Total):** $freeMem / $totalMem
        """.trimIndent()
    }

    private fun formatBytes(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
            mb >= 1.0 -> String.format(Locale.US, "%.2f MB", mb)
            kb >= 1.0 -> String.format(Locale.US, "%.2f KB", kb)
            else -> "$bytes Bytes"
        }
    }

    private suspend fun gatherModelsInfo(): String {
        val sb = StringBuilder()
        sb.append("\n\n### Configured Models\n")

        // 1. LiteRT (On-device) models
        try {
            val installedLitert = installedLitertModelDao.getAll()
            if (installedLitert.isNotEmpty()) {
                sb.append("- **On-Device (LiteRT):**\n")
                installedLitert.forEach { entity ->
                    val catalogModel = LocalModelCatalog.byId(entity.catalogId)
                    val modelName = catalogModel?.threadModelName ?: entity.catalogId
                    sb.append("  - $modelName\n")
                }
            } else {
                sb.append("- **On-Device (LiteRT):** None installed\n")
            }
        } catch (e: Exception) {
            sb.append("- **On-Device (LiteRT):** Error fetching list\n")
        }

        // 2. Remote Ollama models (excluding server URLs / IPs)
        try {
            val servers = serverConfigDao.getAllServersSync()
            val ollamaServers = servers.filter { it.backendType == ServerBackend.OLLAMA.name }
            if (ollamaServers.isNotEmpty()) {
                sb.append("- **Remote (Ollama):**\n")
                for ((index, server) in ollamaServers.withIndex()) {
                    sb.append("  - Server #${index + 1} (${server.name}):\n")
                    try {
                        val api = apiFactory.create(server.baseUrl)
                        val response = api.listModels()
                        if (response.isSuccessful && response.body() != null) {
                            val models = response.body()!!.models
                            if (models.isNotEmpty()) {
                                models.forEach { model ->
                                    sb.append("    - ${model.name}\n")
                                }
                            } else {
                                sb.append("    - No models available\n")
                            }
                        } else {
                            sb.append("    - Unreachable (HTTP ${response.code()})\n")
                        }
                    } catch (e: Exception) {
                        sb.append("    - Unreachable (Connection failed)\n")
                    }
                }
            } else {
                sb.append("- **Remote (Ollama):** None configured\n")
            }
        } catch (e: Exception) {
            sb.append("- **Remote (Ollama):** Error fetching list\n")
        }

        return sb.toString()
    }
}

