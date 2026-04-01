package com.example.messengerapp.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(chat: ChatEntity)

    @Query("SELECT * FROM chats ORDER BY lastMessageTime DESC")
    fun getAllFlow(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats ORDER BY lastMessageTime DESC")
    suspend fun getAll(): List<ChatEntity>

    @Query("SELECT * FROM chats WHERE partnerName = :partnerName")
    suspend fun getChat(partnerName: String): ChatEntity?

    @Query("DELETE FROM chats WHERE partnerName = :partnerName")
    suspend fun deleteChat(partnerName: String)

    @Query("DELETE FROM chats")
    suspend fun clearAll()
}

@Dao
interface ReadMessageDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(message: ReadMessageEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM read_messages WHERE messageId = :messageId)")
    suspend fun isMessageRead(messageId: String): Boolean

    @Query("DELETE FROM read_messages WHERE chatPartner = :chatPartner")
    suspend fun clearForChat(chatPartner: String)

    @Query("DELETE FROM read_messages")
    suspend fun clearAll()
}