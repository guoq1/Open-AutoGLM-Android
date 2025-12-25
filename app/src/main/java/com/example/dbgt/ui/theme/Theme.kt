package com.example.dbgt.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,       // 浅紫色
    secondary = PurpleGrey40, // 亮粉色
    tertiary = Pink40,        // 鹅黄色
    background = Color(0xFFFFF8F8), // 浅粉色背景
    surface = Color(0xFFFFFFFF),    // 白色表面
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.Black,
    onBackground = Color(0xFF333333),
    onSurface = Color(0xFF333333),
    surfaceVariant = Color(0xFFFFF0F0), // 浅粉色变体
    primaryContainer = Color(0xFFF0E0FF), // 浅紫色容器
    secondaryContainer = Color(0xFFFFE0F0), // 亮粉色容器
    tertiaryContainer = Color(0xFFFFFFE0) // 鹅黄色容器
)

@Composable
fun OpenAutoGLMAndroidTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false, // 禁用动态颜色以保持Q版风格一致性
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}