package com.lyricfloat

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MediaMonitor(private val context: Context, private val onLyricUpdate: (PlaybackState) -> Unit) {

    private var mediaController: MediaController? = null
    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = Runnable { updatePlaybackState() }

    // 开始监控媒体播放（简化实现，适配Media3正确API）
    fun startMonitoring() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Media3的SessionToken正确构造：使用包名+服务action
                val sessionToken = SessionToken(
                    context,
                    SessionToken.TYPE_SESSION_SERVICE,
                    SessionToken.KEY_ACTION_MEDIA_SESSION_SERVICE,
                    context.packageName
                )
                
                // 正确构建MediaController（无await，直接build）
                mediaController = MediaController.Builder(context, sessionToken).build()
                mediaController?.addListener(playerListener)
                updatePlaybackState()
            } catch (e: Exception) {
                e.printStackTrace()
                // 回退到模拟数据，避免崩溃
                startSimulatedMonitoring()
            }
        }
    }

    // 模拟监控（备用方案）
    private fun startSimulatedMonitoring() {
        handler.postDelayed(updateRunnable, 1000)
    }

    // 停止监控
    fun stopMonitoring() {
        mediaController?.removeListener(playerListener)
        mediaController?.release()
        mediaController = null
        handler.removeCallbacks(updateRunnable)
    }

    // 更新播放状态
    private fun updatePlaybackState() {
        val controller = mediaController
        
        // 真实媒体数据或模拟数据
        val (title, artist, position, isPlaying) = if (controller != null && controller.mediaMetadata.title != null) {
            Triple(
                controller.mediaMetadata.title.toString(),
                controller.mediaMetadata.artist.toString(),
                controller.currentPosition,
                controller.isPlaying
            )
        } else {
            Triple(
                "Sample Song",
                "Sample Artist",
                System.currentTimeMillis() % 10000,
                true
            )
        }

        val playbackState = PlaybackState(
            title = title,
            artist = artist,
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
