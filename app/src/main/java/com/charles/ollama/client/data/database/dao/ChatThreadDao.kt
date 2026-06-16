package com.charles.ollama.client.data.database.dao

import androidx.room.*
import com.charles.ollama.client.data.database.entity.ChatThreadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatThreadDao {
    @Query("SELECT * FROM chat_threads WHERE syncDeleted = 0 ORDER BY isPinned DESC, updatedAt DESC")
    fun getAllThreads(): Flow<List<ChatThreadEntity>>

    @Query("SELECT * FROM chat_threads WHERE isArchived = :archived AND syncDeleted = 0 ORDER BY isPinned DESC, updatedAt DESC")
    fun getThreads(archived: Boolean): Flow<List<ChatThreadEntity>>

    @Query("SELECT * FROM chat_threads WHERE id = :threadId AND syncDeleted = 0")
    suspend fun getThreadById(threadId: Long): ChatThreadEntity?
    
    @Query("SELECT * FROM chat_threads WHERE id = :threadId AND syncDeleted = 0")
    fun getThreadByIdFlow(threadId: Long): Flow<ChatThreadEntity?>
    
    @Query("SELECT * FROM chat_threads WHERE (title LIKE :query OR model LIKE :query) AND isArchived = :archived AND syncDeleted = 0 ORDER BY isPinned DESC, updatedAt DESC")
    fun searchThreads(query: String, archived: Boolean): Flow<List<ChatThreadEntity>>

    @Query("UPDATE chat_threads SET isPinned = :pinned WHERE id = :threadId")
    suspend fun setPinned(threadId: Long, pinned: Boolean)

    @Query("UPDATE chat_threads SET isArchived = :archived WHERE id = :threadId")
    suspend fun setArchived(threadId: Long, archived: Boolean)

    @Query("UPDATE chat_threads SET label = :label WHERE id = :threadId")
    suspend fun setLabel(threadId: Long, label: String?)

    @Query("SELECT DISTINCT label FROM chat_threads WHERE label IS NOT NULL AND label != '' AND isArchived = 0 AND syncDeleted = 0 ORDER BY label")
    fun getLabels(): Flow<List<String>>

    @Query("UPDATE chat_threads SET systemPrompt = :systemPrompt, updatedAt = :updatedAt WHERE id = :threadId")
    suspend fun setSystemPrompt(threadId: Long, systemPrompt: String?, updatedAt: Long = System.currentTimeMillis())

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertThread(thread: ChatThreadEntity): Long
    
    @Update
    suspend fun updateThread(thread: ChatThreadEntity)
    
    @Delete
    suspend fun deleteThread(thread: ChatThreadEntity)
    
    @Query("DELETE FROM chat_threads WHERE id = :threadId")
    suspend fun deleteThreadById(threadId: Long)

    @Query("SELECT * FROM chat_threads WHERE syncDirty = 1")
    suspend fun getDirtyThreads(): List<ChatThreadEntity>

    @Query("SELECT * FROM chat_threads WHERE syncId = :syncId")
    suspend fun getThreadBySyncId(syncId: String): ChatThreadEntity?

    @Query("SELECT * FROM chat_threads WHERE syncId IS NULL")
    suspend fun getThreadsMissingSyncId(): List<ChatThreadEntity>

    @Query("SELECT model FROM chat_threads WHERE model IS NOT NULL AND syncDeleted = 0 ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getMostRecentModel(): String?

    @Query("UPDATE chat_threads SET syncId = :syncId, syncDirty = 1 WHERE id = :threadId")
    suspend fun setSyncId(threadId: Long, syncId: String)

    @Query("UPDATE chat_threads SET syncDirty = 0, syncUpdatedAt = :syncUpdatedAt, syncVersion = :syncVersion WHERE id = :threadId")
    suspend fun markSynced(threadId: Long, syncUpdatedAt: Long, syncVersion: Long)

    @Query("UPDATE chat_threads SET syncDirty = 1, syncVersion = syncVersion + 1, syncUpdatedAt = :syncUpdatedAt WHERE id = :threadId")
    suspend fun markDirty(threadId: Long, syncUpdatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE chat_threads SET syncDeleted = 1, syncDirty = 1, syncVersion = syncVersion + 1, syncUpdatedAt = :syncUpdatedAt WHERE id = :threadId")
    suspend fun markDeletedForSync(threadId: Long, syncUpdatedAt: Long = System.currentTimeMillis())
}
