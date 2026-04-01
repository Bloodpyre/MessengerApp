package com.example.messengerapp.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey
    val partnerName: String,
    val lastMessage: String,
    val lastMessageTime: Long,
    val unreadCount: Int
)