package com.lyricfloat

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import java.util.Timer
import java.util.TimerTask

class FloatLyricService : Service() {
    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var isDebugMode = false
    
    private var mediaSessionManager: MediaSessionManager? = null
    private var mediaController: MediaController? = null
    private var testSongTimer: Timer? = null
    
    // Binder for activity communication
    inner class LocalBinder : Binder() {
        fun getService(): FloatLyricService = this@FloatLyricService
    }
    
    private val binder = LocalBinder()
    
    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        
        // 先创建通知通道，确保前台服务能正常启动
        createNotificationChannel()
        
        // 启动为前台服务（必须）
        startForeground(1, createNotification())
        
        // 延迟初始化，避免启动时闪退
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                // 初始化悬浮窗（安全模式）
                createFloatingWindowSafely()
                
                // 初始化媒体监听
                initMediaListenerSafely()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, 1000)
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val channel = NotificationChannel(
                    "lyric_service_channel",
                    "Lyric Service",
                    NotificationManager.IMPORTANCE_LOW
                )
                val manager = getSystemService(NotificationManager::class.java)
                manager.createNotificationChannel(channel)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    private fun createNotification(): Notification {
        return try {
            NotificationCompat.Builder(this, "lyric_service_channel")
                .setContentTitle("LyricFloat")
                .setContentText("歌词服务正在运行")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
        } catch (e: Exception) {
            // 降级处理
            NotificationCompat.Builder(this)
                .setContentTitle("LyricFloat")
                .setContentText("歌词服务正在运行")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .build()
        }
    }
    
    // 安全创建悬浮窗
    private fun createFloatingWindowSafely() {
        // 检查悬浮窗权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            // 不立即创建悬浮窗，等待权限授予
            updateStatus("需要悬浮窗权限才能显示歌词")
            return
        }
        
        try {
            // 设置悬浮窗参数
            val layoutParams = WindowManager.LayoutParams().apply {
                type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    WindowManager.LayoutParams.TYPE_PHONE
                }
                
                gravity = Gravity.TOP or Gravity.START
                x = 100
                y = 200
                width = WindowManager.LayoutParams.WRAP_CONTENT
                height = WindowManager.LayoutParams.WRAP_CONTENT
                
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
                
                format = PixelFormat.TRANSLUCENT
            }
            
            // 创建悬浮窗视图
            floatingView = LayoutInflater.from(this).inflate(R.layout.floating_window, null)
            val lyricText = floatingView?.findViewById<TextView>(R.id.lyric_text)
            lyricText?.text = "等待播放..."
            
            // 获取WindowManager
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            
            // 添加到WindowManager
            windowManager?.addView(floatingView, layoutParams)
            
            // 设置拖动监听
            floatingView?.let {
                setupDragListener(it, layoutParams)
            }
            
            updateStatus("悬浮窗已创建")
        } catch (e: Exception) {
            e.printStackTrace()
            updateStatus("悬浮窗创建失败：${e.message}")
        }
    }
    
    private fun setupDragListener(view: View, params: WindowManager.LayoutParams) {
        view.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                try {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            initialX = params.x
                            initialY = params.y
                            initialTouchX = event.rawX
                            initialTouchY = event.rawY
                            return true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            params.x = initialX + (event.rawX - initialTouchX).toInt()
                            params.y = initialY + (event.rawY - initialTouchY).toInt()
                            windowManager?.updateViewLayout(v, params)
                            return true
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                return false
            }
        })
    }
    
    // 安全初始化媒体监听
    private fun initMediaListenerSafely() {
        try {
            mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            
            // 监听活跃媒体会话变化
            val callback = object : MediaSessionManager.OnActiveSessionsChangedListener {
                override fun onActiveSessionsChanged(controllers: MutableList<MediaController>?) {
                    controllers?.let {
                        if (it.isNotEmpty()) {
                            mediaController = it[0]
                            registerMediaControllerCallback()
                            updateMediaInfo()
                        } else {
                            mediaController = null
                            startTestSongLoop()
                        }
                    }
                }
            }
            
            mediaSessionManager?.addOnActiveSessionsChangedListener(callback, null)
            
            // 初始检查
            val activeSessions = mediaSessionManager?.getActiveSessions(null)
            if (activeSessions?.isNotEmpty() == true) {
                mediaController = activeSessions[0]
                registerMediaControllerCallback()
                updateMediaInfo()
            } else {
                startTestSongLoop()
            }
            
        } catch (e: Exception) {
            e.printStackTrace()
            startTestSongLoop()
        }
    }
    
    private fun registerMediaControllerCallback() {
        mediaController?.registerCallback(object : MediaController.Callback() {
            override fun onMetadataChanged(metadata: MediaMetadata?) {
                super.onMetadataChanged(metadata)
                updateMediaInfo()
            }
            
            override fun onPlaybackStateChanged(state: android.media.session.PlaybackState?) {
                super.onPlaybackStateChanged(state)
                if (state?.state == android.media.session.PlaybackState.STATE_STOPPED) {
                    startTestSongLoop()
                }
            }
        })
    }
    
    private fun updateMediaInfo() {
        mediaController?.metadata?.let { metadata ->
            val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "未知歌曲"
            val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "未知艺术家"
            
            // 更新悬浮窗
            updateFloatingWindow("$title\n$artist")
            
            // 停止测试循环
            stopTestSongLoop()
            
            // 更新状态
            updateStatus("正在播放：$title - $artist")
        } ?: run {
            startTestSongLoop()
        }
    }
    
    fun setTestLyric(title: String, artist: String) {
        updateFloatingWindow("$title\n$artist")
    }
    
    fun setDebugMode(enable: Boolean) {
        isDebugMode = enable
        if (enable) {
            startTestSongLoop()
        } else {
            stopTestSongLoop()
            updateMediaInfo()
        }
    }
    
    fun refreshCurrentLyric() {
        updateMediaInfo()
    }
    
    fun setLyricColor(color: String) {
        try {
            floatingView?.findViewById<TextView>(R.id.lyric_text)?.setTextColor(
                android.graphics.Color.parseColor(color)
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun setAnimationType(type: String) {
        // 实现动画类型切换逻辑
    }
    
    private fun updateFloatingWindow(text: String) {
        try {
            floatingView?.findViewById<TextView>(R.id.lyric_text)?.text = text
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun startTestSongLoop() {
        if (!isDebugMode) return
        
        stopTestSongLoop()
        testSongTimer = Timer()
        testSongTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                Handler(Looper.getMainLooper()).post {
                    updateFloatingWindow("测试歌曲\n测试歌手")
                }
            }
        }, 0, 5000)
    }
    
    private fun stopTestSongLoop() {
        testSongTimer?.cancel()
        testSongTimer = null
    }
    
    private fun updateStatus(message: String) {
        try {
            (application as LyricFloatApp).mainActivity?.updateStatus(message)
        } catch (e: Exception) {
            // 静默失败，不影响应用运行
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopTestSongLoop()
        try {
            floatingView?.let { windowManager?.removeView(it) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
