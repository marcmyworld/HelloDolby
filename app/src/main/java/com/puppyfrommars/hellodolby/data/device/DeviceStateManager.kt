/*
 * SPDX-FileCopyrightText: 2026 marcmyworld <moviesminister@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package com.puppyfrommars.hellodolby.data.device

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AudioDeviceSnapshot(
    val deviceKey: String,
    val displayName: String,
    val effectMode: String,
    val dolbyProfile: Int,
    val surroundEnabled: Boolean,
    val surroundStrength: Float,
    val equalizerEnabled: Boolean,
    val equalizerBands: List<Float>,
    val equalizerPreset: String,
    val autoEqProfileName: String?,
    val bassEnhancerEnabled: Boolean = false,
    val bassEnhancerLevel: Int = 50,
    val midEnhancerEnabled: Boolean = false,
    val midEnhancerLevel: Int = 50,
    val trebleEnhancerEnabled: Boolean = false,
    val trebleEnhancerLevel: Int = 50,
    val volumeLevelerEnabled: Boolean = false,
    val volumeLevelerLevel: Int = 50,
    val dialogueEnhancerEnabled: Boolean = false,
    val dialogueEnhancerLevel: Int = 50
)

class DeviceStateManager(
    private val context: Context,
    private val onDeviceStateRestored: (AudioDeviceSnapshot) -> Unit
) {
    companion object {
        private const val TAG = "DeviceStateManager"
        private const val PREFS_NAME = "hellodolby_device_states"
        private const val KEY_CURRENT_DEVICE = "active_device_key"
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var activeDeviceKey: String = "builtin_speaker"
    private var activeDeviceName: String = "Phone Speaker"

    private val _currentDeviceFlow = MutableStateFlow(activeDeviceName)
    val currentDeviceFlow: StateFlow<String> = _currentDeviceFlow.asStateFlow()

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            Log.d(TAG, "Audio devices added")
            evaluateActiveDevice()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            Log.d(TAG, "Audio devices removed")
            evaluateActiveDevice()
        }
    }

    init {
        audioManager.registerAudioDeviceCallback(deviceCallback, mainHandler)
        evaluateActiveDevice(isInitial = true)
    }

    fun getActiveDeviceKey(): String = activeDeviceKey
    fun getActiveDeviceName(): String = activeDeviceName

    fun evaluateActiveDevice(isInitial: Boolean = false) {
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        var selectedKey = "builtin_speaker"
        var selectedName = "Phone Speaker"

        // Priority: Bluetooth > Wired / USB > Built-in Speaker
        val btDevice = outputs.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
        }
        val wiredDevice = outputs.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_USB_DEVICE
        }

        if (btDevice != null) {
            val addr = btDevice.address?.takeIf { it.isNotBlank() } ?: "unknown_bt"
            selectedKey = "bt_${addr.replace(":", "_")}"
            selectedName = btDevice.productName?.toString()?.takeIf { it.isNotBlank() } ?: "Bluetooth Device"
        } else if (wiredDevice != null) {
            val isUsb = wiredDevice.type == AudioDeviceInfo.TYPE_USB_HEADSET || wiredDevice.type == AudioDeviceInfo.TYPE_USB_DEVICE
            selectedKey = if (isUsb) "usb_audio" else "wired_headphones"
            selectedName = wiredDevice.productName?.toString()?.takeIf { it.isNotBlank() }
                ?: if (isUsb) "USB Audio" else "Wired Headphones"
        }

        Log.d(TAG, "Evaluated active audio device: key=$selectedKey, name=$selectedName (previous=$activeDeviceKey)")

        if (selectedKey != activeDeviceKey || isInitial) {
            activeDeviceKey = selectedKey
            activeDeviceName = selectedName
            _currentDeviceFlow.value = selectedName
            prefs.edit().putString(KEY_CURRENT_DEVICE, selectedKey).apply()

            val snapshot = loadSnapshot(selectedKey, selectedName)
            onDeviceStateRestored(snapshot)
        }
    }

    fun saveSnapshot(
        effectMode: String,
        dolbyProfile: Int,
        surroundEnabled: Boolean,
        surroundStrength: Float,
        equalizerEnabled: Boolean,
        equalizerBands: List<Float>,
        equalizerPreset: String,
        autoEqProfileName: String?,
        bassEnhancerEnabled: Boolean = false,
        bassEnhancerLevel: Int = 50,
        midEnhancerEnabled: Boolean = false,
        midEnhancerLevel: Int = 50,
        trebleEnhancerEnabled: Boolean = false,
        trebleEnhancerLevel: Int = 50,
        volumeLevelerEnabled: Boolean = false,
        volumeLevelerLevel: Int = 50,
        dialogueEnhancerEnabled: Boolean = false,
        dialogueEnhancerLevel: Int = 50
    ) {
        val key = activeDeviceKey
        Log.d(TAG, "Saving snapshot for device key: $key ($activeDeviceName)")
        prefs.edit()
            .putString("${key}_effect_mode", effectMode)
            .putInt("${key}_dolby_profile", dolbyProfile)
            .putBoolean("${key}_surround_enabled", surroundEnabled)
            .putFloat("${key}_surround_strength", surroundStrength)
            .putBoolean("${key}_equalizer_enabled", equalizerEnabled)
            .putString("${key}_equalizer_bands", equalizerBands.joinToString(","))
            .putString("${key}_equalizer_preset", equalizerPreset)
            .putString("${key}_autoeq_name", autoEqProfileName)
            .putBoolean("${key}_bass_enhancer_enabled", bassEnhancerEnabled)
            .putInt("${key}_bass_enhancer_level", bassEnhancerLevel)
            .putBoolean("${key}_mid_enhancer_enabled", midEnhancerEnabled)
            .putInt("${key}_mid_enhancer_level", midEnhancerLevel)
            .putBoolean("${key}_treble_enhancer_enabled", trebleEnhancerEnabled)
            .putInt("${key}_treble_enhancer_level", trebleEnhancerLevel)
            .putBoolean("${key}_volume_leveler_enabled", volumeLevelerEnabled)
            .putInt("${key}_volume_leveler_level", volumeLevelerLevel)
            .putBoolean("${key}_dialogue_enhancer_enabled", dialogueEnhancerEnabled)
            .putInt("${key}_dialogue_enhancer_level", dialogueEnhancerLevel)
            .apply()
    }

    fun loadSnapshot(key: String, name: String): AudioDeviceSnapshot {
        val effectMode = prefs.getString("${key}_effect_mode", "dolby") ?: "dolby"
        val dolbyProfile = prefs.getInt("${key}_dolby_profile", 0)
        val surroundEnabled = prefs.getBoolean("${key}_surround_enabled", true)
        val surroundStrength = prefs.getFloat("${key}_surround_strength", 0.5f)
        val equalizerEnabled = prefs.getBoolean("${key}_equalizer_enabled", false)
        val rawBands = prefs.getString("${key}_equalizer_bands", null)
        val bands = if (rawBands != null) {
            rawBands.split(",").mapNotNull { it.toFloatOrNull() }.takeIf { it.size == 10 }
                ?: List(10) { 0f }
        } else {
            List(10) { 0f }
        }
        val equalizerPreset = prefs.getString("${key}_equalizer_preset", "Flat") ?: "Flat"
        val autoEqName = prefs.getString("${key}_autoeq_name", null)
        val bassEnabled = prefs.getBoolean("${key}_bass_enhancer_enabled", false)
        val bassLevel = prefs.getInt("${key}_bass_enhancer_level", 50)
        val midEnabled = prefs.getBoolean("${key}_mid_enhancer_enabled", false)
        val midLevel = prefs.getInt("${key}_mid_enhancer_level", 50)
        val trebleEnabled = prefs.getBoolean("${key}_treble_enhancer_enabled", false)
        val trebleLevel = prefs.getInt("${key}_treble_enhancer_level", 50)
        val volumeLevelerEnabled = prefs.getBoolean("${key}_volume_leveler_enabled", false)
        val volumeLevelerLevel = prefs.getInt("${key}_volume_leveler_level", 50)
        val dialogueEnabled = prefs.getBoolean("${key}_dialogue_enhancer_enabled", false)
        val dialogueLevel = prefs.getInt("${key}_dialogue_enhancer_level", 50)

        return AudioDeviceSnapshot(
            deviceKey = key,
            displayName = name,
            effectMode = effectMode,
            dolbyProfile = dolbyProfile,
            surroundEnabled = surroundEnabled,
            surroundStrength = surroundStrength,
            equalizerEnabled = equalizerEnabled,
            equalizerBands = bands,
            equalizerPreset = equalizerPreset,
            autoEqProfileName = autoEqName,
            bassEnhancerEnabled = bassEnabled,
            bassEnhancerLevel = bassLevel,
            midEnhancerEnabled = midEnabled,
            midEnhancerLevel = midLevel,
            trebleEnhancerEnabled = trebleEnabled,
            trebleEnhancerLevel = trebleLevel,
            volumeLevelerEnabled = volumeLevelerEnabled,
            volumeLevelerLevel = volumeLevelerLevel,
            dialogueEnhancerEnabled = dialogueEnabled,
            dialogueEnhancerLevel = dialogueLevel
        )
    }

    fun release() {
        try {
            audioManager.unregisterAudioDeviceCallback(deviceCallback)
        } catch (e: Exception) {
            Log.w(TAG, "Failed unregistering audioDeviceCallback", e)
        }
    }
}
