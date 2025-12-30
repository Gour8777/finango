package com.gourav.finango.ui.chatbot

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface ChatApi {
    @POST("chatbot")
    suspend fun send(
        @Header("Authorization") bearer: String, // ✅ "Bearer <token>"
        @Header("Content-Type") contentType: String = "application/json",
        @Body body: ChatRequest
    ): ChatResponse

    companion object {
        fun create(baseUrl: String): ChatApi {
            val log = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            val client = OkHttpClient.Builder()
                .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .addInterceptor(log)
                .build()

            return Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ChatApi::class.java)
        }
    }
}