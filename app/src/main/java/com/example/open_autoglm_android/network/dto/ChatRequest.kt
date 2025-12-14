package com.example.open_autoglm_android.network.dto

import com.google.gson.annotations.SerializedName

data class ChatMessage(
    val role: String,
    val content: List<ContentItem>
)

data class ContentItem(
    val type: String,
    val text: String? = null,
    @SerializedName("image_url")
    val imageUrl: ImageUrl? = null
)

data class ImageUrl(
    val url: String
)

data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    @SerializedName("max_tokens")
    val maxTokens: Int = 3000,
    val temperature: Double = 0.1,
    @SerializedName("frequency_penalty")
    val frequencyPenalty: Double = 0.2
)
