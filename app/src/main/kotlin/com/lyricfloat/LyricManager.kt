package com.lyricfloat

import android.content.Context
import android.os.Environment
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.regex.Pattern

class LyricManager(private val context: Context) {
    private val okHttpClient = OkHttpClient()
    private val gson = Gson()

    // 1. 本地LRC文件解析（优先读取本地歌词）
    fun parseLocalLyric(title: String, artist: String): Lyric? {
        val lyricDir = File(Environment.getExternalStorageDirectory(), "Lyrics")
        if (!lyricDir.exists()) lyricDir.mkdirs()

        // 匹配常见的歌词文件名格式
        val lyricFiles = listOf(
            File(lyricDir, "${title}-${artist}.lrc"),
            File(lyricDir, "${artist}-${title}.lrc"),
            File(lyricDir, "${title}.lrc")
        )

        lyricFiles.forEach { file ->
            if (file.exists()) {
                return try {
                    val lyricText = file.readText(Charsets.UTF_8)
                    parseLrcText(lyricText, title, artist)
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }
        }
        return null
    }

    // 2. 网络歌词获取（对接免费歌词API）
    suspend fun fetchOnlineLyric(title: String, artist: String): Lyric? {
        return try {
            // 使用LRCLib免费API（无需key，适合非商用）
            val url = "https://lrclib.net/api/get?track_name=${title}&artist_name=${artist}"
            val request = Request.Builder().url(url).build()
            val response = okHttpClient.newCall(request).execute()

            if (response.isSuccessful) {
                val json = response.body?.string() ?: return null
                val lyricData = gson.fromJson(json, LyricData::class.java)
                parseLrcText(lyricData.lyrics, title, artist)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // 3. LRC文本解析核心逻辑
    private fun parseLrcText(lrcText: String, title: String, artist: String): Lyric {
        val lyricLines = mutableListOf<LyricLine>()
        val pattern = Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\](.*)")

        lrcText.lines().forEach { line ->
            val matcher = pattern.matcher(line)
            if (matcher.find()) {
                val minute = matcher.group(1)?.toLong() ?: 0
                val second = matcher.group(2)?.toLong() ?: 0
                val millisecond = matcher.group(3)?.padEnd(3, '0')?.take(3)?.toLong() ?: 0
                val time = minute * 60 * 1000 + second * 1000 + millisecond
                val text = matcher.group(4)?.trim() ?: ""

                if (text.isNotBlank()) {
                    lyricLines.add(LyricLine(time, text))
                }
            }
        }

        return Lyric(title, artist, lyricLines.sortedBy { it.time })
    }

    // 4. 获取当前播放进度对应的歌词行
    fun getCurrentLyricLine(lyric: Lyric, currentPosition: Long): LyricLine? {
        return lyric.lines.findLast { it.time <= currentPosition }
    }

    // 辅助数据类
    data class Lyric(val title: String, val artist: String, val lines: List<LyricLine>)
    data class LyricLine(val time: Long, val text: String)
    data class LyricData(val lyrics: String) // 对应LRCLib API返回结构
}
