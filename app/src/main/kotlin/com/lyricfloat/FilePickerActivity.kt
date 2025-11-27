package com.lyricfloat

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class FilePickerActivity : AppCompatActivity() {
    private lateinit var currentPathTextView: TextView
    private lateinit var fileListView: ListView
    private lateinit var currentDir: File
    private var selectDirectory = false
    
    // 支持的音频格式
    private val supportedFormats = listOf(".mp3", ".flac", ".wav", ".m4a", ".ogg", ".ape", ".wma")
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_picker)
        
        currentPathTextView = findViewById(R.id.current_path)
        fileListView = findViewById(R.id.file_list)
        
        selectDirectory = intent.getBooleanExtra("select_directory", false)
        
        // 初始目录为外部存储根目录
        currentDir = Environment.getExternalStorageDirectory()
        updateFileList()
        
        // 文件列表点击事件
        fileListView.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            val selectedItem = fileListView.adapter.getItem(position) as String
            val selectedFile = File(currentDir, selectedItem)
            
            if (selectedFile.isDirectory) {
                // 进入子目录
                currentDir = selectedFile
                updateFileList()
            } else if (!selectDirectory && isAudioFile(selectedFile)) {
                // 选择音频文件
                val resultIntent = Intent()
                resultIntent.putExtra("selected_file", selectedFile.absolutePath)
                setResult(RESULT_OK, resultIntent)
                finish()
            }
        }
        
        // 返回按钮
        findViewById<View>(R.id.btn_back).setOnClickListener {
            if (currentDir.parentFile != null) {
                currentDir = currentDir.parentFile!!
                updateFileList()
            } else {
                finish()
            }
        }
        
        // 确认选择按钮（用于选择目录）
        findViewById<View>(R.id.btn_confirm).setOnClickListener {
            if (selectDirectory) {
                val resultIntent = Intent()
                resultIntent.putExtra("selected_directory", currentDir.absolutePath)
                setResult(RESULT_OK, resultIntent)
                finish()
            }
        }
        
        // 显示/隐藏确认按钮
        findViewById<View>(R.id.btn_confirm).visibility = 
            if (selectDirectory) View.VISIBLE else View.GONE
    }
    
    // 更新文件列表
    private fun updateFileList() {
        currentPathTextView.text = currentDir.absolutePath
        
        val files = currentDir.listFiles() ?: return
        val fileNames = mutableListOf<String>()
        
        // 先添加目录
        files.filter { it.isDirectory && !it.name.startsWith(".") }
            .sortedBy { it.name }
            .forEach { fileNames.add(it.name + "/") }
        
        // 再添加文件（如果不是选择目录模式）
        if (!selectDirectory) {
            files.filter { it.isFile && isAudioFile(it) }
                .sortedBy { it.name }
                .forEach { fileNames.add(it.name) }
        }
        
        // 设置适配器
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, fileNames)
        fileListView.adapter = adapter
    }
    
    // 判断是否为音频文件
    private fun isAudioFile(file: File): Boolean {
        val extension = file.extension.lowercase()
        return supportedFormats.contains(".$extension")
    }
}
