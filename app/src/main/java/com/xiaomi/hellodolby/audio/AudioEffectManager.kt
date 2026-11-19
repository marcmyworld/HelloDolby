package com.xiaomi.hellodolby.audio

import android.content.Context
import android.media.AudioManager
import android.media.Spatializer
import android.media.audiofx.AudioEffect
import android.media.audiofx.Virtualizer
import android.provider.Settings
import android.util.Log
import java.util.UUID

enum class EffectMode(val id: String) {
    DOLBY("dolby"),
    MISOUND("misound"),
    OFF("none");

    companion object {
        fun fromId(id: String?): EffectMode = entries.firstOrNull { it.id == id } ?: DOLBY
    }
}

class AudioEffectManager(private val context: Context) {
    companion object {
        private const val TAG = "HelloDolby"
        private const val PREFS_NAME = "hellodolby_prefs"
        private const val KEY_EFFECT_MODE = "effect_mode"
        private const val KEY_VIRTUALIZER_ENABLED = "virtualizer_enabled"
        private const val KEY_VIRTUALIZER_STRENGTH = "virtualizer_strength"

        // AudioEffect AIDL UUIDs from vendor audio_effects_config.xml
        val EFFECT_TYPE_NULL: UUID = UUID.fromString("ec7178ec-e5e1-4432-a3f4-4f7f7e82dd7f")
        val UUID_DOLBY_DAP: UUID = UUID.fromString("9d4921da-8225-4f29-aefa-39537a04bcaa")
        val UUID_MISOUND_FX: UUID = UUID.fromString("5b8e36a5-144a-4c38-b1d7-0002a5d5c51b")
        val UUID_VIRTUALIZER: UUID = UUID.fromString("d3467faa-acc7-4d34-acaf-0002a5d5c51b")
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val spatializer: Spatializer = audioManager.spatializer

    private var dolbyEffect: AudioEffect? = null
    private var miSoundEffect: AudioEffect? = null
    private var virtualizer: Virtualizer? = null

    init {
        initEffects()
    }

    private fun initEffects() {
        dolbyEffect = createAudioEffect(UUID_DOLBY_DAP)
        miSoundEffect = createAudioEffect(UUID_MISOUND_FX)

        try {
            virtualizer = Virtualizer(0, 0)
            Log.d(TAG, "Virtualizer created")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create Virtualizer", e)
        }

        val mode = getEffectMode()
        applyEffectMode(mode)

        val virtEnabled = isVirtualizerEnabled()
        val virtStrength = getVirtualizerStrength()
        applyVirtualizer(virtEnabled, virtStrength)
    }

    fun getEffectMode(): EffectMode {
        val saved = prefs.getString(KEY_EFFECT_MODE, EffectMode.DOLBY.id)
        return EffectMode.fromId(saved)
    }

    fun setEffectMode(mode: EffectMode) {
        prefs.edit().putString(KEY_EFFECT_MODE, mode.id).apply()
        applyEffectMode(mode)
    }

    private fun applyEffectMode(mode: EffectMode) {
        Log.d(TAG, "Applying effect mode: $mode")
        when (mode) {
            EffectMode.DOLBY -> {
                try {
                    miSoundEffect?.enabled = false
                } catch (e: Exception) {
                    Log.w(TAG, "Error disabling MiSound", e)
                }
                try {
                    dolbyEffect?.enabled = true
                } catch (e: Exception) {
                    Log.w(TAG, "Error enabling Dolby", e)
                }
                syncSystemSetting("dolby")
            }
            EffectMode.MISOUND -> {
                try {
                    dolbyEffect?.enabled = false
                } catch (e: Exception) {
                    Log.w(TAG, "Error disabling Dolby", e)
                }
                try {
                    miSoundEffect?.enabled = true
                } catch (e: Exception) {
                    Log.w(TAG, "Error enabling MiSound", e)
                }
                syncSystemSetting("misound")
            }
            EffectMode.OFF -> {
                try {
                    dolbyEffect?.enabled = false
                } catch (e: Exception) {
                    Log.w(TAG, "Error disabling Dolby", e)
                }
                try {
                    miSoundEffect?.enabled = false
                } catch (e: Exception) {
                    Log.w(TAG, "Error disabling MiSound", e)
                }
                syncSystemSetting("none")
            }
        }
    }

    private fun syncSystemSetting(value: String) {
        try {
            Settings.Global.putString(context.contentResolver, "effect_implementer", value)
        } catch (e: Exception) {
            Log.w(TAG, "Could not write Settings.Global.effect_implementer", e)
        }
    }

    // --- Spatializer (Immersive sound) ---

    fun isSpatialAudioAvailable(): Boolean = spatializer.isAvailable

    fun isSpatialAudioEnabled(): Boolean = spatializer.isEnabled

    fun setSpatialAudioEnabled(enabled: Boolean) {
        try {
            val method = Spatializer::class.java.getMethod("setEnabled", Boolean::class.javaPrimitiveType)
            method.isAccessible = true
            method.invoke(spatializer, enabled)
            Log.d(TAG, "Spatializer isEnabled set to $enabled")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting Spatializer enabled state", e)
        }
    }

    // --- Virtualizer (Surround Sound) ---

    fun isVirtualizerEnabled(): Boolean = prefs.getBoolean(KEY_VIRTUALIZER_ENABLED, false)

    fun getVirtualizerStrength(): Int = prefs.getInt(KEY_VIRTUALIZER_STRENGTH, 500)

    fun setVirtualizerEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VIRTUALIZER_ENABLED, enabled).apply()
        applyVirtualizer(enabled, getVirtualizerStrength())
    }

    fun setVirtualizerStrength(strength: Int) {
        prefs.edit().putInt(KEY_VIRTUALIZER_STRENGTH, strength).apply()
        if (isVirtualizerEnabled()) {
            applyVirtualizer(true, strength)
        }
    }

    private fun applyVirtualizer(enabled: Boolean, strength: Int) {
        try {
            virtualizer?.let {
                if (it.strengthSupported) {
                    it.setStrength(strength.toShort())
                }
                it.enabled = enabled
                Log.d(TAG, "Virtualizer enabled: $enabled, strength: $strength")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error applying Virtualizer parameters", e)
        }
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
            Log.w(TAG, "Failed to instantiate AudioEffect for UUID: ", e)
            null
        }
    }

    fun release() {
        try {
            dolbyEffect?.release()
            miSoundEffect?.release()
            virtualizer?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing audio effects", e)
        }
    }
}
