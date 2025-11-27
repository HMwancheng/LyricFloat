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
import android.provider.Settings
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
    private lateinit var resultBtn: Button
    
    private var lyricService: FloatLyricService? = null
    private var isServiceBound = false
    private var isDebugMode = false
    private val selectedFolders = mutableListOf<String>()
    private lateinit var scannedMusicList: List<LocalMusicScanner.MusicInfo>
    
    // Service连接回调
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as FloatLyricService.LocalBinder
            lyricService = binder.getService()
            isServiceBound = true
            updateStatus("已连接到歌词服务")
            
            // 仅在调试模式下设置测试歌词
            if (isDebugMode) {
                lyricService?.setTestLyric("初始化测试", "测试歌手")
            }
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            isServiceBound = false
            updateStatus("与歌词服务断开连接")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 初始化UI控件（带异常处理）
        try {
            statusText = findViewById(R.id.status_text)
            debugModeBtn = findViewById(R.id.debug_mode_btn)
            scanLocalBtn = findViewById(R.id.scan_local_btn)
            manualSelectBtn = findViewById(R.id.manual_select_btn)
            resultBtn = findViewById(R.id.result_btn)
            
            // 默认隐藏结果按钮
            resultBtn.visibility = android.view.View.GONE
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "UI初始化失败", Toast.LENGTH_SHORT).show()
        }
        
        // 保存MainActivity实例到Application
        (application as LyricFloatApp).mainActivity = this
        
        // 加载设置页面
        try {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        } catch (e: Exception) {
            e.printStackTrace()
            updateStatus("设置页面加载失败")
        }

        // 先检查权限，再初始化其他功能
        updateStatus("正在检查权限...")
        checkPermissions()
        
        // 设置按钮点击事件
        setupButtonListeners()
        disablePermissionDependentButtons()
    }
    
    // 禁用依赖权限的按钮
    private fun disablePermissionDependentButtons() {
        scanLocalBtn.isEnabled = false
        manualSelectBtn.isEnabled = false
        debugModeBtn.isEnabled = false
    }
    
    // 启用权限相关按钮
    private fun enablePermissionDependentButtons() {
        scanLocalBtn.isEnabled = true
        manualSelectBtn.isEnabled = true
        debugModeBtn.isEnabled = true
    }
    
    // 权限检查逻辑
    private fun checkPermissions() {
        // 优先检查存储权限
        val storageGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
        } else {
            ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
        
        if (!storageGranted) {
            requestStoragePermission()
            return
        }
        
        // 存储权限已授予，检查悬浮窗权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            requestOverlayPermission()
        } else {
            // 所有权限就绪，初始化服务
            initializeService()
            enablePermissionDependentButtons()
        }
    }
    
    // 请求存储权限
    private fun requestStoragePermission() {
        AlertDialog.Builder(this)
            .setTitle("需要存储权限")
            .setMessage("应用需要访问音频文件的权限才能扫描歌曲和下载歌词")
            .setPositiveButton("授予") { _, _ ->
                val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
                } else {
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
                ActivityCompat.requestPermissions(this, permissions, STORAGE_PERMISSION_REQUEST_CODE)
            }
            .setNegativeButton("取消") { _, _ ->
                finish() // 用户拒绝权限，退出应用
            }
            .setCancelable(false)
            .show()
    }
    
    // 请求悬浮窗权限
    private fun requestOverlayPermission() {
        AlertDialog.Builder(this)
            .setTitle("需要悬浮窗权限")
            .setMessage("应用需要悬浮窗权限才能显示歌词")
            .setPositiveButton("授予") { _, _ ->
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:$packageName")
                )
                startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE)
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    // 初始化Service（安全模式）
    private fun initializeService() {
        try {
            val serviceIntent = Intent(this, FloatLyricService::class.java)
            
            // Android O+ 需要前台服务
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            
            // 避免重复绑定
            if (!isServiceBound) {
                bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
            }
            
            updateStatus("服务初始化成功")
        } catch (e: Exception) {
            e.printStackTrace()
            updateStatus("服务启动失败：${e.message?.take(20)}")
        }
    }
    
    // 按钮点击事件设置
    private fun setupButtonListeners() {
        // 调试模式按钮
        debugModeBtn.setOnClickListener {
            if (!isServiceBound) {
                Toast.makeText(this, "服务未连接", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            isDebugMode = !isDebugMode
            lyricService?.setDebugMode(isDebugMode)
            
            if (isDebugMode) {
                debugModeBtn.text = "退出调试"
                updateStatus("调试模式已开启 - 显示测试歌词")
            } else {
                debugModeBtn.text = "调试模式"
                updateStatus("调试模式已关闭 - 监听实际播放")
            }
        }
        
        // 扫描按钮
        scanLocalBtn.setOnClickListener {
            if (selectedFolders.isEmpty()) {
                Toast.makeText(this, "请先选择要扫描的文件夹", Toast.LENGTH_SHORT).show()
                manualSelectBtn.performClick()
                return@setOnClickListener
            }
            
            scanSelectedFolders()
        }
        
        // 选择文件夹按钮
        manualSelectBtn.setOnClickListener {
            val intent = Intent(this, FilePickerActivity::class.java)
            startActivityForResult(intent, 1003)
        }
        
        // 刷新歌词按钮
        findViewById<Button>(R.id.refresh_lyric_btn)?.setOnClickListener {
            if (isServiceBound) {
                lyricService?.refreshCurrentLyric()
                updateStatus("正在刷新歌词...")
            } else {
                Toast.makeText(this, "服务未连接", Toast.LENGTH_SHORT).show()
            }
        }
        
        // 查看结果按钮
        resultBtn.setOnClickListener {
            try {
                val intent = Intent(this, MusicListActivity::class.java)
                intent.putExtra(MusicListActivity.EXTRA_MUSIC_LIST, ArrayList(scannedMusicList))
                startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "无法打开结果页面", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    // 扫描选中的文件夹
    private fun scanSelectedFolders() {
        scanLocalBtn.isEnabled = false
        scanLocalBtn.text = "扫描中..."
        resultBtn.visibility = android.view.View.GONE
        updateStatus("正在扫描 ${selectedFolders.size} 个文件夹...")
        
        CoroutineScope(Dispatchers.IO).launch {
            val scanner = LocalMusicScanner(this@MainActivity)
            scannedMusicList = scanner.scanSelectedFolders(selectedFolders)
            
            runOnUiThread {
                updateStatus("扫描完成：找到 ${scannedMusicList.size} 首歌曲")
                
                if (scannedMusicList.isNotEmpty()) {
                    resultBtn.visibility = android.view.View.VISIBLE
                    
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("扫描完成")
                        .setMessage("找到 ${scannedMusicList.size} 首歌曲，是否批量下载歌词？")
                        .setPositiveButton("下载") { _, _ ->
                            downloadLyricsForScannedSongs()
                        }
                        .setNegativeButton("取消", null)
                        .setNeutralButton("查看列表") { _, _ ->
                            resultBtn.performClick()
                        }
                        .show()
                } else {
                    Toast.makeText(this@MainActivity, "未找到音乐文件", Toast.LENGTH_SHORT).show()
                }
                
                scanLocalBtn.isEnabled = true
                scanLocalBtn.text = "扫描选中文件夹"
            }
        }
    }
    
    // 批量下载歌词
    private fun downloadLyricsForScannedSongs() {
        scanLocalBtn.isEnabled = false
        scanLocalBtn.text = "下载中..."
        updateStatus("正在下载歌词...")
        
        CoroutineScope(Dispatchers.IO).launch {
            val scanner = LocalMusicScanner(this@MainActivity)
            val successCount = scanner.batchDownloadLyrics(scannedMusicList)
            
            runOnUiThread {
                updateStatus("歌词下载完成：成功 $successCount/${scannedMusicList.size}")
                Toast.makeText(this@MainActivity, 
                    "成功下载 $successCount 首歌词", Toast.LENGTH_LONG).show()
                
                scanLocalBtn.isEnabled = true
                scanLocalBtn.text = "扫描选中文件夹"
            }
        }
    }
    
    // 更新状态文本
    fun updateStatus(message: String) {
        try {
            runOnUiThread {
                statusText.text = message
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // 权限请求结果处理
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == STORAGE_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                updateStatus("存储权限已授予")
                enablePermissionDependentButtons()
                
                // 检查悬浮窗权限
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                    requestOverlayPermission()
                } else {
                    initializeService()
                }
            } else {
                updateStatus("未授予存储权限，部分功能将不可用")
                AlertDialog.Builder(this)
                    .setTitle("权限拒绝")
                    .setMessage("没有存储权限，应用无法扫描歌曲和下载歌词")
                    .setPositiveButton("重新授予") { _, _ ->
                        requestStoragePermission()
                    }
                    .setNegativeButton("退出") { _, _ ->
                        finish()
                    }
                    .show()
            }
        }
    }

    // Activity结果处理
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == OVERLAY_PERMISSION_REQUEST_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                updateStatus("悬浮窗权限已授予，正在显示悬浮窗...")
                // 手动触发悬浮窗创建
                lyricService?.recreateFloatingWindow()
            } else {
                updateStatus("未授予悬浮窗权限，悬浮窗无法显示")
            }
        } else if (requestCode == 1003 && resultCode == RESULT_OK) {
            // 获取选中的文件夹
            val folders = data?.getStringArrayListExtra("selected_folders")
            folders?.let {
                selectedFolders.clear()
                selectedFolders.addAll(it)
                updateStatus("已选择 ${it.size} 个文件夹")
                
                if (it.isNotEmpty()) {
                    scanLocalBtn.text = "扫描选中文件夹(${it.size})"
                    scanLocalBtn.isEnabled = true
                }
            }
        }
    }

    // 销毁时解绑Service
    override fun onDestroy() {
        super.onDestroy()
        try {
            if (isServiceBound) {
                unbindService(serviceConnection)
                isServiceBound = false
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        (application as LyricFloatApp).mainActivity = null
    }

    // 设置Fragment
    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            try {
                setPreferencesFromResource(R.xml.root_preferences, rootKey)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
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
            
            // 扫描本地歌曲设置项
            findPreference<Preference>("scan_local_songs")?.setOnPreferenceClickListener {
                activity?.let { act ->
                    (act as MainActivity).scanLocalBtn.performClick()
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
                val lyricDir = File(android.os.Environment.getExternalStorageDirectory(), "Lyrics")
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
