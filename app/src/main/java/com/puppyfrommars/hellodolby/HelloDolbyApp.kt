/*
 * SPDX-FileCopyrightText: 2026 marcmyworld <moviesminister@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package com.puppyfrommars.hellodolby

import android.app.Application
import com.puppyfrommars.hellodolby.audio.AudioEffectManager

/**
 * Application entry point holding the dummy UI state controller until DSP backends are wired.
 */
class HelloDolbyApp : Application() {
    lateinit var audioEffectManager: AudioEffectManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        audioEffectManager = AudioEffectManager.getInstance(this)
    }

    companion object {
        lateinit var instance: HelloDolbyApp
            private set
    }
}
