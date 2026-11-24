/*
 * SPDX-FileCopyrightText: 2026 marcmyworld <moviesminister@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package com.puppyfrommars.hellodolby.audio

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

/**
 * Pure in-memory UI state holder for the HelloDolby dummy UI milestone.
 * Functional DSP, HAL/AIDL audio effects, routing, and persistence will be implemented later.
 */
class AudioEffectManager private constructor(@Suppress("UNUSED_PARAMETER") context: Context) {
    companion object {
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
    }

    private val _connectedDeviceState = MutableStateFlow("Phone Speaker")
    val connectedDeviceState: StateFlow<String> = _connectedDeviceState.asStateFlow()

    private val _effectModeState = MutableStateFlow(EffectMode.DOLBY)
    val effectModeState: StateFlow<EffectMode> = _effectModeState.asStateFlow()

    private var currentDolbyProfile: DolbyProfile = DolbyProfile.DYNAMIC

    private val _spatialAudioState = MutableStateFlow(false)
    val spatialAudioState: StateFlow<Boolean> = _spatialAudioState.asStateFlow()

    private var currentSurroundStrength: Int = 500

    private val _bassEnhancerEnabledState = MutableStateFlow(false)
    val bassEnhancerEnabledState: StateFlow<Boolean> = _bassEnhancerEnabledState.asStateFlow()

    private val _bassEnhancerLevelState = MutableStateFlow(50)
    val bassEnhancerLevelState: StateFlow<Int> = _bassEnhancerLevelState.asStateFlow()

    private val _dialogueEnhancerEnabledState = MutableStateFlow(false)
    val dialogueEnhancerEnabledState: StateFlow<Boolean> = _dialogueEnhancerEnabledState.asStateFlow()

    private val _dialogueEnhancerLevelState = MutableStateFlow(50)
    val dialogueEnhancerLevelState: StateFlow<Int> = _dialogueEnhancerLevelState.asStateFlow()

    private val _volumeLevelerEnabledState = MutableStateFlow(false)
    val volumeLevelerEnabledState: StateFlow<Boolean> = _volumeLevelerEnabledState.asStateFlow()

    private val _volumeLevelerLevelState = MutableStateFlow(50)
    val volumeLevelerLevelState: StateFlow<Int> = _volumeLevelerLevelState.asStateFlow()

    private val _equalizerEnabledState = MutableStateFlow(false)
    val equalizerEnabledState: StateFlow<Boolean> = _equalizerEnabledState.asStateFlow()

    private val _equalizerBandsState = MutableStateFlow(DEFAULT_EQUALIZER_BANDS)
    val equalizerBandsState: StateFlow<List<Float>> = _equalizerBandsState.asStateFlow()

    private val _equalizerPresetState = MutableStateFlow("Flat")
    val equalizerPresetState: StateFlow<String> = _equalizerPresetState.asStateFlow()

    private val _autoEqProfileNameState = MutableStateFlow<String?>(null)
    val autoEqProfileNameState: StateFlow<String?> = _autoEqProfileNameState.asStateFlow()

    private val _customPresetsState = MutableStateFlow<Map<String, List<Float>>>(emptyMap())
    val customPresetsState: StateFlow<Map<String, List<Float>>> = _customPresetsState.asStateFlow()

    fun getEffectMode(): EffectMode = _effectModeState.value

    fun setEffectMode(mode: EffectMode) {
        _effectModeState.value = mode
    }

    fun getDolbyProfile(): DolbyProfile = currentDolbyProfile

    fun setDolbyProfile(profile: DolbyProfile) {
        currentDolbyProfile = profile
    }

    fun isSpatialAudioEnabled(): Boolean = _spatialAudioState.value

    fun setSpatialAudioEnabled(enabled: Boolean) {
        _spatialAudioState.value = enabled
    }

    fun getSurroundStrength(): Int = currentSurroundStrength

    fun setSurroundStrength(strength: Int) {
        currentSurroundStrength = strength.coerceIn(0, 1000)
    }

    fun setBassEnhancerEnabled(enabled: Boolean) {
        _bassEnhancerEnabledState.value = enabled
    }

    fun setBassEnhancerLevel(level: Int) {
        _bassEnhancerLevelState.value = level.coerceIn(0, 100)
    }

    fun setDialogueEnhancerEnabled(enabled: Boolean) {
        _dialogueEnhancerEnabledState.value = enabled
    }

    fun setDialogueEnhancerLevel(level: Int) {
        _dialogueEnhancerLevelState.value = level.coerceIn(0, 100)
    }

    fun setVolumeLevelerEnabled(enabled: Boolean) {
        _volumeLevelerEnabledState.value = enabled
    }

    fun setVolumeLevelerLevel(level: Int) {
        _volumeLevelerLevelState.value = level.coerceIn(0, 100)
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        _equalizerEnabledState.value = enabled
    }

    fun setEqualizerBand(index: Int, gainDb: Float) {
        if (index !in 0 until 10) return
        val updated = _equalizerBandsState.value.toMutableList()
        updated[index] = (gainDb.coerceIn(-10f, 10f) * 10f).roundToInt() / 10f
        _equalizerBandsState.value = updated
        _equalizerPresetState.value = "Custom"
        _autoEqProfileNameState.value = null
    }

    fun setEqualizerPreset(presetName: String) {
        val gains = BUILTIN_PRESETS[presetName] ?: _customPresetsState.value[presetName] ?: return
        _equalizerPresetState.value = presetName
        _equalizerBandsState.value = normalizeBands(gains)
        _autoEqProfileNameState.value = null
    }

    fun saveCustomPreset(name: String, bands: List<Float>) {
        if (name.isBlank()) return
        val normalized = normalizeBands(bands)
        _customPresetsState.value = _customPresetsState.value.toMutableMap().apply {
            put(name, normalized)
        }
        _equalizerPresetState.value = name
        _equalizerBandsState.value = normalized
        _autoEqProfileNameState.value = null
    }

    fun renameCustomPreset(oldName: String, newName: String) {
        if (newName.isBlank() || oldName == newName) return
        val current = _customPresetsState.value.toMutableMap()
        val bands = current.remove(oldName) ?: return
        current[newName] = bands
        _customPresetsState.value = current
        if (_equalizerPresetState.value == oldName) {
            _equalizerPresetState.value = newName
        }
    }

    fun deleteCustomPreset(name: String) {
        val current = _customPresetsState.value.toMutableMap()
        if (current.remove(name) == null) return
        _customPresetsState.value = current
        if (_equalizerPresetState.value == name) {
            _equalizerPresetState.value = "Custom"
        }
    }

    fun setAutoEqProfile(name: String, bands: List<Float>) {
        _autoEqProfileNameState.value = name
        _equalizerPresetState.value = "Custom"
        _equalizerBandsState.value = normalizeBands(bands)
    }

    fun resetEqualizer() {
        _equalizerBandsState.value = DEFAULT_EQUALIZER_BANDS
        _equalizerPresetState.value = "Flat"
        _autoEqProfileNameState.value = null
    }

    fun resetDolbyDsp() {
        _effectModeState.value = EffectMode.DOLBY
        currentDolbyProfile = DolbyProfile.DYNAMIC
        _spatialAudioState.value = false
        currentSurroundStrength = 500
        _bassEnhancerEnabledState.value = false
        _bassEnhancerLevelState.value = 50
        _dialogueEnhancerEnabledState.value = false
        _dialogueEnhancerLevelState.value = 50
        _volumeLevelerEnabledState.value = false
        _volumeLevelerLevelState.value = 50
        _equalizerEnabledState.value = false
        resetEqualizer()
    }
}
