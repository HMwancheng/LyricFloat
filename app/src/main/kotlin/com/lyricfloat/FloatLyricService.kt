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

class FloatLyricService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatView: View
    private lateinit var lyricTextView: TextView
    private var x = 0f
    private var y = 0f
    private var lastX = 0f
    private var lastY = 0f

    private val CHANNEL_ID = "LyricFloatChannel"
    private val NOTIFICATION_ID = 1001
    private val lyricManager = LyricManager(this)
    private val mediaMonitor = MediaMonitor(this) { playbackState ->
        updateLyric(playbackState)
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initFloatView()
        mediaMonitor.startMonitoring()
    }

    // 初始化悬浮窗（添加拖拽功能）
    private fun initFloatView() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        floatView = LayoutInflater.from(this).inflate(R.layout.float_lyric_view, null)
        lyricTextView = floatView.findViewById(R.id.lyric_text)

        // 悬浮窗参数
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        // 添加拖拽监听
        floatView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    x = event.rawX
                    y = event.rawY
                    lastX = event.x
                    lastY = event.y
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = (event.rawX - lastX).toInt()
                    params.y = (event.rawY - lastY).toInt()
                    windowManager.updateViewLayout(floatView, params)
                }
            }
            true
        }

        windowManager.addView(floatView, params)
    }

    // 更新歌词显示
    private fun updateLyric(playbackState: PlaybackState) {
        if (playbackState.isPlaying) {
            // 获取歌词（先本地后网络）
            val lyric = lyricManager.parseLyricFromFile(playbackState.title, playbackState.artist)
                ?: run {
                    // 协程获取网络歌词
                    // lifecycleScope.launch { lyricManager.fetchLyricFromNetwork(...) }
                    null
                }
            // 显示当前歌词行
            lyric?.let {
                val currentLine = lyricManager.getCurrentLyricLine(it, playbackState.position)
                lyricTextView.text = currentLine?.text ?: playbackState.title
            } ?: run {
                lyricTextView.text = "${playbackState.title}\n${playbackState.artist}"
            }
        }
    }

    // 其他原有方法（createNotificationChannel、createNotification等）保持不变

    override fun onDestroy() {
        super.onDestroy()
        mediaMonitor.stopMonitoring()
        windowManager.removeView(floatView)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
