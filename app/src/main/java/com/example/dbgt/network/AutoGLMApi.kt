package com.example.dbgt.network

import com.example.dbgt.network.dto.ChatRequest
import com.example.dbgt.network.dto.ChatResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AutoGLMApi {
    @POST("chat/completions")
    suspend fun chatCompletion(
        @Body request: ChatRequest
    ): Response<ChatResponse>
}
