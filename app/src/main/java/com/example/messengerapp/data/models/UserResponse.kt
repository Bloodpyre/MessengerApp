package com.example.messengerapp.data.models

data class UserResponse(
    val user_id: String,
    val username: String
)

data class PublicKeyResponse(
    val username: String,
    val public_key: String
)