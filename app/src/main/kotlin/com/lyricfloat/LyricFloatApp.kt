package com.lyricfloat

import android.app.Application

class LyricFloatApp : Application() {
    var mainActivity: MainActivity? = null
    
    override fun onCreate() {
        super.onCreate()
        instance = this
    }
    
    companion object {
        lateinit var instance: LyricFloatApp
            private set
    }
}
