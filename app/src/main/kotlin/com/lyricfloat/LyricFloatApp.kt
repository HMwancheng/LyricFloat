package com.lyricfloat

import android.app.Application

class LyricFloatApp : Application() {
    var mainActivity: MainActivity? = null
    
    override fun onCreate() {
        super.onCreate()
        // 初始化操作放在这里，避免空指针
    }
}
