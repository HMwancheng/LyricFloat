package com.lyricfloat

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MusicListActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_MUSIC_LIST = "extra_music_list"
    }
    
    private lateinit var musicList: List<LocalMusicScanner.MusicInfo>
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_music_list)
        
        // 获取传递的音乐列表
        val musicArrayList = intent.getSerializableExtra(EXTRA_MUSIC_LIST) as? ArrayList<LocalMusicScanner.MusicInfo>
        musicList = musicArrayList ?: emptyList()
        
        // 初始化UI
        val countText = findViewById<TextView>(R.id.music_count)
        countText.text = "共找到 ${musicList.size} 首歌曲"
        
        val listView = findViewById<ListView>(R.id.music_list_view)
        val adapter = ArrayAdapter<String>(this, android.R.layout.simple_list_item_2, android.R.id.text1).apply {
            musicList.forEach { music ->
                add("${music.title}\n${music.artist} - ${music.album}")
            }
        }
        
        listView.adapter = adapter
        
        // 列表项点击事件
        listView.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            val selectedMusic = musicList[position]
            downloadSingleLyric(selectedMusic)
        }
    }
    
    private fun downloadSingleLyric(music: LocalMusicScanner.MusicInfo) {
        Toast.makeText(this, "正在下载《${music.title}》的歌词...", Toast.LENGTH_SHORT).show()
        
        CoroutineScope(Dispatchers.IO).launch {
            val scanner = LocalMusicScanner(this@MusicListActivity)
            val success = scanner.downloadLyricForSong(music)
            
            runOnUiThread {
                if (success) {
                    Toast.makeText(this@MusicListActivity, 
                        "《${music.title}》歌词下载成功", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MusicListActivity, 
                        "《${music.title}》歌词下载失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
