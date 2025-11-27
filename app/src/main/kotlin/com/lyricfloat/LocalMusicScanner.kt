package com.lyricfloat

import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Environment
import java.io.File
import java.util.Locale

class LocalMusicScanner(private val context: Context) {
    // 支持的音频格式
    private val supportedFormats = listOf(".mp3", ".flac", ".wav", ".m4a", ".ogg")
    
    // 扫描本地音乐文件
    fun scanLocalMusic(): List<MusicInfo> {
        val musicList = mutableListOf<MusicInfo>()
        val musicDirs = listOf(
            Environment.getExternalStorageDirectory().absolutePath + "/Music",
            Environment.getExternalStorageDirectory().absolutePath + "/Download",
            Environment.getExternalStorageDirectory().absolutePath + "/网易云音乐",
            Environment.getExternalStorageDirectory().absolutePath + "/QQ音乐"
        )
        
        musicDirs.forEach { dirPath ->
            val dir = File(dirPath)
            if (dir.exists() && dir.isDirectory) {
                scanDirectory(dir, musicList)
            }
        }
        
        return musicList.distinctBy { it.path }
    }
    
    private fun scanDirectory(dir: File, musicList: MutableList<MusicInfo>) {
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                scanDirectory(file, musicList)
            } else {
                val extension = file.extension.lowercase(Locale.getDefault())
                if (supportedFormats.contains(".$extension")) {
                    try {
                        val mmr = MediaMetadataRetriever()
                        mmr.setDataSource(file.absolutePath)
                        
                        val title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: file.nameWithoutExtension
                        val artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "未知艺术家"
                        val album = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: "未知专辑"
                        
                        musicList.add(MusicInfo(
                            title = title,
                            artist = artist,
                            album = album,
                            path = file.absolutePath
                        ))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }
    
    // 为单首歌曲下载歌词
    suspend fun downloadLyricForSong(musicInfo: MusicInfo): Boolean {
        val lyricManager = LyricManager(context)
        val lyric = lyricManager.fetchOnlineLyric(musicInfo.title, musicInfo.artist)
        
        lyric?.let {
            return saveLyricToLocal(musicInfo, it)
        } ?: return false
    }
    
    // 批量下载歌词
    suspend fun batchDownloadLyrics(musicList: List<MusicInfo>): Int {
        var successCount = 0
        musicList.forEach { music ->
            if (downloadLyricForSong(music)) {
                successCount++
            }
        }
        return successCount
    }
    
    // 保存歌词到本地
    private fun saveLyricToLocal(musicInfo: MusicInfo, lyric: LyricManager.Lyric): Boolean {
        return try {
            val lyricDir = File(Environment.getExternalStorageDirectory(), "Lyrics")
            if (!lyricDir.exists()) lyricDir.mkdirs()
            
            val lyricFile = File(lyricDir, "${musicInfo.title}-${musicInfo.artist}.lrc")
            val lrcContent = buildLrcContent(lyric)
            
            lyricFile.writeText(lrcContent, Charsets.UTF_8)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    // 构建LRC格式内容
    private fun buildLrcContent(lyric: LyricManager.Lyric): String {
        val sb = StringBuilder()
        sb.append("[ti:${lyric.title}]\n")
        sb.append("[ar:${lyric.artist}]\n")
        sb.append("[al:${lyric.album}]\n\n")
        
        lyric.lines.forEach { line ->
            val timeStr = formatTime(line.time)
            sb.append("[$timeStr]${line.text}\n")
        }
        
        return sb.toString()
    }
    
    // 格式化时间为LRC格式
    private fun formatTime(timeMs: Long): String {
        val totalSeconds = timeMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val milliseconds = timeMs % 1000 / 10
        
        return String.format(Locale.getDefault(), "%02d:%02d.%02d", minutes, seconds, milliseconds)
    }
    
    data class MusicInfo(
        val title: String,
        val artist: String,
        val album: String,
        val path: String
    )
}
