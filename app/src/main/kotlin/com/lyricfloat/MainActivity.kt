package com.lyricfloat

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceFragmentCompat

class MainActivity : AppCompatActivity() {
    private val OVERLAY_PERMISSION_REQUEST_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 检查悬浮窗权限（Android M+必需）
        checkOverlayPermission()

        // 加载设置页面
        supportFragmentManager.beginTransaction()
            .replace(R.id.settings_container, SettingsFragment())
            .commit()
    }

    private fun checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                // 显示权限申请提示框
                AlertDialog.Builder(this)
                    .setTitle("需要悬浮窗权限")
                    .setMessage("应用需要悬浮窗权限才能显示歌词，请前往设置开启")
                    .setPositiveButton("去设置") { _, _ ->
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                        startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE)
                    }
                    .setNegativeButton("取消") { dialog, _ ->
                        dialog.dismiss()
                        finish() // 用户拒绝则退出应用
                    }
                    .show()
            } else {
                startFloatService() // 已有权限，启动悬浮窗服务
            }
        } else {
            startFloatService() // 低版本无需权限，直接启动
        }
    }

    // 启动悬浮窗服务
    private fun startFloatService() {
        Intent(this, FloatLyricService::class.java).also { intent ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent) // Android O+需启动前台服务
            } else {
                startService(intent)
            }
        }
    }

    // 权限申请回调
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_PERMISSION_REQUEST_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                startFloatService()
            } else {
                Toast.makeText(this, "未开启悬浮窗权限，应用将退出", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    // 设置页面Fragment（可扩展歌词样式设置）
    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)
        }
    }
}
