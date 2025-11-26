package com.lyricfloat

data class PlaybackState(
    val title: String = "",
    val artist: String = "",
    val position: Long = 0L,
    val isPlaying: Boolean = false
)
