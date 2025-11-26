package com.lyricfloat

import android.content.Context
import android.os.Environment
import java.io.File
import java.net.URL
import java.util.regex.Pattern

class LyricManager(private val context: Context) {

    // 从本地LRC文件解析歌词
    fun parseLyricFromFile(title: String, artist: String): Lyric? {
        val lyricDir = File(Environment.getExternalStorageDirectory(), "Lyrics")
        if (!lyricDir.exists()) lyricDir.mkdirs()
        val lyricFile = File(lyricDir, "${title}-${artist}.lrc")
        if (!lyricFile.exists()) return null

        return try {
            val lyricText = lyricFile.readText(Charsets.UTF_8)
            parseLyricText(lyricText, title, artist)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // 从网络获取歌词（示例：对接LrcLib API）
    suspend fun fetchLyricFromNetwork(title: String, artist: String): Lyric? {
        return try {
            val url = "https://lrclib.net/api/get?track_name=${title}&artist_name=${artist}"
            val response = URL(url).readText(Charsets.UTF_8)
            // 解析JSON响应（需添加Gson依赖）
            val lyricText = extractLyricFromJson(response)
            parseLyricText(lyricText, title, artist)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // 解析LRC文本
    private fun parseLyricText(lyricText: String, title: String, artist: String): Lyric {
        val lines = mutableListOf<LyricLine>()
        val pattern = Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\](.*)")

        lyricText.lines().forEach { line ->
            val matcher = pattern.matcher(line)
            if (matcher.find()) {
                val minute = matcher.group(1)?.toLong() ?: 0
                val second = matcher.group(2)?.toLong() ?: 0
                val millisecond = matcher.group(3)?.padEnd(3, '0')?.take(3)?.toLong() ?: 0
                val time = minute * 60 * 1000 + second * 1000 + millisecond
                val text = matcher.group(4) ?: ""
                if (text.isNotBlank()) lines.add(LyricLine(time, text))
            }
        }

        return Lyric(title, artist, lines.sortedBy { it.time })
    }

    // 获取当前歌词行
    fun getCurrentLyricLine(lyric: Lyric, position: Long): LyricLine? {
        return lyric.lines.findLast { it.time <= position }
    }

    // 辅助方法：从JSON响应提取歌词（需添加Gson依赖）
    private fun extractLyricFromJson(json: String): String {
        // 示例：使用Gson解析
        // val jsonObject = JsonParser.parseString(json).asJsonObject
        // return jsonObject.get("lyrics").asString
        return json // 实际需替换为真实解析逻辑
    }
}

// 歌词数据类
data class Lyric(val title: String, val artist: String, val lines: List<LyricLine>)
data class LyricLine(val time: Long, val text: String)
