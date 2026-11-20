package com.puppyfrommars.hellodolby.audio

import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.Spatializer
import android.media.audiofx.AudioEffect
import android.os.IBinder
import android.os.Parcel
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
    GAME(4),
    VOICE(8),
    CUSTOM(3);

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
        private const val KEY_SPATIAL_AUDIO_ENABLED = "spatial_audio_enabled"
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
        private const val EFFECT_PARAM_SELECTED_TUNING_DEVICE = 4

        // Exact parameter IDs from com.dolby.dax.DsParams
        private const val DAP_PARAM_HEADPHONE_VIRTUALIZER = 101       // 0x65 (DolbyHeadphoneVirtualizerControl)
        private const val DAP_PARAM_SPEAKER_VIRTUALIZER = 102         // 0x66 (DolbyVirtualSpeakerVirtualizerControl)
        private const val DAP_PARAM_VOLUME_LEVELER_ENABLE = 103       // 0x67 (DolbyVolumeLevelerEnable)
        private const val DAP_PARAM_IEQ_PRESET = 104                  // 0x68 (IntelligentEqualizerPreset)
        private const val DAP_PARAM_DIALOG_ENHANCER_ENABLE = 105      // 0x69 (DialogEnhancementEnable)
        private const val DAP_PARAM_GEQ_ENABLE = 106                  // 0x6a (GraphicEqualizerEnable)
        private const val DAP_PARAM_IEQ_AMOUNT = 107                  // 0x6b (IntelligentEqualizerAmount)
        private const val DAP_PARAM_DIALOG_ENHANCER_AMOUNT = 108      // 0x6c (DialogEnhancementAmount)
        private const val DAP_PARAM_DIALOG_ENHANCER_DUCKING = 109     // 0x6d (DialogEnhancementDucking)
        private const val DAP_PARAM_GEQ_BAND_GAINS = 110              // 0x6e (GraphicEqualizerBandGains)
        private const val DAP_PARAM_BASS_ENHANCER_ENABLE = 111        // 0x6f (BassEnable)
        private const val DAP_PARAM_VIRTUAL_BASS_ENABLE = 112         // 0x70 (VirtualBassEnable)
        private const val DAP_PARAM_STEREO_WIDENING = 113             // 0x71 (StereoWideningAmount)
        private const val DAP_PARAM_REVERB_REDUCTION_ENABLE = 114     // 0x72 (ReverbReductionEnable)
        private const val DAP_PARAM_REVERB_REDUCTION_AMOUNT = 115     // 0x73 (ReverbReductionAmount)
        private const val DAP_PARAM_VOLUME_LEVELER_AMOUNT = 116       // 0x74 (DolbyVolumeLevelerAmount)

        // Equalizer & AutoEQ Constants
        private const val KEY_EQUALIZER_ENABLED = "equalizer_enabled"
        private const val KEY_EQUALIZER_BANDS = "equalizer_bands"
        private const val KEY_EQUALIZER_PRESET = "equalizer_preset"
        private const val KEY_AUTOEQ_PROFILE_NAME = "autoeq_profile_name"
        private const val KEY_CUSTOM_PRESETS = "custom_presets_json"

        // Enhancer Constants
        private const val KEY_BASS_ENHANCER_ENABLED = "bass_enhancer_enabled"
        private const val KEY_BASS_ENHANCER_LEVEL = "bass_enhancer_level"
        private const val KEY_MID_ENHANCER_ENABLED = "mid_enhancer_enabled"
        private const val KEY_MID_ENHANCER_LEVEL = "mid_enhancer_level"
        private const val KEY_TREBLE_ENHANCER_ENABLED = "treble_enhancer_enabled"
        private const val KEY_TREBLE_ENHANCER_LEVEL = "treble_enhancer_level"
        private const val KEY_VOLUME_LEVELER_ENABLED = "volume_leveler_enabled"
        private const val KEY_VOLUME_LEVELER_LEVEL = "volume_leveler_level"
        private const val KEY_DIALOGUE_ENHANCER_ENABLED = "dialogue_enhancer_enabled"
        private const val KEY_DIALOGUE_ENHANCER_LEVEL = "dialogue_enhancer_level"
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

        @Volatile
        private var instance: AudioEffectManager? = null

        fun getInstance(context: Context): AudioEffectManager {
            return instance ?: synchronized(this) {
                instance ?: AudioEffectManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val spatializer: Spatializer = audioManager.spatializer
    private val executor = java.util.concurrent.Executors.newSingleThreadExecutor()

    private var dolbyEffect: AudioEffect? = null
    private var miSoundEffect: AudioEffect? = null

    private val _isHeadphonesConnected = MutableStateFlow(isHeadphonesActive())
    val isHeadphonesConnectedState: StateFlow<Boolean> = _isHeadphonesConnected.asStateFlow()

    private val _spatialAudioState by lazy { MutableStateFlow(isSpatialAudioEnabled()) }
    val spatialAudioState: StateFlow<Boolean> by lazy { _spatialAudioState.asStateFlow() }

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

    private val _bassEnhancerEnabledState by lazy { MutableStateFlow(isBassEnhancerEnabled()) }
    val bassEnhancerEnabledState: StateFlow<Boolean> by lazy { _bassEnhancerEnabledState.asStateFlow() }

    private val _bassEnhancerLevelState by lazy { MutableStateFlow(getBassEnhancerLevel()) }
    val bassEnhancerLevelState: StateFlow<Int> by lazy { _bassEnhancerLevelState.asStateFlow() }

    private val _midEnhancerEnabledState by lazy { MutableStateFlow(isMidEnhancerEnabled()) }
    val midEnhancerEnabledState: StateFlow<Boolean> by lazy { _midEnhancerEnabledState.asStateFlow() }

    private val _midEnhancerLevelState by lazy { MutableStateFlow(getMidEnhancerLevel()) }
    val midEnhancerLevelState: StateFlow<Int> by lazy { _midEnhancerLevelState.asStateFlow() }

    private val _trebleEnhancerEnabledState by lazy { MutableStateFlow(isTrebleEnhancerEnabled()) }
    val trebleEnhancerEnabledState: StateFlow<Boolean> by lazy { _trebleEnhancerEnabledState.asStateFlow() }

    private val _trebleEnhancerLevelState by lazy { MutableStateFlow(getTrebleEnhancerLevel()) }
    val trebleEnhancerLevelState: StateFlow<Int> by lazy { _trebleEnhancerLevelState.asStateFlow() }

    private val _volumeLevelerEnabledState by lazy { MutableStateFlow(isVolumeLevelerEnabled()) }
    val volumeLevelerEnabledState: StateFlow<Boolean> by lazy { _volumeLevelerEnabledState.asStateFlow() }

    private val _volumeLevelerLevelState by lazy { MutableStateFlow(getVolumeLevelerLevel()) }
    val volumeLevelerLevelState: StateFlow<Int> by lazy { _volumeLevelerLevelState.asStateFlow() }

    private val _dialogueEnhancerEnabledState by lazy { MutableStateFlow(isDialogueEnhancerEnabled()) }
    val dialogueEnhancerEnabledState: StateFlow<Boolean> by lazy { _dialogueEnhancerEnabledState.asStateFlow() }

    private val _dialogueEnhancerLevelState by lazy { MutableStateFlow(getDialogueEnhancerLevel()) }
    val dialogueEnhancerLevelState: StateFlow<Int> by lazy { _dialogueEnhancerLevelState.asStateFlow() }

    private var deviceStateManager: DeviceStateManager? = null
    private val _connectedDeviceState = MutableStateFlow("Phone Speaker")
    val connectedDeviceState: StateFlow<String> = _connectedDeviceState.asStateFlow()

    private val spatializerListener = object : Spatializer.OnSpatializerStateChangedListener {
        override fun onSpatializerEnabledChanged(spat: Spatializer, enabled: Boolean) {
            Log.d(TAG, "Spatializer listener: onSpatializerEnabledChanged = $enabled")
            if (isHeadphonesActive() && spat.isAvailable) {
                _spatialAudioState.value = enabled
                prefs.edit().putBoolean(KEY_SPATIAL_AUDIO_ENABLED, enabled).apply()
            }
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
            _spatialAudioAvailable.value = spatializer.isAvailable
            Log.d(TAG, "Registered Spatializer state listener successfully")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register Spatializer listener", e)
        }
    }

    fun setSelectedTuningDevice(port: Int, device: String) {
        try {
            val devBytes = device.toByteArray(Charsets.UTF_8)
            val buffer = ByteArray(devBytes.size + 4)
            intToByteArray(port, buffer, 0)
            System.arraycopy(devBytes, 0, buffer, 4, devBytes.size)
            setAudioEffectParameter(dolbyEffect, EFFECT_PARAM_SELECTED_TUNING_DEVICE, buffer)
            Log.d(TAG, "setSelectedTuningDevice: port=$port, device=$device")
        } catch (e: Exception) {
            Log.w(TAG, "Error in setSelectedTuningDevice", e)
        }
    }

    fun updateTuningDevice() {
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val btDevice = outputs.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
            it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
        }
        val wiredDevice = outputs.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET
        }
        val usbDevice = outputs.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
            it.type == AudioDeviceInfo.TYPE_USB_DEVICE
        }

        when {
            btDevice != null -> setSelectedTuningDevice(4, "default_bluetooth")
            wiredDevice != null -> setSelectedTuningDevice(3, "default_headphone")
            usbDevice != null -> setSelectedTuningDevice(5, "default_usb")
            else -> setSelectedTuningDevice(0, "default_internal_speaker")
        }
    }

    private fun initEffects() {
        dolbyEffect = createAudioEffect(UUID_DOLBY_DAP)
        miSoundEffect = createAudioEffect(UUID_MISOUND_FX)

        val mode = getEffectMode()
        applyEffectMode(mode)

        val surroundEnabled = isSurroundEnabled()
        val surroundStrength = getSurroundStrength()
        applySurroundSound(surroundEnabled, surroundStrength)

        applyEqualizerSettings()
        verifyDolbyCommunication()
    }

    // --- Audio Processing Engine ---

    fun getEffectMode(): EffectMode {
        val saved = prefs.getString(KEY_EFFECT_MODE, EffectMode.DOLBY.id)
        val mode = EffectMode.fromId(saved)
        return if (mode == EffectMode.OFF && !isHeadphonesActive()) EffectMode.MISOUND else mode
    }

    fun setEffectMode(mode: EffectMode) {
        val effectiveMode = if (mode == EffectMode.OFF && !isHeadphonesActive()) {
            EffectMode.MISOUND
        } else {
            mode
        }
        prefs.edit().putString(KEY_EFFECT_MODE, effectiveMode.id).apply()
        _effectModeState.value = effectiveMode
        applyEffectMode(effectiveMode)
    }

    private fun applyEffectMode(mode: EffectMode) {
        val effectiveMode = if (mode == EffectMode.OFF && !isHeadphonesActive()) {
            EffectMode.MISOUND
        } else {
            mode
        }
        Log.d(TAG, "applyEffectMode: $effectiveMode (requested: $mode)")
        when (effectiveMode) {
            EffectMode.DOLBY -> {
                // 1. Disable MiSound first
                try {
                    setMiSoundParameter(MISOUND_PARAM_3D_SURROUND, 0)
                    miSoundEffect?.enabled = false
                } catch (e: Exception) {
                    Log.w(TAG, "Error disabling MiSound", e)
                }

                // 2. Set Audio HAL parameter for Dolby routing & DSP path
                try {
                    audioManager.setParameters("dolby_mode=true")
                    audioManager.setParameters("effect_switch_fade=true")
                } catch (e: Exception) {
                    Log.w(TAG, "Error setting dolby_mode audio parameters", e)
                }

                // 3. Enable Dolby DAP internal DSP and effect
                try {
                    setDolbyBoolParam(EFFECT_PARAM_EFFECT_ENABLE, true)
                    dolbyEffect?.enabled = true
                    applyDolbyProfile(getDolbyProfile())
                    updateTuningDevice()
                } catch (e: Exception) {
                    Log.w(TAG, "Error activating Dolby DAP", e)
                }

                // 4. Apply surround settings
                applySurroundSound(isSurroundEnabled(), getSurroundStrength())

                // 5. Update system settings & notify HAL
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

                // 2. Set Audio HAL parameter for standard routing
                try {
                    audioManager.setParameters("dolby_mode=false")
                    audioManager.setParameters("effect_switch_fade=true")
                } catch (e: Exception) {
                    Log.w(TAG, "Error setting dolby_mode=false", e)
                }

                // 3. Enable MiSound effect and set default mode
                try {
                    miSoundEffect?.enabled = true
                    setMiSoundParameter(MISOUND_PARAM_MODE, 1)
                } catch (e: Exception) {
                    Log.w(TAG, "Error activating MiSound", e)
                }

                // 4. Apply surround settings
                applySurroundSound(isSurroundEnabled(), getSurroundStrength())

                // 5. Update system settings & notify HAL
                syncSystemSetting("misound", isNone = false)
            }
            EffectMode.OFF -> {
                // 1. Disable Dolby DAP
                try {
                    setDolbyBoolParam(EFFECT_PARAM_EFFECT_ENABLE, false)
                    dolbyEffect?.enabled = false
                } catch (e: Exception) {
                    Log.w(TAG, "Error disabling Dolby", e)
                }

                // 2. Disable MiSound
                try {
                    setMiSoundParameter(MISOUND_PARAM_3D_SURROUND, 0)
                    miSoundEffect?.enabled = false
                } catch (e: Exception) {
                    Log.w(TAG, "Error disabling MiSound", e)
                }

                // 3. Set Audio HAL parameter for standard routing
                try {
                    audioManager.setParameters("dolby_mode=false")
                    audioManager.setParameters("effect_switch_fade=true")
                } catch (e: Exception) {
                    Log.w(TAG, "Error setting dolby_mode=false", e)
                }

                // 4. Disable surround & spatial
                applySurroundSound(false, getSurroundStrength())

                // 5. Update system settings & notify HAL
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
            val userHandleAll = UserHandle::class.java.getField("ALL").get(null) as UserHandle
            val sendMethod = context.javaClass.getMethod("sendBroadcastAsUser", Intent::class.java, UserHandle::class.java)
            val intent = Intent("miui.intent.action.ACTION_AUDIO_EFFECT_CHANGED")
            sendMethod.invoke(context, intent, userHandleAll)
        } catch (e: Exception) {
            try {
                val intent = Intent("miui.intent.action.ACTION_AUDIO_EFFECT_CHANGED")
                context.sendBroadcast(intent)
            } catch (ex: Exception) {
                Log.w(TAG, "Could not broadcast ACTION_AUDIO_EFFECT_CHANGED", ex)
            }
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

    // --- Immersive Sound (MiSound 3D Surround & Dolby DAP Spatializer) ---

    fun isSpatialAudioAvailable(): Boolean = true

    fun isSpatialAudioEnabled(): Boolean =
        prefs.getBoolean(KEY_SPATIAL_AUDIO_ENABLED, prefs.getBoolean(KEY_SURROUND_ENABLED, false))

    fun isHeadphonesActive(): Boolean {
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        return outputs.any {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
            it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
            it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
            it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
            it.type == AudioDeviceInfo.TYPE_HEARING_AID
        }
    }

    fun setSpatialAudioEnabled(enabled: Boolean) {
        Log.d(TAG, "setSpatialAudioEnabled: $enabled")
        // Optimistically update state flow for instant UI feedback
        _spatialAudioState.value = enabled
        prefs.edit()
            .putBoolean(KEY_SPATIAL_AUDIO_ENABLED, enabled)
            .putBoolean(KEY_SURROUND_ENABLED, enabled)
            .apply()

        executor.execute {
            // 1. Dispatch MiSound AIDL 3D Surround parameter (20)
            try {
                setMiSoundParameter(MISOUND_PARAM_3D_SURROUND, if (enabled) 1 else 0)
                Log.d(TAG, "Dispatched MiSound 3D surround parameter: $enabled")
            } catch (e: Exception) {
                Log.w(TAG, "Error setting MiSound 3D surround parameter", e)
            }

            // 2. Set persistent vendor property for 3D surround (hardware HAL switch)
            try {
                executeRootCommand("setprop persist.vendor.audio.3dsurround.enable ${if (enabled) "true" else "false"}")
            } catch (e: Exception) {
                Log.w(TAG, "Error setting persist.vendor.audio.3dsurround.enable", e)
            }

            // 3. Audio HAL parameters & switch fade
            try {
                audioManager.setParameters("3d_surround_enable=${if (enabled) "true" else "false"}")
                audioManager.setParameters("effect_switch_fade=true")
            } catch (e: Exception) {
                Log.w(TAG, "Error setting audio parameters for 3D surround", e)
            }

            // 4. Send MiSound 3D Surround broadcast
            try {
                val intent = Intent("com.miui.misound.ACTION_3D_SURROUND_STATE_CHANGED").apply {
                    putExtra("extra_state", enabled)
                    putExtra("state", enabled)
                }
                context.sendBroadcast(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Could not broadcast ACTION_3D_SURROUND_STATE_CHANGED", e)
            }

            // 5. Update system and global settings provider
            try {
                val code = if (enabled) 1 else 0
                Settings.Global.putInt(context.contentResolver, "spatial_audio_feature_enable", code)
                Settings.Global.putInt(context.contentResolver, "last_spatial_status", code)
                executeRootCommand("settings put system is_spatial_audio $code && settings put global spatial_audio_feature_enable $code")
            } catch (e: Exception) {
                Log.d(TAG, "Note: spatial settings updated via fallback root", e)
            }

            // 6. Apply DAP Virtualizer DSP
            applySurroundSound(enabled, getSurroundStrength())

            saveCurrentStateToDevice()
        }
    }

    // --- Surround Sound (Virtualizer) ---

    fun isSurroundEnabled(): Boolean = isSpatialAudioEnabled()

    fun getSurroundStrength(): Int = prefs.getInt(KEY_SURROUND_STRENGTH, 500)

    fun setSurroundEnabled(enabled: Boolean) {
        setSpatialAudioEnabled(enabled)
    }

    fun setSurroundStrength(strength: Int) {
        prefs.edit().putInt(KEY_SURROUND_STRENGTH, strength).apply()
        if (isSpatialAudioEnabled()) {
            applySurroundSound(true, strength)
        }
    }

    private fun applySurroundSound(enabled: Boolean, strength: Int) {
        val mode = getEffectMode()
        val isHeadphones = isHeadphonesActive()
        Log.d(TAG, "applySurroundSound: enabled=$enabled, strength=$strength, mode=$mode, isHeadphones=$isHeadphones")

        when (mode) {
            EffectMode.DOLBY -> {
                val profile = getDolbyProfile().id
                try {
                    if (isHeadphones) {
                        setDolbyProfileParam(profile, DAP_PARAM_HEADPHONE_VIRTUALIZER, intArrayOf(if (enabled) 1 else 0))
                        val widening = 4 + (strength * (160 - 4) / 1000)
                        setDolbyPortParam(profile, 3, DAP_PARAM_STEREO_WIDENING, intArrayOf(if (enabled) widening else 0))
                        Log.d(TAG, "Applied Dolby surround on headphones: enabled=$enabled, widening=$widening")
                    } else {
                        // On built-in phone speakers, leave Dolby's native profile speaker tuning untouched
                        Log.d(TAG, "Dolby on speaker: preserving native speaker tuning")
                    }
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
            val hasCtrl = try { effect.hasControl() } catch (e: Exception) { false }
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
                Log.w(TAG, "setParameter($param) returned status $status [hasControl=$hasCtrl]")
            } else {
                Log.d(TAG, "setParameter($param) SUCCESS [hasControl=$hasCtrl]")
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
            constructor.newInstance(EFFECT_TYPE_NULL, uuid, 1000, 0) as AudioEffect
        } catch (e: Exception) {
            Log.w(TAG, "Failed to instantiate AudioEffect for UUID: $uuid", e)
            null
        }
    }

    fun getDmsService(): IBinder? {
        return try {
            val smClass = Class.forName("android.os.ServiceManager")
            val getServiceMethod = smClass.getMethod("getService", String::class.java)
            getServiceMethod.invoke(null, "vendor.dolby.dms.IDms/default") as? IBinder
        } catch (e: Exception) {
            Log.w(TAG, "Could not get vendor.dolby.dms.IDms/default", e)
            null
        }
    }

    fun sendDmsCommand(commandId: Int, inData: ByteArray): Boolean {
        val binder = getDmsService() ?: return false
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken("vendor.dolby.dms.IDms")
            data.writeInt(commandId)
            data.writeByteArray(inData)
            val ok = binder.transact(1, data, reply, 0)
            reply.readException()
            ok
        } catch (e: Exception) {
            Log.e(TAG, "sendDmsCommand(0x${Integer.toHexString(commandId)}) failed", e)
            false
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    fun handleDmsRequest(requestId: Int, inData: ByteArray = ByteArray(0)): ByteArray? {
        val binder = getDmsService() ?: return null
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken("vendor.dolby.dms.IDms")
            data.writeInt(requestId)
            data.writeByteArray(inData)
            binder.transact(2, data, reply, 0)
            reply.readException()
            reply.createByteArray()
        } catch (e: Exception) {
            Log.e(TAG, "handleDmsRequest(0x${Integer.toHexString(requestId)}) failed", e)
            null
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    fun verifyDolbyCommunication(): String {
        val sb = StringBuilder()
        sb.append("\n=======================================================\n")
        sb.append("=== HelloDolby -> Dolby AIDL HAL Diagnostic Report ===\n")
        sb.append("=======================================================\n")

        // 1. AudioEffect AIDL Effect (libhwdapaidl.so / DAP_offload)
        val effect = dolbyEffect
        if (effect != null) {
            val hasCtrl = try { effect.hasControl() } catch (e: Exception) { false }
            val id = try { effect.id } catch (e: Exception) { -1 }
            val enabled = try { effect.enabled } catch (e: Exception) { false }
            sb.append("1. AudioEffect AIDL (DAP_offload): INSTANTIATED\n")
            sb.append("   - Effect ID: $id\n")
            sb.append("   - Has Control: $hasCtrl\n")
            sb.append("   - Enabled: $enabled\n")
        } else {
            sb.append("1. AudioEffect AIDL (DAP_offload): NOT INSTANTIATED (null)\n")
        }

        // 2. Direct AIDL HAL Service (vendor.dolby.dms.IDms/default)
        val binder = getDmsService()
        if (binder != null) {
            val ping = binder.pingBinder()
            val desc = binder.interfaceDescriptor
            sb.append("2. AIDL Service (vendor.dolby.dms.IDms/default): CONNECTED\n")
            sb.append("   - Ping: $ping\n")
            sb.append("   - Interface Descriptor: $desc\n")

            // Test Transaction 16777215 (getInterfaceVersion)
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken("vendor.dolby.dms.IDms")
                val ok = binder.transact(16777215, data, reply, 0)
                reply.readException()
                val version = reply.readInt()
                sb.append("   - getInterfaceVersion(): SUCCESS (AIDL Version = $version, transactOk = $ok)\n")
            } catch (e: Exception) {
                sb.append("   - getInterfaceVersion(): FAILED (${e.javaClass.simpleName}: ${e.message})\n")
            } finally {
                data.recycle()
                reply.recycle()
            }

            // Test Transaction 2 (handleRequest: GADV = 0x47414456 Get Active Device)
            try {
                val outBytes = handleDmsRequest(0x47414456)
                sb.append("   - handleRequest(GADV): SUCCESS (Received ${outBytes?.size ?: 0} bytes)\n")
            } catch (e: Exception) {
                sb.append("   - handleRequest(GADV): FAILED (${e.javaClass.simpleName}: ${e.message})\n")
            }
        } else {
            sb.append("2. AIDL Service (vendor.dolby.dms.IDms/default): BINDER NULL (SELinux or service unavailable)\n")
        }
        sb.append("=======================================================")

        val report = sb.toString()
        Log.i(TAG, report)
        return report
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
        prefs.edit()
            .remove(KEY_AUTOEQ_PROFILE_NAME)
            .putBoolean(KEY_BASS_ENHANCER_ENABLED, false)
            .putInt(KEY_BASS_ENHANCER_LEVEL, 50)
            .putBoolean(KEY_MID_ENHANCER_ENABLED, false)
            .putInt(KEY_MID_ENHANCER_LEVEL, 50)
            .putBoolean(KEY_TREBLE_ENHANCER_ENABLED, false)
            .putInt(KEY_TREBLE_ENHANCER_LEVEL, 50)
            .putBoolean(KEY_VOLUME_LEVELER_ENABLED, false)
            .putInt(KEY_VOLUME_LEVELER_LEVEL, 50)
            .putBoolean(KEY_DIALOGUE_ENHANCER_ENABLED, false)
            .putInt(KEY_DIALOGUE_ENHANCER_LEVEL, 50)
            .apply()
        _autoEqProfileNameState.value = null
        _bassEnhancerEnabledState.value = false
        _bassEnhancerLevelState.value = 50
        _midEnhancerEnabledState.value = false
        _midEnhancerLevelState.value = 50
        _trebleEnhancerEnabledState.value = false
        _trebleEnhancerLevelState.value = 50
        _volumeLevelerEnabledState.value = false
        _volumeLevelerLevelState.value = 50
        _dialogueEnhancerEnabledState.value = false
        _dialogueEnhancerLevelState.value = 50
        applyEqualizerSettings()
    }

    // --- Enhancers (Bass, Mid, Treble, Volume Leveler, Dialogue) ---

    fun isBassEnhancerEnabled(): Boolean = prefs.getBoolean(KEY_BASS_ENHANCER_ENABLED, false)
    fun getBassEnhancerLevel(): Int = prefs.getInt(KEY_BASS_ENHANCER_LEVEL, 50)
    fun setBassEnhancerEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BASS_ENHANCER_ENABLED, enabled).apply()
        _bassEnhancerEnabledState.value = enabled
        applyEqualizerSettings()
    }
    fun setBassEnhancerLevel(level: Int) {
        val clamped = level.coerceIn(0, 100)
        prefs.edit().putInt(KEY_BASS_ENHANCER_LEVEL, clamped).apply()
        _bassEnhancerLevelState.value = clamped
        if (isBassEnhancerEnabled()) applyEqualizerSettings()
    }

    fun isMidEnhancerEnabled(): Boolean = prefs.getBoolean(KEY_MID_ENHANCER_ENABLED, false)
    fun getMidEnhancerLevel(): Int = prefs.getInt(KEY_MID_ENHANCER_LEVEL, 50)
    fun setMidEnhancerEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MID_ENHANCER_ENABLED, enabled).apply()
        _midEnhancerEnabledState.value = enabled
        applyEqualizerSettings()
    }
    fun setMidEnhancerLevel(level: Int) {
        val clamped = level.coerceIn(0, 100)
        prefs.edit().putInt(KEY_MID_ENHANCER_LEVEL, clamped).apply()
        _midEnhancerLevelState.value = clamped
        if (isMidEnhancerEnabled()) applyEqualizerSettings()
    }

    fun isTrebleEnhancerEnabled(): Boolean = prefs.getBoolean(KEY_TREBLE_ENHANCER_ENABLED, false)
    fun getTrebleEnhancerLevel(): Int = prefs.getInt(KEY_TREBLE_ENHANCER_LEVEL, 50)
    fun setTrebleEnhancerEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TREBLE_ENHANCER_ENABLED, enabled).apply()
        _trebleEnhancerEnabledState.value = enabled
        applyEqualizerSettings()
    }
    fun setTrebleEnhancerLevel(level: Int) {
        val clamped = level.coerceIn(0, 100)
        prefs.edit().putInt(KEY_TREBLE_ENHANCER_LEVEL, clamped).apply()
        _trebleEnhancerLevelState.value = clamped
        if (isTrebleEnhancerEnabled()) applyEqualizerSettings()
    }

    fun isVolumeLevelerEnabled(): Boolean = prefs.getBoolean(KEY_VOLUME_LEVELER_ENABLED, false)
    fun getVolumeLevelerLevel(): Int = prefs.getInt(KEY_VOLUME_LEVELER_LEVEL, 50)
    fun setVolumeLevelerEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VOLUME_LEVELER_ENABLED, enabled).apply()
        _volumeLevelerEnabledState.value = enabled
        applyEqualizerSettings()
    }
    fun setVolumeLevelerLevel(level: Int) {
        val clamped = level.coerceIn(0, 100)
        prefs.edit().putInt(KEY_VOLUME_LEVELER_LEVEL, clamped).apply()
        _volumeLevelerLevelState.value = clamped
        if (isVolumeLevelerEnabled()) applyEqualizerSettings()
    }

    fun isDialogueEnhancerEnabled(): Boolean = prefs.getBoolean(KEY_DIALOGUE_ENHANCER_ENABLED, false)
    fun getDialogueEnhancerLevel(): Int = prefs.getInt(KEY_DIALOGUE_ENHANCER_LEVEL, 50)
    fun setDialogueEnhancerEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DIALOGUE_ENHANCER_ENABLED, enabled).apply()
        _dialogueEnhancerEnabledState.value = enabled
        applyEqualizerSettings()
    }
    fun setDialogueEnhancerLevel(level: Int) {
        val clamped = level.coerceIn(0, 100)
        prefs.edit().putInt(KEY_DIALOGUE_ENHANCER_LEVEL, clamped).apply()
        _dialogueEnhancerLevelState.value = clamped
        if (isDialogueEnhancerEnabled()) applyEqualizerSettings()
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
            autoEqProfileName = getAutoEqProfileName(),
            bassEnhancerEnabled = isBassEnhancerEnabled(),
            bassEnhancerLevel = getBassEnhancerLevel(),
            midEnhancerEnabled = isMidEnhancerEnabled(),
            midEnhancerLevel = getMidEnhancerLevel(),
            trebleEnhancerEnabled = isTrebleEnhancerEnabled(),
            trebleEnhancerLevel = getTrebleEnhancerLevel(),
            volumeLevelerEnabled = isVolumeLevelerEnabled(),
            volumeLevelerLevel = getVolumeLevelerLevel(),
            dialogueEnhancerEnabled = isDialogueEnhancerEnabled(),
            dialogueEnhancerLevel = getDialogueEnhancerLevel()
        )
    }

    private fun onDeviceSnapshotRestored(snapshot: AudioDeviceSnapshot) {
        val isHeadphones = isHeadphonesActive()
        _isHeadphonesConnected.value = isHeadphones
        Log.d(TAG, "Restoring audio device snapshot: ${snapshot.displayName} (isHeadphones=$isHeadphones)")
        _connectedDeviceState.value = snapshot.displayName

        val rawMode = EffectMode.fromId(snapshot.effectMode)
        val mode = if (rawMode == EffectMode.OFF && !isHeadphones) EffectMode.MISOUND else rawMode
        val profile = DolbyProfile.fromId(snapshot.dolbyProfile)

        prefs.edit()
            .putString(KEY_EFFECT_MODE, mode.id)
            .putInt(KEY_DOLBY_PROFILE, profile.id)
            .putBoolean(KEY_SURROUND_ENABLED, snapshot.surroundEnabled)
            .putBoolean(KEY_SPATIAL_AUDIO_ENABLED, snapshot.surroundEnabled)
            .putInt(KEY_SURROUND_STRENGTH, (snapshot.surroundStrength * 10).toInt())
            .putBoolean(KEY_EQUALIZER_ENABLED, snapshot.equalizerEnabled)
            .putString(KEY_EQUALIZER_BANDS, snapshot.equalizerBands.joinToString(","))
            .putString(KEY_EQUALIZER_PRESET, snapshot.equalizerPreset)
            .putBoolean(KEY_BASS_ENHANCER_ENABLED, snapshot.bassEnhancerEnabled)
            .putInt(KEY_BASS_ENHANCER_LEVEL, snapshot.bassEnhancerLevel)
            .putBoolean(KEY_MID_ENHANCER_ENABLED, snapshot.midEnhancerEnabled)
            .putInt(KEY_MID_ENHANCER_LEVEL, snapshot.midEnhancerLevel)
            .putBoolean(KEY_TREBLE_ENHANCER_ENABLED, snapshot.trebleEnhancerEnabled)
            .putInt(KEY_TREBLE_ENHANCER_LEVEL, snapshot.trebleEnhancerLevel)
            .putBoolean(KEY_VOLUME_LEVELER_ENABLED, snapshot.volumeLevelerEnabled)
            .putInt(KEY_VOLUME_LEVELER_LEVEL, snapshot.volumeLevelerLevel)
            .putBoolean(KEY_DIALOGUE_ENHANCER_ENABLED, snapshot.dialogueEnhancerEnabled)
            .putInt(KEY_DIALOGUE_ENHANCER_LEVEL, snapshot.dialogueEnhancerLevel)
            .apply()

        if (snapshot.autoEqProfileName != null) {
            prefs.edit().putString(KEY_AUTOEQ_PROFILE_NAME, snapshot.autoEqProfileName).apply()
        } else {
            prefs.edit().remove(KEY_AUTOEQ_PROFILE_NAME).apply()
        }

        _effectModeState.value = mode
        _spatialAudioState.value = snapshot.surroundEnabled
        _equalizerEnabledState.value = snapshot.equalizerEnabled
        _equalizerBandsState.value = snapshot.equalizerBands
        _equalizerPresetState.value = snapshot.equalizerPreset
        _autoEqProfileNameState.value = snapshot.autoEqProfileName
        _bassEnhancerEnabledState.value = snapshot.bassEnhancerEnabled
        _bassEnhancerLevelState.value = snapshot.bassEnhancerLevel
        _midEnhancerEnabledState.value = snapshot.midEnhancerEnabled
        _midEnhancerLevelState.value = snapshot.midEnhancerLevel
        _trebleEnhancerEnabledState.value = snapshot.trebleEnhancerEnabled
        _trebleEnhancerLevelState.value = snapshot.trebleEnhancerLevel
        _volumeLevelerEnabledState.value = snapshot.volumeLevelerEnabled
        _volumeLevelerLevelState.value = snapshot.volumeLevelerLevel
        _dialogueEnhancerEnabledState.value = snapshot.dialogueEnhancerEnabled
        _dialogueEnhancerLevelState.value = snapshot.dialogueEnhancerLevel

        applyEffectMode(mode)
        applySurroundSound(snapshot.surroundEnabled, (snapshot.surroundStrength * 10).toInt())
        applyEqualizerSettings()
    }

    fun resetDolbyDsp() {
        Log.d(TAG, "Resetting Dolby DAP DSP to factory defaults")
        try {
            val profiles = listOf(0, 1, 2, 3, 4, 8)
            for (p in profiles) {
                val buf = ByteArray(12)
                intToByteArray(EFFECT_PARAM_RESET_PROFILE_SETTINGS, buf, 0)
                intToByteArray(1, buf, 4)
                intToByteArray(p, buf, 8)
                setAudioEffectParameter(dolbyEffect, EFFECT_PARAM_CPDP_VALUES, buf)
            }
            Log.d(TAG, "Dolby DSP reset successfully across all profiles to factory defaults")
        } catch (e: Exception) {
            Log.w(TAG, "Error resetting Dolby DSP", e)
        }

        // Reset enhancer states to factory defaults (all disabled, 50% baseline)
        prefs.edit()
            .putBoolean(KEY_BASS_ENHANCER_ENABLED, false)
            .putInt(KEY_BASS_ENHANCER_LEVEL, 50)
            .putBoolean(KEY_MID_ENHANCER_ENABLED, false)
            .putInt(KEY_MID_ENHANCER_LEVEL, 50)
            .putBoolean(KEY_TREBLE_ENHANCER_ENABLED, false)
            .putInt(KEY_TREBLE_ENHANCER_LEVEL, 50)
            .putBoolean(KEY_VOLUME_LEVELER_ENABLED, false)
            .putInt(KEY_VOLUME_LEVELER_LEVEL, 50)
            .putBoolean(KEY_DIALOGUE_ENHANCER_ENABLED, false)
            .putInt(KEY_DIALOGUE_ENHANCER_LEVEL, 50)
            .apply()

        _bassEnhancerEnabledState.value = false
        _bassEnhancerLevelState.value = 50
        _midEnhancerEnabledState.value = false
        _midEnhancerLevelState.value = 50
        _trebleEnhancerEnabledState.value = false
        _trebleEnhancerLevelState.value = 50
        _volumeLevelerEnabledState.value = false
        _volumeLevelerLevelState.value = 50
        _dialogueEnhancerEnabledState.value = false
        _dialogueEnhancerLevelState.value = 50

        resetEqualizer()
        applyDolbyProfile(getDolbyProfile())
        updateTuningDevice()
        saveCurrentStateToDevice()
    }

    fun applyEqualizerSettings() {
        val isEnabled = isEqualizerEnabled()
        val bands = getEqualizerBands()
        val profileId = getDolbyProfile().id

        val bassOn = isBassEnhancerEnabled()
        val midOn = isMidEnhancerEnabled()
        val trebleOn = isTrebleEnhancerEnabled()
        val dialogueOn = isDialogueEnhancerEnabled()
        val volumeOn = isVolumeLevelerEnabled()

        var computedBands = if (isEnabled) bands.toMutableList() else MutableList(10) { 0f }

        if (bassOn) {
            val boost = (getBassEnhancerLevel() / 100f) * 6.0f
            computedBands[0] = (computedBands[0] + boost).coerceIn(-12f, 12f)
            computedBands[1] = (computedBands[1] + boost * 0.8f).coerceIn(-12f, 12f)
            computedBands[2] = (computedBands[2] + boost * 0.4f).coerceIn(-12f, 12f)
        }
        if (midOn) {
            val boost = (getMidEnhancerLevel() / 100f) * 4.5f
            computedBands[3] = (computedBands[3] + boost * 0.3f).coerceIn(-12f, 12f)
            computedBands[4] = (computedBands[4] + boost).coerceIn(-12f, 12f)
            computedBands[5] = (computedBands[5] + boost).coerceIn(-12f, 12f)
            computedBands[6] = (computedBands[6] + boost * 0.4f).coerceIn(-12f, 12f)
        }
        if (trebleOn) {
            val boost = (getTrebleEnhancerLevel() / 100f) * 5.0f
            computedBands[7] = (computedBands[7] + boost * 0.5f).coerceIn(-12f, 12f)
            computedBands[8] = (computedBands[8] + boost * 0.85f).coerceIn(-12f, 12f)
            computedBands[9] = (computedBands[9] + boost).coerceIn(-12f, 12f)
        }
        if (dialogueOn) {
            val boost = (getDialogueEnhancerLevel() / 100f) * 4.0f
            computedBands[5] = (computedBands[5] + boost).coerceIn(-12f, 12f)
            computedBands[6] = (computedBands[6] + boost * 0.8f).coerceIn(-12f, 12f)
        }

        // 1. Dolby DAP Offload GEQ & Enhancers
        try {
            // Dialogue Enhancer parameters (105, 108) - STANDALONE: independent of isEnabled
            setDolbyProfileParam(profileId, DAP_PARAM_DIALOG_ENHANCER_ENABLE, intArrayOf(if (dialogueOn) 1 else 0))
            val dialogueAmount = (getDialogueEnhancerLevel() * 16 / 100).coerceIn(0, 16)
            setDolbyProfileParam(profileId, DAP_PARAM_DIALOG_ENHANCER_AMOUNT, intArrayOf(dialogueAmount))

            // Volume Leveler parameters (103, 116) - STANDALONE: independent of isEnabled
            setDolbyProfileParam(profileId, DAP_PARAM_VOLUME_LEVELER_ENABLE, intArrayOf(if (volumeOn) 1 else 0))
            val volumeAmount = (getVolumeLevelerLevel() * 10 / 100).coerceIn(0, 10)
            setDolbyProfileParam(profileId, DAP_PARAM_VOLUME_LEVELER_AMOUNT, intArrayOf(volumeAmount))

            // Bass Enhancer parameters (111, 112) - ONLY active if user explicitly toggled bassOn
            setDolbyProfileParam(profileId, DAP_PARAM_BASS_ENHANCER_ENABLE, intArrayOf(if (bassOn) 1 else 0))
            setDolbyProfileParam(profileId, DAP_PARAM_VIRTUAL_BASS_ENABLE, intArrayOf(if (bassOn) 1 else 0))

            val hasGeqAdjustment = isEnabled || bassOn || midOn || trebleOn || dialogueOn
            if (hasGeqAdjustment) {
                // Enable Graphic Equalizer and apply computed band gains
                setDolbyProfileParam(profileId, DAP_PARAM_GEQ_ENABLE, intArrayOf(1))
                val dapGains = serialize10To20DapGains(computedBands)
                setDolbyEqualizerGains(profileId, dapGains)
            } else {
                // Disable Graphic Equalizer to keep factory acoustic optimizer & regulator intact
                setDolbyProfileParam(profileId, DAP_PARAM_GEQ_ENABLE, intArrayOf(0))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error setting Dolby DAP GEQ & enhancers", e)
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
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing audio effects", e)
        }
    }
}
