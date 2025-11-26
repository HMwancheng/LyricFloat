package com.lyricfloat

import android.content.Context
import android.media.browse.MediaBrowser
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper

class MediaMonitor(private val context: Context, private val onLyricUpdate: (PlaybackState) -> Unit) {

    private var mediaBrowser: MediaBrowser? = null
    private var mediaController: MediaController? = null
    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = Runnable { updatePlaybackState() }

    // 开始监听系统媒体会话
    fun startMonitoring() {
        // 初始化MediaBrowser连接系统媒体服务
        mediaBrowser = MediaBrowser(
            context,
            MediaBrowser.ServiceBrowserRoot("media", null),
            object : MediaBrowser.ConnectionCallback() {
                override fun onConnected() {
                    mediaBrowser?.sessionToken?.let { token ->
                        mediaController = MediaController(context, token)
                        mediaController?.registerCallback(mediaControllerCallback)
                    }
                    updatePlaybackState()
                    handler.postDelayed(updateRunnable, 500)
                }

                override fun onConnectionFailed() {
                    // 连接失败时回退到模拟数据
                    startSimulatedMonitoring()
                }
            },
            null
        ).apply { connect() }
    }

    // 停止监听
    fun stopMonitoring() {
        mediaController?.unregisterCallback(mediaControllerCallback)
        mediaBrowser?.disconnect()
        handler.removeCallbacks(updateRunnable)
    }

    // 更新真实播放状态
    private fun updatePlaybackState() {
        val controller = mediaController ?: return

        // 获取真实媒体信息
        val metadata = controller.metadata
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "未知歌曲"
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "未知歌手"
        val position = controller.playbackState?.position ?: 0L
        val isPlaying = controller.playbackState?.state == PlaybackState.STATE_PLAYING

        // 发送真实播放状态
        onLyricUpdate(PlaybackState(title, artist, position, isPlaying))
        handler.postDelayed(updateRunnable, 500)
    }

    // 媒体控制器回调
    private val mediaControllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            updatePlaybackState()
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            updatePlaybackState()
        }
    }

    // 模拟监控（备用）
    private fun startSimulatedMonitoring() {
        updatePlaybackState()
        handler.postDelayed(updateRunnable, 1000)
    }
}

// 修正PlaybackState数据类（与媒体库类型对齐）
data class PlaybackState(
    val title: String = "",
    val artist: String = "",
    val position: Long = 0L,
    val isPlaying: Boolean = false
)
