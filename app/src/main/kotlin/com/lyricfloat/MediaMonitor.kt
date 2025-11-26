package com.lyricfloat

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.session.MediaLibraryService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MediaMonitor(private val context: Context, private val onLyricUpdate: (PlaybackState) -> Unit) {

    private var mediaController: MediaController? = null
    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = Runnable { updatePlaybackState() }

    // 开始监控媒体播放
    fun startMonitoring() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val sessionToken = SessionToken(context, MediaLibraryService::class.java)
                mediaController = MediaController.Builder(context, sessionToken).build()
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
    }
}
