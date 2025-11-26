package com.lyricfloat

import android.content.Context
import android.os.Handler
import android.os.Looper

class MediaMonitor(private val context: Context, private val onLyricUpdate: (PlaybackState) -> Unit) {

    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = Runnable { updatePlaybackState() }

    // 开始模拟监控（完全不依赖Media3）
    fun startMonitoring() {
        handler.postDelayed(updateRunnable, 1000)
    }

    // 停止监控
    fun stopMonitoring() {
        handler.removeCallbacks(updateRunnable)
    }

    // 模拟播放状态更新
    private fun updatePlaybackState() {
        // 模拟真实歌曲数据
        val title = "Hello World"
        val artist = "Sample Artist"
        val position = System.currentTimeMillis() % 15000 // 模拟15秒歌曲进度
        val isPlaying = true

        // 发送播放状态
        val playbackState = PlaybackState(
            title = title,
            artist = artist,
            position = position,
            isPlaying = isPlaying
        )
        onLyricUpdate(playbackState)

        // 循环更新
        handler.postDelayed(updateRunnable, 500)
    }
}
