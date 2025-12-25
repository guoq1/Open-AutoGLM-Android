package com.example.dbgt.ui.screen

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dbgt.data.InputMode
import com.example.dbgt.ui.viewmodel.SettingsViewModel
import com.example.dbgt.util.AuthHelper
import kotlin.math.roundToInt

/**
 * 带有超链接的文本组件
 * @param fullText 完整文本
 * @param linkText 需要设置为超链接的文本
 * @param linkUrl 点击链接时打开的URL
 * @param modifier 修饰符
 */
@Composable
fun HyperlinkText(
    fullText: String,
    linkText: String,
    linkUrl: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // 创建可变的AnnotatedString
    val annotatedString = buildAnnotatedString {
        append(fullText)
        
        // 查找链接文本的位置并添加样式
        val startIndex = fullText.indexOf(linkText)
        if (startIndex != -1) {
            addStyle(
                style = SpanStyle(
                    color = MaterialTheme.colorScheme.primary, // 使用主题色
                    textDecoration = TextDecoration.Underline,
                    fontWeight = FontWeight.Bold
                ),
                start = startIndex,
                end = startIndex + linkText.length
            )
            
            // 添加URL点击处理
            addStringAnnotation(
                tag = "URL",
                annotation = linkUrl,
                start = startIndex,
                end = startIndex + linkText.length
            )
        }
    }
    
    ClickableText(
        text = annotatedString,
        style = MaterialTheme.typography.bodySmall.copy(
            color = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        modifier = modifier
    ) { offset ->
        // 检查点击位置是否在链接上
        annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset).firstOrNull()?.let {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(it.item))
            context.startActivity(intent)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(),
    onNavigateToAdvancedAuth: () -> Unit,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val hasWriteSecureSettings = remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(Unit) {
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasWriteSecureSettings.value = AuthHelper.hasWriteSecureSettingsPermission(context)
                viewModel.checkAccessibilityService()
                viewModel.checkOverlayPermission()
                viewModel.checkImeStatus()
                viewModel.checkBatteryOptimizationStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "返回", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 无障碍服务状态
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (uiState.isAccessibilityEnabled) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    }
                ),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "无障碍服务", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = if (uiState.isAccessibilityEnabled) "已启用" else "未启用 - 点击前往设置",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (!uiState.isAccessibilityEnabled) {
                            Button(onClick = {
                                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            },
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )) { Text("前往设置") }
                        }
                    }
                }
            }

            // 悬浮窗设置
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (uiState.floatingWindowEnabled && uiState.hasOverlayPermission) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                ),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "悬浮窗", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = if (!uiState.hasOverlayPermission) "需要悬浮窗权限" else if (uiState.floatingWindowEnabled) "已启用" else "未启用",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (!uiState.hasOverlayPermission) {
                            Button(onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(intent)
                                }
                            },
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )) { Text("授权") }
                        } else {
                            Switch(
                                checked = uiState.floatingWindowEnabled,
                                onCheckedChange = { viewModel.setFloatingWindowEnabled(it) })
                        }
                    }
                }
            }



            // 电池优化设置
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (uiState.isIgnoringBatteryOptimizations) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    }
                ),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "电池优化", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = if (uiState.isIgnoringBatteryOptimizations) "已忽略电池优化" else "电池优化生效中 - 点击前往设置",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (!uiState.isIgnoringBatteryOptimizations) {
                            Button(onClick = {
                                try {
                                    // 尝试直接使用 ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS 弹出系统对话框
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                            data = android.net.Uri.parse("package:${context.packageName}")
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        if (intent.resolveActivity(context.packageManager) != null) {
                                            context.startActivity(intent)
                                        } else {
                                            // 如果系统意图不可用，使用应用详情页面
                                            val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                data = android.net.Uri.parse("package:${context.packageName}")
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(fallbackIntent)
                                        }
                                    }
                                } catch (e: Exception) {
                                    // 如果出现异常，使用应用详情页面作为最后的备选方案
                                    try {
                                        val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = android.net.Uri.parse("package:${context.packageName}")
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(fallbackIntent)
                                    } catch (ex: Exception) {
                                        // 如果所有方法都失败，尝试使用通用设置页面
                                        val genericIntent = Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)
                                        context.startActivity(genericIntent)
                                    }
                                }
                            },
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )) { Text("前往设置") }
                        } else {
                            Text("已忽略")
                        }
                    }
                }
            }

            Divider()

            OutlinedTextField(
                value = uiState.apiKey,
                onValueChange = { viewModel.updateApiKey(it) },
                label = { Text("API Key") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.secondary
                )
            )



            Button(
                onClick = { viewModel.saveSettings() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isLoading,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                if (uiState.isLoading) CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
                else Text("保存设置")
            }

            uiState.saveSuccess?.let { success ->
                if (success) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) { 
                        Text(
                            text = "设置已保存", 
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium
                        ) 
                    }
                }
            }
            uiState.error?.let { error ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = error); TextButton(onClick = { viewModel.clearError() },
                            shape = RoundedCornerShape(12.dp)) {
                        Text(
                            "关闭"
                        )
                    }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 使用可组合函数创建带有超链接的文本
            HyperlinkText(
                fullText = "说明：\n1. 开启无障碍服务\n2. 开启忽略电池优化\n3. 输入智普平台的API Key并保存",
                linkText = "智普平台API Key",
                linkUrl = "https://bigmodel.cn/usercenter/proj-mgmt/apikeys",
                modifier = Modifier
            )
        }
    }

}
