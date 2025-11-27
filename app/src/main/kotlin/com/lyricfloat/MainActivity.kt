package com.lyricfloat

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : AppCompatActivity() {
    private val OVERLAY_PERMISSION_REQUEST_CODE = 1001
    private val STORAGE_PERMISSION_REQUEST_CODE = 1002
    
    private lateinit var statusText: TextView
    private lateinit var debugModeBtn: Button
    private lateinit var scanLocalBtn: Button
    private lateinit var manualSelectBtn: Button
    
    private var lyricService: FloatLyricService? = null
    private var isServiceBound = false
    private var isDebugMode = false
    
    // Service连接
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as FloatLyricService.LocalBinder
            lyricService = binder.getService()
            isServiceBound = true
            updateStatus("已连接到歌词服务")
            
            // 连接成功后立即设置测试歌词
            lyricService?.setTestLyric("初始化测试", "测试歌手")
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            isServiceBound = false
            updateStatus("与歌词服务断开连接")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 初始化控件
        statusText = findViewById(R.id.status_text)
        debugModeBtn = findViewById(R.id.debug_mode_btn)
        scanLocalBtn = findViewById(R.id.scan_local_btn)
        manualSelectBtn = findViewById(R.id.manual_select_btn)
        
        // 保存MainActivity实例到Application
        (application as LyricFloatApp).mainActivity = this
        
        // 加载设置页面
        supportFragmentManager.beginTransaction()
            .replace(R.id.settings_container, SettingsFragment())
            .commit()

        // 检查权限
        checkPermissions()
        
        // 绑定服务
        Intent(this@MainActivity, FloatLyricService::class.java).also { intent ->
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
        
        // 设置按钮点击事件
        setupButtonListeners()
    }
    
    // 检查所有必要权限
    private fun checkPermissions() {
        // 检查悬浮窗权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            requestOverlayPermission()
        }
        
        // 检查存储权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) 
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.READ_MEDIA_AUDIO),
                    STORAGE_PERMISSION_REQUEST_CODE
                )
            }
        } else {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) 
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    STORAGE_PERMISSION_REQUEST_CODE
                )
            }
        }
    }
    
    // 请求悬浮窗权限
    private fun requestOverlayPermission() {
        AlertDialog.Builder(this)
            .setTitle("需要悬浮窗权限")
            .setMessage("应用需要悬浮窗权限才能显示歌词，请前往设置开启")
            .setPositiveButton("去设置") { _, _ ->
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:$packageName")
                )
                startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE)
            }
            .setNegativeButton("取消") { dialog, _ ->
                dialog.dismiss()
                updateStatus("未授予悬浮窗权限，无法显示歌词")
            }
            .show()
    }
    
    private fun setupButtonListeners() {
        // 调试模式按钮
        debugModeBtn.setOnClickListener {
            if (!isServiceBound) {
                Toast.makeText(this, "服务未连接", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            isDebugMode = !isDebugMode
            if (isDebugMode) {
                debugModeBtn.text = "退出调试"
                lyricService?.setDebugMode(true)
                updateStatus("调试模式已开启 - 显示测试歌词")
            } else {
                debugModeBtn.text = "调试模式"
                lyricService?.setDebugMode(false)
                updateStatus("调试模式已关闭 - 监听实际播放")
            }
        }
        
        // 扫描本地歌曲按钮
        scanLocalBtn.setOnClickListener {
            if (checkStoragePermission()) {
                scanLocalSongs()
            } else {
                requestStoragePermission()
            }
        }
        
        // 手动选择文件按钮
        manualSelectBtn.setOnClickListener {
            if (checkStoragePermission()) {
                val intent = Intent(this, FilePickerActivity::class.java)
                startActivityForResult(intent, 1003)
            } else {
                requestStoragePermission()
            }
        }
        
        // 刷新歌词按钮
        findViewById<Button>(R.id.refresh_lyric_btn).setOnClickListener {
            if (isServiceBound) {
                lyricService?.refreshCurrentLyric()
                updateStatus("正在刷新歌词...")
                lyricService?.setTestLyric("刷新测试", "测试歌手")
            } else {
                Toast.makeText(this, "服务未连接", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    // 检查存储权限（修复后：确保返回Boolean类型，语法正确）
    private fun checkStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
        } else {
            ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    // 请求存储权限
    private fun requestStoragePermission() {
        ActivityCompat.requestPermissions(
            this,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
            } else {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            },
            STORAGE_PERMISSION_REQUEST_CODE
        )
    }
    
    // 扫描本地歌曲
    fun scanLocalSongs() {
        scanLocalBtn.isEnabled = false
        scanLocalBtn.text = "扫描中..."
        updateStatus("正在扫描本地歌曲...")
        
        CoroutineScope(Dispatchers.IO).launch {
            val scanner = LocalMusicScanner(this@MainActivity)
            val musicList = scanner.scanLocalMusic()
            
            runOnUiThread {
                updateStatus("找到 ${musicList.size} 首本地歌曲")
                
                if (musicList.isEmpty()) {
                    Toast.makeText(this@MainActivity, "未找到音乐文件", Toast.LENGTH_SHORT).show()
                    scanLocalBtn.isEnabled = true
                    scanLocalBtn.text = "扫描本地歌曲"
                    return@runOnUiThread
                }
                
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("扫描完成")
                    .setMessage("找到 ${musicList.size} 首歌曲，是否批量下载歌词？")
                    .setPositiveButton("下载") { _, _ ->
                        downloadLyricsForLocalSongs(musicList)
                    }
                    .setNegativeButton("取消", null)
                    .show()
                
                scanLocalBtn.isEnabled = true
                scanLocalBtn.text = "扫描本地歌曲"
            }
        }
    }
    
    // 批量下载歌词
    private fun downloadLyricsForLocalSongs(musicList: List<LocalMusicScanner.MusicInfo>) {
        scanLocalBtn.isEnabled = false
        scanLocalBtn.text = "下载中..."
        updateStatus("正在下载歌词...")
        
        CoroutineScope(Dispatchers.IO).launch {
            val scanner = LocalMusicScanner(this@MainActivity)
            val successCount = scanner.batchDownloadLyrics(musicList)
            
            runOnUiThread {
                updateStatus("歌词下载完成：成功 $successCount/${musicList.size}")
                Toast.makeText(this@MainActivity, 
                    "成功下载 $successCount 首歌词", Toast.LENGTH_LONG).show()
                
                scanLocalBtn.isEnabled = true
                scanLocalBtn.text = "扫描本地歌曲"
            }
        }
    }
    
    // 安全启动Service
    private fun startFloatServiceSafely() {
        try {
            Intent(this, FloatLyricService::class.java).also { intent ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
                updateStatus("悬浮窗服务已启动")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            updateStatus("启动失败：${e.message}")
        }
    }
    
    // 更新状态栏文本
    fun updateStatus(message: String) {
        runOnUiThread {
            statusText.text = message
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_PERMISSION_REQUEST_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                startFloatServiceSafely()
                updateStatus("悬浮窗权限已授予")
            } else {
                updateStatus("未授予悬浮窗权限")
            }
        } else if (requestCode == 1003 && resultCode == RESULT_OK) {
            // 处理文件选择结果
            data?.getStringExtra("selected_file")?.let { filePath ->
                updateStatus("已选择文件：$filePath")
                CoroutineScope(Dispatchers.IO).launch {
                    val scanner = LocalMusicScanner(this@MainActivity)
                    val musicInfo = scanner.getMusicInfoFromFile(filePath)
                    musicInfo?.let { info ->
                        val success = scanner.downloadLyricForSong(info)
                        runOnUiThread {
                            if (success) {
                                updateStatus("歌词下载成功")
                                lyricService?.setTestLyric(info.title, info.artist)
                            } else {
                                updateStatus("歌词下载失败")
                            }
                        }
                    }
                }
            }
        } else if (requestCode == 1004 && resultCode == RESULT_OK) {
            // 处理目录选择结果
            data?.getStringExtra("selected_directory")?.let { dirPath ->
                updateStatus("缓存目录已设置为：$dirPath")
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                updateStatus("存储权限已授予")
            } else {
                updateStatus("未授予存储权限，无法扫描本地歌曲")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unbindService(serviceConnection)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        (application as LyricFloatApp).mainActivity = null
    }

    // 设置Fragment
    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)
            
            // 歌词源优先级设置
            findPreference<ListPreference>("lyric_source_priority")?.setOnPreferenceChangeListener { _, _ ->
                true
            }
            
            // 文本颜色模式设置
            findPreference<ListPreference>("text_color_mode")?.setOnPreferenceChangeListener { _, newValue ->
                val colorMode = newValue as String
                val activity = activity as MainActivity
                when (colorMode) {
                    "auto", "light" -> activity.lyricService?.setLyricColor("#FFFFFF")
                    "dark" -> activity.lyricService?.setLyricColor("#000000")
                    "custom" -> {
                        val customColor = findPreference<EditTextPreference>("custom_text_color")?.text ?: "#FFFFFF"
                        activity.lyricService?.setLyricColor(customColor)
                    }
                }
                true
            }
            
            // 自定义文本颜色设置
            findPreference<EditTextPreference>("custom_text_color")?.setOnPreferenceChangeListener { _, newValue ->
                val color = newValue as String
                if ((color.startsWith("#") && (color.length == 7 || color.length == 9)) || 
                    color.matches(Regex("^[0-9a-fA-F]{6}$")) || color.matches(Regex("^[0-9a-fA-F]{8}$"))) {
                    val finalColor = if (color.startsWith("#")) color else "#$color"
                    (activity as MainActivity).lyricService?.setLyricColor(finalColor)
                    true
                } else {
                    Toast.makeText(context, "请输入有效的颜色值（如#FFFFFF）", Toast.LENGTH_SHORT).show()
                    false
                }
            }
            
            // 动画类型设置
            findPreference<ListPreference>("animation_type")?.setOnPreferenceChangeListener { _, newValue ->
                val animationType = newValue as String
                (activity as MainActivity).lyricService?.setAnimationType(animationType)
                true
            }
            
            // 缓存目录设置
            findPreference<Preference>("cache_directory")?.setOnPreferenceClickListener {
                val activity = activity as MainActivity
                val currentDir = Environment.getExternalStorageDirectory().absolutePath + "/Lyrics"
                
                AlertDialog.Builder(requireContext())
                    .setTitle("缓存目录")
                    .setMessage("当前缓存目录：$currentDir\n\n是否更改？")
                    .setPositiveButton("选择目录") { _, _ ->
                        val intent = Intent(activity, FilePickerActivity::class.java)
                        intent.putExtra("select_directory", true)
                        activity.startActivityForResult(intent, 1004)
                    }
                    .setNegativeButton("取消", null)
                    .show()
                true
            }
            
            // 自动缓存歌词开关
            findPreference<Preference>("auto_cache_lyrics")?.setOnPreferenceChangeListener { _, _ ->
                true
            }
            
            // 扫描本地歌曲设置项
            findPreference<Preference>("scan_local_songs")?.setOnPreferenceClickListener {
                activity?.let { act ->
                    (act as MainActivity).scanLocalSongs()
                }
                true
            }
            
            // 清除缓存设置项
            findPreference<Preference>("clear_cache")?.setOnPreferenceClickListener {
                AlertDialog.Builder(requireContext())
                    .setTitle("清除缓存")
                    .setMessage("确定要清除所有歌词缓存吗？")
                    .setPositiveButton("确定") { _, _ ->
                        clearLyricCache()
                        Toast.makeText(context, "缓存已清除", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("取消", null)
                    .show()
                true
            }
        }
        
        // 清除歌词缓存
        private fun clearLyricCache() {
            try {
                val lyricDir = File(Environment.getExternalStorageDirectory(), "Lyrics")
                if (lyricDir.exists() && lyricDir.isDirectory) {
                    lyricDir.listFiles()?.forEach { file ->
                        if (file.name.endsWith(".lrc") && file.isFile) {
                            file.delete()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "清除缓存失败：${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
