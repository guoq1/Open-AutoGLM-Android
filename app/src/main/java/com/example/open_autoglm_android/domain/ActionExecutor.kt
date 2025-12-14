package com.example.open_autoglm_android.domain

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import com.example.open_autoglm_android.service.AutoGLMAccessibilityService
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.delay

data class ExecuteResult(
    val success: Boolean,
    val message: String? = null
)

class ActionExecutor(private val service: AutoGLMAccessibilityService) {
    
    suspend fun execute(actionJson: String, screenWidth: Int, screenHeight: Int): ExecuteResult {
        return try {
            val jsonElement = JsonParser.parseString(actionJson)
            val actionObj = jsonElement.asJsonObject
            
            val metadata = actionObj.get("_metadata")?.asString ?: ""
            
            when (metadata) {
                "finish" -> {
                    val message = actionObj.get("message")?.asString ?: "任务完成"
                    ExecuteResult(success = true, message = message)
                }
                "do" -> {
                    val action = actionObj.get("action")?.asString ?: ""
                    executeAction(action, actionObj, screenWidth, screenHeight)
                }
                else -> {
                    ExecuteResult(success = false, message = "未知的动作类型: $metadata")
                }
            }
        } catch (e: Exception) {
            ExecuteResult(success = false, message = "解析动作失败: ${e.message}")
        }
    }
    
    private suspend fun executeAction(
        action: String,
        actionObj: JsonObject,
        screenWidth: Int,
        screenHeight: Int
    ): ExecuteResult {
        return when (action.lowercase()) {
            "launch" -> launchApp(actionObj)
            "tap" -> tap(actionObj, screenWidth, screenHeight)
            "type" -> type(actionObj)
            "swipe" -> swipe(actionObj, screenWidth, screenHeight)
            "back" -> back()
            "home" -> home()
            "longpress", "long press" -> longPress(actionObj, screenWidth, screenHeight)
            "doubletap", "double tap" -> doubleTap(actionObj, screenWidth, screenHeight)
            "wait" -> wait(actionObj)
            else -> ExecuteResult(success = false, message = "不支持的操作: $action")
        }
    }
    
    private suspend fun launchApp(actionObj: JsonObject): ExecuteResult {
        val appName = actionObj.get("app")?.asString ?: return ExecuteResult(
            success = false,
            message = "Launch 操作缺少 app 参数"
        )
        
        return try {
            val packageManager = service.packageManager
            val intent = packageManager.getLaunchIntentForPackage(getPackageName(appName))
                ?: return ExecuteResult(success = false, message = "找不到应用: $appName")
            
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            service.startActivity(intent)
            delay(2000) // 等待应用启动
            ExecuteResult(success = true)
        } catch (e: Exception) {
            ExecuteResult(success = false, message = "启动应用失败: ${e.message}")
        }
    }
    
    private suspend fun tap(actionObj: JsonObject, screenWidth: Int, screenHeight: Int): ExecuteResult {
        val element = actionObj.get("element")
        
        return if (element?.isJsonArray == true) {
            // 坐标形式 [x, y]
            val array = element.asJsonArray
            if (array.size() >= 2) {
                val x = array[0].asFloat
                val y = array[1].asFloat
                service.tap(x, y)
                delay(500)
                ExecuteResult(success = true)
            } else {
                ExecuteResult(success = false, message = "坐标格式错误")
            }
        } else {
            // 尝试通过文本查找元素
            val text = actionObj.get("text")?.asString
            if (text != null) {
                val node = service.findNodeByText(text)
                if (node != null) {
                    val success = service.performClick(node)
                    node.recycle()
                    delay(500)
                    ExecuteResult(success = success)
                } else {
                    ExecuteResult(success = false, message = "找不到元素: $text")
                }
            } else {
                ExecuteResult(success = false, message = "Tap 操作缺少 element 或 text 参数")
            }
        }
    }
    
    private suspend fun type(actionObj: JsonObject): ExecuteResult {
        val text = actionObj.get("text")?.asString ?: return ExecuteResult(
            success = false,
            message = "Type 操作缺少 text 参数"
        )
        
        // 尝试查找输入框
        val root = service.getRootNode() ?: return ExecuteResult(
            success = false,
            message = "无法获取根节点"
        )
        
        // 查找可编辑的节点 - 使用递归查找
        val inputNode = findEditableNode(root)
        
        if (inputNode != null) {
            val success = service.setText(inputNode, text)
            inputNode.recycle()
            delay(500)
            root.recycle()
            return ExecuteResult(success = success)
        }
        
        root.recycle()
        return ExecuteResult(success = false, message = "找不到输入框")
    }
    
    private suspend fun swipe(actionObj: JsonObject, screenWidth: Int, screenHeight: Int): ExecuteResult {
        val start = actionObj.get("start")?.asJsonArray
        val end = actionObj.get("end")?.asJsonArray
        
        if (start == null || end == null || start.size() < 2 || end.size() < 2) {
            return ExecuteResult(success = false, message = "Swipe 操作缺少 start 或 end 参数")
        }
        
        val startX = start[0].asFloat
        val startY = start[1].asFloat
        val endX = end[0].asFloat
        val endY = end[1].asFloat
        
        service.swipe(startX, startY, endX, endY)
        delay(500)
        return ExecuteResult(success = true)
    }
    
    private suspend fun back(): ExecuteResult {
        service.performBack()
        delay(500)
        return ExecuteResult(success = true)
    }
    
    private suspend fun home(): ExecuteResult {
        service.performHome()
        delay(500)
        return ExecuteResult(success = true)
    }
    
    private suspend fun longPress(actionObj: JsonObject, screenWidth: Int, screenHeight: Int): ExecuteResult {
        val element = actionObj.get("element")?.asJsonArray
        
        if (element == null || element.size() < 2) {
            return ExecuteResult(success = false, message = "LongPress 操作缺少 element 参数")
        }
        
        val x = element[0].asFloat
        val y = element[1].asFloat
        service.longPress(x, y)
        delay(800)
        return ExecuteResult(success = true)
    }
    
    private suspend fun doubleTap(actionObj: JsonObject, screenWidth: Int, screenHeight: Int): ExecuteResult {
        val element = actionObj.get("element")?.asJsonArray
        
        if (element == null || element.size() < 2) {
            return ExecuteResult(success = false, message = "DoubleTap 操作缺少 element 参数")
        }
        
        val x = element[0].asFloat
        val y = element[1].asFloat
        
        // 双击就是连续两次点击
        service.tap(x, y)
        delay(100)
        service.tap(x, y)
        delay(500)
        return ExecuteResult(success = true)
    }
    
    private suspend fun wait(actionObj: JsonObject): ExecuteResult {
        val duration = actionObj.get("duration")?.asInt ?: 1000
        delay(duration.toLong())
        return ExecuteResult(success = true)
    }
    
    private fun getPackageName(appName: String): String {
        // 简单的应用名到包名映射，可以后续扩展
        val appPackageMap = mapOf(
            "微信" to "com.tencent.mm",
            "淘宝" to "com.taobao.taobao",
            "京东" to "com.jingdong.app.mall",
            "美团" to "com.sankuai.meituan",
            "小红书" to "com.xingin.xhs",
            "抖音" to "com.ss.android.ugc.aweme",
            "bilibili" to "tv.danmaku.bili",
            "知乎" to "com.zhihu.android"
        )
        return appPackageMap[appName] ?: appName
    }
    
    private fun findEditableNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            if (root.isEditable) {
                return root
            }
        }
        
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val editable = findEditableNode(child)
            if (editable != null) {
                child.recycle()
                return editable
            }
            child.recycle()
        }
        
        return null
    }
}

