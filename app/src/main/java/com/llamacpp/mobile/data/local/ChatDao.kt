package com.llamacpp.mobile.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

    @Query("SELECT * FROM conversations WHERE serverId = :serverId ORDER BY updatedAt DESC")
    fun conversations(serverId: String): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getConversation(id: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConversation(entity: ConversationEntity)

    @Query("UPDATE conversations SET title = :title, updatedAt = :now WHERE id = :id")
    suspend fun renameConversation(id: String, title: String, now: Long)

    @Query("UPDATE conversations SET model = :model, updatedAt = :now WHERE id = :id")
    suspend fun setModel(id: String, model: String?, now: Long)

    @Query("UPDATE conversations SET systemPrompt = :prompt, updatedAt = :now WHERE id = :id")
    suspend fun setSystemPrompt(id: String, prompt: String, now: Long)

    @Query("UPDATE conversations SET updatedAt = :now WHERE id = :id")
    suspend fun touch(id: String, now: Long)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteConversation(id: String)

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY id ASC")
    fun messages(conversationId: String): Flow<List<MessageEntity>>

    @Insert
    suspend fun insertMessage(entity: MessageEntity): Long

    @Update
    suspend fun updateMessage(entity: MessageEntity)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteMessage(id: Long)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId AND id >= :fromId")
    suspend fun deleteMessagesFrom(conversationId: String, fromId: Long)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteMessages(conversationId: String)

    /** Deletes a conversation and its messages atomically so a kill mid-way cannot orphan rows. */
    @Transaction
    suspend fun deleteConversationCascade(id: String) {
        deleteMessages(id)
        deleteConversation(id)
    }

    /** Inserts a message and bumps the conversation's `updatedAt` in one transaction. */
    @Transaction
    suspend fun insertMessageAndTouch(message: MessageEntity, now: Long): Long {
        val rowId = insertMessage(message)
        touch(message.conversationId, now)
        return rowId
    }
}
