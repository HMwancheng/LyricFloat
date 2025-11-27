package com.lyricfloat

import android.app.Application

// 改为普通Application（Android 10+无需MultiDexApplication）
class LyricFloatApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // 移除MultiDex.install()及相关代码
    }
}
