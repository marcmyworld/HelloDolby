package com.puppyfrommars.hellodolby

import android.app.Application
import com.puppyfrommars.hellodolby.audio.AudioEffectManager

class HelloDolbyApp : Application() {
    lateinit var audioEffectManager: AudioEffectManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        audioEffectManager = AudioEffectManager(this)
    }

    companion object {
        lateinit var instance: HelloDolbyApp
            private set
    }
}
