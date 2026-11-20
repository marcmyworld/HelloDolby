package com.puppyfrommars.hellodolby.audio

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.Spatializer
import android.media.audiofx.AudioEffect
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.os.UserHandle
import android.provider.Settings
import android.util.Log
import com.puppyfrommars.hellodolby.data.device.AudioDeviceSnapshot
import com.puppyfrommars.hellodolby.data.device.DeviceStateManager
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
        private const val EFFECT_PARAM_RESET_PROFILE_SETTINGS = 0x0c000000
        private const val DAP_PARAM_HEADPHONE_VIRTUALIZER = 0x65 // 101
        private const val DAP_PARAM_SPEAKER_VIRTUALIZER = 0x66   // 102
        private const val DAP_PARAM_GEQ_BAND_GAINS = 110
        private const val DAP_PARAM_BASS_ENHANCER_ENABLE = 111
        private const val DAP_PARAM_STEREO_WIDENING = 0x71       // 113

        // Equalizer & AutoEQ Constants
        private const val KEY_EQUALIZER_ENABLED = "equalizer_enabled"
        private const val KEY_EQUALIZER_BANDS = "equalizer_bands"
        private const val KEY_EQUALIZER_PRESET = "equalizer_preset"
        private const val KEY_AUTOEQ_PROFILE_NAME = "autoeq_profile_name"
        private const val KEY_CUSTOM_PRESETS = "custom_presets_json"
        val DEFAULT_EQUALIZER_BANDS = listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)

        val BUILTIN_PRESETS = mapOf(
            "Flat" to listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
            "Rock" to listOf(5f, 3.5f, 1.5f, 0f, -1f, 0.5f, 2f, 3.5f, 4.5f, 5f),
            "Jazz" to listOf(3.5f, 2f, 1f, 2f, -1f, -1f, 0.5f, 1.5f, 2.5f, 3.5f),
            "Pop" to listOf(-1f, 1.5f, 3f, 4f, 3f, 1.5f, -0.5f, -1f, 2f, 3f),
            "Classical" to listOf(4f, 3f, 2f, 2f, -1f, -1f, 0f, 2f, 3f, 3.5f),
            "Hip Hop" to listOf(5.5f, 4.5f, 2f, 1f, -1f, -1f, 1f, 2.5f, 3.5f, 4.5f),
            "Electronic" to listOf(4.5f, 3.5f, 1.5f, 0f, -1f, 1f, 0f, 2.5f, 4f, 4.5f),
            "Vocal Boost" to listOf(-2f, -2f, -1f, 1.5f, 3.5f, 4f, 3.5f, 1.5f, 0f, -1f),
            "Bass Boost" to listOf(6f, 5f, 4f, 2f, 0.5f, 0f, 0f, 0f, 0f, 0f)
        )

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
    private var equalizerEffect: Equalizer? = null

    private val _spatialAudioState = MutableStateFlow(spatializer.isEnabled)
    val spatialAudioState: StateFlow<Boolean> = _spatialAudioState.asStateFlow()

    private val _spatialAudioAvailable = MutableStateFlow(spatializer.isAvailable)
    val spatialAudioAvailable: StateFlow<Boolean> = _spatialAudioAvailable.asStateFlow()

    private val _effectModeState by lazy { MutableStateFlow(getEffectMode()) }
    val effectModeState: StateFlow<EffectMode> by lazy { _effectModeState.asStateFlow() }

    private val _equalizerEnabledState by lazy { MutableStateFlow(isEqualizerEnabled()) }
    val equalizerEnabledState: StateFlow<Boolean> by lazy { _equalizerEnabledState.asStateFlow() }

    private val _equalizerBandsState by lazy { MutableStateFlow(getEqualizerBands()) }
    val equalizerBandsState: StateFlow<List<Float>> by lazy { _equalizerBandsState.asStateFlow() }

    private val _equalizerPresetState by lazy { MutableStateFlow(getEqualizerPreset()) }
    val equalizerPresetState: StateFlow<String> by lazy { _equalizerPresetState.asStateFlow() }

    private val _autoEqProfileNameState by lazy { MutableStateFlow(getAutoEqProfileName()) }
    val autoEqProfileNameState: StateFlow<String?> by lazy { _autoEqProfileNameState.asStateFlow() }

    private val _customPresetsState by lazy { MutableStateFlow(loadCustomPresets()) }
    val customPresetsState: StateFlow<Map<String, List<Float>>> by lazy { _customPresetsState.asStateFlow() }

    private var deviceStateManager: DeviceStateManager? = null
    private val _connectedDeviceState = MutableStateFlow("Phone Speaker")
    val connectedDeviceState: StateFlow<String> = _connectedDeviceState.asStateFlow()

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
        deviceStateManager = DeviceStateManager(context) { snapshot ->
            onDeviceSnapshotRestored(snapshot)
        }
        _connectedDeviceState.value = deviceStateManager?.getActiveDeviceName() ?: "Phone Speaker"
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

        try {
            equalizerEffect = Equalizer(0, 0)
        } catch (e: Exception) {
            Log.w(TAG, "Fallback Equalizer not available", e)
        }

        val mode = getEffectMode()
        applyEffectMode(mode)

        val surroundEnabled = isSurroundEnabled()
        val surroundStrength = getSurroundStrength()
        applySurroundSound(surroundEnabled, surroundStrength)

        applyEqualizerSettings()
    }

    // --- Audio Processing Engine ---

    fun getEffectMode(): EffectMode {
        val saved = prefs.getString(KEY_EFFECT_MODE, EffectMode.DOLBY.id)
        return EffectMode.fromId(saved)
    }

    fun setEffectMode(mode: EffectMode) {
        prefs.edit().putString(KEY_EFFECT_MODE, mode.id).apply()
        _effectModeState.value = mode
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

        applyEqualizerSettings()
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

    // --- Equalizer & AutoEQ Management ---

    fun isEqualizerEnabled(): Boolean = prefs.getBoolean(KEY_EQUALIZER_ENABLED, false)

    fun setEqualizerEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_EQUALIZER_ENABLED, enabled).apply()
        _equalizerEnabledState.value = enabled
        applyEqualizerSettings()
    }

    fun getEqualizerBands(): List<Float> {
        val saved = prefs.getString(KEY_EQUALIZER_BANDS, null) ?: return DEFAULT_EQUALIZER_BANDS
        val parsed = saved.split(",").mapNotNull { it.toFloatOrNull() }
        return if (parsed.size == 10) parsed else DEFAULT_EQUALIZER_BANDS
    }

    fun setEqualizerBands(bands: List<Float>, preset: String = "Custom") {
        val clamped = bands.map { it.coerceIn(-10f, 10f) }
        prefs.edit()
            .putString(KEY_EQUALIZER_BANDS, clamped.joinToString(","))
            .putString(KEY_EQUALIZER_PRESET, preset)
            .apply()
        _equalizerBandsState.value = clamped
        _equalizerPresetState.value = preset
        applyEqualizerSettings()
    }

    fun setEqualizerBand(index: Int, gainDb: Float) {
        if (index !in 0 until 10) return
        val current = getEqualizerBands().toMutableList()
        current[index] = (Math.round(gainDb * 10f) / 10f).coerceIn(-10f, 10f)
        setEqualizerBands(current, "Custom")
    }

    fun getEqualizerPreset(): String = prefs.getString(KEY_EQUALIZER_PRESET, "Flat") ?: "Flat"

    fun setEqualizerPreset(presetName: String) {
        val presetBands = BUILTIN_PRESETS[presetName] ?: loadCustomPresets()[presetName]
        if (presetBands != null) {
            setEqualizerBands(presetBands, presetName)
        } else {
            prefs.edit().putString(KEY_EQUALIZER_PRESET, presetName).apply()
            _equalizerPresetState.value = presetName
        }
    }

    fun loadCustomPresets(): Map<String, List<Float>> {
        val jsonStr = prefs.getString(KEY_CUSTOM_PRESETS, null) ?: return emptyMap()
        return try {
            val json = org.json.JSONObject(jsonStr)
            val map = mutableMapOf<String, List<Float>>()
            for (key in json.keys()) {
                val arr = json.getJSONArray(key)
                val list = mutableListOf<Float>()
                for (i in 0 until arr.length()) {
                    list.add(arr.getDouble(i).toFloat())
                }
                if (list.size == 10) {
                    map[key] = list
                }
            }
            map
        } catch (e: Exception) {
            Log.w(TAG, "Failed parsing custom presets", e)
            emptyMap()
        }
    }

    fun saveCustomPreset(name: String, bands: List<Float>) {
        val current = loadCustomPresets().toMutableMap()
        val clamped = bands.map { it.coerceIn(-10f, 10f) }
        current[name] = clamped
        saveCustomPresetsMap(current)
        setEqualizerBands(clamped, name)
    }

    fun deleteCustomPreset(name: String) {
        val current = loadCustomPresets().toMutableMap()
        current.remove(name)
        saveCustomPresetsMap(current)
        if (getEqualizerPreset() == name) {
            setEqualizerPreset("Flat")
        }
    }

    fun renameCustomPreset(oldName: String, newName: String) {
        val current = loadCustomPresets().toMutableMap()
        val bands = current.remove(oldName) ?: return
        current[newName] = bands
        saveCustomPresetsMap(current)
        if (getEqualizerPreset() == oldName) {
            prefs.edit().putString(KEY_EQUALIZER_PRESET, newName).apply()
            _equalizerPresetState.value = newName
            saveCurrentStateToDevice()
        }
    }

    private fun saveCustomPresetsMap(map: Map<String, List<Float>>) {
        val json = org.json.JSONObject()
        for ((k, v) in map) {
            val arr = org.json.JSONArray()
            for (f in v) {
                arr.put(f.toDouble())
            }
            json.put(k, arr)
        }
        prefs.edit().putString(KEY_CUSTOM_PRESETS, json.toString()).apply()
        _customPresetsState.value = map
    }

    fun getAutoEqProfileName(): String? = prefs.getString(KEY_AUTOEQ_PROFILE_NAME, null)

    fun setAutoEqProfile(name: String, bands: List<Float>) {
        prefs.edit().putString(KEY_AUTOEQ_PROFILE_NAME, name).apply()
        _autoEqProfileNameState.value = name
        setEqualizerBands(bands, name)
    }

    fun resetEqualizer() {
        setEqualizerBands(DEFAULT_EQUALIZER_BANDS, "Flat")
        prefs.edit().remove(KEY_AUTOEQ_PROFILE_NAME).apply()
        _autoEqProfileNameState.value = null
    }

    fun saveCurrentStateToDevice() {
        deviceStateManager?.saveSnapshot(
            effectMode = getEffectMode().id,
            dolbyProfile = getDolbyProfile().id,
            surroundEnabled = isSurroundEnabled(),
            surroundStrength = getSurroundStrength() / 10f,
            equalizerEnabled = isEqualizerEnabled(),
            equalizerBands = getEqualizerBands(),
            equalizerPreset = getEqualizerPreset(),
            autoEqProfileName = getAutoEqProfileName()
        )
    }

    private fun onDeviceSnapshotRestored(snapshot: AudioDeviceSnapshot) {
        Log.d(TAG, "Restoring audio device snapshot: ${snapshot.displayName}")
        _connectedDeviceState.value = snapshot.displayName

        val mode = EffectMode.fromId(snapshot.effectMode)
        val profile = DolbyProfile.fromId(snapshot.dolbyProfile)

        prefs.edit()
            .putString(KEY_EFFECT_MODE, mode.id)
            .putInt(KEY_DOLBY_PROFILE, profile.id)
            .putBoolean(KEY_SURROUND_ENABLED, snapshot.surroundEnabled)
            .putInt(KEY_SURROUND_STRENGTH, (snapshot.surroundStrength * 10).toInt())
            .putBoolean(KEY_EQUALIZER_ENABLED, snapshot.equalizerEnabled)
            .putString(KEY_EQUALIZER_BANDS, snapshot.equalizerBands.joinToString(","))
            .putString(KEY_EQUALIZER_PRESET, snapshot.equalizerPreset)
            .apply()

        if (snapshot.autoEqProfileName != null) {
            prefs.edit().putString(KEY_AUTOEQ_PROFILE_NAME, snapshot.autoEqProfileName).apply()
        } else {
            prefs.edit().remove(KEY_AUTOEQ_PROFILE_NAME).apply()
        }

        _effectModeState.value = mode
        _equalizerEnabledState.value = snapshot.equalizerEnabled
        _equalizerBandsState.value = snapshot.equalizerBands
        _equalizerPresetState.value = snapshot.equalizerPreset
        _autoEqProfileNameState.value = snapshot.autoEqProfileName

        applyEffectMode(mode)
        applySurroundSound(snapshot.surroundEnabled, (snapshot.surroundStrength * 10).toInt())
        applyEqualizerSettings()
    }

    fun resetDolbyDsp() {
        Log.d(TAG, "Resetting Dolby DAP DSP to factory defaults")
        try {
            val profiles = listOf(0, 1, 2, 8)
            for (p in profiles) {
                val buf = ByteArray(12)
                intToByteArray(EFFECT_PARAM_RESET_PROFILE_SETTINGS, buf, 0)
                intToByteArray(1, buf, 4)
                intToByteArray(p, buf, 8)
                setAudioEffectParameter(dolbyEffect, EFFECT_PARAM_CPDP_VALUES, buf)
                setDolbyProfileParam(p, DAP_PARAM_BASS_ENHANCER_ENABLE, intArrayOf(1))
            }
            Log.d(TAG, "Dolby DSP reset successfully across all profiles with Bass Enhancer active")
        } catch (e: Exception) {
            Log.w(TAG, "Error resetting Dolby DSP", e)
        }
        resetEqualizer()
        applyDolbyProfile(getDolbyProfile())
        saveCurrentStateToDevice()
    }

    fun applyEqualizerSettings() {
        val isEnabled = isEqualizerEnabled()
        val bands = getEqualizerBands()

        // 1. Dolby DAP Offload GEQ (20 internal DSP bands)
        try {
            if (isEnabled) {
                val dapGains = serialize10To20DapGains(bands)
                setDolbyEqualizerGains(getDolbyProfile().id, dapGains)
            } else {
                // When EQ is disabled, restore factory profile calibration and Bass Enhancer
                val buf = ByteArray(12)
                intToByteArray(EFFECT_PARAM_RESET_PROFILE_SETTINGS, buf, 0)
                intToByteArray(1, buf, 4)
                intToByteArray(getDolbyProfile().id, buf, 8)
                setAudioEffectParameter(dolbyEffect, EFFECT_PARAM_CPDP_VALUES, buf)
                setDolbyProfileParam(getDolbyProfile().id, DAP_PARAM_BASS_ENHANCER_ENABLE, intArrayOf(1))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error setting Dolby DAP GEQ gains", e)
        }

        // 2. Global Equalizer Effect (MiSound & AOSP Output)
        try {
            equalizerEffect?.let { eq ->
                eq.enabled = isEnabled
                if (isEnabled) {
                    val numBands = eq.numberOfBands.toInt()
                    for (i in 0 until minOf(bands.size, numBands)) {
                        val millibels = (bands[i] * 100).toInt().coerceIn(-1500, 1500).toShort()
                        eq.setBandLevel(i.toShort(), millibels)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error applying fallback Equalizer effect", e)
        }

        saveCurrentStateToDevice()
    }

    private fun setDolbyEqualizerGains(profile: Int, gains20: IntArray) {
        val length = gains20.size
        val buffer = ByteArray((length + 4) * 4)
        intToByteArray(EFFECT_PARAM_PROFILE_PARAMETER, buffer, 0)
        intToByteArray(length + 1, buffer, 4)
        intToByteArray(profile, buffer, 8)
        intToByteArray(DAP_PARAM_GEQ_BAND_GAINS, buffer, 12)
        for (i in gains20.indices) {
            intToByteArray(gains20[i], buffer, 16 + i * 4)
        }
        setAudioEffectParameter(dolbyEffect, EFFECT_PARAM_CPDP_VALUES, buffer)
    }

    private fun serialize10To20DapGains(tenBandGains: List<Float>): IntArray {
        val result = IntArray(20) { 0 }
        val gainsInt = tenBandGains.map { (it * 10f).toInt() }
        for (i in 0 until minOf(10, gainsInt.size)) {
            result[i * 2] = gainsInt[i]
        }
        for (i in 0 until 18 step 2) {
            result[i + 1] = (result[i] + result[i + 2]) / 2
        }
        result[19] = result[18]
        return result
    }

    fun release() {
        try {
            spatializer.removeOnSpatializerStateChangedListener(spatializerListener)
            deviceStateManager?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering Spatializer listener", e)
        }
        try {
            dolbyEffect?.release()
            miSoundEffect?.release()
            virtualizer?.release()
            equalizerEffect?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing audio effects", e)
        }
    }
}
