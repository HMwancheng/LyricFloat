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
    private var isFloatingWindowCreated = false // 标记悬浮窗是否已创建
    private val initHandler = Handler(Looper.getMainLooper())
    private var initRunnable: Runnable? = null // 保存延迟初始化任务，方便取消
    
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
        
        // 1. 优先创建通知通道（前台服务必需，避免Service被系统杀死）
        createNotificationChannel()
        startForeground(1, createNotification())
        
        // 2. 延迟初始化（避免启动时资源竞争）
        initRunnable = Runnable {
            try {
                initMediaListenerSafely() // 先初始化媒体监听（不依赖悬浮窗）
                createFloatingWindowSafely() // 再创建悬浮窗
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        initHandler.postDelayed(initRunnable!!, 1000)
    }
    
    // 新增：手动触发悬浮窗创建（用于权限授予后回调）
    fun recreateFloatingWindow() {
        initHandler.removeCallbacks(initRunnable!!) // 取消之前的延迟任务
        createFloatingWindowSafely() // 立即创建悬浮窗
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
            val channelId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) "lyric_service_channel" else ""
            NotificationCompat.Builder(this, channelId)
                .setContentTitle("LyricFloat")
                .setContentText("歌词服务正在运行")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
        } catch (e: Exception) {
            NotificationCompat.Builder(this)
                .setContentTitle("LyricFloat")
                .setContentText("歌词服务正在运行")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .build()
        }
    }
    
    // 关键修复：悬浮窗创建防重复、强空指针防护
    private fun createFloatingWindowSafely() {
        // 跳过已创建的情况（避免重复添加）
        if (isFloatingWindowCreated) return
        
        // 再次检查权限（防止权限变化）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            updateStatus("悬浮窗权限未授予，无法显示")
            return
        }
        
        try {
            // 初始化WindowManager（确保非空）
            if (windowManager == null) {
                windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            }
            
            // 悬浮窗参数优化（兼容不同Android版本）
            val layoutParams = WindowManager.LayoutParams().apply {
                type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY // Android O+ 必需
                } else {
                    WindowManager.LayoutParams.TYPE_PHONE
                }
                
                // 基础配置（避免布局冲突）
                gravity = Gravity.TOP or Gravity.START
                x = 100
                y = 200
                width = WindowManager.LayoutParams.WRAP_CONTENT
                height = WindowManager.LayoutParams.WRAP_CONTENT
                
                // 关键Flags（减少系统限制冲突）
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                
                format = PixelFormat.TRANSLUCENT
            }
            
            // 加载视图（避免布局文件不存在导致崩溃）
            floatingView = try {
                LayoutInflater.from(this).inflate(R.layout.floating_window, null)
            } catch (e: Exception) {
                updateStatus("悬浮窗布局加载失败")
                return
            }
            
            // 初始化文本视图
            val lyricText = floatingView?.findViewById<TextView>(R.id.lyric_text)
            lyricText?.text = "等待播放..."
            lyricText?.setTextColor(android.graphics.Color.WHITE) // 确保文本可见
            
            // 安全添加视图到WindowManager
            floatingView?.let { view ->
                windowManager?.addView(view, layoutParams)
                setupDragListener(view, layoutParams)
                isFloatingWindowCreated = true // 标记为已创建
                updateStatus("悬浮窗已显示")
            } ?: run {
                updateStatus("悬浮窗视图创建失败")
            }
            
        } catch (e: Exception) {
            // 捕获所有异常，避免闪退
            e.printStackTrace()
            updateStatus("悬浮窗创建失败：${e.message?.take(20)}") // 限制错误信息长度
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
                            // 计算新位置（避免越界）
                            val newX = initialX + (event.rawX - initialTouchX).toInt()
                            val newY = initialY + (event.rawY - initialTouchY).toInt()
                            
                            // 限制X、Y坐标在合理范围（避免超出屏幕导致异常）
                            params.x = newX.coerceAtLeast(0)
                            params.y = newY.coerceAtLeast(0)
                            
                            windowManager?.updateViewLayout(v, params)
                            return true
                        }
                        else -> return false
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    return false
                }
            }
        })
    }
    
    private fun initMediaListenerSafely() {
        try {
            mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            
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
            updateFloatingWindow("$title\n$artist")
            stopTestSongLoop()
            updateStatus("正在播放：$title - $artist")
        } ?: run {
            startTestSongLoop()
        }
    }
    
    // 其他方法保持不变，仅优化空指针防护
    fun setTestLyric(title: String, artist: String) {
        if (isFloatingWindowCreated) {
            updateFloatingWindow("$title\n$artist")
        }
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
            if (isFloatingWindowCreated) {
                floatingView?.findViewById<TextView>(R.id.lyric_text)?.setTextColor(
                    android.graphics.Color.parseColor(color)
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun setAnimationType(type: String) {}
    
    private fun updateFloatingWindow(text: String) {
        try {
            if (isFloatingWindowCreated) {
                floatingView?.findViewById<TextView>(R.id.lyric_text)?.text = text
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun startTestSongLoop() {
        if (!isDebugMode || !isFloatingWindowCreated) return
        
        stopTestSongLoop()
        testSongTimer = Timer()
        testSongTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                initHandler.post {
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
        } catch (e: Exception) {}
    }
    
    // 关键修复：悬浮窗安全销毁（避免内存泄漏和重复移除）
    override fun onDestroy() {
        super.onDestroy()
        stopTestSongLoop()
        initHandler.removeCallbacks(initRunnable!!) // 取消延迟任务
        
        try {
            if (isFloatingWindowCreated && floatingView != null) {
                windowManager?.removeView(floatingView) // 移除悬浮窗视图
                floatingView = null
                windowManager = null
                isFloatingWindowCreated = false
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
