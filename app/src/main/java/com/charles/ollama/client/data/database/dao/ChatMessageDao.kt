package com.charles.ollama.client.data.database.dao

import androidx.room.*
import com.charles.ollama.client.data.database.entity.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages WHERE threadId = :threadId AND syncDeleted = 0 ORDER BY timestamp ASC")
    fun getMessagesByThreadId(threadId: Long): Flow<List<ChatMessageEntity>>
    
    // Load messages in batches to avoid CursorWindow overflow
    // This query loads messages with a limit to prevent loading too much data at once
    @Query("SELECT * FROM chat_messages WHERE threadId = :threadId AND syncDeleted = 0 ORDER BY timestamp ASC LIMIT :limit OFFSET :offset")
    suspend fun getMessagesByThreadIdPaged(threadId: Long, limit: Int, offset: Int): List<ChatMessageEntity>
    
    // Load message metadata without large content fields (for messages that are too large)
    // Note: images are excluded to avoid CursorWindow overflow - will be loaded separately if needed
    @Query("SELECT id, threadId, role, '' as content, null as thinking, null as images, null as evalCount, null as evalDurationNs, null as promptEvalCount, null as totalDurationNs, timestamp, syncId, syncVersion, syncUpdatedAt, syncDeleted, syncDirty FROM chat_messages WHERE threadId = :threadId AND syncDeleted = 0 ORDER BY timestamp ASC LIMIT :limit OFFSET :offset")
    suspend fun getMessagesByThreadIdPagedMetadata(threadId: Long, limit: Int, offset: Int): List<ChatMessageEntity>
    
    // Load message IDs and basic info only (for messages that are too large even for metadata)
    @Query("SELECT id, threadId, role, '' as content, null as thinking, null as images, null as evalCount, null as evalDurationNs, null as promptEvalCount, null as totalDurationNs, timestamp, syncId, syncVersion, syncUpdatedAt, syncDeleted, syncDirty FROM chat_messages WHERE threadId = :threadId AND syncDeleted = 0 ORDER BY timestamp ASC LIMIT :limit OFFSET :offset")
    suspend fun getMessagesByThreadIdPagedBasic(threadId: Long, limit: Int, offset: Int): List<ChatMessageEntity>
    
    // Get a single message by ID (for loading large content separately)
    @Query("SELECT * FROM chat_messages WHERE id = :messageId AND syncDeleted = 0")
    suspend fun getMessageById(messageId: Long): ChatMessageEntity?
    
    // Get count of messages for a thread
    @Query("SELECT COUNT(*) FROM chat_messages WHERE threadId = :threadId AND syncDeleted = 0")
    suspend fun getMessageCount(threadId: Long): Int

    // Global search across all message bodies. Selects only a short snippet (not the
    // full content/images) so large rows can't overflow the CursorWindow.
    @Query(
        """
        SELECT m.id AS id, m.threadId AS threadId, m.role AS role,
               substr(m.content, 1, 200) AS snippet, m.timestamp AS timestamp,
               t.title AS threadTitle
        FROM chat_messages m
        JOIN chat_threads t ON t.id = m.threadId
        WHERE m.content LIKE :query AND m.syncDeleted = 0 AND t.syncDeleted = 0
        ORDER BY m.timestamp DESC
        LIMIT 100
        """
    )
    suspend fun searchMessages(query: String): List<MessageSearchResult>
    
    // Full query for sync operations - loads all messages but with error handling in repository
    @Query("SELECT * FROM chat_messages WHERE threadId = :threadId AND syncDeleted = 0 ORDER BY timestamp ASC")
    suspend fun getMessagesByThreadIdSync(threadId: Long): List<ChatMessageEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<ChatMessageEntity>)
    
    @Update
    suspend fun updateMessage(message: ChatMessageEntity)
    
    @Delete
    suspend fun deleteMessage(message: ChatMessageEntity)
    
    @Query("DELETE FROM chat_messages WHERE threadId = :threadId")
    suspend fun deleteMessagesByThreadId(threadId: Long)

    @Query("DELETE FROM chat_messages WHERE id = :messageId")
    suspend fun deleteMessageById(messageId: Long)

    @Query("DELETE FROM chat_messages WHERE threadId = :threadId AND timestamp >= :fromTimestamp")
    suspend fun deleteMessagesFrom(threadId: Long, fromTimestamp: Long)

    @Query("SELECT * FROM chat_messages WHERE syncDirty = 1")
    suspend fun getDirtyMessages(): List<ChatMessageEntity>

    @Query("SELECT * FROM chat_messages WHERE syncId = :syncId")
    suspend fun getMessageBySyncId(syncId: String): ChatMessageEntity?

    @Query("SELECT * FROM chat_messages WHERE syncId IS NULL")
    suspend fun getMessagesMissingSyncId(): List<ChatMessageEntity>

    @Query("UPDATE chat_messages SET syncId = :syncId, syncDirty = 1 WHERE id = :messageId")
    suspend fun setSyncId(messageId: Long, syncId: String)

    @Query("UPDATE chat_messages SET syncDirty = 0, syncUpdatedAt = :syncUpdatedAt, syncVersion = :syncVersion WHERE id = :messageId")
    suspend fun markSynced(messageId: Long, syncUpdatedAt: Long, syncVersion: Long)

    @Query("UPDATE chat_messages SET syncDirty = 1, syncVersion = syncVersion + 1, syncUpdatedAt = :syncUpdatedAt WHERE id = :messageId")
    suspend fun markDirty(messageId: Long, syncUpdatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE chat_messages SET syncDeleted = 1, syncDirty = 1, syncVersion = syncVersion + 1, syncUpdatedAt = :syncUpdatedAt WHERE id = :messageId")
    suspend fun markDeletedForSync(messageId: Long, syncUpdatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE chat_messages SET syncDeleted = 1, syncDirty = 1, syncVersion = syncVersion + 1, syncUpdatedAt = :syncUpdatedAt WHERE threadId = :threadId AND timestamp >= :fromTimestamp")
    suspend fun markDeletedFromForSync(threadId: Long, fromTimestamp: Long, syncUpdatedAt: Long = System.currentTimeMillis())
}
