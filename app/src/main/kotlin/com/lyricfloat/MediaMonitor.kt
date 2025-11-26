package com.lyricfloat

import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.session.SessionToken.TYPE_LIBRARY_SERVICE
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MediaMonitor(private val context: Context, private val onLyricUpdate: (PlaybackState) -> Unit) {

    private var mediaController: MediaController? = null
    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = Runnable { updatePlaybackState() }

    // 开始监控媒体播放
    fun startMonitoring() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // 修正SessionToken构造：使用ComponentName（包名+服务类名）
                val componentName = ComponentName(
                    context.packageName,
                    "androidx.media3.session.MediaLibraryService" // 系统媒体服务类名
                )
                val sessionToken = SessionToken(context, componentName, TYPE_LIBRARY_SERVICE)
                
                // 修正MediaController构建（使用await()等待异步构建）
                mediaController = MediaController.Builder(context, sessionToken)
                    .buildAsync()
                    .await() // 异步构建并等待结果
                
                mediaController?.addListener(playerListener)
                updatePlaybackState()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        handler.postDelayed(updateRunnable, 1000)
    }

    // 停止监控
    fun stopMonitoring() {
        mediaController?.removeListener(playerListener)
        mediaController?.release() // 释放资源
        mediaController = null
        handler.removeCallbacks(updateRunnable)
    }

    // 更新播放状态
    private fun updatePlaybackState() {
        val controller = mediaController ?: return

        val metadata = controller.mediaMetadata
        val title = metadata.title ?: ""
        val artist = metadata.artist ?: ""
        val position = controller.currentPosition
        val isPlaying = controller.isPlaying

        val playbackState = PlaybackState(
            title = title.toString(),
            artist = artist.toString(),
            position = position,
            isPlaying = isPlaying
        )

        onLyricUpdate(playbackState)
        handler.postDelayed(updateRunnable, 1000)
    }

    // 播放器监听器
    private val playerListener = object : Player.Listener {
        override fun onMediaMetadataChanged(metadata: MediaMetadata) {
            updatePlaybackState()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            updatePlaybackState()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updatePlaybackState()
        }
    }
}
