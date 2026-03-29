package com.example.messengerapp.data.network

import com.example.messengerapp.data.models.*
import retrofit2.http.*

interface ApiService {

    @GET("/")
    suspend fun checkServer(): Map<String, String>

    @POST("/register")
    suspend fun register(@Body user: UserRegister): Map<String, String>

    @POST("/login")
    suspend fun login(@Body user: UserLogin): Map<String, String>

    @GET("/users")
    suspend fun getUsers(): List<UserResponse>

    @GET("/users/{username}/public_key")
    suspend fun getPublicKey(@Path("username") username: String): PublicKeyResponse

    @POST("/messages")
    suspend fun sendMessage(@Body message: MessageSend): Map<String, String>

    @GET("/messages/{username}")
    suspend fun getMessages(@Path("username") username: String): List<MessageResponse>
}