package com.example.messengerapp.data.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    // Для эмулятора Android
    private const val BASE_URL = "https://messangerapp-bloodpyre.amvera.io/"
    // Для реального устройства: замени на IP компьютера
    // private const val BASE_URL = "http://192.168.1.xxx:8000/"

    val instance: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}