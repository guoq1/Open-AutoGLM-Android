package com.example.dbgt.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

class FloatingWindowService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var params: WindowManager.LayoutParams? = null
    
    // 状态
    private var currentStatus = "空闲"
    private var currentStep = 0
    private var isExpanded = true
    private var isPaused = false
    
    // 拖动相关
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    
    // 大小调整
    private var currentWidth = 120  // 减小宽度到120dp
    private var currentHeight = 30  // 减小到只有一行文字的高度
    private val minWidth = 80
    private val maxWidth = 200
    private val minHeight = 30
    private val maxHeight = 100

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        createFloatingWindow()
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        removeFloatingWindow()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createFloatingWindow() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        // 创建悬浮窗视图
        floatingView = createFloatingView()
        
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        
        params = WindowManager.LayoutParams(
            dpToPx(currentWidth),
            dpToPx(currentHeight),
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 50
            y = 200
        }
        
        windowManager?.addView(floatingView, params)
        setupTouchListener()
    }
    
    @SuppressLint("SetTextI18n")
    private fun createFloatingView(): View {
        val context = this
        
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL  // 水平布局
            setBackgroundColor(0x80303030.toInt())  // 50% 透明度 (0x80 = 128/255 ≈ 50%)
            setPadding(dpToPx(6), dpToPx(4), dpToPx(6), dpToPx(4))
            
            // 添加黑色边框
            val strokeDrawable = GradientDrawable().apply {
                setColor(0x80303030.toInt())  // 背景色
                setStroke(2, 0xFF000000.toInt())  // 2px黑色边框
                cornerRadius = dpToPx(4).toFloat()  // 可选的圆角
            }
            background = strokeDrawable
            
            // 合并的状态和步骤信息
            addView(TextView(context).apply {
                tag = "status"
                text = "$currentStatus ($currentStep)"
                setTextColor(0xFFCCCCCC.toInt())
                textSize = 10f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)  // 占据剩余空间
            })
            
            // 停止按钮 (⏹)
            addView(TextView(context).apply {
                tag = "stop_btn"
                text = "⏹"
                setTextColor(0xFFFF4444.toInt())
                textSize = 14f
                setPadding(dpToPx(4), 0, 0, 0)
                visibility = View.GONE // 默认隐藏，仅在执行中显示
                setOnClickListener { onStopClickListener?.invoke() }
            })
        }
    }
    
    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouchListener() {
        floatingView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params?.x ?: 0
                    initialY = params?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    // 计算移动距离
                    val deltaX = (event.rawX - initialTouchX).toInt()
                    val deltaY = (event.rawY - initialTouchY).toInt()
                    
                    // 对于Gravity.END，x参数是距离屏幕右侧的距离，需要特殊处理
                    params?.x = initialX - deltaX
                    params?.y = initialY + deltaY
                    
                    windowManager?.updateViewLayout(floatingView, params)
                    true
                }
                else -> false
            }
        }
    }
    

    
    fun updateStatus(status: String, step: Int = currentStep, detail: String = "") {
        currentStatus = status
        currentStep = step
        
        floatingView?.post {
            floatingView?.findViewWithTag<TextView>("status")?.text = "$status ($step)"
            
            val isRunningOrPaused = status == "执行中" || status == "已暂停"
            
            // 按钮可见性
            floatingView?.findViewWithTag<View>("stop_btn")?.visibility = if (isRunningOrPaused) View.VISIBLE else View.GONE
            floatingView?.findViewWithTag<View>("pause_resume_btn")?.visibility = if (isRunningOrPaused) View.VISIBLE else View.GONE
        }
    }
    
    fun updatePauseStatus(paused: Boolean) {
        this.isPaused = paused
        floatingView?.post {
            val pauseBtn = floatingView?.findViewWithTag<TextView>("pause_resume_btn")
            pauseBtn?.text = if (paused) "▶" else "⏸"
            
            if (paused) {
                updateStatus("已暂停")
            } else if (currentStatus == "已暂停") {
                updateStatus("执行中")
            }
        }
    }

    /**
     * 设置悬浮窗可见性
     */
    fun setVisibility(visible: Boolean) {
        floatingView?.post {
            floatingView?.visibility = if (visible) View.VISIBLE else View.GONE
        }
    }
    
    private fun removeFloatingWindow() {
        floatingView?.let {
            windowManager?.removeView(it)
            floatingView = null
        }
    }
    
    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    companion object {
        private var instance: FloatingWindowService? = null
        var onStopClickListener: (() -> Unit)? = null
        var onPauseResumeClickListener: (() -> Unit)? = null
        
        fun getInstance(): FloatingWindowService? = instance
        
        fun hasOverlayPermission(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else {
                true
            }
        }
        
        fun startService(context: Context) {
            if (hasOverlayPermission(context)) {
                context.startService(Intent(context, FloatingWindowService::class.java))
            }
        }
        
        fun stopService(context: Context) {
            context.stopService(Intent(context, FloatingWindowService::class.java))
        }
    }
}
