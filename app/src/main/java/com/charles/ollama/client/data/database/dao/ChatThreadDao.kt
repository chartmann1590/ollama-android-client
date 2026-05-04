package com.charles.ollama.client.data.database.dao

import androidx.room.*
import com.charles.ollama.client.data.database.entity.ChatThreadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatThreadDao {
    @Query("SELECT * FROM chat_threads ORDER BY isPinned DESC, updatedAt DESC")
    fun getAllThreads(): Flow<List<ChatThreadEntity>>

    @Query("SELECT * FROM chat_threads WHERE isArchived = :archived ORDER BY isPinned DESC, updatedAt DESC")
    fun getThreads(archived: Boolean): Flow<List<ChatThreadEntity>>

    @Query("SELECT * FROM chat_threads WHERE id = :threadId")
    suspend fun getThreadById(threadId: Long): ChatThreadEntity?
    
    @Query("SELECT * FROM chat_threads WHERE id = :threadId")
    fun getThreadByIdFlow(threadId: Long): Flow<ChatThreadEntity?>
    
    @Query("SELECT * FROM chat_threads WHERE (title LIKE :query OR model LIKE :query) AND isArchived = :archived ORDER BY isPinned DESC, updatedAt DESC")
    fun searchThreads(query: String, archived: Boolean): Flow<List<ChatThreadEntity>>

    @Query("UPDATE chat_threads SET isPinned = :pinned WHERE id = :threadId")
    suspend fun setPinned(threadId: Long, pinned: Boolean)

    @Query("UPDATE chat_threads SET isArchived = :archived WHERE id = :threadId")
    suspend fun setArchived(threadId: Long, archived: Boolean)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertThread(thread: ChatThreadEntity): Long
    
    @Update
    suspend fun updateThread(thread: ChatThreadEntity)
    
    @Delete
    suspend fun deleteThread(thread: ChatThreadEntity)
    
    @Query("DELETE FROM chat_threads WHERE id = :threadId")
    suspend fun deleteThreadById(threadId: Long)
}

