package com.lyricfloat

import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Environment
import java.io.File
import java.nio.charset.Charset
import java.util.Locale

class LocalMusicScanner(private val context: Context) {
    // 支持的音频格式
    private val supportedFormats = listOf(".mp3", ".flac", ".wav", ".m4a", ".ogg", ".ape", ".wma")
    
    // 扫描本地音乐文件
    fun scanLocalMusic(): List<MusicInfo> {
        val musicList = mutableListOf<MusicInfo>()
        val musicDirs = listOf(
            Environment.getExternalStorageDirectory().absolutePath + "/Music",
            Environment.getExternalStorageDirectory().absolutePath + "/Download",
            Environment.getExternalStorageDirectory().absolutePath + "/网易云音乐",
            Environment.getExternalStorageDirectory().absolutePath + "/QQ音乐",
            Environment.getExternalStorageDirectory().absolutePath + "/酷狗音乐",
            Environment.getExternalStorageDirectory().absolutePath + "/酷我音乐",
            Environment.getExternalStorageDirectory().absolutePath + "/虾米音乐",
            Environment.getExternalStorageDirectory().absolutePath + "/咪咕音乐"
        )
        
        musicDirs.forEach { dirPath ->
            val dir = File(dirPath)
            if (dir.exists() && dir.isDirectory) {
                scanDirectory(dir, musicList)
            }
        }
        
        // 扫描外部存储根目录下的音频文件
        val externalDir = Environment.getExternalStorageDirectory()
        if (externalDir.exists() && externalDir.isDirectory) {
            scanDirectory(externalDir, musicList, recursive = false)
        }
        
        return musicList.distinctBy { it.path }
    }
    
    // 新增：从单个文件获取音乐信息（MainActivity调用）
    fun getMusicInfoFromFile(filePath: String): MusicInfo? {
        try {
            val file = File(filePath)
            if (!file.exists() || !isAudioFile(file)) {
                return null
            }
            
            val mmr = MediaMetadataRetriever()
            mmr.setDataSource(file.absolutePath)
            
            val title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: file.nameWithoutExtension
            val artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "未知艺术家"
            val album = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: "未知专辑"
            
            return MusicInfo(
                title = title,
                artist = artist,
                album = album,
                path = file.absolutePath,
                filePath = file.absolutePath // 兼容MainActivity的filePath字段
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
    
    // 递归扫描目录
    private fun scanDirectory(dir: File, musicList: MutableList<MusicInfo>, recursive: Boolean = true) {
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory && recursive) {
                // 跳过系统目录和隐藏目录
                if (!file.name.startsWith(".") && !file.name.equals("Android", ignoreCase = true)) {
                    scanDirectory(file, musicList)
                }
            } else {
                if (isAudioFile(file)) {
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
                            path = file.absolutePath,
                            filePath = file.absolutePath // 兼容字段
                        ))
                    } catch (e: Exception) {
                        // 跳过无法读取的文件
                        e.printStackTrace()
                    }
                }
            }
        }
    }
    
    // 判断是否为音频文件（工具方法）
    private fun isAudioFile(file: File): Boolean {
        val extension = file.extension.lowercase(Locale.getDefault())
        return supportedFormats.contains(".$extension")
    }
    
    // 为单首歌曲下载歌词（suspend方法）
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
            
            // 清理文件名特殊字符（避免文件创建失败）
            val safeTitle = musicInfo.title.replace("[\\\\/:*?\"<>|]".toRegex(), "_")
            val safeArtist = musicInfo.artist.replace("[\\\\/:*?\"<>|]".toRegex(), "_")
            val lyricFile = File(lyricDir, "$safeTitle-$safeArtist.lrc")
            
            val lrcContent = buildLrcContent(lyric, musicInfo.album)
            lyricFile.writeText(lrcContent, Charsets.UTF_8) // 明确指定UTF-8编码
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    // 构建LRC格式内容
    private fun buildLrcContent(lyric: LyricManager.Lyric, album: String): String {
        val sb = StringBuilder()
        sb.append("[ti:${lyric.title}]\n")
        sb.append("[ar:${lyric.artist}]\n")
        sb.append("[al:$album]\n")
        sb.append("[by:LyricFloat]\n\n")
        
        lyric.lines.forEach { line ->
            val timeStr = formatTime(line.time)
            sb.append("[$timeStr]${line.text}\n")
        }
        
        return sb.toString()
    }
    
    // 格式化时间为LRC格式（mm:ss.xx）
    private fun formatTime(timeMs: Long): String {
        val totalSeconds = timeMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val milliseconds = (timeMs % 1000) / 10 // 保留两位毫秒
        
        return String.format(Locale.getDefault(), "%02d:%02d.%02d", minutes, seconds, milliseconds)
    }
    
    // 音乐信息数据类（新增filePath字段兼容MainActivity）
    data class MusicInfo(
        val title: String,
        val artist: String,
        val album: String,
        val path: String,
        val filePath: String // 兼容字段，避免MainActivity报错
    )
}
