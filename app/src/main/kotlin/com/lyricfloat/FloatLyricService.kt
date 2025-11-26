package com.lyricfloat

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FloatLyricService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatView: View
    private lateinit var lyricTextView: TextView
    private var lastX = 0f
    private var lastY = 0f

    private val CHANNEL_ID = "LyricFloat_Channel"
    private val NOTIFICATION_ID = 1001
    private val lyricManager = LyricManager(this)
    private var currentLyric: LyricManager.Lyric? = null

    private val mediaMonitor = MediaMonitor(this) { playbackState ->
        updateLyricDisplay(playbackState)
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel() // 真实创建通知渠道（前台服务必需）
        startForeground(NOTIFICATION_ID, createNotification()) // 启动前台服务
        initFloatView()
        mediaMonitor.startMonitoring()
    }

    // 1. 真实创建通知渠道（Android O+必需）
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

    // 2. 创建前台服务通知（避免服务被系统杀死）
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

    // 3. 初始化悬浮窗（优化拖拽体验）
    private fun initFloatView() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        floatView = LayoutInflater.from(this).inflate(R.layout.float_lyric_view, null)
        lyricTextView = floatView.findViewById(R.id.lyric_text)

        // 悬浮窗参数（适配Android版本）
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

        // 优化拖拽逻辑（支持平滑拖动）
        floatView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = event.rawX - layoutParams.x
                    lastY = event.rawY - layoutParams.y
                }
                MotionEvent.ACTION_MOVE -> {
                    layoutParams.x = (event.rawX - lastX).toInt()
                    layoutParams.y = (event.rawY - lastY).toInt()
                    windowManager.updateViewLayout(floatView, layoutParams)
                }
            }
            true
        }

        windowManager.addView(floatView, layoutParams)
    }

    // 4. 更新歌词显示（优先本地，后网络）
    private fun updateLyricDisplay(playbackState: AppPlaybackState) {
        if (playbackState.isPlaying) {
            // 检查是否切换歌曲（避免重复解析）
            if (currentLyric?.title != playbackState.title || currentLyric?.artist != playbackState.artist) {
                lifecycleScope.launch(Dispatchers.IO) {
                    // 第一步：读取本地歌词
                    currentLyric = lyricManager.parseLocalLyric(playbackState.title, playbackState.artist)
                    // 第二步：本地无歌词则请求网络
                    if (currentLyric == null) {
                        currentLyric = lyricManager.fetchOnlineLyric(playbackState.title, playbackState.artist)
                    }
                }
            }

            // 显示歌词或歌曲信息
            currentLyric?.let { lyric ->
                val currentLine = lyricManager.getCurrentLyricLine(lyric, playbackState.position)
                lifecycleScope.launch(Dispatchers.Main) {
                    lyricTextView.text = currentLine?.text ?: "${playbackState.title}\n${playbackState.artist}"
                }
            } ?: run {
                lyricTextView.text = "${playbackState.title}\n${playbackState.artist}"
            }
        } else {
            lyricTextView.text = "未播放音乐"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaMonitor.stopMonitoring()
        windowManager.removeView(floatView)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
