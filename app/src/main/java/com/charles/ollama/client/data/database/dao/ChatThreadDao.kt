package com.charles.ollama.client.data.database.dao

import androidx.room.*
import com.charles.ollama.client.data.database.entity.ChatThreadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatThreadDao {
    @Query("SELECT * FROM chat_threads ORDER BY updatedAt DESC")
    fun getAllThreads(): Flow<List<ChatThreadEntity>>
    
    @Query("SELECT * FROM chat_threads WHERE id = :threadId")
    suspend fun getThreadById(threadId: Long): ChatThreadEntity?
    
    @Query("SELECT * FROM chat_threads WHERE id = :threadId")
    fun getThreadByIdFlow(threadId: Long): Flow<ChatThreadEntity?>
    
    @Query("SELECT * FROM chat_threads WHERE title LIKE :query OR model LIKE :query ORDER BY updatedAt DESC")
    fun searchThreads(query: String): Flow<List<ChatThreadEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertThread(thread: ChatThreadEntity): Long
    
    @Update
    suspend fun updateThread(thread: ChatThreadEntity)
    
    @Delete
    suspend fun deleteThread(thread: ChatThreadEntity)
    
    @Query("DELETE FROM chat_threads WHERE id = :threadId")
    suspend fun deleteThreadById(threadId: Long)
}

