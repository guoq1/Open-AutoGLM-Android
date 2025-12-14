package com.example.open_autoglm_android.network

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
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
    
    /**
     * 请求模型（使用消息上下文）
     */
    suspend fun request(
        messages: List<ChatMessage>,
        modelName: String,
        apiKey: String
    ): ModelResponse {
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
    
    /**
     * 创建系统消息
     */
    fun createSystemMessage(): ChatMessage {
        val systemPrompt = buildSystemPrompt()
        return ChatMessage(
            role = "system",
            content = listOf(ContentItem(type = "text", text = systemPrompt))
        )
    }
    
    /**
     * 创建用户消息（第一次调用，包含原始任务）
     */
    fun createUserMessage(userPrompt: String, screenshot: Bitmap?, currentApp: String?): ChatMessage {
        val userContent = mutableListOf<ContentItem>()
        val screenInfo = buildScreenInfo(currentApp)
        val textContent = "$userPrompt\n\n$screenInfo"
        userContent.add(ContentItem(type = "text", text = textContent))
        
        screenshot?.let { bitmap ->
            val base64Image = bitmapToBase64(bitmap)
            userContent.add(
                ContentItem(
                    type = "image_url",
                    imageUrl = ImageUrl(url = "data:image/jpeg;base64,$base64Image")
                )
            )
        }
        
        return ChatMessage(role = "user", content = userContent)
    }
    
    /**
     * 创建屏幕信息消息（后续调用，只包含屏幕信息）
     */
    fun createScreenInfoMessage(screenshot: Bitmap?, currentApp: String?): ChatMessage {
        val userContent = mutableListOf<ContentItem>()
        val screenInfo = buildScreenInfo(currentApp)
        val textContent = "** Screen Info **\n\n$screenInfo"
        userContent.add(ContentItem(type = "text", text = textContent))
        
        screenshot?.let { bitmap ->
            val base64Image = bitmapToBase64(bitmap)
            userContent.add(
                ContentItem(
                    type = "image_url",
                    imageUrl = ImageUrl(url = "data:image/jpeg;base64,$base64Image")
                )
            )
        }
        
        return ChatMessage(role = "user", content = userContent)
    }
    
    /**
     * 创建助手消息（添加到上下文）
     */
    fun createAssistantMessage(thinking: String, action: String): ChatMessage {
        val content = "<think>$thinking</think><answer>$action</answer>"
        return ChatMessage(
            role = "assistant",
            content = listOf(ContentItem(type = "text", text = content))
        )
    }
    
    /**
     * 构建屏幕信息
     */
    private fun buildScreenInfo(currentApp: String?): String {
        return if (currentApp != null) {
            "当前应用: $currentApp"
        } else {
            "当前应用: 未知"
        }
    }
    
    /**
     * 从消息中移除图片内容，只保留文本（节省 token）
     * 参考原项目的 MessageBuilder.remove_images_from_message
     */
    fun removeImagesFromMessage(message: ChatMessage): ChatMessage {
        val textOnlyContent = message.content.filter { it.type == "text" }
        return ChatMessage(
            role = message.role,
            content = textOnlyContent
        )
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
    
    
    private fun parseResponse(content: String): ModelResponse {
        Log.d("ModelClient", "解析响应内容: ${content.take(500)}")
        
        // 解析响应，提取思考过程和动作
        // 尝试多种标签格式
        val thinkingMatch = Regex("<(?:think|redacted_reasoning)>(.*?)</(?:think|redacted_reasoning)>", RegexOption.DOT_MATCHES_ALL)
            .find(content)
        val thinking = thinkingMatch?.groupValues?.get(1)?.trim() ?: ""
        
        // 尝试从 <answer> 标签中提取
        val answerMatch = Regex("<answer>(.*?)</answer>", RegexOption.DOT_MATCHES_ALL)
            .find(content)
        var action = answerMatch?.groupValues?.get(1)?.trim() ?: ""
        
        Log.d("ModelClient", "从 answer 标签提取: $action")
        
        // 如果没有 <answer> 标签，尝试从文本中提取 JSON 或 do(...) 格式
        if (action.isEmpty() || !action.trim().startsWith("{")) {
            // 首先尝试提取 do(...) 或 finish(...) 格式
            val functionCallPattern = Regex("""(do|finish)\s*\([^)]+\)""", RegexOption.IGNORE_CASE)
            val functionMatch = functionCallPattern.find(content)
            if (functionMatch != null) {
                action = functionMatch.value
                Log.d("ModelClient", "从内容中提取函数调用: $action")
            } else {
                // 如果没有找到函数调用，尝试提取 JSON
                val extractedJson = extractJsonFromContent(content)
                if (extractedJson.isNotEmpty()) {
                    action = extractedJson
                    Log.d("ModelClient", "从内容中提取 JSON: $action")
                }
            }
        }
        
        // 如果还是找不到，使用整个内容
        if (action.isEmpty()) {
            action = content.trim()
            Log.w("ModelClient", "未找到 JSON 或函数调用，使用整个内容")
        }
        
        return ModelResponse(thinking = thinking, action = action)
    }
    
    /**
     * 从内容中提取 JSON 对象
     */
    private fun extractJsonFromContent(content: String): String {
        // 尝试找到 JSON 对象（匹配嵌套的大括号）
        var startIndex = -1
        var braceCount = 0
        val candidates = mutableListOf<String>()
        
        for (i in content.indices) {
            when (content[i]) {
                '{' -> {
                    if (startIndex == -1) {
                        startIndex = i
                    }
                    braceCount++
                }
                '}' -> {
                    braceCount--
                    if (braceCount == 0 && startIndex != -1) {
                        val candidate = content.substring(startIndex, i + 1)
                        try {
                            // 验证是否是有效的 JSON
                            com.google.gson.JsonParser.parseString(candidate)
                            candidates.add(candidate)
                        } catch (e: Exception) {
                            // 不是有效 JSON，继续查找
                        }
                        startIndex = -1
                    }
                }
            }
        }
        
        // 返回第一个有效的 JSON 对象
        return candidates.firstOrNull() ?: ""
    }
    
    private fun String.ensureTrailingSlash(): String {
        return if (this.endsWith("/")) this else "$this/"
    }
}
