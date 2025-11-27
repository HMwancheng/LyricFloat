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
    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private var isDebugMode = false
    
    private lateinit var mediaSessionManager: MediaSessionManager
    private var mediaController: MediaController? = null
    private var testSongTimer: Timer? = null
    
    // Binder for activity communication
    inner class LocalBinder : IBinder() {
        fun getService(): FloatLyricService = this@FloatLyricService
    }
    
    override fun onBind(intent: Intent): IBinder {
        return LocalBinder()
    }

    override fun onCreate() {
        super.onCreate()
        
        // 创建通知通道（前台服务必需）
        createNotificationChannel()
        
        // 启动为前台服务
        startForeground(1, createNotification())
        
        // 初始化悬浮窗
        createFloatingWindow()
        
        // 初始化媒体监听
        initMediaListener()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "lyric_service_channel",
                "Lyric Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, "lyric_service_channel")
            .setContentTitle("LyricFloat")
            .setContentText("歌词服务正在运行")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    
    private fun createFloatingWindow() {
        // 检查悬浮窗权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            // 通知MainActivity请求权限
            (application as LyricFloatApp).mainActivity?.updateStatus("需要悬浮窗权限才能显示歌词")
            return
        }
        
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
        
        // 添加到WindowManager
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        try {
            windowManager.addView(floatingView, layoutParams)
            setupDragListener(floatingView!!, layoutParams)
        } catch (e: Exception) {
            e.printStackTrace()
            (application as LyricFloatApp).mainActivity?.updateStatus("悬浮窗创建失败：${e.message}")
        }
    }
    
    private fun setupDragListener(view: View, params: WindowManager.LayoutParams) {
        view.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            
            override fun onTouch(v: View, event: MotionEvent): Boolean {
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
                        windowManager.updateViewLayout(v, params)
                        return true
                    }
                }
                return false
            }
        })
    }
    
    private fun initMediaListener() {
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
            
            mediaSessionManager.addOnActiveSessionsChangedListener(callback, null)
            
            // 初始检查
            val activeSessions = mediaSessionManager.getActiveSessions(null)
            if (activeSessions.isNotEmpty()) {
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
            (application as LyricFloatApp).mainActivity?.updateStatus("正在播放：$title - $artist")
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
        floatingView?.findViewById<TextView>(R.id.lyric_text)?.setTextColor(
            android.graphics.Color.parseColor(color)
        )
    }
    
    fun setAnimationType(type: String) {
        // 实现动画类型切换逻辑
    }
    
    private fun updateFloatingWindow(text: String) {
        floatingView?.findViewById<TextView>(R.id.lyric_text)?.text = text
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
    
    override fun onDestroy() {
        super.onDestroy()
        stopTestSongLoop()
        floatingView?.let { windowManager.removeView(it) }
    }
}
