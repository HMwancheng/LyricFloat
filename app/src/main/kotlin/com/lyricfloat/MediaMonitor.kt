package com.lyricfloat

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.lyricfloat.model.PlaybackState

class MediaMonitor(private val context: Context, private val onPlaybackChanged: (PlaybackState) -> Unit) {

    private var mediaController: MediaControllerCompat? = null
    private val handler = Handler(Looper.getMainLooper())
    private val positionUpdateRunnable = object : Runnable {
        override fun run() {
            updateCurrentPosition()
            handler.postDelayed(this, 500)
        }
    }

    // 音频焦点变化广播接收器
    private val audioBecomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                onPlaybackChanged(PlaybackState("", "", isPlaying = false))
            }
        }
    }

    fun startMonitoring() {
        // 注册音频焦点广播
        context.registerReceiver(
            audioBecomingNoisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        )

        // 初始化媒体控制器
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                val mediaSessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as android.media.session.MediaSessionManager
                val activeSessions = mediaSessionManager.getActiveSessions(null)
                activeSessions.forEach { session ->
                    connectToSession(session.sessionToken)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 开始位置更新
        handler.post(positionUpdateRunnable)
    }

    fun stopMonitoring() {
        context.unregisterReceiver(audioBecomingNoisyReceiver)
        mediaController?.unregisterCallback(mediaControllerCallback)
        handler.removeCallbacks(positionUpdateRunnable)
    }

    private fun connectToSession(token: MediaSessionCompat.Token?) {
        token ?: return
        
        try {
            mediaController = MediaControllerCompat(context, token).apply {
                registerCallback(mediaControllerCallback)
                updatePlaybackState()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updatePlaybackState() {
        val controller = mediaController ?: return
        
        val metadata = controller.metadata
        val playbackState = controller.playbackState
        
        val title = metadata?.getString(MediaMetadataCompat.METADATA_KEY_TITLE) ?: ""
        val artist = metadata?.getString(MediaMetadataCompat.METADATA_KEY_ARTIST) ?: ""
        val album = metadata?.getString(MediaMetadataCompat.METADATA_KEY_ALBUM)
        val duration = metadata?.getLong(MediaMetadataCompat.METADATA_KEY_DURATION) ?: 0
        val currentPosition = playbackState?.position ?: 0
        val isPlaying = playbackState?.state == PlaybackStateCompat.STATE_PLAYING

        onPlaybackChanged(
            PlaybackState(
                title = title,
                artist = artist,
                album = album,
                duration = duration,
                currentPosition = currentPosition,
                isPlaying = isPlaying
            )
        )
    }

    private fun updateCurrentPosition() {
        mediaController?.playbackState?.let { state ->
            if (state.state == PlaybackStateCompat.STATE_PLAYING) {
                val currentPosition = System.currentTimeMillis() - state.lastPositionUpdateTime + state.position
                onPlaybackChanged(
                    PlaybackState(
                        title = mediaController?.metadata?.getString(MediaMetadataCompat.METADATA_KEY_TITLE) ?: "",
                        artist = mediaController?.metadata?.getString(MediaMetadataCompat.METADATA_KEY_ARTIST) ?: "",
                        currentPosition = currentPosition,
                        isPlaying = true
                    )
                )
            }
        }
    }

    private val mediaControllerCallback = object : MediaControllerCompat.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            updatePlaybackState()
        }

        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            updatePlaybackState()
        }

        override fun onSessionDestroyed() {
            mediaController?.unregisterCallback(this)
            mediaController = null
        }
    }
}
