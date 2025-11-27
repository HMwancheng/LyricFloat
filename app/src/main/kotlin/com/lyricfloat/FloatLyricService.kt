package com.lyricfloat

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.TranslateAnimation
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class FloatLyricService : Service() {
    // Binder用于Activity与Service通信
    private val binder = LocalBinder()
    
    // 悬浮窗相关
    private var windowManager: WindowManager? = null
    private var floatView: View? = null
    private var lyricTextView: TextView? = null
    private var lastX = 0f
    private var lastY = 0f

    // 通知相关
    private val CHANNEL_ID = "LyricFloat_Channel"
    private val NOTIFICATION_ID = 1001
    
    // 歌词相关
    private val lyricManager = LyricManager(this)
    private var currentLyric: LyricManager.Lyric? = null
    private val mediaMonitor = MediaMonitor(this) { playbackState ->
        updateLyricDisplay(playbackState)
    }

    // 调试模式相关
    private var isDebugMode = false
    private val debugLyrics = listOf(
        "这是第一句测试歌词",
        "这是第二句测试歌词，稍微长一点看看显示效果",
        "第三句测试歌词",
        "测试歌词的换行和显示效果",
        "最后一句测试歌词"
    )
    private var currentDebugLine = 0
    private val debugHandler = Handler(Looper.getMainLooper())
    private val debugRunnable = object : Runnable {
        override fun run() {
            currentDebugLine = (currentDebugLine + 1) % debugLyrics.size
            updateLyricWithAnimation(debugLyrics[currentDebugLine])
            debugHandler.postDelayed(this, 2000) // 每2秒切换一行
        }
    }

    // 动画相关
    private var currentAnimationType = "fade"

    inner class LocalBinder : Binder() {
        fun getService(): FloatLyricService = this@FloatLyricService
    }

    override fun onCreate() {
        super.onCreate()
        try {
            createNotificationChannel()
            startForeground(NOTIFICATION_ID, createNotification())
            initFloatViewSafely()
            mediaMonitor.startMonitoring()
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }
    }

    // 安全初始化悬浮窗
    private fun initFloatViewSafely() {
        try {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            floatView = LayoutInflater.from(this).inflate(R.layout.float_lyric_view, null)
            lyricTextView = floatView?.findViewById(R.id.lyric_text)

            val layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )

            // 设置初始位置（屏幕中间偏下）
            layoutParams.x = 0
            layoutParams.y = 500

            // 悬浮窗拖拽逻辑
            floatView?.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        lastX = event.rawX - layoutParams.x
                        lastY = event.rawY - layoutParams.y
                    }
                    MotionEvent.ACTION_MOVE -> {
                        layoutParams.x = (event.rawX - lastX).toInt()
                        layoutParams.y = (event.rawY - lastY).toInt()
                        windowManager?.updateViewLayout(floatView, layoutParams)
                    }
                }
                true
            }

            windowManager?.addView(floatView, layoutParams)
        } catch (e: Exception) {
            e.printStackTrace()
            GlobalScope.launch(Dispatchers.Main) {
                lyricTextView?.text = "悬浮窗创建失败，请检查权限"
            }
        }
    }

    // 创建通知渠道
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "歌词悬浮窗",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "用于保持悬浮窗服务后台运行"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    // 创建前台服务通知
    private fun createNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setContentTitle("歌词悬浮窗")
            .setContentText("正在监听媒体播放...")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()
    }

    // 更新歌词显示
    private fun updateLyricDisplay(playbackState: AppPlaybackState) {
        if (isDebugMode) return // 调试模式下不更新歌词

        if (playbackState.isPlaying) {
            if (currentLyric?.title != playbackState.title || currentLyric?.artist != playbackState.artist) {
                GlobalScope.launch(Dispatchers.IO) {
                    currentLyric = lyricManager.parseLocalLyric(playbackState.title, playbackState.artist)
                    if (currentLyric == null) {
                        currentLyric = lyricManager.fetchOnlineLyric(playbackState.title, playbackState.artist)
                    }
                }
            }

            currentLyric?.let { lyric ->
                val currentLine = lyricManager.getCurrentLyricLine(lyric, playbackState.position)
                GlobalScope.launch(Dispatchers.Main) {
                    val displayText = currentLine?.text ?: "${playbackState.title}\n${playbackState.artist}"
                    updateLyricWithAnimation(displayText)
                    
                    // 同步更新MainActivity的状态
                    (application as LyricFloatApp).mainActivity?.updateStatus(
                        "正在播放：${playbackState.title} - ${playbackState.artist}"
                    )
                }
            } ?: run {
                val displayText = "${playbackState.title}\n${playbackState.artist}"
                updateLyricWithAnimation(displayText)
                
                (application as LyricFloatApp).mainActivity?.updateStatus(
                    "未找到歌词：${playbackState.title} - ${playbackState.artist}"
                )
            }
        } else {
            updateLyricWithAnimation("未播放音乐")
            (application as LyricFloatApp).mainActivity?.updateStatus("未检测到播放")
        }
    }

    // 带动画更新歌词
    private fun updateLyricWithAnimation(text: String) {
        val textView = lyricTextView ?: return
        
        when (currentAnimationType) {
            "fade" -> {
                val fadeOut = AlphaAnimation(1.0f, 0.0f)
                fadeOut.duration = 200
                fadeOut.setAnimationListener(object : Animation.AnimationListener {
                    override fun onAnimationStart(animation: Animation) {}
                    override fun onAnimationEnd(animation: Animation) {
                        textView.text = text
                        val fadeIn = AlphaAnimation(0.0f, 1.0f)
                        fadeIn.duration = 200
                        textView.startAnimation(fadeIn)
                    }
                    override fun onAnimationRepeat(animation: Animation) {}
                })
                textView.startAnimation(fadeOut)
            }
            "slide" -> {
                val slideOut = TranslateAnimation(0f, -50f, 0f, 0f)
                slideOut.duration = 200
                slideOut.setAnimationListener(object : Animation.AnimationListener {
                    override fun onAnimationStart(animation: Animation) {}
                    override fun onAnimationEnd(animation: Animation) {
                        textView.text = text
                        val slideIn = TranslateAnimation(50f, 0f, 0f, 0f)
                        slideIn.duration = 200
                        textView.startAnimation(slideIn)
                    }
                    override fun onAnimationRepeat(animation: Animation) {}
                })
                textView.startAnimation(slideOut)
            }
            else -> {
                textView.text = text
            }
        }
    }

    // 调试模式切换
    fun setDebugMode(enable: Boolean) {
        isDebugMode = enable
        if (enable) {
            mediaMonitor.stopMonitoring()
            debugHandler.post(debugRunnable)
            updateLyricWithAnimation(debugLyrics[0])
        } else {
            debugHandler.removeCallbacks(debugRunnable)
            mediaMonitor.startMonitoring()
            updateLyricWithAnimation("未播放音乐")
        }
    }

    // 刷新当前歌词
    fun refreshCurrentLyric() {
        currentLyric = null
        if (!isDebugMode) {
            mediaMonitor.startMonitoring()
        }
    }

    // 设置歌词字体大小
    fun setLyricFontSize(size: Int) {
        lyricTextView?.textSize = size.toFloat()
    }

    // 设置歌词颜色
    fun setLyricColor(colorHex: String) {
        try {
            val color = Color.parseColor(colorHex)
            lyricTextView?.setTextColor(color)
        } catch (e: Exception) {
            e.printStackTrace()
            lyricTextView?.setTextColor(Color.WHITE) // 默认白色
        }
    }

    // 设置动画类型
    fun setAnimationType(type: String) {
        currentAnimationType = type
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        debugHandler.removeCallbacks(debugRunnable)
        mediaMonitor.stopMonitoring()
        floatView?.let { view ->
            windowManager?.removeView(view)
        }
    }
}

// 媒体播放状态数据类
data class AppPlaybackState(
    val title: String,
    val artist: String,
    val position: Long,
    val isPlaying: Boolean
)

// 媒体监控类（简化实现，实际可通过AudioManager或媒体库监听）
class MediaMonitor(private val context: Context, private val callback: (AppPlaybackState) -> Unit) {
    fun startMonitoring() {
        // 实际项目中可通过MediaBrowserServiceCompat或AudioManager监听播放状态
        // 此处为示例，模拟播放状态
        callback.invoke(AppPlaybackState("测试歌曲", "测试歌手", 0, false))
    }

    fun stopMonitoring() {}
}
