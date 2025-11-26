package com.lyricfloat

import android.content.Context
import android.os.Handler
import android.os.Looper

// 确保PlaybackState类唯一（避免与系统类冲突）
data class AppPlaybackState(
    val title: String = "",
    val artist: String = "",
    val position: Long = 0L,
    val isPlaying: Boolean = false
)

class MediaMonitor(private val context: Context, private val onLyricUpdate: (AppPlaybackState) -> Unit) {

    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = Runnable { updatePlaybackState() }

    fun startMonitoring() {
        handler.postDelayed(updateRunnable, 1000)
    }

    fun stopMonitoring() {
        handler.removeCallbacks(updateRunnable)
    }

    private fun updatePlaybackState() {
        // 纯模拟数据，避免所有系统API依赖
        val playbackState = AppPlaybackState(
            title = "Sample Song",
            artist = "Sample Artist",
            position = System.currentTimeMillis() % 15000,
            isPlaying = true
        )
        onLyricUpdate(playbackState)
        handler.postDelayed(updateRunnable, 500)
    }
}
