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
import org.json.JSONArray
import org.json.JSONObject

data class AudioDeviceSnapshot(
    val deviceKey: String,
    val displayName: String,
    val effectMode: String,
    val dolbyProfile: Int,
    val surroundEnabled: Boolean,
    val surroundStrength: Int,
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
    val dialogueEnhancerLevel: Int = 50,
    val ieqPreset: Int = 0,
    val ieqAmount: Int = 50,
    val dialogueDucking: Int = 0,
    val virtualBassEnabled: Boolean = false,
    val reverbSuppressionEnabled: Boolean = false,
    val reverbSuppressionAmount: Int = 50,
    val loudnessBoostEnabled: Boolean = false,
    val loudnessBoostDb: Int = 3,
    val speakerTuningDevice: String = "default_internal_speaker"
)

/**
 * Tracks active audio output routing via [AudioDeviceCallback] and stores/restores
 * per-device DSP snapshots in device-protected storage.
 */
class DeviceStateManager(
    context: Context,
    private val defaultSnapshotProvider: (deviceKey: String, displayName: String) -> AudioDeviceSnapshot,
    private val onRouteChanged: (AudioDeviceSnapshot) -> Unit
) {
    companion object {
        private const val TAG = "DeviceStateManager"
        private const val PREFS_NAME = "hellodolby_device_states"
        private const val KEY_ACTIVE_DEVICE = "active_device_key"
        private const val KEY_SNAPSHOT_PREFIX = "snapshot_"
    }

    private val storageContext = context.createDeviceProtectedStorageContext() ?: context
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val prefs: SharedPreferences = storageContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var activeDeviceKey: String = "builtin_speaker"
    @Volatile
    private var activeDeviceName: String = "Phone Speaker"

    private val _currentDeviceFlow = MutableStateFlow(activeDeviceName)
    val currentDeviceFlow: StateFlow<String> = _currentDeviceFlow.asStateFlow()

    private val _activeDeviceKeyFlow = MutableStateFlow(activeDeviceKey)
    val activeDeviceKeyFlow: StateFlow<String> = _activeDeviceKeyFlow.asStateFlow()

    private val _savedSnapshotsFlow = MutableStateFlow<List<AudioDeviceSnapshot>>(emptyList())
    val savedSnapshotsFlow: StateFlow<List<AudioDeviceSnapshot>> = _savedSnapshotsFlow.asStateFlow()

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            evaluateActiveDevice(notifyIfChanged = true)
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            evaluateActiveDevice(notifyIfChanged = true)
        }
    }

    init {
        val (initialKey, initialName) = resolveActiveOutputDevice()
        activeDeviceKey = initialKey
        activeDeviceName = initialName
        _currentDeviceFlow.value = initialName
        _activeDeviceKeyFlow.value = initialKey
        prefs.edit().putString(KEY_ACTIVE_DEVICE, initialKey).apply()
        refreshSavedSnapshots()
        audioManager.registerAudioDeviceCallback(deviceCallback, mainHandler)
    }

    fun getActiveDeviceKey(): String = activeDeviceKey
    fun getActiveDeviceName(): String = activeDeviceName

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

    fun resolveActiveOutputDevice(): Pair<String, String> {
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)

        val btDevice = outputs.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
        }
        if (btDevice != null) {
            val addr = btDevice.address?.takeIf { it.isNotBlank() } ?: "unknown_bt"
            val name = btDevice.productName?.toString()?.takeIf { it.isNotBlank() } ?: "Bluetooth Audio"
            return "bt_${addr.replace(":", "_")}" to name
        }

        val wiredDevice = outputs.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_USB_DEVICE
        }
        if (wiredDevice != null) {
            val isUsb = wiredDevice.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                wiredDevice.type == AudioDeviceInfo.TYPE_USB_DEVICE
            val key = if (isUsb) "usb_audio" else "wired_headphones"
            val name = wiredDevice.productName?.toString()?.takeIf { it.isNotBlank() }
                ?: if (isUsb) "USB Audio" else "Wired Headphones"
            return key to name
        }

        return "builtin_speaker" to "Phone Speaker"
    }

    fun evaluateActiveDevice(notifyIfChanged: Boolean = true) {
        val (selectedKey, selectedName) = resolveActiveOutputDevice()
        if (selectedKey != activeDeviceKey) {
            Log.d(TAG, "Audio route changed: $activeDeviceKey -> $selectedKey ($selectedName)")
            activeDeviceKey = selectedKey
            activeDeviceName = selectedName
            _currentDeviceFlow.value = selectedName
            _activeDeviceKeyFlow.value = selectedKey
            prefs.edit().putString(KEY_ACTIVE_DEVICE, selectedKey).apply()
            if (notifyIfChanged) {
                onRouteChanged(loadSnapshot(selectedKey, selectedName))
            }
        } else if (selectedName != activeDeviceName) {
            activeDeviceName = selectedName
            _currentDeviceFlow.value = selectedName
        }
    }

    private fun parseSnapshotJson(key: String, fallbackName: String, raw: String): AudioDeviceSnapshot? = try {
        val json = JSONObject(raw)
        val arr = json.optJSONArray("equalizerBands")
        val bands = if (arr != null && arr.length() == 10) {
            List(10) { idx -> arr.getDouble(idx).toFloat().coerceIn(-10f, 10f) }
        } else {
            List(10) { 0f }
        }
        AudioDeviceSnapshot(
            deviceKey = json.optString("deviceKey", key).ifEmpty { key },
            displayName = json.optString("displayName", fallbackName).ifEmpty { fallbackName },
            effectMode = json.optString("effectMode", "dolby"),
            dolbyProfile = json.optInt("dolbyProfile", 0),
            surroundEnabled = json.optBoolean("surroundEnabled", false),
            surroundStrength = json.optInt("surroundStrength", 500).coerceIn(0, 1000),
            equalizerEnabled = json.optBoolean("equalizerEnabled", false),
            equalizerBands = bands,
            equalizerPreset = json.optString("equalizerPreset", "Flat"),
            autoEqProfileName = json.optString("autoEqProfileName", "").takeIf { it.isNotEmpty() },
            bassEnhancerEnabled = json.optBoolean("bassEnhancerEnabled", false),
            bassEnhancerLevel = json.optInt("bassEnhancerLevel", 50).coerceIn(0, 100),
            midEnhancerEnabled = json.optBoolean("midEnhancerEnabled", false),
            midEnhancerLevel = json.optInt("midEnhancerLevel", 50).coerceIn(0, 100),
            trebleEnhancerEnabled = json.optBoolean("trebleEnhancerEnabled", false),
            trebleEnhancerLevel = json.optInt("trebleEnhancerLevel", 50).coerceIn(0, 100),
            volumeLevelerEnabled = json.optBoolean("volumeLevelerEnabled", false),
            volumeLevelerLevel = json.optInt("volumeLevelerLevel", 50).coerceIn(0, 100),
            dialogueEnhancerEnabled = json.optBoolean("dialogueEnhancerEnabled", false),
            dialogueEnhancerLevel = json.optInt("dialogueEnhancerLevel", 50).coerceIn(0, 100),
            ieqPreset = json.optInt("ieqPreset", 0).coerceIn(0, 3),
            ieqAmount = json.optInt("ieqAmount", 50).coerceIn(0, 100),
            dialogueDucking = json.optInt("dialogueDucking", 0).coerceIn(0, 100),
            virtualBassEnabled = json.optBoolean("virtualBassEnabled", false),
            reverbSuppressionEnabled = json.optBoolean("reverbSuppressionEnabled", false),
            reverbSuppressionAmount = json.optInt("reverbSuppressionAmount", 50).coerceIn(0, 100),
            loudnessBoostEnabled = json.optBoolean("loudnessBoostEnabled", false),
            loudnessBoostDb = json.optInt("loudnessBoostDb", 3).coerceIn(0, 10),
            speakerTuningDevice = json.optString("speakerTuningDevice", "default_internal_speaker").ifEmpty { "default_internal_speaker" }
        )
    } catch (e: Exception) {
        Log.w(TAG, "Failed parsing snapshot for $key", e)
        null
    }

    fun getSavedSnapshots(): List<AudioDeviceSnapshot> {
        val all = prefs.all
        val list = mutableListOf<AudioDeviceSnapshot>()
        for ((k, v) in all) {
            if (k.startsWith(KEY_SNAPSHOT_PREFIX) && v is String) {
                val deviceKey = k.removePrefix(KEY_SNAPSHOT_PREFIX)
                val parsed = parseSnapshotJson(deviceKey, deviceKey, v)
                if (parsed != null) {
                    list.add(parsed)
                }
            }
        }
        return list.sortedWith(
            compareByDescending<AudioDeviceSnapshot> { it.deviceKey == activeDeviceKey }
                .thenBy { it.displayName.lowercase() }
        )
    }

    private fun refreshSavedSnapshots() {
        _savedSnapshotsFlow.value = getSavedSnapshots()
    }

    fun loadActiveSnapshot(): AudioDeviceSnapshot = loadSnapshot(activeDeviceKey, activeDeviceName)

    fun loadSnapshot(deviceKey: String, displayName: String): AudioDeviceSnapshot {
        val raw = prefs.getString("$KEY_SNAPSHOT_PREFIX$deviceKey", null)
            ?: return defaultSnapshotProvider(deviceKey, displayName).also { saveSnapshot(it) }

        return parseSnapshotJson(deviceKey, displayName, raw)
            ?: defaultSnapshotProvider(deviceKey, displayName)
    }

    fun saveSnapshot(snapshot: AudioDeviceSnapshot) {
        val bandsJson = JSONArray()
        snapshot.equalizerBands.forEach { bandsJson.put(it.toDouble()) }
        val json = JSONObject().apply {
            put("deviceKey", snapshot.deviceKey)
            put("displayName", snapshot.displayName)
            put("effectMode", snapshot.effectMode)
            put("dolbyProfile", snapshot.dolbyProfile)
            put("surroundEnabled", snapshot.surroundEnabled)
            put("surroundStrength", snapshot.surroundStrength)
            put("equalizerEnabled", snapshot.equalizerEnabled)
            put("equalizerBands", bandsJson)
            put("equalizerPreset", snapshot.equalizerPreset)
            put("autoEqProfileName", snapshot.autoEqProfileName ?: "")
            put("bassEnhancerEnabled", snapshot.bassEnhancerEnabled)
            put("bassEnhancerLevel", snapshot.bassEnhancerLevel)
            put("midEnhancerEnabled", snapshot.midEnhancerEnabled)
            put("midEnhancerLevel", snapshot.midEnhancerLevel)
            put("trebleEnhancerEnabled", snapshot.trebleEnhancerEnabled)
            put("trebleEnhancerLevel", snapshot.trebleEnhancerLevel)
            put("volumeLevelerEnabled", snapshot.volumeLevelerEnabled)
            put("volumeLevelerLevel", snapshot.volumeLevelerLevel)
            put("dialogueEnhancerEnabled", snapshot.dialogueEnhancerEnabled)
            put("dialogueEnhancerLevel", snapshot.dialogueEnhancerLevel)
            put("ieqPreset", snapshot.ieqPreset)
            put("ieqAmount", snapshot.ieqAmount)
            put("dialogueDucking", snapshot.dialogueDucking)
            put("virtualBassEnabled", snapshot.virtualBassEnabled)
            put("reverbSuppressionEnabled", snapshot.reverbSuppressionEnabled)
            put("reverbSuppressionAmount", snapshot.reverbSuppressionAmount)
            put("loudnessBoostEnabled", snapshot.loudnessBoostEnabled)
            put("loudnessBoostDb", snapshot.loudnessBoostDb)
            put("speakerTuningDevice", snapshot.speakerTuningDevice)
        }
        prefs.edit().putString("$KEY_SNAPSHOT_PREFIX${snapshot.deviceKey}", json.toString()).apply()
        refreshSavedSnapshots()
    }

    fun deleteSnapshot(deviceKey: String) {
        prefs.edit().remove("$KEY_SNAPSHOT_PREFIX$deviceKey").apply()
        refreshSavedSnapshots()
    }

    fun deleteAllSnapshots() {
        val editor = prefs.edit()
        for (key in prefs.all.keys) {
            if (key.startsWith(KEY_SNAPSHOT_PREFIX)) {
                editor.remove(key)
            }
        }
        editor.apply()
        refreshSavedSnapshots()
    }

    fun release() {
        try {
            audioManager.unregisterAudioDeviceCallback(deviceCallback)
        } catch (_: Exception) {
        }
    }
}
