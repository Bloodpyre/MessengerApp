package com.example.messengerapp.data.models

data class MessageSend(
    val recipient: String,
    val encrypted_text: String,
    val sender: String
)