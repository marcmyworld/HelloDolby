package com.xiaomi.hellodolby.audio

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.Spatializer
import android.media.audiofx.AudioEffect
import android.media.audiofx.Virtualizer
import android.os.UserHandle
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.DataOutputStream
import java.lang.reflect.Method
import java.util.UUID

enum class EffectMode(val id: String) {
    DOLBY("dolby"),
    MISOUND("misound"),
    OFF("none");

    companion object {
        fun fromId(id: String?): EffectMode = entries.firstOrNull { it.id == id } ?: DOLBY
    }
}

enum class DolbyProfile(val id: Int) {
    DYNAMIC(0),
    MOVIE(1),
    MUSIC(2),
    VOICE(8);

    companion object {
        fun fromId(id: Int): DolbyProfile = entries.firstOrNull { it.id == id } ?: DYNAMIC
    }
}

class AudioEffectManager(private val context: Context) {
    companion object {
        private const val TAG = "HelloDolby"
        private const val PREFS_NAME = "hellodolby_prefs"
        private const val KEY_EFFECT_MODE = "effect_mode"
        private const val KEY_DOLBY_PROFILE = "dolby_profile"
        private const val KEY_SURROUND_ENABLED = "surround_enabled"
        private const val KEY_SURROUND_STRENGTH = "surround_strength"

        // AudioEffect AIDL UUIDs from vendor audio_effects_config.xml
        val EFFECT_TYPE_NULL: UUID = UUID.fromString("ec7178ec-e5e1-4432-a3f4-4f7f7e82dd7f")
        val UUID_DOLBY_DAP: UUID = UUID.fromString("9d4921da-8225-4f29-aefa-39537a04bcaa")
        val UUID_MISOUND_FX: UUID = UUID.fromString("5b8e36a5-144a-4c38-b1d7-0002a5d5c51b")

        // Dolby DAP Parameter IDs
        private const val EFFECT_PARAM_CPDP_VALUES = 5
        private const val EFFECT_PARAM_EFFECT_ENABLE = 0
        private const val EFFECT_PARAM_PROFILE = 0x0a000000
        private const val EFFECT_PARAM_PROFILE_PARAMETER = 0x1000000
        private const val EFFECT_PARAM_PROFILE_PORT_PARAMETER = 0x2000000
        private const val DAP_PARAM_HEADPHONE_VIRTUALIZER = 0x65 // 101
        private const val DAP_PARAM_SPEAKER_VIRTUALIZER = 0x66   // 102
        private const val DAP_PARAM_STEREO_WIDENING = 0x71       // 113

        // MiSound Parameter IDs
        private const val MISOUND_PARAM_MODE = 0
        private const val MISOUND_PARAM_3D_SURROUND = 20 // 0x14
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val spatializer: Spatializer = audioManager.spatializer
    private val executor = java.util.concurrent.Executors.newSingleThreadExecutor()

    private var dolbyEffect: AudioEffect? = null
    private var miSoundEffect: AudioEffect? = null
    private var virtualizer: Virtualizer? = null

    private val _spatialAudioState = MutableStateFlow(spatializer.isEnabled)
    val spatialAudioState: StateFlow<Boolean> = _spatialAudioState.asStateFlow()

    private val _spatialAudioAvailable = MutableStateFlow(spatializer.isAvailable)
    val spatialAudioAvailable: StateFlow<Boolean> = _spatialAudioAvailable.asStateFlow()

    private val spatializerListener = object : Spatializer.OnSpatializerStateChangedListener {
        override fun onSpatializerEnabledChanged(spat: Spatializer, enabled: Boolean) {
            Log.d(TAG, "Spatializer listener: onSpatializerEnabledChanged = $enabled")
            _spatialAudioState.value = enabled
        }

        override fun onSpatializerAvailableChanged(spat: Spatializer, available: Boolean) {
            Log.d(TAG, "Spatializer listener: onSpatializerAvailableChanged = $available")
            _spatialAudioAvailable.value = available
        }
    }

    init {
        initEffects()
        registerSpatializerListener()
    }

    private fun registerSpatializerListener() {
        try {
            spatializer.addOnSpatializerStateChangedListener(context.mainExecutor, spatializerListener)
            _spatialAudioState.value = spatializer.isEnabled
            _spatialAudioAvailable.value = spatializer.isAvailable
            Log.d(TAG, "Registered Spatializer state listener successfully")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register Spatializer listener", e)
        }
    }

    private fun initEffects() {
        dolbyEffect = createAudioEffect(UUID_DOLBY_DAP)
        miSoundEffect = createAudioEffect(UUID_MISOUND_FX)

        try {
            virtualizer = Virtualizer(0, 0)
        } catch (e: Exception) {
            Log.w(TAG, "Fallback Virtualizer not available", e)
        }

        val mode = getEffectMode()
        applyEffectMode(mode)

        val surroundEnabled = isSurroundEnabled()
        val surroundStrength = getSurroundStrength()
        applySurroundSound(surroundEnabled, surroundStrength)
    }

    // --- Audio Processing Engine ---

    fun getEffectMode(): EffectMode {
        val saved = prefs.getString(KEY_EFFECT_MODE, EffectMode.DOLBY.id)
        return EffectMode.fromId(saved)
    }

    fun setEffectMode(mode: EffectMode) {
        prefs.edit().putString(KEY_EFFECT_MODE, mode.id).apply()
        applyEffectMode(mode)
    }

    private fun applyEffectMode(mode: EffectMode) {
        Log.d(TAG, "applyEffectMode: $mode")
        when (mode) {
            EffectMode.DOLBY -> {
                // 1. Disable MiSound first
                try {
                    setMiSoundParameter(MISOUND_PARAM_3D_SURROUND, 0)
                    miSoundEffect?.enabled = false
                } catch (e: Exception) {
                    Log.w(TAG, "Error disabling MiSound", e)
                }

                // 2. Enable Dolby DAP internal DSP and effect
                try {
                    setDolbyBoolParam(EFFECT_PARAM_EFFECT_ENABLE, true)
                    dolbyEffect?.enabled = true
                    applyDolbyProfile(getDolbyProfile())
                } catch (e: Exception) {
                    Log.w(TAG, "Error activating Dolby DAP", e)
                }

                // 3. Apply surround settings
                applySurroundSound(isSurroundEnabled(), getSurroundStrength())

                // 4. Update system settings & notify HAL
                syncSystemSetting("dolby", isNone = false)
            }
            EffectMode.MISOUND -> {
                // 1. Disable Dolby DAP
                try {
                    setDolbyBoolParam(EFFECT_PARAM_EFFECT_ENABLE, false)
                    dolbyEffect?.enabled = false
                } catch (e: Exception) {
                    Log.w(TAG, "Error disabling Dolby", e)
                }

                // 2. Enable MiSound effect and set default mode
                try {
                    miSoundEffect?.enabled = true
                    setMiSoundParameter(MISOUND_PARAM_MODE, 1)
                } catch (e: Exception) {
                    Log.w(TAG, "Error activating MiSound", e)
                }

                // 3. Apply surround settings
                applySurroundSound(isSurroundEnabled(), getSurroundStrength())

                // 4. Update system settings & notify HAL
                syncSystemSetting("misound", isNone = false)
            }
            EffectMode.OFF -> {
                // Disable both
                try {
                    setDolbyBoolParam(EFFECT_PARAM_EFFECT_ENABLE, false)
                    dolbyEffect?.enabled = false
                } catch (e: Exception) {
                    Log.w(TAG, "Error disabling Dolby", e)
                }
                try {
                    setMiSoundParameter(MISOUND_PARAM_3D_SURROUND, 0)
                    miSoundEffect?.enabled = false
                } catch (e: Exception) {
                    Log.w(TAG, "Error disabling MiSound", e)
                }

                // Disable surround & spatial
                applySurroundSound(false, getSurroundStrength())

                syncSystemSetting("none", isNone = true)
            }
        }
    }

    private fun syncSystemSetting(value: String, isNone: Boolean) {
        try {
            Settings.Global.putString(context.contentResolver, "effect_implementer", value)
            Settings.Global.putInt(context.contentResolver, "last_select_effect_none", if (isNone) 1 else 0)
        } catch (e: Exception) {
            Log.w(TAG, "Could not write Settings.Global.effect_implementer", e)
        }

        try {
            audioManager.setParameters("effect_switch_fade=true")
        } catch (e: Exception) {
            Log.w(TAG, "Could not set audioManager fade parameter", e)
        }

        try {
            val intent = Intent("miui.intent.action.ACTION_AUDIO_EFFECT_CHANGED")
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Could not broadcast ACTION_AUDIO_EFFECT_CHANGED", e)
        }
    }

    // --- Dolby Profile Management ---

    fun getDolbyProfile(): DolbyProfile {
        val saved = prefs.getInt(KEY_DOLBY_PROFILE, DolbyProfile.DYNAMIC.id)
        return DolbyProfile.fromId(saved)
    }

    fun setDolbyProfile(profile: DolbyProfile) {
        prefs.edit().putInt(KEY_DOLBY_PROFILE, profile.id).apply()
        if (getEffectMode() == EffectMode.DOLBY) {
            applyDolbyProfile(profile)
        }
    }

    private fun applyDolbyProfile(profile: DolbyProfile) {
        try {
            setDolbyIntParam(EFFECT_PARAM_PROFILE, profile.id)
            Log.d(TAG, "Applied Dolby profile: $profile (${profile.id})")
        } catch (e: Exception) {
            Log.w(TAG, "Error setting Dolby profile $profile", e)
        }

        // Forward to Spatializer if enabled
        if (spatializer.isEnabled) {
            try {
                val method = spatializer.javaClass.getDeclaredMethod(
                    "setEffectParameter",
                    Int::class.javaPrimitiveType,
                    ByteArray::class.java
                )
                method.isAccessible = true
                val b = ByteArray(4)
                intToByteArray(profile.id, b, 0)
                method.invoke(spatializer, 0x100, b)
                Log.d(TAG, "Sent profile to Spatializer: ${profile.id}")
            } catch (e: Exception) {
                // Expected when unprivileged in userdebug / data install
            }
        }
    }

    // --- Immersive Sound (AOSP Spatializer) ---

    fun isSpatialAudioAvailable(): Boolean = spatializer.isAvailable

    fun isSpatialAudioEnabled(): Boolean = _spatialAudioState.value

    fun setSpatialAudioEnabled(enabled: Boolean) {
        Log.d(TAG, "setSpatialAudioEnabled: $enabled")
        // Optimistically update state flow for instant UI feedback
        _spatialAudioState.value = enabled

        executor.execute {
            var success = false

            // 1. Direct framework call (works when shipped as priv-app)
            try {
                val method: Method = Spatializer::class.java.getMethod("setEnabled", Boolean::class.javaPrimitiveType)
                method.isAccessible = true
                method.invoke(spatializer, enabled)
                success = true
                Log.d(TAG, "Spatializer.setEnabled($enabled) succeeded directly")
            } catch (e: Exception) {
                Log.w(TAG, "Direct Spatializer.setEnabled failed, trying root fallback", e)
            }

            // 2. Root fallback via service call (transaction 229 = TRANSACTION_setSpatializerEnabled)
            if (!success) {
                val code = if (enabled) 1 else 0
                val cmd = "service call audio 229 i32 $code"
                val exitCode = executeRootCommand(cmd)
                if (exitCode == 0) {
                    success = true
                    Log.d(TAG, "Root fallback service call audio 229 i32 $code succeeded")
                } else {
                    Log.e(TAG, "Root fallback service call audio 229 failed with exit code $exitCode")
                }
            }

            // 3. Update system settings and audio HAL fade
            try {
                Settings.Global.putInt(context.contentResolver, "spatial_audio_feature_enable", if (enabled) 1 else 0)
                Settings.Global.putInt(context.contentResolver, "last_spatial_status", if (enabled) 1 else 0)
                audioManager.setParameters("effect_switch_fade=true")
            } catch (e: Exception) {
                Log.w(TAG, "Error updating spatial settings", e)
            }
        }
    }

    // --- Surround Sound (Virtualizer) ---

    fun isSurroundEnabled(): Boolean = prefs.getBoolean(KEY_SURROUND_ENABLED, false)

    fun getSurroundStrength(): Int = prefs.getInt(KEY_SURROUND_STRENGTH, 500)

    fun setSurroundEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SURROUND_ENABLED, enabled).apply()
        applySurroundSound(enabled, getSurroundStrength())
    }

    fun setSurroundStrength(strength: Int) {
        prefs.edit().putInt(KEY_SURROUND_STRENGTH, strength).apply()
        if (isSurroundEnabled()) {
            applySurroundSound(true, strength)
        }
    }

    private fun applySurroundSound(enabled: Boolean, strength: Int) {
        Log.d(TAG, "applySurroundSound: enabled=$enabled, strength=$strength, mode=${getEffectMode()}")
        val mode = getEffectMode()

        when (mode) {
            EffectMode.DOLBY -> {
                val profile = getDolbyProfile().id
                try {
                    // Enable/disable virtualizer controls on DAP
                    setDolbyProfileParam(profile, DAP_PARAM_SPEAKER_VIRTUALIZER, intArrayOf(if (enabled) 1 else 0))
                    setDolbyProfileParam(profile, DAP_PARAM_HEADPHONE_VIRTUALIZER, intArrayOf(if (enabled) 1 else 0))

                    // Map strength (0..1000) to Dolby stereo widening range (4..160)
                    val widening = 4 + (strength * (160 - 4) / 1000)
                    setDolbyPortParam(profile, 0, DAP_PARAM_STEREO_WIDENING, intArrayOf(widening))
                    setDolbyPortParam(profile, 1, DAP_PARAM_STEREO_WIDENING, intArrayOf(widening))
                    Log.d(TAG, "Applied Dolby surround virtualizer: enabled=$enabled, widening=$widening")
                } catch (e: Exception) {
                    Log.w(TAG, "Error setting Dolby surround parameters", e)
                }
            }
            EffectMode.MISOUND -> {
                try {
                    // MiSound 3D Surround parameter (0x14 = 20)
                    setMiSoundParameter(MISOUND_PARAM_3D_SURROUND, if (enabled) 1 else 0)
                    Log.d(TAG, "Applied MiSound 3D surround: enabled=$enabled")
                } catch (e: Exception) {
                    Log.w(TAG, "Error setting MiSound 3D surround", e)
                }
            }
            EffectMode.OFF -> {
                // Do nothing
            }
        }

        // Broadcast surround state change
        try {
            val intent = Intent("com.miui.misound.ACTION_3D_SURROUND_STATE_CHANGED")
            intent.putExtra("state", enabled)
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Could not broadcast ACTION_3D_SURROUND_STATE_CHANGED", e)
        }

        // Set persistent vendor property for 3D surround
        executor.execute {
            executeRootCommand("setprop persist.vendor.audio.3dsurround.enable ${if (enabled) "true" else "false"}")
        }

        // Apply fallback standard Virtualizer if available
        try {
            virtualizer?.let {
                if (it.strengthSupported) {
                    it.setStrength(strength.toShort())
                }
                it.enabled = enabled
            }
        } catch (e: Exception) {
            Log.w(TAG, "Fallback Virtualizer error", e)
        }
    }

    // --- AudioEffect Parameter Serialization & Dispatch ---

    private fun setDolbyBoolParam(effEvt: Int, enable: Boolean) {
        val buffer = ByteArray(12)
        intToByteArray(effEvt, buffer, 0)
        intToByteArray(1, buffer, 4)
        intToByteArray(if (enable) 1 else 0, buffer, 8)
        setAudioEffectParameter(dolbyEffect, EFFECT_PARAM_CPDP_VALUES, buffer)
    }

    private fun setDolbyIntParam(effEvt: Int, value: Int) {
        val buffer = ByteArray(12)
        intToByteArray(effEvt, buffer, 0)
        intToByteArray(1, buffer, 4)
        intToByteArray(value, buffer, 8)
        setAudioEffectParameter(dolbyEffect, EFFECT_PARAM_CPDP_VALUES, buffer)
    }

    private fun setDolbyProfileParam(profile: Int, paramId: Int, values: IntArray) {
        val buffer = ByteArray((values.size + 4) * 4)
        var index = 0
        index += intToByteArray(EFFECT_PARAM_PROFILE_PARAMETER, buffer, index)
        index += intToByteArray(values.size + 1, buffer, index)
        index += intToByteArray(profile, buffer, index)
        index += intToByteArray(paramId, buffer, index)
        for (v in values) {
            index += intToByteArray(v, buffer, index)
        }
        setAudioEffectParameter(dolbyEffect, EFFECT_PARAM_CPDP_VALUES, buffer)
    }

    private fun setDolbyPortParam(profile: Int, port: Int, paramId: Int, values: IntArray) {
        val buffer = ByteArray((values.size + 5) * 4)
        var index = 0
        index += intToByteArray(EFFECT_PARAM_PROFILE_PORT_PARAMETER, buffer, index)
        index += intToByteArray(values.size + 1, buffer, index)
        index += intToByteArray(profile, buffer, index)
        index += intToByteArray(port, buffer, index)
        index += intToByteArray(paramId, buffer, index)
        for (v in values) {
            index += intToByteArray(v, buffer, index)
        }
        setAudioEffectParameter(dolbyEffect, EFFECT_PARAM_CPDP_VALUES, buffer)
    }

    private fun setMiSoundParameter(param: Int, value: Int) {
        miSoundEffect?.let { effect ->
            val p = ByteArray(4)
            intToByteArray(param, p, 0)
            val v = ByteArray(4)
            intToByteArray(value, v, 0)
            try {
                val method = AudioEffect::class.java.getMethod(
                    "setParameter",
                    ByteArray::class.java,
                    ByteArray::class.java
                )
                method.isAccessible = true
                val status = method.invoke(effect, p, v) as Int
                Log.d(TAG, "MiSound setParameter($param, $value) status: $status")
            } catch (e: Exception) {
                Log.w(TAG, "Failed setMiSoundParameter($param, $value)", e)
            }
        }
    }

    private fun setAudioEffectParameter(effect: AudioEffect?, param: Int, value: ByteArray) {
        if (effect == null) return
        try {
            val paramBytes = ByteArray(4)
            intToByteArray(param, paramBytes, 0)
            val method = AudioEffect::class.java.getMethod(
                "setParameter",
                ByteArray::class.java,
                ByteArray::class.java
            )
            method.isAccessible = true
            val status = method.invoke(effect, paramBytes, value) as Int
            if (status != AudioEffect.SUCCESS) {
                Log.w(TAG, "setParameter($param) returned status $status")
            } else {
                Log.d(TAG, "setParameter($param) SUCCESS")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed setAudioEffectParameter($param)", e)
        }
    }

    private fun intToByteArray(value: Int, buffer: ByteArray, offset: Int): Int {
        buffer[offset] = (value and 0xFF).toByte()
        buffer[offset + 1] = ((value ushr 8) and 0xFF).toByte()
        buffer[offset + 2] = ((value ushr 16) and 0xFF).toByte()
        buffer[offset + 3] = ((value ushr 24) and 0xFF).toByte()
        return 4
    }

    private fun createAudioEffect(uuid: UUID): AudioEffect? {
        return try {
            val constructor = AudioEffect::class.java.getDeclaredConstructor(
                UUID::class.java,
                UUID::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            constructor.isAccessible = true
            constructor.newInstance(EFFECT_TYPE_NULL, uuid, 0, 0) as AudioEffect
        } catch (e: Exception) {
            Log.w(TAG, "Failed to instantiate AudioEffect for UUID: $uuid", e)
            null
        }
    }

    private fun executeRootCommand(command: String): Int {
        val suBinaries = arrayOf("/system/bin/su", "/system/xbin/su", "/data/adb/ksu/bin/su", "su")
        for (su in suBinaries) {
            try {
                val process = Runtime.getRuntime().exec(su)
                DataOutputStream(process.outputStream).use { os ->
                    os.writeBytes(command + "\n")
                    os.writeBytes("exit\n")
                    os.flush()
                }
                return process.waitFor()
            } catch (e: Exception) {
                // Try next binary
            }
        }
        Log.e(TAG, "Failed to execute root command: $command (no working su binary)")
        return -1
    }

    fun release() {
        try {
            spatializer.removeOnSpatializerStateChangedListener(spatializerListener)
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering Spatializer listener", e)
        }
        try {
            dolbyEffect?.release()
            miSoundEffect?.release()
            virtualizer?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing audio effects", e)
        }
    }
}
