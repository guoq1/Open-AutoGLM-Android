package com.example.open_autoglm_android.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.open_autoglm_android.data.PreferencesRepository
import com.example.open_autoglm_android.domain.ActionExecutor
import com.example.open_autoglm_android.network.ModelClient
import com.example.open_autoglm_android.service.AutoGLMAccessibilityService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

data class ChatMessage(
    val id: String,
    val role: MessageRole,
    val content: String,
    val thinking: String? = null,
    val action: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

enum class MessageRole {
    USER, ASSISTANT
}

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentApp: String? = null
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    
    private val preferencesRepository = PreferencesRepository(application)
    
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    
    private var modelClient: ModelClient? = null
    private var actionExecutor: ActionExecutor? = null
    
    init {
        viewModelScope.launch {
            // 初始化 ModelClient
            val baseUrl = preferencesRepository.getBaseUrlSync()
            val apiKey = preferencesRepository.getApiKeySync() ?: "EMPTY"
            modelClient = ModelClient(baseUrl, apiKey)
            
            // 初始化 ActionExecutor
            AutoGLMAccessibilityService.getInstance()?.let { service ->
                actionExecutor = ActionExecutor(service)
            }
            
            // 监听当前应用变化
            launch {
                AutoGLMAccessibilityService.getInstance()?.currentApp?.collect { app ->
                    _uiState.value = _uiState.value.copy(currentApp = app)
                }
            }
        }
    }
    
    fun sendMessage(userInput: String) {
        if (userInput.isBlank() || _uiState.value.isLoading) return
        
        val accessibilityService = AutoGLMAccessibilityService.getInstance()
        if (accessibilityService == null) {
            _uiState.value = _uiState.value.copy(
                error = "无障碍服务未启用，请前往设置开启"
            )
            return
        }
        
        val userMessage = ChatMessage(
            id = System.currentTimeMillis().toString(),
            role = MessageRole.USER,
            content = userInput
        )
        
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + userMessage,
            isLoading = true,
            error = null
        )
        
        viewModelScope.launch {
            try {
                // 重新初始化 ModelClient（以防配置变化）
                val baseUrl = preferencesRepository.getBaseUrlSync()
                val apiKey = preferencesRepository.getApiKeySync() ?: "EMPTY"
                val modelName = preferencesRepository.getModelNameSync()
                
                if (apiKey == null || apiKey.isEmpty() || apiKey == "EMPTY") {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "请先在设置页面配置 API Key"
                    )
                    return@launch
                }
                
                modelClient = ModelClient(baseUrl, apiKey)
                actionExecutor = ActionExecutor(accessibilityService)
                
                // 执行任务循环
                executeTaskLoop(userInput, modelName)
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "错误: ${e.message}"
                )
            }
        }
    }
    
    private suspend fun executeTaskLoop(userPrompt: String, modelName: String) {
        val accessibilityService = AutoGLMAccessibilityService.getInstance() ?: return
        val client = modelClient ?: return
        val executor = actionExecutor ?: return
        
        var currentPrompt = userPrompt
        var stepCount = 0
        val maxSteps = 50
        
        while (stepCount < maxSteps) {
            // 截图
            var screenshot: Bitmap? = null
            accessibilityService.takeScreenshot { bitmap ->
                screenshot = bitmap
            }
            
            // 等待截图完成
            var waitCount = 0
            while (screenshot == null && waitCount < 50) {
                delay(100)
                waitCount++
            }
            
            if (screenshot == null) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "无法获取屏幕截图"
                )
                return
            }
            
            // 获取当前应用
            val currentApp = accessibilityService.currentApp.value
            
            // 调用模型
            val response = client.request(
                userPrompt = if (stepCount == 0) currentPrompt else "继续执行任务",
                screenshot = screenshot,
                modelName = modelName,
                apiKey = preferencesRepository.getApiKeySync() ?: "EMPTY",
                currentApp = currentApp
            )
            
            // 添加助手消息
            val assistantMessage = ChatMessage(
                id = "${System.currentTimeMillis()}_$stepCount",
                role = MessageRole.ASSISTANT,
                content = response.action,
                thinking = response.thinking,
                action = response.action
            )
            
            _uiState.value = _uiState.value.copy(
                messages = _uiState.value.messages + assistantMessage
            )
            
            // 解析并执行动作
            val result = executor.execute(
                response.action,
                screenshot!!.width,
                screenshot!!.height
            )
            
            val isFinished = result.message != null && (result.message!!.contains("完成") || 
                result.message!!.contains("finish")) || 
                response.action.contains("\"_metadata\":\"finish\"") ||
                response.action.contains("\"_metadata\": \"finish\"")
            
            if (isFinished) {
                // 任务完成
                _uiState.value = _uiState.value.copy(isLoading = false)
                return
            }
            
            if (!result.success) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = result.message ?: "执行动作失败"
                )
                return
            }
            
            // 等待界面稳定
            delay(1000)
            stepCount++
        }
        
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            error = "达到最大步数限制"
        )
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    fun refreshModelClient() {
        viewModelScope.launch {
            val baseUrl = preferencesRepository.getBaseUrlSync()
            val apiKey = preferencesRepository.getApiKeySync() ?: "EMPTY"
            modelClient = ModelClient(baseUrl, apiKey)
        }
    }
}
