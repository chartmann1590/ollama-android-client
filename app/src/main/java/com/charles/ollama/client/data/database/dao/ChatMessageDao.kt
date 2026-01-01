package com.charles.ollama.client.data.database.dao

import androidx.room.*
import com.charles.ollama.client.data.database.entity.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages WHERE threadId = :threadId ORDER BY timestamp ASC")
    fun getMessagesByThreadId(threadId: Long): Flow<List<ChatMessageEntity>>
    
    // Load messages in batches to avoid CursorWindow overflow
    // This query loads messages with a limit to prevent loading too much data at once
    @Query("SELECT * FROM chat_messages WHERE threadId = :threadId ORDER BY timestamp ASC LIMIT :limit OFFSET :offset")
    suspend fun getMessagesByThreadIdPaged(threadId: Long, limit: Int, offset: Int): List<ChatMessageEntity>
    
    // Load message metadata without large content fields (for messages that are too large)
    // Note: images are excluded to avoid CursorWindow overflow - will be loaded separately if needed
    @Query("SELECT id, threadId, role, '' as content, null as thinking, null as images, timestamp FROM chat_messages WHERE threadId = :threadId ORDER BY timestamp ASC LIMIT :limit OFFSET :offset")
    suspend fun getMessagesByThreadIdPagedMetadata(threadId: Long, limit: Int, offset: Int): List<ChatMessageEntity>
    
    // Load message IDs and basic info only (for messages that are too large even for metadata)
    @Query("SELECT id, threadId, role, '' as content, null as thinking, null as images, timestamp FROM chat_messages WHERE threadId = :threadId ORDER BY timestamp ASC LIMIT :limit OFFSET :offset")
    suspend fun getMessagesByThreadIdPagedBasic(threadId: Long, limit: Int, offset: Int): List<ChatMessageEntity>
    
    // Get a single message by ID (for loading large content separately)
    @Query("SELECT * FROM chat_messages WHERE id = :messageId")
    suspend fun getMessageById(messageId: Long): ChatMessageEntity?
    
    // Get count of messages for a thread
    @Query("SELECT COUNT(*) FROM chat_messages WHERE threadId = :threadId")
    suspend fun getMessageCount(threadId: Long): Int
    
    // Full query for sync operations - loads all messages but with error handling in repository
    @Query("SELECT * FROM chat_messages WHERE threadId = :threadId ORDER BY timestamp ASC")
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
}

