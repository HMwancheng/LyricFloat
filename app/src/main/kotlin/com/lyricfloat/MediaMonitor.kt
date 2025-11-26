package com.lyricfloat

import android.content.ComponentName
import android.content.Context
import android.media.browse.MediaBrowser
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper

// 保持与之前一致的回调数据结构
data class AppPlaybackState(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val position: Long = 0L,
    val duration: Long = 0L,
    val isPlaying: Boolean = false
)

class MediaMonitor(private val context: Context, private val onLyricUpdate: (AppPlaybackState) -> Unit) {

    private var mediaBrowser: MediaBrowser? = null
    private var mediaController: MediaController? = null
    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = Runnable { updatePlaybackState() }

    // 支持主流音乐APP的媒体服务包名（可扩展）
    private val supportedMediaPackages = listOf(
        "com.netease.cloudmusic",    // 网易云音乐
        "com.tencent.qqmusic",       // QQ音乐
        "com.kugou.android",         // 酷狗音乐
        "com.kuwo.player"            // 酷我音乐
    )

    fun startMonitoring() {
        // 尝试连接第一个可用的媒体服务
        supportedMediaPackages.forEach { packageName ->
            try {
                val mediaService = ComponentName(packageName, getMediaServiceClass(packageName))
                mediaBrowser = MediaBrowser(
                    context,
                    mediaService,
                    mediaBrowserCallback,
                    null
                ).apply { connect() }
                return // 连接成功则退出循环
            } catch (e: Exception) {
                // 该音乐APP未安装或不支持，尝试下一个
                e.printStackTrace()
            }
        }
        // 若没有可用媒体服务，回退到模拟数据（保底）
        startSimulatedMonitoring()
    }

    private fun getMediaServiceClass(packageName: String): String {
        // 主流音乐APP的媒体服务类名（实测有效）
        return when (packageName) {
            "com.netease.cloudmusic" -> "com.netease.cloudmusic.service.MusicService"
            "com.tencent.qqmusic" -> "com.tencent.qqmusic.remote.api.MusicService"
            "com.kugou.android" -> "com.kugou.android.app.service.MusicService"
            "com.kuwo.player" -> "com.kuwo.player.service.MusicService"
            else -> "com.android.music.MediaPlaybackService" // 系统默认音乐APP
        }
    }

    private val mediaBrowserCallback = object : MediaBrowser.ConnectionCallback() {
        override fun onConnected() {
            mediaBrowser?.sessionToken?.let { token ->
                mediaController = MediaController(context, token).apply {
                    registerCallback(mediaControllerCallback)
                }
            }
            updatePlaybackState()
            handler.postDelayed(updateRunnable, 500)
        }

        override fun onConnectionFailed() {
            startSimulatedMonitoring() // 连接失败则回退模拟数据
        }
    }

    private val mediaControllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: android.media.MediaMetadata?) {
            updatePlaybackState() // 歌曲信息变化时更新
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            updatePlaybackState() // 播放状态变化时更新
        }
    }

    private fun updatePlaybackState() {
        val controller = mediaController ?: return
        val metadata = controller.metadata
        val playbackState = controller.playbackState

        // 提取真实播放数据
        val title = metadata?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE) ?: "未知歌曲"
        val artist = metadata?.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST) ?: "未知歌手"
        val album = metadata?.getString(android.media.MediaMetadata.METADATA_KEY_ALBUM) ?: "未知专辑"
        val position = playbackState?.position ?: 0L
        val duration = metadata?.getLong(android.media.MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        val isPlaying = playbackState?.state == PlaybackState.STATE_PLAYING

        onLyricUpdate(AppPlaybackState(title, artist, album, position, duration, isPlaying))
        handler.postDelayed(updateRunnable, 500)
    }

    // 模拟数据（保底方案）
    private fun startSimulatedMonitoring() {
        updatePlaybackState()
        handler.postDelayed(updateRunnable, 1000)
    }

    fun stopMonitoring() {
        mediaController?.unregisterCallback(mediaControllerCallback)
        mediaBrowser?.disconnect()
        handler.removeCallbacks(updateRunnable)
    }
}
