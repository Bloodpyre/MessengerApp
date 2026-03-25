package com.example.messengerapp.data.models

data class MessageResponse(
    val sender: String,
    val encrypted_text: String,
    val message_id: String
)