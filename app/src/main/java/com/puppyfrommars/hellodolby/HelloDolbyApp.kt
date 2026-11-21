package com.puppyfrommars.hellodolby

import android.app.Application
import android.util.Log
import com.puppyfrommars.hellodolby.audio.AudioEffectManager
import java.lang.reflect.Method

class HelloDolbyApp : Application() {
    lateinit var audioEffectManager: AudioEffectManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        unsealHiddenApiRestrictions()
        audioEffectManager = AudioEffectManager.getInstance(this)
    }

    private fun unsealHiddenApiRestrictions() {
        try {
            val forName = Class::class.java.getDeclaredMethod("forName", String::class.java)
            val getDeclaredMethod = Class::class.java.getDeclaredMethod(
                "getDeclaredMethod",
                String::class.java,
                arrayOf<Class<*>>()::class.java
            )
            val vmRuntimeClass = forName.invoke(null, "dalvik.system.VMRuntime") as Class<*>
            val getRuntime = getDeclaredMethod.invoke(vmRuntimeClass, "getRuntime", null) as Method
            val stringArrayClass = Class.forName("[Ljava.lang.String;")
            val setHiddenApiExemptions = getDeclaredMethod.invoke(
                vmRuntimeClass,
                "setHiddenApiExemptions",
                arrayOf<Class<*>>(stringArrayClass)
            ) as Method
            val vmRuntime = getRuntime.invoke(null)
            setHiddenApiExemptions.invoke(vmRuntime, arrayOf<Any>(arrayOf("L")))
            Log.d("HelloDolbyApp", "Hidden API restrictions unsealed successfully via VMRuntime")
        } catch (t: Throwable) {
            Log.w("HelloDolbyApp", "Failed to unseal hidden API restrictions via VMRuntime", t)
            try {
                Runtime.getRuntime().exec(arrayOf("su", "-c", "settings put global hidden_api_policy 1")).waitFor()
            } catch (_: Exception) {}
        }
    }

    companion object {
        lateinit var instance: HelloDolbyApp
            private set
    }
}

