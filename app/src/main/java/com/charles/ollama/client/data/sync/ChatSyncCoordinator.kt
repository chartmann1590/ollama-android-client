package com.charles.ollama.client.data.sync

import android.content.Context
import android.provider.Settings
import com.charles.ollama.client.data.auth.AuthRepository
import com.charles.ollama.client.data.database.dao.ChatThreadDao
import com.charles.ollama.client.data.database.entity.ChatThreadEntity
import com.charles.ollama.client.data.litert.LitertConstants
import com.charles.ollama.client.data.repository.ChatRepository
import com.charles.ollama.client.data.repository.ModelRepository
import com.charles.ollama.client.data.repository.ServerRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatSyncCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository,
    private val syncPreferences: SyncPreferences,
    private val syncRepository: RealtimeChatSyncRepository,
    private val chatRepository: ChatRepository,
    private val chatThreadDao: ChatThreadDao,
    private val serverRepository: ServerRepository,
    private val modelRepository: ModelRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val deviceId: String by lazy { loadDeviceId() }

    fun start() {
        scope.launch {
            combine(authRepository.currentUser, syncPreferences.syncEnabled) { user, enabled ->
                user?.uid to enabled
            }.collect { (uid, enabled) ->
                if (uid != null && enabled) {
                    syncRepository.start(uid, deviceId) { request -> handleWebRequest(uid, request) }
                    scope.launch { pushAvailableModels(uid) }
                } else {
                    syncRepository.stop()
                }
            }
        }
    }

    private suspend fun pushAvailableModels(uid: String) {
        runCatching {
            val allModels = mutableListOf<String>()
            serverRepository.getAllServers().first().forEach { server ->
                val backend = if (LitertConstants.isLitertLocalBaseUrl(server.baseUrl))
                    com.charles.ollama.client.data.litert.ServerBackend.LITERT_LOCAL
                else
                    com.charles.ollama.client.data.litert.ServerBackend.OLLAMA
                modelRepository.getModelsForBackend(server.baseUrl, backend)
                    .getOrNull()
                    ?.map { it.name }
                    ?.let { allModels.addAll(it) }
            }
            if (allModels.isNotEmpty()) {
                syncRepository.publishAvailableModels(uid, allModels.distinct())
            }
        }
    }

    private suspend fun handleWebRequest(uid: String, request: WebChatRequest): Result<Unit> {
        return runCatching {
            val claimed = syncRepository.markWebRequestRunning(uid, request.requestId, deviceId)
            if (!claimed) return Result.success(Unit)

            val server = serverRepository.getDefaultServerSync()
                ?: error("No default phone server is configured.")
            val thread = chatThreadDao.getThreadBySyncId(request.threadSyncId)
                ?: createThreadForWebRequest(request, server.id)
            val model = request.model ?: thread.model
                ?: chatThreadDao.getMostRecentModel()
                ?: error("No model available — open the app and start a chat to set a default model.")

            if (thread.streamEnabled) {
                chatRepository.streamMessage(
                    threadId = thread.id,
                    content = request.content,
                    model = model,
                    baseUrl = server.baseUrl,
                    systemPrompt = thread.systemPrompt,
                    images = request.images
                ).collect { /* Persisted by ChatRepository. */ }
            } else {
                chatRepository.sendMessage(
                    threadId = thread.id,
                    content = request.content,
                    model = model,
                    baseUrl = server.baseUrl,
                    streamEnabled = false,
                    systemPrompt = thread.systemPrompt,
                    images = request.images
                ).getOrThrow()
            }

            syncRepository.uploadDirtyRows(uid)
            syncRepository.completeWebRequest(uid, request.requestId)
        }.onFailure { error ->
            syncRepository.failWebRequest(uid, request.requestId, error.localizedMessage ?: "Phone execution failed")
        }
    }

    private suspend fun createThreadForWebRequest(request: WebChatRequest, serverId: Long): ChatThreadEntity {
        val now = System.currentTimeMillis()
        val thread = ChatThreadEntity(
            title = request.threadTitle?.takeIf { it.isNotBlank() } ?: "Web chat",
            model = request.model,
            serverId = serverId,
            syncId = request.threadSyncId,
            syncUpdatedAt = now,
            syncDirty = true
        )
        val id = chatThreadDao.insertThread(thread)
        return thread.copy(id = id)
    }

    private fun loadDeviceId(): String {
        val prefs = context.getSharedPreferences("sync_device", Context.MODE_PRIVATE)
        prefs.getString("device_id", null)?.let { return it }
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?.takeIf { it.isNotBlank() }
        val id = androidId ?: UUID.randomUUID().toString()
        prefs.edit().putString("device_id", id).apply()
        return id
    }
}
