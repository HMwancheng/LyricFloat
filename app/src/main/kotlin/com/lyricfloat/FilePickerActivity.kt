package com.lyricfloat

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class FilePickerActivity : AppCompatActivity() {
    private lateinit var currentPath: String
    private lateinit var fileListAdapter: ArrayAdapter<String>
    private val selectedFolders = mutableListOf<String>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_picker)
        
        // 初始化UI组件
        val currentPathText = findViewById<TextView>(R.id.current_path)
        val fileListView = findViewById<ListView>(R.id.file_list)
        val backBtn = findViewById<Button>(R.id.btn_back)
        val confirmBtn = findViewById<Button>(R.id.btn_confirm)
        
        // 默认路径
        currentPath = Environment.getExternalStorageDirectory().absolutePath
        currentPathText.text = currentPath
        
        // 设置列表适配器
        fileListAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_multiple_choice)
        fileListView.adapter = fileListAdapter
        fileListView.choiceMode = ListView.CHOICE_MODE_MULTIPLE
        
        // 加载目录内容
        loadDirectory(currentPath)
        
        // 返回上级按钮
        backBtn.setOnClickListener {
            val parentFile = File(currentPath).parentFile
            if (parentFile != null) {
                loadDirectory(parentFile.absolutePath)
            }
        }
        
        // 确认选择按钮
        confirmBtn.visibility = View.VISIBLE
        confirmBtn.setOnClickListener {
            val resultIntent = Intent()
            resultIntent.putStringArrayListExtra("selected_folders", ArrayList(selectedFolders))
            setResult(RESULT_OK, resultIntent)
            finish()
        }
        
        // 列表项点击事件
        fileListView.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            val item = fileListAdapter.getItem(position) ?: return@OnItemClickListener
            
            if (item.startsWith("[DIR] ")) {
                val dirName = item.substring(5)
                val newPath = File(currentPath, dirName).absolutePath
                val dirFile = File(newPath)
                
                if (dirFile.name == "..") {
                    // 返回上级
                    loadDirectory(dirFile.absolutePath)
                } else {
                    // 处理文件夹选择
                    if (fileListView.isItemChecked(position)) {
                        if (!selectedFolders.contains(newPath)) {
                            selectedFolders.add(newPath)
                        }
                        
                        // 双击进入文件夹（延迟处理）
                        Handler(Looper.getMainLooper()).postDelayed({
                            if (fileListView.isItemChecked(position)) {
                                loadDirectory(newPath)
                            }
                        }, 300)
                    } else {
                        selectedFolders.remove(newPath)
                    }
                }
            }
        }
    }
    
    private fun loadDirectory(path: String) {
        currentPath = path
        findViewById<TextView>(R.id.current_path).text = path
        
        val fileList = mutableListOf<String>()
        val dir = File(path)
        
        // 添加返回上级
        if (dir.parent != null) {
            fileList.add("[DIR] ..")
        }
        
        // 获取所有目录
        dir.listFiles()?.filter { it.isDirectory }?.forEach { file ->
            if (!file.name.startsWith(".") && 
                !file.name.equals("Android", ignoreCase = true) &&
                !file.name.equals("data", ignoreCase = true)) {
                fileList.add("[DIR] ${file.name}")
            }
        }
        
        // 更新列表
        fileListAdapter.clear()
        fileListAdapter.addAll(fileList)
        
        // 恢复选中状态
        val listView = findViewById<ListView>(R.id.file_list)
        for (i in 0 until fileListAdapter.count) {
            val item = fileListAdapter.getItem(i) ?: continue
            if (item.startsWith("[DIR] ")) {
                val dirName = item.substring(5)
                val itemPath = File(currentPath, dirName).absolutePath
                listView.setItemChecked(i, selectedFolders.contains(itemPath))
            }
        }
    }
}
