package com.lyricfloat

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.lyricfloat.databinding.FloatLyricViewBinding
import com.lyricfloat.model.Lyric
import com.lyricfloat.model.PlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FloatLyricService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatViewBinding: FloatLyricViewBinding
    private lateinit var params: WindowManager.LayoutParams
    
    private var currentLyric: Lyric? = null
    private var currentPlaybackState: PlaybackState? = null
    
    private val mediaMonitor by lazy { MediaMonitor(this, ::onPlaybackChanged) }
    private val lyricManager by lazy { LyricManager(this) }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, createNotification())
        
        initFloatWindow()
        mediaMonitor.startMonitoring()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaMonitor.stopMonitoring()
        windowManager.removeView(floatViewBinding.root)
    }

    // 初始化悬浮窗
    private fun initFloatWindow() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        // 初始化布局
        floatViewBinding = FloatLyricViewBinding.inflate(layoutInflater)
        
        // 设置悬浮窗参数
        params = WindowManager.LayoutParams().apply {
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            }
            
            format = PixelFormat.RGBA_8888
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 100 // 初始Y坐标
        }
        
        // 设置触摸监听（拖动悬浮窗）
        setupTouchListener()
        
        // 添加悬浮窗到WindowManager
        windowManager.addView(floatViewBinding.root, params)
    }

    // 设置悬浮窗拖动
    private fun setupTouchListener() {
        var x = 0f
        var y = 0f
        var startX = 0
        var startY = 0
        
        floatViewBinding.root.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    x = event.rawX
                    y = event.rawY
                    startX = params.x
                    startY = params.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (event.rawX - x).toInt()
                    params.y = startY + (event.rawY - y).toInt()
                    windowManager.updateViewLayout(floatViewBinding.root, params)
                    true
                }
                else -> false
            }
        }
    }

    // 播放状态变化回调
    private fun onPlaybackChanged(playbackState: PlaybackState) {
        currentPlaybackState = playbackState
        
        // 如果歌曲变化，获取新歌词
        if (currentLyric?.title != playbackState.title || currentLyric?.artist != playbackState.artist) {
            CoroutineScope(Dispatchers.IO).launch {
                currentLyric = lyricManager.getLyric(playbackState)
            }
        }
        
        // 更新歌词显示
        updateLyricDisplay()
    }

    // 更新歌词显示
    private fun updateLyricDisplay() {
        val lyric = currentLyric ?: return
        val playbackState = currentPlaybackState ?: return
        
        // 找到当前时间对应的歌词行
        val currentLine = lyric.lines.findLast { it.time <= playbackState.currentPosition }
        
        currentLine?.let {
            // 设置歌词动画（淡入淡出）
            floatViewBinding.lyricText.alpha = 0f
            floatViewBinding.lyricText.text = it.text
            floatViewBinding.lyricText.animate().alpha(1f).duration = 300
        }
    }

    // 创建通知渠道（前台服务必需）
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "LYRIC_FLOAT_CHANNEL",
                "Lyric Float Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    // 创建前台服务通知
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, "LYRIC_FLOAT_CHANNEL")
            .setContentTitle("Lyric Float")
            .setContentText("Lyric floating window is running")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
