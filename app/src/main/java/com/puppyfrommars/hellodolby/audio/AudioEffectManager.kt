/*
 * SPDX-FileCopyrightText: 2026 marcmyworld <moviesminister@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package com.puppyfrommars.hellodolby.audio

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioManager
import android.media.Spatializer
import android.media.audiofx.AudioEffect
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.os.Process
import android.provider.Settings
import android.service.quicksettings.TileService
import android.util.Log
import com.puppyfrommars.hellodolby.data.device.AudioDeviceSnapshot
import com.puppyfrommars.hellodolby.data.device.DeviceStateManager
import com.puppyfrommars.hellodolby.service.DolbyTileService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.lang.reflect.Method
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.math.roundToInt

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

enum class IeqPreset(val id: Int) {
    OFF(0),
    DETAILED(1),
    BALANCED(2),
    WARM(3);

    companion object {
        fun fromId(id: Int): IeqPreset = entries.firstOrNull { it.id == id } ?: OFF
    }
}

/**
 * Real SM8850 audio DSP controller bridging:
 * - Dolby DAP HAL (`vendor.dolby.dms.IDms/default` + `libhwdapaidl.so` + `libswgamedapaidl.so`)
 * - Xiaomi Sound HAL (`libmisoundfx_aosp_aidl_ext.so` with verified `t_misound_params` IDs)
 * - Direct DAC bypass (`EffectMode.OFF`), AOSP [Spatializer], [Equalizer] fallback, and [DeviceStateManager]
 */
class AudioEffectManager private constructor(context: Context) {
    companion object {
        private const val TAG = "HelloDolby"
        private const val PREFS_NAME = "hellodolby_prefs"

        private const val KEY_EFFECT_MODE = "effect_mode"
        private const val KEY_DOLBY_PROFILE = "dolby_profile"
        private const val KEY_SPATIAL_AUDIO_ENABLED = "spatial_audio_enabled"
        private const val KEY_SURROUND_STRENGTH = "surround_strength"
        private const val KEY_BASS_ENHANCER_ENABLED = "bass_enhancer_enabled"
        private const val KEY_BASS_ENHANCER_LEVEL = "bass_enhancer_level"
        private const val KEY_MID_ENHANCER_ENABLED = "mid_enhancer_enabled"
        private const val KEY_MID_ENHANCER_LEVEL = "mid_enhancer_level"
        private const val KEY_TREBLE_ENHANCER_ENABLED = "treble_enhancer_enabled"
        private const val KEY_TREBLE_ENHANCER_LEVEL = "treble_enhancer_level"
        private const val KEY_DIALOGUE_ENHANCER_ENABLED = "dialogue_enhancer_enabled"
        private const val KEY_DIALOGUE_ENHANCER_LEVEL = "dialogue_enhancer_level"
        private const val KEY_DIALOGUE_DUCKING = "dialogue_ducking"
        private const val KEY_VOLUME_LEVELER_ENABLED = "volume_leveler_enabled"
        private const val KEY_VOLUME_LEVELER_LEVEL = "volume_leveler_level"
        private const val KEY_VIRTUAL_BASS_ENABLED = "virtual_bass_enabled"
        private const val KEY_REVERB_SUPPRESSION_ENABLED = "reverb_suppression_enabled"
        private const val KEY_REVERB_SUPPRESSION_AMOUNT = "reverb_suppression_amount"
        private const val KEY_LOUDNESS_BOOST_ENABLED = "loudness_boost_enabled"
        private const val KEY_LOUDNESS_BOOST_DB = "loudness_boost_db"
        private const val KEY_SPEAKER_TUNING_DEVICE = "speaker_tuning_device"
        private const val KEY_IEQ_PRESET = "ieq_preset"
        private const val KEY_IEQ_AMOUNT = "ieq_amount"
        private const val KEY_EQUALIZER_ENABLED = "equalizer_enabled"
        private const val KEY_EQUALIZER_BANDS = "equalizer_bands"
        private const val KEY_EQUALIZER_PRESET = "equalizer_preset"
        private const val KEY_AUTOEQ_PROFILE_NAME = "autoeq_profile_name"
        private const val KEY_CUSTOM_PRESETS = "custom_presets_json"

        // Verified AOSP EFFECT_UUID_NULL (system/media/audio/include/system/audio_effects/effect_uuid.h)
        val EFFECT_TYPE_NULL: UUID = UUID.fromString("ec7178ec-e5e1-4432-a3f4-4657e6795210")

        // Verified SM8850 /vendor/etc/audio/sku_alor/audio_effects_config.xml UUIDs
        val UUID_DOLBY_DAP: UUID = UUID.fromString("9d4921da-8225-4f29-aefa-39537a04bcaa")
        val UUID_DOLBY_GAME: UUID = UUID.fromString("3783c334-d3a0-4d13-874f-0032e5fb80e2")
        val UUID_MISOUND_FX: UUID = UUID.fromString("5b8e36a5-144a-4c38-b1d7-0002a5d5c51b")

        // libhwdapaidl.so DapEffectContext::setParamImpl IDs
        private const val DAP_EFFECT_PARAM_CPDP = 5

        // Verified libmisoundfx_aosp_aidl_ext.so t_misound_params jump-table IDs
        private const val MISOUND_PARAM_EQ_LEVEL = 2
        private const val MISOUND_PARAM_3D_SURROUND = 20
        private const val MISOUND_PARAM_ENABLE = 25
        private const val MISOUND_PARAM_SUPER_PROFILE = 31

        val DEFAULT_EQUALIZER_BANDS = List(10) { 0f }

        val BUILTIN_PRESETS: Map<String, List<Float>> = mapOf(
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

        @Volatile
        private var instance: AudioEffectManager? = null

        private val setParameterMethod: Method? by lazy {
            try {
                AudioEffect::class.java.getMethod("setParameter", ByteArray::class.java, ByteArray::class.java).apply {
                    isAccessible = true
                }
            } catch (_: Exception) {
                try {
                    AudioEffect::class.java.getDeclaredMethod("setParameter", ByteArray::class.java, ByteArray::class.java).apply {
                        isAccessible = true
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Unable to resolve AudioEffect.setParameter", e)
                    null
                }
            }
        }

        private val spatializerSetEnabledMethod: Method? by lazy {
            try {
                Spatializer::class.java.getDeclaredMethod("setEnabled", Boolean::class.javaPrimitiveType).apply {
                    isAccessible = true
                }
            } catch (_: Exception) {
                null
            }
        }

        fun getInstance(context: Context): AudioEffectManager {
            return instance ?: synchronized(this) {
                instance ?: AudioEffectManager(context.applicationContext).also { instance = it }
            }
        }

        private fun normalizeBands(bands: List<Float>): List<Float> =
            List(10) { idx ->
                val raw = bands.getOrElse(idx) { 0f }.coerceIn(-10f, 10f)
                (raw * 10f).roundToInt() / 10f
            }

        private fun intLe(v: Int): ByteArray =
            ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array()
    }

    private val appContext = context.applicationContext
    private val storageContext = appContext.createDeviceProtectedStorageContext() ?: appContext
    private val prefs: SharedPreferences = storageContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val spatializer: Spatializer? = runCatching { audioManager.spatializer }.getOrNull()
    private val dmsClient = DmsBinderClient()
    private val halExecutor = Executors.newSingleThreadExecutor()

    private var dolbyEffect: AudioEffect? = null
    private var gameDapEffect: AudioEffect? = null
    private var miSoundEffect: AudioEffect? = null
    private var fallbackEqualizer: Equalizer? = null
    private var fallbackVirtualizer: Virtualizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null

    private val _aospSpatializerActiveState = MutableStateFlow(
        runCatching { spatializer?.isEnabled == true }.getOrDefault(false)
    )
    val aospSpatializerActiveState: StateFlow<Boolean> = _aospSpatializerActiveState.asStateFlow()

    private val _effectModeState = MutableStateFlow(loadEffectMode())
    val effectModeState: StateFlow<EffectMode> = _effectModeState.asStateFlow()

    private var currentDolbyProfile: DolbyProfile = loadDolbyProfile()
    private val _dolbyProfileState = MutableStateFlow(currentDolbyProfile)
    val dolbyProfileState: StateFlow<DolbyProfile> = _dolbyProfileState.asStateFlow()

    private val _spatialAudioState = MutableStateFlow(prefs.getBoolean(KEY_SPATIAL_AUDIO_ENABLED, false))
    val spatialAudioState: StateFlow<Boolean> = _spatialAudioState.asStateFlow()

    private var currentSurroundStrength: Int = prefs.getInt(KEY_SURROUND_STRENGTH, 500)
    private val _surroundStrengthState = MutableStateFlow(currentSurroundStrength)
    val surroundStrengthState: StateFlow<Int> = _surroundStrengthState.asStateFlow()

    private val _bassEnhancerEnabledState = MutableStateFlow(prefs.getBoolean(KEY_BASS_ENHANCER_ENABLED, false))
    val bassEnhancerEnabledState: StateFlow<Boolean> = _bassEnhancerEnabledState.asStateFlow()

    private val _bassEnhancerLevelState = MutableStateFlow(prefs.getInt(KEY_BASS_ENHANCER_LEVEL, 50))
    val bassEnhancerLevelState: StateFlow<Int> = _bassEnhancerLevelState.asStateFlow()

    private val _midEnhancerEnabledState = MutableStateFlow(prefs.getBoolean(KEY_MID_ENHANCER_ENABLED, false))
    val midEnhancerEnabledState: StateFlow<Boolean> = _midEnhancerEnabledState.asStateFlow()

    private val _midEnhancerLevelState = MutableStateFlow(prefs.getInt(KEY_MID_ENHANCER_LEVEL, 50))
    val midEnhancerLevelState: StateFlow<Int> = _midEnhancerLevelState.asStateFlow()

    private val _trebleEnhancerEnabledState = MutableStateFlow(prefs.getBoolean(KEY_TREBLE_ENHANCER_ENABLED, false))
    val trebleEnhancerEnabledState: StateFlow<Boolean> = _trebleEnhancerEnabledState.asStateFlow()

    private val _trebleEnhancerLevelState = MutableStateFlow(prefs.getInt(KEY_TREBLE_ENHANCER_LEVEL, 50))
    val trebleEnhancerLevelState: StateFlow<Int> = _trebleEnhancerLevelState.asStateFlow()

    private val _dialogueEnhancerEnabledState = MutableStateFlow(prefs.getBoolean(KEY_DIALOGUE_ENHANCER_ENABLED, false))
    val dialogueEnhancerEnabledState: StateFlow<Boolean> = _dialogueEnhancerEnabledState.asStateFlow()

    private val _dialogueEnhancerLevelState = MutableStateFlow(prefs.getInt(KEY_DIALOGUE_ENHANCER_LEVEL, 50))
    val dialogueEnhancerLevelState: StateFlow<Int> = _dialogueEnhancerLevelState.asStateFlow()

    private val _volumeLevelerEnabledState = MutableStateFlow(prefs.getBoolean(KEY_VOLUME_LEVELER_ENABLED, false))
    val volumeLevelerEnabledState: StateFlow<Boolean> = _volumeLevelerEnabledState.asStateFlow()

    private val _volumeLevelerLevelState = MutableStateFlow(prefs.getInt(KEY_VOLUME_LEVELER_LEVEL, 50))
    val volumeLevelerLevelState: StateFlow<Int> = _volumeLevelerLevelState.asStateFlow()

    private val _ieqPresetState = MutableStateFlow(IeqPreset.fromId(prefs.getInt(KEY_IEQ_PRESET, 0)))
    val ieqPresetState: StateFlow<IeqPreset> = _ieqPresetState.asStateFlow()

    private val _ieqAmountState = MutableStateFlow(prefs.getInt(KEY_IEQ_AMOUNT, 50))
    val ieqAmountState: StateFlow<Int> = _ieqAmountState.asStateFlow()

    private val _dialogueDuckingState = MutableStateFlow(prefs.getInt(KEY_DIALOGUE_DUCKING, 0))
    val dialogueDuckingState: StateFlow<Int> = _dialogueDuckingState.asStateFlow()

    private val _virtualBassEnabledState = MutableStateFlow(prefs.getBoolean(KEY_VIRTUAL_BASS_ENABLED, false))
    val virtualBassEnabledState: StateFlow<Boolean> = _virtualBassEnabledState.asStateFlow()

    private val _reverbSuppressionEnabledState = MutableStateFlow(prefs.getBoolean(KEY_REVERB_SUPPRESSION_ENABLED, false))
    val reverbSuppressionEnabledState: StateFlow<Boolean> = _reverbSuppressionEnabledState.asStateFlow()

    private val _reverbSuppressionAmountState = MutableStateFlow(prefs.getInt(KEY_REVERB_SUPPRESSION_AMOUNT, 50))
    val reverbSuppressionAmountState: StateFlow<Int> = _reverbSuppressionAmountState.asStateFlow()

    private val _loudnessBoostEnabledState = MutableStateFlow(prefs.getBoolean(KEY_LOUDNESS_BOOST_ENABLED, false))
    val loudnessBoostEnabledState: StateFlow<Boolean> = _loudnessBoostEnabledState.asStateFlow()

    private val _loudnessBoostDbState = MutableStateFlow(prefs.getInt(KEY_LOUDNESS_BOOST_DB, 3))
    val loudnessBoostDbState: StateFlow<Int> = _loudnessBoostDbState.asStateFlow()

    private val _speakerTuningDeviceState = MutableStateFlow(
        prefs.getString(KEY_SPEAKER_TUNING_DEVICE, "default_internal_speaker") ?: "default_internal_speaker"
    )
    val speakerTuningDeviceState: StateFlow<String> = _speakerTuningDeviceState.asStateFlow()

    private val _availableSpeakerTuningsState = MutableStateFlow(
        listOf(
            "default_internal_speaker",
            "small_mode_speaker",
            "medium_mode_speaker",
            "large_mode_speaker",
            "speaker_portrait",
            "speaker_landscape"
        )
    )
    val availableSpeakerTuningsState: StateFlow<List<String>> = _availableSpeakerTuningsState.asStateFlow()

    private val _equalizerEnabledState = MutableStateFlow(prefs.getBoolean(KEY_EQUALIZER_ENABLED, false))
    val equalizerEnabledState: StateFlow<Boolean> = _equalizerEnabledState.asStateFlow()

    private val _equalizerBandsState = MutableStateFlow(loadEqualizerBands())
    val equalizerBandsState: StateFlow<List<Float>> = _equalizerBandsState.asStateFlow()

    private val _equalizerPresetState = MutableStateFlow(prefs.getString(KEY_EQUALIZER_PRESET, "Flat") ?: "Flat")
    val equalizerPresetState: StateFlow<String> = _equalizerPresetState.asStateFlow()

    private val _autoEqProfileNameState = MutableStateFlow(prefs.getString(KEY_AUTOEQ_PROFILE_NAME, null))
    val autoEqProfileNameState: StateFlow<String?> = _autoEqProfileNameState.asStateFlow()

    private val _customPresetsState = MutableStateFlow(loadCustomPresets())
    val customPresetsState: StateFlow<Map<String, List<Float>>> = _customPresetsState.asStateFlow()

    private val deviceStateManager = DeviceStateManager(
        context = appContext,
        defaultSnapshotProvider = { key, name -> buildSnapshot(key, name) },
        onRouteChanged = { snapshot -> onDeviceRouteChanged(snapshot) }
    )

    val connectedDeviceState: StateFlow<String> = deviceStateManager.currentDeviceFlow
    val activeDeviceKeyState: StateFlow<String> = deviceStateManager.activeDeviceKeyFlow
    val savedDeviceSnapshotsState: StateFlow<List<AudioDeviceSnapshot>> = deviceStateManager.savedSnapshotsFlow

    private val spatializerListener = object : Spatializer.OnSpatializerStateChangedListener {
        override fun onSpatializerEnabledChanged(spat: Spatializer, enabled: Boolean) {
            Log.d(TAG, "System Spatializer enabled changed: enabled=$enabled, available=${spat.isAvailable}")
            _aospSpatializerActiveState.value = enabled
        }

        override fun onSpatializerAvailableChanged(spat: Spatializer, available: Boolean) {
            Log.d(TAG, "System Spatializer available changed: available=$available")
            _aospSpatializerActiveState.value = runCatching { spat.isEnabled }.getOrDefault(false)
        }
    }

    fun refreshAospSpatializerState() {
        _aospSpatializerActiveState.value = runCatching { spatializer?.isEnabled == true }.getOrDefault(false)
    }

    init {
        val activeSnapshot = deviceStateManager.loadActiveSnapshot()
        applySnapshotToState(activeSnapshot)

        runCatching {
            spatializer?.addOnSpatializerStateChangedListener(appContext.mainExecutor, spatializerListener)
        }.onFailure { Log.w(TAG, "Failed registering Spatializer listener", it) }

        halExecutor.execute {
            initHalEffects()
            applyAllDspState()
            Log.i(TAG, verifyHalStatus())
        }
    }

    private fun loadEffectMode(): EffectMode =
        EffectMode.fromId(prefs.getString(KEY_EFFECT_MODE, EffectMode.DOLBY.id))

    private fun loadDolbyProfile(): DolbyProfile =
        DolbyProfile.fromId(prefs.getInt(KEY_DOLBY_PROFILE, DolbyProfile.DYNAMIC.id))

    private fun loadEqualizerBands(): List<Float> {
        val raw = prefs.getString(KEY_EQUALIZER_BANDS, null) ?: return DEFAULT_EQUALIZER_BANDS
        val parsed = raw.split(',').mapNotNull { it.toFloatOrNull() }
        return if (parsed.size == 10) normalizeBands(parsed) else DEFAULT_EQUALIZER_BANDS
    }

    private fun loadCustomPresets(): Map<String, List<Float>> {
        val jsonStr = prefs.getString(KEY_CUSTOM_PRESETS, null) ?: return emptyMap()
        return try {
            val json = JSONObject(jsonStr)
            val result = mutableMapOf<String, List<Float>>()
            for (key in json.keys()) {
                val arr = json.getJSONArray(key)
                if (arr.length() == 10) {
                    result[key] = normalizeBands(List(10) { idx -> arr.getDouble(idx).toFloat() })
                }
            }
            result
        } catch (e: Exception) {
            Log.w(TAG, "Failed parsing custom presets", e)
            emptyMap()
        }
    }

    private fun persistCustomPresets(presets: Map<String, List<Float>>) {
        val json = JSONObject()
        for ((k, bands) in presets) {
            val arr = JSONArray()
            bands.forEach { arr.put(it.toDouble()) }
            json.put(k, arr)
        }
        prefs.edit().putString(KEY_CUSTOM_PRESETS, json.toString()).apply()
    }

    private fun buildSnapshot(
        key: String = deviceStateManager.getActiveDeviceKey(),
        name: String = deviceStateManager.getActiveDeviceName()
    ): AudioDeviceSnapshot = AudioDeviceSnapshot(
        deviceKey = key,
        displayName = name,
        effectMode = _effectModeState.value.id,
        dolbyProfile = currentDolbyProfile.id,
        surroundEnabled = _spatialAudioState.value,
        surroundStrength = currentSurroundStrength,
        equalizerEnabled = _equalizerEnabledState.value,
        equalizerBands = _equalizerBandsState.value,
        equalizerPreset = _equalizerPresetState.value,
        autoEqProfileName = _autoEqProfileNameState.value,
        bassEnhancerEnabled = _bassEnhancerEnabledState.value,
        bassEnhancerLevel = _bassEnhancerLevelState.value,
        midEnhancerEnabled = _midEnhancerEnabledState.value,
        midEnhancerLevel = _midEnhancerLevelState.value,
        trebleEnhancerEnabled = _trebleEnhancerEnabledState.value,
        trebleEnhancerLevel = _trebleEnhancerLevelState.value,
        volumeLevelerEnabled = _volumeLevelerEnabledState.value,
        volumeLevelerLevel = _volumeLevelerLevelState.value,
        dialogueEnhancerEnabled = _dialogueEnhancerEnabledState.value,
        dialogueEnhancerLevel = _dialogueEnhancerLevelState.value,
        ieqPreset = _ieqPresetState.value.id,
        ieqAmount = _ieqAmountState.value,
        dialogueDucking = _dialogueDuckingState.value,
        virtualBassEnabled = _virtualBassEnabledState.value,
        reverbSuppressionEnabled = _reverbSuppressionEnabledState.value,
        reverbSuppressionAmount = _reverbSuppressionAmountState.value,
        loudnessBoostEnabled = _loudnessBoostEnabledState.value,
        loudnessBoostDb = _loudnessBoostDbState.value,
        speakerTuningDevice = _speakerTuningDeviceState.value
    )

    private fun saveCurrentDeviceSnapshot() {
        deviceStateManager.saveSnapshot(buildSnapshot())
    }

    private fun applySnapshotToState(snapshot: AudioDeviceSnapshot) {
        val mode = EffectMode.fromId(snapshot.effectMode)
        val profile = DolbyProfile.fromId(snapshot.dolbyProfile)
        _effectModeState.value = mode
        currentDolbyProfile = profile
        _dolbyProfileState.value = profile
        _spatialAudioState.value = snapshot.surroundEnabled
        currentSurroundStrength = snapshot.surroundStrength
        _surroundStrengthState.value = snapshot.surroundStrength
        _equalizerEnabledState.value = snapshot.equalizerEnabled
        _equalizerBandsState.value = normalizeBands(snapshot.equalizerBands)
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
        val ieqPreset = if (snapshot.equalizerEnabled) IeqPreset.OFF else IeqPreset.fromId(snapshot.ieqPreset)
        _ieqPresetState.value = ieqPreset
        _ieqAmountState.value = snapshot.ieqAmount
        _dialogueDuckingState.value = snapshot.dialogueDucking
        _virtualBassEnabledState.value = snapshot.virtualBassEnabled
        _reverbSuppressionEnabledState.value = snapshot.reverbSuppressionEnabled
        _reverbSuppressionAmountState.value = snapshot.reverbSuppressionAmount
        _loudnessBoostEnabledState.value = snapshot.loudnessBoostEnabled
        _loudnessBoostDbState.value = snapshot.loudnessBoostDb
        _speakerTuningDeviceState.value = snapshot.speakerTuningDevice

        prefs.edit()
            .putString(KEY_EFFECT_MODE, mode.id)
            .putInt(KEY_DOLBY_PROFILE, profile.id)
            .putBoolean(KEY_SPATIAL_AUDIO_ENABLED, snapshot.surroundEnabled)
            .putInt(KEY_SURROUND_STRENGTH, snapshot.surroundStrength)
            .putBoolean(KEY_EQUALIZER_ENABLED, snapshot.equalizerEnabled)
            .putString(KEY_EQUALIZER_BANDS, _equalizerBandsState.value.joinToString(","))
            .putString(KEY_EQUALIZER_PRESET, snapshot.equalizerPreset)
            .apply {
                if (snapshot.autoEqProfileName != null) {
                    putString(KEY_AUTOEQ_PROFILE_NAME, snapshot.autoEqProfileName)
                } else {
                    remove(KEY_AUTOEQ_PROFILE_NAME)
                }
            }
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
            .putInt(KEY_IEQ_PRESET, ieqPreset.id)
            .putInt(KEY_IEQ_AMOUNT, snapshot.ieqAmount)
            .putInt(KEY_DIALOGUE_DUCKING, snapshot.dialogueDucking)
            .putBoolean(KEY_VIRTUAL_BASS_ENABLED, snapshot.virtualBassEnabled)
            .putBoolean(KEY_REVERB_SUPPRESSION_ENABLED, snapshot.reverbSuppressionEnabled)
            .putInt(KEY_REVERB_SUPPRESSION_AMOUNT, snapshot.reverbSuppressionAmount)
            .putBoolean(KEY_LOUDNESS_BOOST_ENABLED, snapshot.loudnessBoostEnabled)
            .putInt(KEY_LOUDNESS_BOOST_DB, snapshot.loudnessBoostDb)
            .putString(KEY_SPEAKER_TUNING_DEVICE, snapshot.speakerTuningDevice)
            .apply()
    }

    private fun onDeviceRouteChanged(snapshot: AudioDeviceSnapshot) {
        applySnapshotToState(snapshot)
        requestTileRefresh()
        halExecutor.execute { applyAllDspState() }
    }

    private fun createAudioEffect(uuid: UUID, priority: Int = 0): AudioEffect? {
        return try {
            val ctor = AudioEffect::class.java.getDeclaredConstructor(
                UUID::class.java,
                UUID::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            ctor.isAccessible = true
            ctor.newInstance(EFFECT_TYPE_NULL, uuid, priority, 0) as AudioEffect
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create AudioEffect($uuid)", e)
            null
        }
    }

    private fun setEffectParam(effect: AudioEffect?, paramId: Int, value: ByteArray): Int {
        if (effect == null) return -1
        val method = setParameterMethod ?: return -1
        return try {
            val rc = method.invoke(effect, intLe(paramId), value) as? Int ?: -1
            if (rc != AudioEffect.SUCCESS) {
                Log.w(TAG, "AudioEffect.setParameter($paramId, ${value.size}B) returned $rc")
            }
            rc
        } catch (e: Exception) {
            Log.w(TAG, "AudioEffect.setParameter($paramId) failed", e)
            -1
        }
    }

    private fun ensureDolbyEffect(): AudioEffect? {
        if (dolbyEffect == null) {
            dolbyEffect = createAudioEffect(UUID_DOLBY_DAP, priority = 0)
        }
        return dolbyEffect
    }

    private fun ensureGameDapEffect(): AudioEffect? {
        if (gameDapEffect == null) {
            gameDapEffect = createAudioEffect(UUID_DOLBY_GAME, priority = 0)
        }
        return gameDapEffect
    }

    private fun ensureMiSoundEffect(): AudioEffect? {
        if (miSoundEffect == null) {
            miSoundEffect = createAudioEffect(UUID_MISOUND_FX, priority = 0)
        }
        return miSoundEffect
    }

    private fun ensureFallbackEqualizer(): Equalizer? {
        if (fallbackEqualizer == null) {
            fallbackEqualizer = runCatching { Equalizer(0, 0) }
                .onFailure { Log.d(TAG, "Global AOSP Equalizer unavailable: ${it.message}") }
                .getOrNull()
        }
        return fallbackEqualizer
    }

    private fun ensureFallbackVirtualizer(): Virtualizer? {
        if (fallbackVirtualizer == null) {
            fallbackVirtualizer = runCatching { Virtualizer(0, 0) }
                .onFailure { Log.d(TAG, "Global AOSP Virtualizer unavailable: ${it.message}") }
                .getOrNull()
        }
        return fallbackVirtualizer
    }

    private fun ensureLoudnessEnhancer(): LoudnessEnhancer? {
        if (loudnessEnhancer == null) {
            loudnessEnhancer = runCatching { LoudnessEnhancer(0) }
                .onFailure { Log.d(TAG, "Global LoudnessEnhancer unavailable: ${it.message}") }
                .getOrNull()
        }
        return loudnessEnhancer
    }

    private fun initHalEffects() {
        ensureDolbyEffect()
        ensureMiSoundEffect()
        ensureFallbackEqualizer()
        ensureFallbackVirtualizer()
        ensureLoudnessEnhancer()
        val queried = dmsClient.getAvailableTuningDevices(0)
        if (queried.isNotEmpty()) {
            _availableSpeakerTuningsState.value = queried
        }
    }

    private fun resolveDmsActiveDeviceMask(): Int {
        val key = deviceStateManager.getActiveDeviceKey()
        return when {
            key == "usb_audio" -> 0x04000000 // AUDIO_DEVICE_OUT_USB_HEADSET
            key == "wired_headphones" -> 0x8 // AUDIO_DEVICE_OUT_WIRED_HEADPHONE
            key.startsWith("bt_") -> 0x80 // AUDIO_DEVICE_OUT_BLUETOOTH_A2DP
            else -> 0x2 // AUDIO_DEVICE_OUT_SPEAKER
        }
    }

    private fun sendDolbyDapPacket(packet: ByteArray): Boolean {
        if (dmsClient.sendCommand(DmsBinderClient.CMD_SDAP, packet)) {
            return true
        }
        return setEffectParam(ensureDolbyEffect(), DAP_EFFECT_PARAM_CPDP, packet) == AudioEffect.SUCCESS
    }

    private fun setDolbyProfileParam(profileId: Int, paramId: Int, vararg values: Int) {
        sendDolbyDapPacket(DmsBinderClient.buildProfileParamPacket(profileId, paramId, values))
    }

    private fun setDolbyPortParam(profileId: Int, port: Int, paramId: Int, vararg values: Int) {
        sendDolbyDapPacket(DmsBinderClient.buildProfilePortParamPacket(profileId, port, paramId, values))
    }

    private fun setMiSoundIntParam(paramId: Int, value: Int) {
        setEffectParam(ensureMiSoundEffect(), paramId, intLe(value))
    }

    private fun setMiSoundEqBand(bandIndex: Int, gainDb: Float) {
        val gainBytes = String.format(Locale.US, "%.2f", gainDb).toByteArray(Charsets.UTF_8)
        val payload = ByteArray(4 + gainBytes.size)
        val hdr = intLe(bandIndex)
        System.arraycopy(hdr, 0, payload, 0, 4)
        System.arraycopy(gainBytes, 0, payload, 4, gainBytes.size)
        setEffectParam(ensureMiSoundEffect(), MISOUND_PARAM_EQ_LEVEL, payload)
    }

    private fun syncSystemAudioFlags(mode: EffectMode) {
        val halVal = mode.id
        val isNone = (mode == EffectMode.OFF)
        val misoundDisable = (mode != EffectMode.MISOUND)

        runCatching {
            Settings.Global.putString(appContext.contentResolver, "effect_implementer", halVal)
            Settings.Global.putInt(appContext.contentResolver, "last_select_effect_none", if (isNone) 1 else 0)
        }.onFailure {
            Log.d(TAG, "Settings.Global write skipped (${it.javaClass.simpleName})")
        }

        runCatching {
            audioManager.setParameters("effectimplenter=$halVal")
            audioManager.setParameters("misound_disable=$misoundDisable")
        }.onFailure {
            Log.w(TAG, "AudioManager.setParameters failed", it)
        }

        runCatching {
            appContext.sendBroadcast(
                Intent("miui.intent.action.ACTION_AUDIO_EFFECT_CHANGED").apply {
                    putExtra("effect_mode", halVal)
                }
            )
        }
    }

    private fun applyAllDspState() {
        applyEffectModeInternal(_effectModeState.value)
        if (_effectModeState.value == EffectMode.DOLBY) {
            dmsClient.resetProfile(currentDolbyProfile.id)
        }
        applySpatialAudioInternal(_spatialAudioState.value, currentSurroundStrength)
        applyEqAndEnhancersInternal()
    }

    private fun applyEffectModeInternal(mode: EffectMode) {
        dmsClient.setActiveDevice(resolveDmsActiveDeviceMask())
        when (mode) {
            EffectMode.DOLBY -> {
                // Disable MiSound natively via HAL (sets persist.vendor.audio.misound.disable=true inside hal_audio_default)
                setMiSoundIntParam(MISOUND_PARAM_3D_SURROUND, 0)
                setMiSoundIntParam(MISOUND_PARAM_ENABLE, 0)
                runCatching { miSoundEffect?.enabled = false }

                runCatching { ensureDolbyEffect()?.enabled = true }
                    .onFailure { Log.w(TAG, "Failed enabling dap_hw", it) }
                sendDolbyDapPacket(DmsBinderClient.buildSetDsOnPacket(true))
                if (!deviceStateManager.isHeadphonesActive()) {
                    dmsClient.setSelectedTuningDevice(0, _speakerTuningDeviceState.value)
                }
                applyDolbyProfileInternal(currentDolbyProfile)
            }
            EffectMode.MISOUND -> {
                sendDolbyDapPacket(DmsBinderClient.buildSetDsOnPacket(false))
                runCatching { dolbyEffect?.enabled = false }
                runCatching { gameDapEffect?.enabled = false }

                runCatching { ensureMiSoundEffect()?.enabled = true }
                    .onFailure { Log.w(TAG, "Failed enabling misoundfx", it) }
                // Verified t_misound_params=25 updates persist.vendor.audio.misound.disable=false & MiSound QDSP state
                setMiSoundIntParam(MISOUND_PARAM_ENABLE, 1)
                setMiSoundIntParam(MISOUND_PARAM_SUPER_PROFILE, 0)
            }
            EffectMode.OFF -> {
                // Direct DAC: cleanly bypass Dolby, MiSound, AOSP EQ, and Virtualizer
                sendDolbyDapPacket(DmsBinderClient.buildSetDsOnPacket(false))
                runCatching { dolbyEffect?.enabled = false }
                runCatching { gameDapEffect?.enabled = false }

                setMiSoundIntParam(MISOUND_PARAM_3D_SURROUND, 0)
                setMiSoundIntParam(MISOUND_PARAM_ENABLE, 0)
                runCatching { miSoundEffect?.enabled = false }
            }
        }
        syncSystemAudioFlags(mode)
    }

    private fun applyDolbyProfileInternal(profile: DolbyProfile) {
        sendDolbyDapPacket(DmsBinderClient.buildSetProfilePacket(profile.id))
        // Keep DAP_game disabled on global session 0: on speaker deep-buffer/offload it can conflict with DAP_offload
        runCatching { gameDapEffect?.enabled = false }
    }

    private fun applySpatialAudioInternal(enabled: Boolean, strength: Int) {
        val mode = _effectModeState.value
        val headphones = deviceStateManager.isHeadphonesActive()
        // Immersive Audio toggle is Dolby-exclusive (disabled for both Direct DAC and Xiaomi Sound)
        val effectiveOn = enabled && mode == EffectMode.DOLBY

        when (mode) {
            EffectMode.DOLBY -> {
                val pId = currentDolbyProfile.id
                if (effectiveOn) {
                    if (headphones) {
                        setDolbyProfileParam(pId, DmsBinderClient.DAP_PARAM_HEADPHONE_VIRTUALIZER, 1)
                        setDolbyProfileParam(pId, DmsBinderClient.DAP_PARAM_SPEAKER_VIRTUALIZER, 0)
                        val widening = (4 + (strength * 156) / 1000).coerceIn(4, 160)
                        setDolbyProfileParam(pId, DmsBinderClient.DAP_PARAM_STEREO_WIDENING, widening)
                    } else {
                        setDolbyProfileParam(pId, DmsBinderClient.DAP_PARAM_SPEAKER_VIRTUALIZER, 1)
                        setDolbyProfileParam(pId, DmsBinderClient.DAP_PARAM_HEADPHONE_VIRTUALIZER, 0)
                    }
                } else {
                    setDolbyProfileParam(pId, DmsBinderClient.DAP_PARAM_HEADPHONE_VIRTUALIZER, 0)
                    setDolbyProfileParam(pId, DmsBinderClient.DAP_PARAM_SPEAKER_VIRTUALIZER, 0)
                }
                setMiSoundIntParam(MISOUND_PARAM_3D_SURROUND, 0)
            }
            EffectMode.MISOUND, EffectMode.OFF -> {
                setMiSoundIntParam(MISOUND_PARAM_3D_SURROUND, 0)
            }
        }

        // Drive AOSP Virtualizer companion only for headphone PCM outputs in Dolby mode (speaker offload rejects offload tags)
        ensureFallbackVirtualizer()?.let { virt ->
            val enableAospVirt = effectiveOn && headphones
            runCatching {
                virt.enabled = enableAospVirt
                if (enableAospVirt && virt.strengthSupported) {
                    virt.setStrength(strength.coerceIn(0, 1000).toShort())
                }
            }.onFailure {
                Log.d(TAG, "Global AOSP Virtualizer update failed: ${it.message}")
            }
        }
    }

    private fun computeEffectiveBands(): MutableList<Float> {
        val base = if (_equalizerEnabledState.value) {
            _equalizerBandsState.value.toMutableList()
        } else {
            MutableList(10) { 0f }
        }
        if (_bassEnhancerEnabledState.value) {
            val boost = (_bassEnhancerLevelState.value / 100f) * 6.0f
            base[0] = (base[0] + boost).coerceIn(-12f, 12f)
            base[1] = (base[1] + boost * 0.8f).coerceIn(-12f, 12f)
            base[2] = (base[2] + boost * 0.4f).coerceIn(-12f, 12f)
        }
        if (_midEnhancerEnabledState.value) {
            val boost = (_midEnhancerLevelState.value / 100f) * 5.5f
            base[3] = (base[3] + boost * 0.45f).coerceIn(-12f, 12f)
            base[4] = (base[4] + boost * 0.85f).coerceIn(-12f, 12f)
            base[5] = (base[5] + boost).coerceIn(-12f, 12f)
            base[6] = (base[6] + boost * 0.65f).coerceIn(-12f, 12f)
        }
        if (_trebleEnhancerEnabledState.value) {
            val boost = (_trebleEnhancerLevelState.value / 100f) * 6.0f
            base[7] = (base[7] + boost * 0.55f).coerceIn(-12f, 12f)
            base[8] = (base[8] + boost * 0.85f).coerceIn(-12f, 12f)
            base[9] = (base[9] + boost).coerceIn(-12f, 12f)
        }
        if (_loudnessBoostEnabledState.value && _effectModeState.value != EffectMode.OFF) {
            val loudnessDb = _loudnessBoostDbState.value.coerceIn(0, 10).toFloat()
            if (loudnessDb > 0f) {
                for (i in base.indices) {
                    base[i] = (base[i] + loudnessDb).coerceIn(-12f, 18f)
                }
            }
        }
        return base
    }

    private fun interpolate10To20Q4Gains(bands10: List<Float>): IntArray {
        val result = IntArray(20)
        // Verified libdlbdsservice.so ProfileGeqParameterStrategy range: [-576, 576] (Q4 fixed-point: 1 dB = 16)
        for (i in 0 until 10) {
            result[i * 2] = (bands10[i] * 16f).roundToInt().coerceIn(-576, 576)
        }
        for (i in 0 until 18 step 2) {
            result[i + 1] = (result[i] + result[i + 2]) / 2
        }
        result[19] = result[18]
        return result
    }

    private fun applyEqAndEnhancersInternal() {
        val mode = _effectModeState.value
        val eqOn = _equalizerEnabledState.value
        val bassOn = _bassEnhancerEnabledState.value
        val midOn = _midEnhancerEnabledState.value
        val trebleOn = _trebleEnhancerEnabledState.value
        val dialogueOn = _dialogueEnhancerEnabledState.value
        val volumeOn = _volumeLevelerEnabledState.value
        val loudnessOn = _loudnessBoostEnabledState.value && _loudnessBoostDbState.value > 0
        val anyBandBoostOn = eqOn || bassOn || midOn || trebleOn || loudnessOn
        val computedBands = computeEffectiveBands()

        if (mode == EffectMode.DOLBY) {
            val pId = currentDolbyProfile.id

            // Apply only active user overrides so Dolby presets preserve their XML tuning when toggles are off
            if (dialogueOn) {
                setDolbyProfileParam(pId, DmsBinderClient.DAP_PARAM_DIALOG_ENHANCER_ENABLE, 1)
                val dialogueAmount = (_dialogueEnhancerLevelState.value * 16 / 100).coerceIn(0, 16)
                setDolbyProfileParam(pId, DmsBinderClient.DAP_PARAM_DIALOG_ENHANCER_AMOUNT, dialogueAmount)
            }
            val duckingAmount = (_dialogueDuckingState.value * 16 / 100).coerceIn(0, 16)
            setDolbyProfileParam(pId, DmsBinderClient.DAP_PARAM_DIALOG_ENHANCER_DUCKING, duckingAmount)

            if (volumeOn) {
                setDolbyProfileParam(pId, DmsBinderClient.DAP_PARAM_VOLUME_LEVELER_ENABLE, 1)
                val volumeAmount = (_volumeLevelerLevelState.value * 10 / 100).coerceIn(0, 10)
                setDolbyProfileParam(pId, DmsBinderClient.DAP_PARAM_VOLUME_LEVELER_AMOUNT, volumeAmount)
            }

            if (bassOn) {
                setDolbyProfileParam(pId, DmsBinderClient.DAP_PARAM_BASS_ENHANCER_ENABLE, 1)
            }

            setDolbyProfileParam(
                pId,
                DmsBinderClient.DAP_PARAM_VIRTUAL_BASS_ENABLE,
                if (_virtualBassEnabledState.value) 1 else 0
            )

            val reverbOn = _reverbSuppressionEnabledState.value
            setDolbyProfileParam(pId, DmsBinderClient.DAP_PARAM_REVERB_SUPPRESSION_ENABLE, if (reverbOn) 1 else 0)
            if (reverbOn) {
                val reverbAmount = (_reverbSuppressionAmountState.value * 9 / 100).coerceIn(0, 9)
                setDolbyProfileParam(pId, DmsBinderClient.DAP_PARAM_REVERB_SUPPRESSION_AMOUNT, reverbAmount)
            }

            // Intelligent Equalizer and Custom 10-band Equalizer are mutually exclusive
            val activeIeq = if (eqOn) IeqPreset.OFF else _ieqPresetState.value
            setDolbyProfileParam(pId, DmsBinderClient.DAP_PARAM_IEQ_ID, activeIeq.id)
            if (activeIeq != IeqPreset.OFF) {
                val ieqAmt = (_ieqAmountState.value * 16 / 100).coerceIn(0, 16)
                setDolbyProfileParam(pId, DmsBinderClient.DAP_PARAM_IEQ_AMOUNT, ieqAmt)
            }

            if (anyBandBoostOn) {
                setDolbyProfileParam(pId, DmsBinderClient.DAP_PARAM_GRAPHIC_EQUALIZER_ENABLE, 1)
                val gains20 = interpolate10To20Q4Gains(computedBands)
                setDolbyProfileParam(pId, DmsBinderClient.DAP_PARAM_GEQ_BAND_GAINS, *gains20)
            } else {
                setDolbyProfileParam(pId, DmsBinderClient.DAP_PARAM_GRAPHIC_EQUALIZER_ENABLE, 0)
            }
        }

        if (mode == EffectMode.MISOUND) {
            val bandsToSend = if (anyBandBoostOn) computedBands else List(10) { 0f }
            for (bandIdx in 0 until 10) {
                setMiSoundEqBand(bandIdx, bandsToSend[bandIdx])
            }
            ensureFallbackEqualizer()?.let { eq ->
                runCatching {
                    eq.enabled = anyBandBoostOn
                    if (eq.enabled) {
                        val n = eq.numberOfBands.toInt()
                        for (i in 0 until minOf(bandsToSend.size, n)) {
                            val mb = (bandsToSend[i] * 100f).roundToInt().coerceIn(-1500, 1500).toShort()
                            eq.setBandLevel(i.toShort(), mb)
                        }
                    }
                }
            }
        } else {
            runCatching { fallbackEqualizer?.enabled = false }
        }

        ensureLoudnessEnhancer()?.let { loudness ->
            val enableLoudness = _loudnessBoostEnabledState.value && mode != EffectMode.OFF
            runCatching {
                loudness.enabled = enableLoudness
                if (enableLoudness) {
                    // In Dolby mode QDSP GEQ (gebs) provides clean broadband gain protected by AudioRegulator;
                    // avoid stacking AOSP ADRC pumping on top of hardware QDSP processing.
                    val aospTargetMb = if (mode == EffectMode.DOLBY) 0 else _loudnessBoostDbState.value.coerceIn(0, 10) * 100
                    loudness.setTargetGain(aospTargetMb)
                }
            }.onFailure {
                Log.d(TAG, "LoudnessEnhancer update failed: ${it.message}")
            }
        }
    }

    private fun requestTileRefresh() {
        runCatching {
            TileService.requestListeningState(appContext, ComponentName(appContext, DolbyTileService::class.java))
        }
    }

    fun getEffectMode(): EffectMode = _effectModeState.value

    fun setEffectMode(mode: EffectMode) {
        _effectModeState.value = mode
        prefs.edit().putString(KEY_EFFECT_MODE, mode.id).apply()
        saveCurrentDeviceSnapshot()
        requestTileRefresh()
        halExecutor.execute { applyAllDspState() }
    }

    private fun reapplyDolbyWithCleanPreset() {
        if (_effectModeState.value == EffectMode.DOLBY) {
            dmsClient.resetProfile(currentDolbyProfile.id)
            applyDolbyProfileInternal(currentDolbyProfile)
        }
        applySpatialAudioInternal(_spatialAudioState.value, currentSurroundStrength)
        applyEqAndEnhancersInternal()
    }

    fun getDolbyProfile(): DolbyProfile = currentDolbyProfile

    fun setDolbyProfile(profile: DolbyProfile) {
        currentDolbyProfile = profile
        _dolbyProfileState.value = profile
        prefs.edit().putInt(KEY_DOLBY_PROFILE, profile.id).apply()
        saveCurrentDeviceSnapshot()
        requestTileRefresh()
        if (_effectModeState.value == EffectMode.DOLBY) {
            halExecutor.execute { reapplyDolbyWithCleanPreset() }
        }
    }

    fun isSpatialAudioEnabled(): Boolean = _spatialAudioState.value

    fun setSpatialAudioEnabled(enabled: Boolean) {
        _spatialAudioState.value = enabled
        prefs.edit().putBoolean(KEY_SPATIAL_AUDIO_ENABLED, enabled).apply()
        saveCurrentDeviceSnapshot()
        halExecutor.execute {
            if (!enabled) {
                reapplyDolbyWithCleanPreset()
            } else {
                applySpatialAudioInternal(true, currentSurroundStrength)
            }
        }
    }

    fun getSurroundStrength(): Int = currentSurroundStrength

    fun setSurroundStrength(strength: Int) {
        val clamped = strength.coerceIn(0, 1000)
        currentSurroundStrength = clamped
        _surroundStrengthState.value = clamped
        prefs.edit().putInt(KEY_SURROUND_STRENGTH, clamped).apply()
        saveCurrentDeviceSnapshot()
        if (_spatialAudioState.value) {
            halExecutor.execute { applySpatialAudioInternal(true, clamped) }
        }
    }

    fun setBassEnhancerEnabled(enabled: Boolean) {
        _bassEnhancerEnabledState.value = enabled
        prefs.edit().putBoolean(KEY_BASS_ENHANCER_ENABLED, enabled).apply()
        saveCurrentDeviceSnapshot()
        halExecutor.execute {
            if (!enabled) reapplyDolbyWithCleanPreset() else applyEqAndEnhancersInternal()
        }
    }

    fun setBassEnhancerLevel(level: Int) {
        val clamped = level.coerceIn(0, 100)
        _bassEnhancerLevelState.value = clamped
        prefs.edit().putInt(KEY_BASS_ENHANCER_LEVEL, clamped).apply()
        saveCurrentDeviceSnapshot()
        if (_bassEnhancerEnabledState.value) {
            halExecutor.execute { applyEqAndEnhancersInternal() }
        }
    }

    fun setMidEnhancerEnabled(enabled: Boolean) {
        _midEnhancerEnabledState.value = enabled
        prefs.edit().putBoolean(KEY_MID_ENHANCER_ENABLED, enabled).apply()
        saveCurrentDeviceSnapshot()
        halExecutor.execute {
            if (!enabled) reapplyDolbyWithCleanPreset() else applyEqAndEnhancersInternal()
        }
    }

    fun setMidEnhancerLevel(level: Int) {
        val clamped = level.coerceIn(0, 100)
        _midEnhancerLevelState.value = clamped
        prefs.edit().putInt(KEY_MID_ENHANCER_LEVEL, clamped).apply()
        saveCurrentDeviceSnapshot()
        if (_midEnhancerEnabledState.value) {
            halExecutor.execute { applyEqAndEnhancersInternal() }
        }
    }

    fun setTrebleEnhancerEnabled(enabled: Boolean) {
        _trebleEnhancerEnabledState.value = enabled
        prefs.edit().putBoolean(KEY_TREBLE_ENHANCER_ENABLED, enabled).apply()
        saveCurrentDeviceSnapshot()
        halExecutor.execute {
            if (!enabled) reapplyDolbyWithCleanPreset() else applyEqAndEnhancersInternal()
        }
    }

    fun setTrebleEnhancerLevel(level: Int) {
        val clamped = level.coerceIn(0, 100)
        _trebleEnhancerLevelState.value = clamped
        prefs.edit().putInt(KEY_TREBLE_ENHANCER_LEVEL, clamped).apply()
        saveCurrentDeviceSnapshot()
        if (_trebleEnhancerEnabledState.value) {
            halExecutor.execute { applyEqAndEnhancersInternal() }
        }
    }

    fun setDialogueEnhancerEnabled(enabled: Boolean) {
        _dialogueEnhancerEnabledState.value = enabled
        prefs.edit().putBoolean(KEY_DIALOGUE_ENHANCER_ENABLED, enabled).apply()
        saveCurrentDeviceSnapshot()
        halExecutor.execute {
            if (!enabled) reapplyDolbyWithCleanPreset() else applyEqAndEnhancersInternal()
        }
    }

    fun setDialogueEnhancerLevel(level: Int) {
        val clamped = level.coerceIn(0, 100)
        _dialogueEnhancerLevelState.value = clamped
        prefs.edit().putInt(KEY_DIALOGUE_ENHANCER_LEVEL, clamped).apply()
        saveCurrentDeviceSnapshot()
        if (_dialogueEnhancerEnabledState.value) {
            halExecutor.execute { applyEqAndEnhancersInternal() }
        }
    }

    fun setVolumeLevelerEnabled(enabled: Boolean) {
        _volumeLevelerEnabledState.value = enabled
        prefs.edit().putBoolean(KEY_VOLUME_LEVELER_ENABLED, enabled).apply()
        saveCurrentDeviceSnapshot()
        halExecutor.execute {
            if (!enabled) reapplyDolbyWithCleanPreset() else applyEqAndEnhancersInternal()
        }
    }

    fun setVolumeLevelerLevel(level: Int) {
        val clamped = level.coerceIn(0, 100)
        _volumeLevelerLevelState.value = clamped
        prefs.edit().putInt(KEY_VOLUME_LEVELER_LEVEL, clamped).apply()
        saveCurrentDeviceSnapshot()
        if (_volumeLevelerEnabledState.value) {
            halExecutor.execute { applyEqAndEnhancersInternal() }
        }
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        _equalizerEnabledState.value = enabled
        val editor = prefs.edit().putBoolean(KEY_EQUALIZER_ENABLED, enabled)
        if (enabled && _ieqPresetState.value != IeqPreset.OFF) {
            _ieqPresetState.value = IeqPreset.OFF
            editor.putInt(KEY_IEQ_PRESET, IeqPreset.OFF.id)
        }
        editor.apply()
        saveCurrentDeviceSnapshot()
        halExecutor.execute {
            if (!enabled) reapplyDolbyWithCleanPreset() else applyEqAndEnhancersInternal()
        }
    }

    fun setIeqPreset(preset: IeqPreset) {
        _ieqPresetState.value = preset
        val editor = prefs.edit().putInt(KEY_IEQ_PRESET, preset.id)
        val clearedCustomEq = preset != IeqPreset.OFF && _equalizerEnabledState.value
        if (clearedCustomEq) {
            _equalizerEnabledState.value = false
            editor.putBoolean(KEY_EQUALIZER_ENABLED, false)
        }
        editor.apply()
        saveCurrentDeviceSnapshot()
        halExecutor.execute {
            if (preset == IeqPreset.OFF || clearedCustomEq) {
                reapplyDolbyWithCleanPreset()
            } else {
                applyEqAndEnhancersInternal()
            }
        }
    }

    fun setIeqAmount(level: Int) {
        val clamped = level.coerceIn(0, 100)
        _ieqAmountState.value = clamped
        prefs.edit().putInt(KEY_IEQ_AMOUNT, clamped).apply()
        saveCurrentDeviceSnapshot()
        if (_ieqPresetState.value != IeqPreset.OFF && !_equalizerEnabledState.value) {
            halExecutor.execute { applyEqAndEnhancersInternal() }
        }
    }

    fun setDialogueDucking(level: Int) {
        val clamped = level.coerceIn(0, 100)
        _dialogueDuckingState.value = clamped
        prefs.edit().putInt(KEY_DIALOGUE_DUCKING, clamped).apply()
        saveCurrentDeviceSnapshot()
        halExecutor.execute { applyEqAndEnhancersInternal() }
    }

    fun setVirtualBassEnabled(enabled: Boolean) {
        _virtualBassEnabledState.value = enabled
        prefs.edit().putBoolean(KEY_VIRTUAL_BASS_ENABLED, enabled).apply()
        saveCurrentDeviceSnapshot()
        halExecutor.execute {
            if (!enabled) reapplyDolbyWithCleanPreset() else applyEqAndEnhancersInternal()
        }
    }

    fun setReverbSuppressionEnabled(enabled: Boolean) {
        _reverbSuppressionEnabledState.value = enabled
        prefs.edit().putBoolean(KEY_REVERB_SUPPRESSION_ENABLED, enabled).apply()
        saveCurrentDeviceSnapshot()
        halExecutor.execute {
            if (!enabled) reapplyDolbyWithCleanPreset() else applyEqAndEnhancersInternal()
        }
    }

    fun setReverbSuppressionAmount(level: Int) {
        val clamped = level.coerceIn(0, 100)
        _reverbSuppressionAmountState.value = clamped
        prefs.edit().putInt(KEY_REVERB_SUPPRESSION_AMOUNT, clamped).apply()
        saveCurrentDeviceSnapshot()
        if (_reverbSuppressionEnabledState.value) {
            halExecutor.execute { applyEqAndEnhancersInternal() }
        }
    }

    fun setLoudnessBoostEnabled(enabled: Boolean) {
        _loudnessBoostEnabledState.value = enabled
        prefs.edit().putBoolean(KEY_LOUDNESS_BOOST_ENABLED, enabled).apply()
        saveCurrentDeviceSnapshot()
        halExecutor.execute { applyEqAndEnhancersInternal() }
    }

    fun setLoudnessBoostDb(db: Int) {
        val clamped = db.coerceIn(0, 10)
        _loudnessBoostDbState.value = clamped
        prefs.edit().putInt(KEY_LOUDNESS_BOOST_DB, clamped).apply()
        saveCurrentDeviceSnapshot()
        if (_loudnessBoostEnabledState.value) {
            halExecutor.execute { applyEqAndEnhancersInternal() }
        }
    }

    fun setSpeakerTuningDevice(deviceName: String) {
        if (deviceName.isBlank()) return
        _speakerTuningDeviceState.value = deviceName
        prefs.edit().putString(KEY_SPEAKER_TUNING_DEVICE, deviceName).apply()
        saveCurrentDeviceSnapshot()
        halExecutor.execute {
            if (_effectModeState.value == EffectMode.DOLBY && !deviceStateManager.isHeadphonesActive()) {
                dmsClient.setSelectedTuningDevice(0, deviceName)
            }
        }
    }

    fun setEqualizerBand(index: Int, gainDb: Float) {
        if (index !in 0 until 10) return
        val updated = _equalizerBandsState.value.toMutableList()
        updated[index] = (gainDb.coerceIn(-10f, 10f) * 10f).roundToInt() / 10f
        _equalizerBandsState.value = updated
        _equalizerPresetState.value = "Custom"
        _autoEqProfileNameState.value = null
        prefs.edit()
            .putString(KEY_EQUALIZER_BANDS, updated.joinToString(","))
            .putString(KEY_EQUALIZER_PRESET, "Custom")
            .remove(KEY_AUTOEQ_PROFILE_NAME)
            .apply()
        saveCurrentDeviceSnapshot()
        halExecutor.execute { applyEqAndEnhancersInternal() }
    }

    fun setEqualizerPreset(presetName: String) {
        val gains = BUILTIN_PRESETS[presetName] ?: _customPresetsState.value[presetName] ?: return
        val normalized = normalizeBands(gains)
        _equalizerPresetState.value = presetName
        _equalizerBandsState.value = normalized
        _autoEqProfileNameState.value = null
        prefs.edit()
            .putString(KEY_EQUALIZER_BANDS, normalized.joinToString(","))
            .putString(KEY_EQUALIZER_PRESET, presetName)
            .remove(KEY_AUTOEQ_PROFILE_NAME)
            .apply()
        saveCurrentDeviceSnapshot()
        halExecutor.execute { applyEqAndEnhancersInternal() }
    }

    fun saveCustomPreset(name: String, bands: List<Float>) {
        if (name.isBlank()) return
        val normalized = normalizeBands(bands)
        val updated = _customPresetsState.value.toMutableMap().apply { put(name, normalized) }
        _customPresetsState.value = updated
        _equalizerPresetState.value = name
        _equalizerBandsState.value = normalized
        _autoEqProfileNameState.value = null
        persistCustomPresets(updated)
        prefs.edit()
            .putString(KEY_EQUALIZER_BANDS, normalized.joinToString(","))
            .putString(KEY_EQUALIZER_PRESET, name)
            .remove(KEY_AUTOEQ_PROFILE_NAME)
            .apply()
        saveCurrentDeviceSnapshot()
        halExecutor.execute { applyEqAndEnhancersInternal() }
    }

    fun renameCustomPreset(oldName: String, newName: String) {
        if (newName.isBlank() || oldName == newName) return
        val current = _customPresetsState.value.toMutableMap()
        val bands = current.remove(oldName) ?: return
        current[newName] = bands
        _customPresetsState.value = current
        persistCustomPresets(current)
        if (_equalizerPresetState.value == oldName) {
            _equalizerPresetState.value = newName
            prefs.edit().putString(KEY_EQUALIZER_PRESET, newName).apply()
            saveCurrentDeviceSnapshot()
        }
    }

    fun deleteCustomPreset(name: String) {
        val current = _customPresetsState.value.toMutableMap()
        if (current.remove(name) == null) return
        _customPresetsState.value = current
        persistCustomPresets(current)
        if (_equalizerPresetState.value == name) {
            _equalizerPresetState.value = "Custom"
            prefs.edit().putString(KEY_EQUALIZER_PRESET, "Custom").apply()
            saveCurrentDeviceSnapshot()
        }
    }

    fun setAutoEqProfile(name: String, bands: List<Float>) {
        val normalized = normalizeBands(bands)
        _autoEqProfileNameState.value = name
        _equalizerPresetState.value = "Custom"
        _equalizerBandsState.value = normalized
        prefs.edit()
            .putString(KEY_EQUALIZER_BANDS, normalized.joinToString(","))
            .putString(KEY_EQUALIZER_PRESET, "Custom")
            .putString(KEY_AUTOEQ_PROFILE_NAME, name)
            .apply()
        saveCurrentDeviceSnapshot()
        halExecutor.execute { applyEqAndEnhancersInternal() }
    }

    fun resetEqualizer() {
        _equalizerBandsState.value = DEFAULT_EQUALIZER_BANDS
        _equalizerPresetState.value = "Flat"
        _autoEqProfileNameState.value = null
        prefs.edit()
            .putString(KEY_EQUALIZER_BANDS, DEFAULT_EQUALIZER_BANDS.joinToString(","))
            .putString(KEY_EQUALIZER_PRESET, "Flat")
            .remove(KEY_AUTOEQ_PROFILE_NAME)
            .apply()
        saveCurrentDeviceSnapshot()
        halExecutor.execute { reapplyDolbyWithCleanPreset() }
    }

    private fun buildDefaultSnapshotForDevice(deviceKey: String, displayName: String) = AudioDeviceSnapshot(
        deviceKey = deviceKey,
        displayName = displayName,
        effectMode = EffectMode.DOLBY.id,
        dolbyProfile = DolbyProfile.DYNAMIC.id,
        surroundEnabled = false,
        surroundStrength = 500,
        equalizerEnabled = false,
        equalizerBands = DEFAULT_EQUALIZER_BANDS,
        equalizerPreset = "Flat",
        autoEqProfileName = null,
        bassEnhancerEnabled = false,
        bassEnhancerLevel = 50,
        midEnhancerEnabled = false,
        midEnhancerLevel = 50,
        trebleEnhancerEnabled = false,
        trebleEnhancerLevel = 50,
        volumeLevelerEnabled = false,
        volumeLevelerLevel = 50,
        dialogueEnhancerEnabled = false,
        dialogueEnhancerLevel = 50,
        ieqPreset = 0,
        ieqAmount = 50,
        dialogueDucking = 0,
        virtualBassEnabled = false,
        reverbSuppressionEnabled = false,
        reverbSuppressionAmount = 50,
        loudnessBoostEnabled = false,
        loudnessBoostDb = 3,
        speakerTuningDevice = "default_internal_speaker"
    )

    fun resetDolbyDsp() {
        val activeKey = deviceStateManager.getActiveDeviceKey()
        val activeName = deviceStateManager.getActiveDeviceName()
        val defaults = buildDefaultSnapshotForDevice(activeKey, activeName)
        applySnapshotToState(defaults)
        deviceStateManager.saveSnapshot(defaults)
        requestTileRefresh()
        halExecutor.execute {
            for (p in listOf(0, 1, 2, 3, 4, 8)) {
                dmsClient.resetProfile(p)
            }
            applyAllDspState()
        }
    }

    fun resetDeviceDefaults(deviceKey: String) {
        if (deviceKey == deviceStateManager.getActiveDeviceKey()) {
            resetDolbyDsp()
            return
        }
        val existing = deviceStateManager.getSavedSnapshots().firstOrNull { it.deviceKey == deviceKey } ?: return
        deviceStateManager.saveSnapshot(buildDefaultSnapshotForDevice(existing.deviceKey, existing.displayName))
    }

    fun resetAllDevicesDefaults() {
        val saved = deviceStateManager.getSavedSnapshots()
        for (snap in saved) {
            if (snap.deviceKey != deviceStateManager.getActiveDeviceKey()) {
                deviceStateManager.saveSnapshot(buildDefaultSnapshotForDevice(snap.deviceKey, snap.displayName))
            }
        }
        resetDolbyDsp()
    }

    fun deleteSavedDeviceProfile(deviceKey: String) {
        deviceStateManager.deleteSnapshot(deviceKey)
        if (deviceKey == deviceStateManager.getActiveDeviceKey()) {
            resetDolbyDsp()
        }
    }

    fun deleteAllSavedDeviceProfiles() {
        deviceStateManager.deleteAllSnapshots()
        resetDolbyDsp()
    }

    fun verifyHalStatus(): String = buildString {
        appendLine("=== HelloDolby HAL Bridge Probe ===")
        val selinuxCtx = runCatching {
            java.io.File("/proc/self/attr/current").readText().trim('\u0000', '\n', ' ')
        }.getOrElse { "unknown (${it.message})" }
        appendLine("Process UID=${Process.myUid()}, PID=${Process.myPid()}, SELinux=$selinuxCtx")
        appendLine("Active route: ${deviceStateManager.getActiveDeviceKey()} (${deviceStateManager.getActiveDeviceName()}), headphones=${deviceStateManager.isHeadphonesActive()}")
        appendLine("Mode=${_effectModeState.value.id}, DolbyProfile=${currentDolbyProfile.name}(${currentDolbyProfile.id}), EQ=${_equalizerEnabledState.value} (${_equalizerPresetState.value})")

        fun describeEffect(label: String, fx: AudioEffect?) {
            if (fx == null) {
                appendLine("$label: NULL")
            } else {
                val desc = runCatching { fx.descriptor }.getOrNull()
                appendLine(
                    "$label: OK (id=${runCatching { fx.id }.getOrNull()}, " +
                        "enabled=${runCatching { fx.enabled }.getOrNull()}, " +
                        "hasControl=${runCatching { fx.hasControl() }.getOrNull()}, " +
                        "name=${desc?.name})"
                )
            }
        }
        describeEffect("dap_hw", dolbyEffect)
        describeEffect("misoundfx", miSoundEffect)
        describeEffect("aosp_virtualizer", fallbackVirtualizer)
        appendLine("aosp_loudness: OK (enabled=${runCatching { loudnessEnhancer?.enabled }.getOrNull()}, db=${_loudnessBoostDbState.value})")
        appendLine("AOSP Spatializer: available=${spatializer?.isAvailable}, enabled=${spatializer?.isEnabled}")
        append(dmsClient.probeDiagnosticReport())
    }
}
