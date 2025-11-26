package com.lyricfloat

import android.content.Context
import java.io.File

class LyricManager(private val context: Context) {

    // 解析歌词文本（原生实现）
    private fun parseLyricText(lyricText: String, playbackState: PlaybackState): Lyric {
        val lines = mutableListOf<LyricLine>()
        val linePattern = Regex("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\](.*)")
        
        lyricText.lines().forEach { line ->
            val matchResult = linePattern.find(line)
            if (matchResult != null) {
                val (minutes, seconds, milliseconds, text) = matchResult.destructured
                val time = minutes.toLong() * 60 * 1000 + seconds.toLong() * 1000 + milliseconds.padEnd(3, '0').take(3).toLong()
                if (text.isNotBlank()) {
                    lines.add(LyricLine(time, text))
                }
            }
        }
        
        return Lyric(playbackState.title, playbackState.artist, lines.sortedBy { it.time })
    }

    // 从文件加载歌词
    fun loadLyricFromFile(filePath: String, playbackState: PlaybackState): Lyric? {
        return try {
            val file = File(filePath)
            if (file.exists()) {
                val lyricText = file.readText()
                parseLyricText(lyricText, playbackState)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // 从网络加载歌词（示例）
    suspend fun loadLyricFromNetwork(playbackState: PlaybackState): Lyric? {
        // 实际项目中替换为真实的网络请求
        return null
    }

    // 获取当前歌词行
    fun getCurrentLyricLine(lyric: Lyric, position: Long): LyricLine? {
        return lyric.lines.findLast { it.time <= position }
    }
}

// 歌词数据类
data class Lyric(
    val title: String,
    val artist: String,
    val lines: List<LyricLine>
)

// 歌词行数据类
data class LyricLine(
    val time: Long,
    val text: String
)
