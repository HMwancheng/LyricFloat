package com.lyricfloat.model

data class Lyric(
    val title: String,
    val artist: String,
    val lines: List<LyricLine>,
    val source: LyricSource
)

data class LyricLine(
    val time: Long,  // 歌词时间戳（毫秒）
    val text: String // 歌词内容
)

enum class LyricSource {
    EMBEDDED,  // 内嵌歌词
    LOCAL_FILE,// 本地LRC文件
    NETWORK    // 网络获取
}

// 播放状态模型
data class PlaybackState(
    val title: String,
    val artist: String,
    val album: String? = null,
    val duration: Long = 0,
    val currentPosition: Long = 0,
    val isPlaying: Boolean = false
)
