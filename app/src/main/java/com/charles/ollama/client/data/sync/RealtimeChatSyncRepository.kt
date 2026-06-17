package com.charles.ollama.client.data.sync

import android.util.Log
import com.charles.ollama.client.data.database.dao.ChatMessageDao
import com.charles.ollama.client.data.database.dao.ChatThreadDao
import com.charles.ollama.client.data.database.entity.ChatMessageEntity
import com.charles.ollama.client.data.database.entity.ChatThreadEntity
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class WebChatRequest(
    val requestId: String,
    val threadSyncId: String,
    val threadTitle: String?,
    val model: String?,
    val content: String,
    val images: List<String>?
)

@Singleton
class RealtimeChatSyncRepository @Inject constructor(
    private val chatThreadDao: ChatThreadDao,
    private val chatMessageDao: ChatMessageDao,
    private val syncPreferences: SyncPreferences
) {
    private val database = FirebaseDatabase.getInstance()
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO +
            CoroutineExceptionHandler { _, e -> Log.e(TAG, "Uncaught sync error", e) }
    )

    private var activeUid: String? = null
    private var activeDeviceId: String? = null
    private var dirtyJob: Job? = null
    private var heartbeatJob: Job? = null
    private var threadListener: ChildEventListener? = null
    private var messageListener: ChildEventListener? = null
    private var requestListener: ChildEventListener? = null

    fun start(uid: String, deviceId: String, webRequestHandler: suspend (WebChatRequest) -> Result<Unit>) {
        if (activeUid == uid) return
        stop()
        activeUid = uid
        activeDeviceId = deviceId
        syncPreferences.setCurrentUid(uid)
        userRef(uid).child("profile").updateChildren(
            mapOf(
                "uid" to uid,
                "androidSyncVersion" to 1,
                "updatedAt" to System.currentTimeMillis()
            )
        )
        scope.launch {
            runCatching {
                ensureLocalSyncIds()
                uploadDirtyRows(uid)
                attachListeners(uid, webRequestHandler)
            }.onFailure { error ->
                syncPreferences.markError(error.localizedMessage ?: "Sync startup failed")
                Log.e(TAG, "Failed to start sync", error)
            }
        }
        dirtyJob = scope.launch {
            while (activeUid == uid) {
                runCatching { uploadDirtyRows(uid) }
                    .onFailure { syncPreferences.markError(it.localizedMessage ?: "Sync failed") }
                delay(5_000)
            }
        }
        heartbeatJob = scope.launch {
            while (activeUid == uid) {
                runCatching {
                    userRef(uid).child("devices").child(deviceId).updateChildren(
                        mapOf("updatedAt" to System.currentTimeMillis(), "online" to true)
                    ).await()
                }
                delay(60_000)
            }
        }
    }

    fun stop() {
        val uid = activeUid
        val deviceId = activeDeviceId
        if (uid != null) {
            threadListener?.let { userRef(uid).child("threads").removeEventListener(it) }
            messageListener?.let { userRef(uid).child("messages").removeEventListener(it) }
            requestListener?.let { userRef(uid).child("webRequests").removeEventListener(it) }
            if (deviceId != null) {
                userRef(uid).child("devices").child(deviceId).updateChildren(
                    mapOf("updatedAt" to System.currentTimeMillis(), "online" to false)
                )
            }
        }
        dirtyJob?.cancel()
        heartbeatJob?.cancel()
        activeUid = null
        activeDeviceId = null
        threadListener = null
        messageListener = null
        requestListener = null
    }

    suspend fun uploadDirtyRows(uidOverride: String? = null) {
        val uid = uidOverride ?: activeUid ?: return
        ensureLocalSyncIds()
        val threadsRef = userRef(uid).child("threads")
        chatThreadDao.getDirtyThreads().forEach { thread ->
            val syncId = thread.syncId ?: return@forEach
            val syncUpdatedAt = thread.syncUpdatedAt ?: System.currentTimeMillis()
            threadsRef.child(syncId).setValue(thread.toRemoteMap(syncUpdatedAt)).await()
            chatThreadDao.markSynced(thread.id, syncUpdatedAt, thread.syncVersion)
        }

        val messagesRoot = userRef(uid).child("messages")
        chatMessageDao.getDirtyMessages().forEach { message ->
            val syncId = message.syncId ?: return@forEach
            val thread = chatThreadDao.getThreadById(message.threadId) ?: return@forEach
            val threadSyncId = thread.syncId ?: return@forEach
            val syncUpdatedAt = message.syncUpdatedAt ?: System.currentTimeMillis()
            messagesRoot.child(threadSyncId).child(syncId)
                .setValue(message.toRemoteMap(threadSyncId, syncUpdatedAt))
                .await()
            chatMessageDao.markSynced(message.id, syncUpdatedAt, message.syncVersion)
        }
        syncPreferences.markSynced()
    }

    suspend fun markWebRequestRunning(uid: String, requestId: String, deviceId: String): Boolean {
        val ref = userRef(uid).child("webRequests").child(requestId)
        val snapshot = ref.get().await()
        if (snapshot.child("status").getValue(String::class.java) != "pending") return false
        ref.updateChildren(
            mapOf(
                "status" to "running",
                "claimedByDeviceId" to deviceId,
                "claimedAt" to System.currentTimeMillis()
            )
        ).await()
        return true
    }

    suspend fun completeWebRequest(uid: String, requestId: String) {
        userRef(uid).child("webRequests").child(requestId).updateChildren(
            mapOf("status" to "complete", "completedAt" to System.currentTimeMillis())
        ).await()
    }

    suspend fun failWebRequest(uid: String, requestId: String, message: String) {
        userRef(uid).child("webRequests").child(requestId).updateChildren(
            mapOf(
                "status" to "error",
                "error" to message.take(500),
                "completedAt" to System.currentTimeMillis()
            )
        ).await()
    }

    fun writePremiumStatus(uid: String, webSyncPremium: Boolean) {
        userRef(uid).child("subscription").setValue(
            mapOf("webSyncPremium" to webSyncPremium, "updatedAt" to System.currentTimeMillis())
        )
    }

    suspend fun publishAvailableModels(uid: String, models: List<String>) {
        if (models.isEmpty()) return
        val now = System.currentTimeMillis()
        val data = models.associate { name ->
            name.replace('.', '_').replace('/', '|') to mapOf("name" to name, "updatedAt" to now)
        }
        userRef(uid).child("availableModels").setValue(data).await()
    }

    private suspend fun ensureLocalSyncIds() {
        chatThreadDao.getThreadsMissingSyncId().forEach { thread ->
            chatThreadDao.setSyncId(thread.id, UUID.randomUUID().toString())
        }
        chatMessageDao.getMessagesMissingSyncId().forEach { message ->
            chatMessageDao.setSyncId(message.id, UUID.randomUUID().toString())
        }
    }

    private fun attachListeners(uid: String, webRequestHandler: suspend (WebChatRequest) -> Result<Unit>) {
        val root = userRef(uid)
        threadListener = object : SimpleChildEventListener() {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                scope.launch { runCatching { importThread(snapshot) }.onFailure { Log.e(TAG, "importThread failed", it) } }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                scope.launch { runCatching { importThread(snapshot) }.onFailure { Log.e(TAG, "importThread failed", it) } }
            }
        }.also { root.child("threads").addChildEventListener(it) }

        messageListener = object : SimpleChildEventListener() {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                scope.launch { runCatching { importThreadMessages(snapshot) }.onFailure { Log.e(TAG, "importThreadMessages failed", it) } }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                scope.launch { runCatching { importThreadMessages(snapshot) }.onFailure { Log.e(TAG, "importThreadMessages failed", it) } }
            }
        }.also { root.child("messages").addChildEventListener(it) }

        requestListener = object : SimpleChildEventListener() {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                handleWebRequestSnapshot(uid, snapshot, webRequestHandler)
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                handleWebRequestSnapshot(uid, snapshot, webRequestHandler)
            }
        }.also { root.child("webRequests").addChildEventListener(it) }
    }

    private fun handleWebRequestSnapshot(
        uid: String,
        snapshot: DataSnapshot,
        webRequestHandler: suspend (WebChatRequest) -> Result<Unit>
    ) {
        if (snapshot.child("status").getValue(String::class.java) != "pending") return
        val request = snapshot.toWebChatRequest() ?: return
        scope.launch {
            runCatching {
                val result = webRequestHandler(request)
                result.onFailure {
                    runCatching { failWebRequest(uid, request.requestId, it.localizedMessage ?: "Web request failed") }
                        .onFailure { e -> Log.e(TAG, "Failed to mark web request failed", e) }
                }
            }.onFailure { e -> Log.e(TAG, "Web request handler threw unexpectedly", e) }
        }
    }

    private suspend fun importThread(snapshot: DataSnapshot) {
        val syncId = snapshot.key ?: return
        val remoteUpdatedAt = snapshot.child("syncUpdatedAt").getValue(Long::class.java) ?: 0L
        val local = chatThreadDao.getThreadBySyncId(syncId)
        if (local != null && (local.syncUpdatedAt ?: 0L) >= remoteUpdatedAt) return
        val entity = snapshot.toThreadEntity(syncId, local?.id ?: 0) ?: return
        if (local == null) {
            chatThreadDao.insertThread(entity)
        } else {
            chatThreadDao.updateThread(entity)
        }
    }

    private suspend fun importThreadMessages(threadSnapshot: DataSnapshot) {
        val threadSyncId = threadSnapshot.key ?: return
        val localThread = chatThreadDao.getThreadBySyncId(threadSyncId) ?: return
        threadSnapshot.children.forEach { messageSnapshot ->
            importMessage(localThread.id, threadSyncId, messageSnapshot)
        }
    }

    private suspend fun importMessage(localThreadId: Long, threadSyncId: String, snapshot: DataSnapshot) {
        val syncId = snapshot.key ?: return
        val remoteUpdatedAt = snapshot.child("syncUpdatedAt").getValue(Long::class.java) ?: 0L
        val local = chatMessageDao.getMessageBySyncId(syncId)
        if (local != null && (local.syncUpdatedAt ?: 0L) >= remoteUpdatedAt) return
        val entity = snapshot.toMessageEntity(localThreadId, threadSyncId, syncId, local?.id ?: 0) ?: return
        if (local == null) {
            chatMessageDao.insertMessage(entity)
        } else {
            chatMessageDao.insertMessage(entity)
        }
    }

    private fun userRef(uid: String): DatabaseReference =
        database.reference.child("users").child(uid)

    /**
     * Permanently remove all of this user's synced data (`/users/{uid}`):
     * threads, messages, web requests, device heartbeats, available models and
     * subscription flag. Called as part of account deletion. Must run while the
     * user is still authenticated (DB rules require auth on the uid).
     */
    suspend fun deleteAllUserData(uid: String) {
        userRef(uid).removeValue().await()
    }

    companion object {
        private const val TAG = "RealtimeChatSync"
    }
}

private abstract class SimpleChildEventListener : com.google.firebase.database.ChildEventListener {
    override fun onChildAdded(snapshot: com.google.firebase.database.DataSnapshot, previousChildName: String?) = Unit
    override fun onChildChanged(snapshot: com.google.firebase.database.DataSnapshot, previousChildName: String?) = Unit
    override fun onChildRemoved(snapshot: com.google.firebase.database.DataSnapshot) = Unit
    override fun onChildMoved(snapshot: com.google.firebase.database.DataSnapshot, previousChildName: String?) = Unit
    override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
        Log.w("RealtimeChatSync", "Realtime Database listener cancelled: ${error.message}")
    }
}

private fun ChatThreadEntity.toRemoteMap(syncUpdatedAt: Long): Map<String, Any?> =
    mapOf(
        "syncId" to syncId,
        "title" to title,
        "model" to model,
        "serverId" to serverId,
        "streamEnabled" to streamEnabled,
        "systemPrompt" to systemPrompt,
        "vibrationEnabled" to vibrationEnabled,
        "showThinking" to showThinking,
        "isPinned" to isPinned,
        "isArchived" to isArchived,
        "label" to label,
        "temperature" to temperature,
        "topP" to topP,
        "topK" to topK,
        "numCtx" to numCtx,
        "seed" to seed,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt,
        "syncVersion" to syncVersion,
        "syncUpdatedAt" to syncUpdatedAt,
        "deleted" to syncDeleted
    )

private fun ChatMessageEntity.toRemoteMap(threadSyncId: String, syncUpdatedAt: Long): Map<String, Any?> =
    mapOf(
        "syncId" to syncId,
        "threadSyncId" to threadSyncId,
        "role" to role,
        "content" to content,
        "thinking" to thinking,
        "images" to images,
        "evalCount" to evalCount,
        "evalDurationNs" to evalDurationNs,
        "promptEvalCount" to promptEvalCount,
        "totalDurationNs" to totalDurationNs,
        "timestamp" to timestamp,
        "syncVersion" to syncVersion,
        "syncUpdatedAt" to syncUpdatedAt,
        "deleted" to syncDeleted
    )

private fun DataSnapshot.toThreadEntity(syncId: String, localId: Long): ChatThreadEntity? {
    val title = child("title").getValue(String::class.java) ?: return null
    return ChatThreadEntity(
        id = localId,
        title = title,
        model = child("model").getValue(String::class.java),
        serverId = child("serverId").getValue(Long::class.java),
        streamEnabled = child("streamEnabled").getValue(Boolean::class.java) ?: true,
        systemPrompt = child("systemPrompt").getValue(String::class.java),
        vibrationEnabled = child("vibrationEnabled").getValue(Boolean::class.java) ?: true,
        showThinking = child("showThinking").getValue(Boolean::class.java) ?: false,
        isPinned = child("isPinned").getValue(Boolean::class.java) ?: false,
        isArchived = child("isArchived").getValue(Boolean::class.java) ?: false,
        label = child("label").getValue(String::class.java),
        temperature = child("temperature").getValue(Double::class.java)?.toFloat(),
        topP = child("topP").getValue(Double::class.java)?.toFloat(),
        topK = child("topK").getValue(Int::class.java),
        numCtx = child("numCtx").getValue(Int::class.java),
        seed = child("seed").getValue(Int::class.java),
        createdAt = child("createdAt").getValue(Long::class.java) ?: System.currentTimeMillis(),
        updatedAt = child("updatedAt").getValue(Long::class.java) ?: System.currentTimeMillis(),
        syncId = syncId,
        syncVersion = child("syncVersion").getValue(Long::class.java) ?: 0L,
        syncUpdatedAt = child("syncUpdatedAt").getValue(Long::class.java),
        syncDeleted = child("deleted").getValue(Boolean::class.java) ?: false,
        syncDirty = false
    )
}

private fun DataSnapshot.toMessageEntity(
    localThreadId: Long,
    threadSyncId: String,
    syncId: String,
    localId: Long
): ChatMessageEntity? {
    if (child("threadSyncId").getValue(String::class.java) != threadSyncId) return null
    val role = child("role").getValue(String::class.java) ?: return null
    val content = child("content").getValue(String::class.java).orEmpty()
    val images = child("images").children.mapNotNull { it.getValue(String::class.java) }.takeIf { it.isNotEmpty() }
    return ChatMessageEntity(
        id = localId,
        threadId = localThreadId,
        role = role,
        content = content,
        thinking = child("thinking").getValue(String::class.java),
        images = images,
        evalCount = child("evalCount").getValue(Int::class.java),
        evalDurationNs = child("evalDurationNs").getValue(Long::class.java),
        promptEvalCount = child("promptEvalCount").getValue(Int::class.java),
        totalDurationNs = child("totalDurationNs").getValue(Long::class.java),
        timestamp = child("timestamp").getValue(Long::class.java) ?: System.currentTimeMillis(),
        syncId = syncId,
        syncVersion = child("syncVersion").getValue(Long::class.java) ?: 0L,
        syncUpdatedAt = child("syncUpdatedAt").getValue(Long::class.java),
        syncDeleted = child("deleted").getValue(Boolean::class.java) ?: false,
        syncDirty = false
    )
}

private fun DataSnapshot.toWebChatRequest(): WebChatRequest? {
    val requestId = key ?: return null
    val threadSyncId = child("threadSyncId").getValue(String::class.java) ?: return null
    val content = child("content").getValue(String::class.java) ?: return null
    val images = child("images").children.mapNotNull { it.getValue(String::class.java) }.takeIf { it.isNotEmpty() }
    return WebChatRequest(
        requestId = requestId,
        threadSyncId = threadSyncId,
        threadTitle = child("threadTitle").getValue(String::class.java),
        model = child("model").getValue(String::class.java),
        content = content,
        images = images
    )
}
