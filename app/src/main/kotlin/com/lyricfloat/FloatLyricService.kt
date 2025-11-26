package com.lyricfloat

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

    private val mediaMonitor = MediaMonitor(this) { playbackState ->
        updateLyric(playbackState)
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel() // 实现该方法
        initFloatView()
        mediaMonitor.startMonitoring()
    }

    private fun createNotificationChannel() {
        // 空实现，避免编译错误
    }

    private fun initFloatView() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        floatView = LayoutInflater.from(this).inflate(R.layout.float_lyric_view, null)
        lyricTextView = floatView.findViewById(R.id.lyric_text)

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

        floatView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    x = event.rawX
                    y = event.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = (event.rawX - x).toInt()
                    params.y = (event.rawY - y).toInt()
                    windowManager.updateViewLayout(floatView, params)
                }
            }
            true
        }

        windowManager.addView(floatView, params)
    }

    private fun updateLyric(playbackState: AppPlaybackState) {
        lyricTextView.text = "${playbackState.title}\n${playbackState.artist}"
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaMonitor.stopMonitoring()
        windowManager.removeView(floatView)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
