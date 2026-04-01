package com.example.messengerapp.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "read_messages")
data class ReadMessageEntity(
    @PrimaryKey
    val messageId: String,
    val chatPartner: String,
    val readAt: Long = System.currentTimeMillis()
)