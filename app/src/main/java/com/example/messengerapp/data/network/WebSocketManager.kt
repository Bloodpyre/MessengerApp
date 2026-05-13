package com.example.messengerapp.data.network

import android.util.Log
import com.google.gson.Gson
import okhttp3.*
import java.util.concurrent.TimeUnit

class WebSocketManager private constructor() {

    companion object {
        @Volatile
        private var INSTANCE: WebSocketManager? = null

        fun getInstance(): WebSocketManager {
            return INSTANCE ?: synchronized(this) {
                val instance = WebSocketManager()
                INSTANCE = instance
                instance
            }
        }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var currentUsername: String? = null
    private var messageListener: ((sender: String, encryptedText: String) -> Unit)? = null
    private var isConnected = false

    fun connect(
        username: String,
        onMessage: (sender: String, encryptedText: String) -> Unit,
        onConnect: () -> Unit = {},
        onDisconnect: () -> Unit = {}
    ) {
        if (isConnected && currentUsername == username) {
            Log.d("WebSocket", "Уже подключен как $username")
            return
        }

        disconnect()

        currentUsername = username
        messageListener = onMessage

        val request = Request.Builder()
            .url("wss://messangerapp-bloodpyre.amvera.io/ws/$username")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnected = true
                Log.d("WebSocket", "✅ Подключен как $username")
                onConnect()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d("WebSocket", "📨 Получено: $text")
                try {
                    val json = Gson().fromJson(text, Map::class.java)
                    val sender = json["sender"] as? String ?: return
                    val encryptedText = json["encrypted_text"] as? String ?: return
                    messageListener?.invoke(sender, encryptedText)
                } catch (e: Exception) {
                    Log.e("WebSocket", "Ошибка парсинга: ${e.message}")
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                isConnected = false
                Log.d("WebSocket", "Закрытие: $reason")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isConnected = false
                Log.e("WebSocket", "❌ Ошибка: ${t.message}")
                onDisconnect()
            }
        })
    }

    fun sendMessage(recipient: String, encryptedText: String) {
        if (!isConnected) {
            Log.e("WebSocket", "Не подключен, сообщение не отправлено")
            return
        }

        val message = mapOf(
            "recipient" to recipient,
            "encrypted_text" to encryptedText
        )
        val json = Gson().toJson(message)
        webSocket?.send(json)
        Log.d("WebSocket", "📤 Отправлено: $json")
    }

    fun disconnect() {
        webSocket?.close(1000, "Normal closure")
        webSocket = null
        isConnected = false
        currentUsername = null
        messageListener = null
        Log.d("WebSocket", "🔌 Отключен")
    }

    fun isConnected(): Boolean = isConnected
}