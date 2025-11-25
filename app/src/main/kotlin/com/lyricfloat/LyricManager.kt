package com.lyricfloat

import android.content.Context
import android.os.Environment
import com.lyricfloat.model.Lyric
import com.lyricfloat.model.LyricLine
import com.lyricfloat.model.LyricSource
import com.mpatric.mp3agic.ID3v2
import com.mpatric.mp3agic.Mp3File
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.io.File

class LyricManager(private val context: Context) {

    // LrcLib API接口
    private interface LrcLibApi {
        @GET("api/getLyric")
        suspend fun getLyric(
            @Query("title") title: String,
            @Query("artist") artist: String
        ): LrcLibResponse
    }

    data class LrcLibResponse(
        val status: Int,
        val lyric: String?,
        val artist: String?,
        val title: String?
    )

    private val api = Retrofit.Builder()
        .baseUrl("https://lrclib.net/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(LrcLibApi::class.java)

    // 歌词获取优先级（可从设置读取）
    private val lyricPriority = listOf(
        LyricSource.EMBEDDED,
        LyricSource.LOCAL_FILE,
        LyricSource.NETWORK
    )

    suspend fun getLyric(playbackState: PlaybackState): Lyric? {
        // 根据优先级获取歌词
        lyricPriority.forEach { source ->
            val lyric = when (source) {
                LyricSource.EMBEDDED -> getEmbeddedLyric(playbackState)
                LyricSource.LOCAL_FILE -> getLocalFileLyric(playbackState)
                LyricSource.NETWORK -> getNetworkLyric(playbackState)
            }
            if (lyric != null) return lyric
        }
        return null
    }

    // 从歌曲文件内嵌歌词获取
    private fun getEmbeddedLyric(playbackState: PlaybackState): Lyric? {
        return try {
            // 扫描媒体库找到对应歌曲文件
            val filePath = findSongFile(playbackState.title, playbackState.artist)
            if (filePath != null) {
                val mp3file = Mp3File(filePath)
                if (mp3file.hasId3v2Tag()) {
                    val id3v2Tag: ID3v2 = mp3file.id3v2Tag
                    val lyrics = id3v2Tag.lyrics
                    if (lyrics != null && lyrics.isNotEmpty()) {
                        parseLyricText(lyrics, playbackState, LyricSource.EMBEDDED)
                    } else null
                } else null
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // 从本地LRC文件获取
    private fun getLocalFileLyric(playbackState: PlaybackState): Lyric? {
        return try {
            // 构建歌词文件名（歌曲名 - 歌手名.lrc）
            val lyricFileName = "${playbackState.title} - ${playbackState.artist}.lrc"
            val lyricDir = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "lyrics")
            
            // 检查应用私有目录
            val lyricFile = File(lyricDir, lyricFileName)
            if (lyricFile.exists()) {
                val lyricText = lyricFile.readText()
                parseLyricText(lyricText, playbackState, LyricSource.LOCAL_FILE)
            } else {
                // 检查公共音乐目录
                val publicLyricFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), lyricFileName)
                if (publicLyricFile.exists()) {
                    val lyricText = publicLyricFile.readText()
                    parseLyricText(lyricText, playbackState, LyricSource.LOCAL_FILE)
                } else null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // 从网络获取歌词
    private suspend fun getNetworkLyric(playbackState: PlaybackState): Lyric? {
        return try {
            val response = api.getLyric(playbackState.title, playbackState.artist)
            if (response.status == 200 && response.lyric != null) {
                // 缓存歌词到本地
                cacheLyricToFile(response.lyric!!, playbackState)
                parseLyricText(response.lyric!!, playbackState, LyricSource.NETWORK)
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // 解析歌词文本
    private fun parseLyricText(lyricText: String, playbackState: PlaybackState, source: LyricSource): Lyric {
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
        
        return Lyric(playbackState.title, playbackState.artist, lines.sortedBy { it.time }, source)
    }

    // 缓存歌词到文件
    private fun cacheLyricToFile(lyricText: String, playbackState: PlaybackState) {
        try {
            val lyricDir = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "lyrics")
            if (!lyricDir.exists()) lyricDir.mkdirs()
            
            val lyricFileName = "${playbackState.title} - ${playbackState.artist}.lrc"
            val lyricFile = File(lyricDir, lyricFileName)
            lyricFile.writeText(lyricText)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // 辅助方法：查找歌曲文件路径
    private fun findSongFile(title: String, artist: String): String? {
        // 实际应用中需通过MediaStore查询，此处简化
        return null
    }

    // 扫描本地歌曲并下载歌词
    suspend fun scanLocalSongsAndDownloadLyrics() {
        // 1. 通过MediaStore查询所有本地歌曲
        // 2. 遍历歌曲列表，调用getNetworkLyric下载歌词
        // 3. 缓存到本地
    }
}
