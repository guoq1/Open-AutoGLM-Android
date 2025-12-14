package com.example.open_autoglm_android.network

import android.graphics.Bitmap
import android.util.Base64
import com.example.open_autoglm_android.network.dto.*
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

data class ModelResponse(
    val thinking: String,
    val action: String
)

class ModelClient(
    baseUrl: String,
    apiKey: String
) {
    private val api: AutoGLMApi
    
    init {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        
        val client = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
        
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl.ensureTrailingSlash())
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        
        api = retrofit.create(AutoGLMApi::class.java)
    }
    
    suspend fun request(
        userPrompt: String,
        screenshot: Bitmap?,
        modelName: String,
        apiKey: String,
        currentApp: String? = null
    ): ModelResponse {
        val messages = mutableListOf<ChatMessage>()
        
        // System message
        val systemPrompt = buildSystemPrompt()
        messages.add(
            ChatMessage(
                role = "system",
                content = listOf(ContentItem(type = "text", text = systemPrompt))
            )
        )
        
        // User message with screenshot
        val userContent = mutableListOf<ContentItem>()
        userContent.add(ContentItem(type = "text", text = buildUserMessage(userPrompt, currentApp)))
        
        screenshot?.let { bitmap ->
            val base64Image = bitmapToBase64(bitmap)
            userContent.add(
                ContentItem(
                    type = "image_url",
                    imageUrl = ImageUrl(url = "data:image/jpeg;base64,$base64Image")
                )
            )
        }
        
        messages.add(ChatMessage(role = "user", content = userContent))
        
        val request = ChatRequest(
            model = modelName,
            messages = messages,
            maxTokens = 3000,
            temperature = 0.1,
            frequencyPenalty = 0.2
        )
        
        val authHeader = if (apiKey.isNotEmpty() && apiKey != "EMPTY") {
            "Bearer $apiKey"
        } else {
            "Bearer EMPTY"
        }
        
        val response = api.chatCompletion(authHeader, request)
        
        if (response.isSuccessful && response.body() != null) {
            val responseBody = response.body()!!
            val content = responseBody.choices.firstOrNull()?.message?.content ?: ""
            return parseResponse(content)
        } else {
            throw Exception("API request failed: ${response.code()} ${response.message()}")
        }
    }
    
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        // 压缩图片以减少传输大小
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }
    
    private fun buildSystemPrompt(): String {
        // 这里使用与 Python 版本类似的系统提示词
        return """你是一个手机助手，能够理解用户的自然语言指令并执行相应的操作。

可用操作：
- Launch: 启动应用
- Tap: 点击屏幕坐标 [x, y]
- Type: 输入文本
- Swipe: 滑动屏幕
- Back: 返回上一页
- Home: 返回桌面
- Long Press: 长按
- Double Tap: 双击
- Wait: 等待
- finish: 完成任务

请以 JSON 格式返回，格式：
{"_metadata": "do", "action": "操作名", ...} 或 {"_metadata": "finish", "message": "完成信息"}

在 <think> 标签中提供你的思考过程，在 <answer> 标签中提供动作 JSON。"""
    }
    
    private fun buildUserMessage(userPrompt: String, currentApp: String?): String {
        return if (currentApp != null) {
            "$userPrompt\n\n当前应用: $currentApp"
        } else {
            userPrompt
        }
    }
    
    private fun parseResponse(content: String): ModelResponse {
        // 解析响应，提取思考过程和动作
        val thinkingMatch = Regex("<think>(.*?)</think>", RegexOption.DOT_MATCHES_ALL)
            .find(content)
        val thinking = thinkingMatch?.groupValues?.get(1)?.trim() ?: ""
        
        val answerMatch = Regex("<answer>(.*?)</answer>", RegexOption.DOT_MATCHES_ALL)
            .find(content)
        val action = answerMatch?.groupValues?.get(1)?.trim() ?: content.trim()
        
        return ModelResponse(thinking = thinking, action = action)
    }
    
    private fun String.ensureTrailingSlash(): String {
        return if (this.endsWith("/")) this else "$this/"
    }
}
