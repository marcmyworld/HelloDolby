/*
 * SPDX-FileCopyrightText: 2026 marcmyworld <moviesminister@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package com.puppyfrommars.hellodolby.audio

import android.os.IBinder
import android.os.Parcel
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Direct AIDL Binder client for `vendor.dolby.dms.IDms/default` (Dolby Manager Service).
 *
 * Verified against `vendor.dolby.dms-V1-ndk.so`, `libdmshal.so`, and `libdlbdsservice.so`:
 * - Transaction 1 (`FLAG_ONEWAY`): `sendCommand(int commandId, byte[] inData)`
 * - Transaction 2 (`flags = 0`):   `handleRequest(int requestId, byte[] inData) -> byte[]`
 */
class DmsBinderClient {
    companion object {
        private const val TAG = "DmsBinderClient"
        private const val SERVICE_NAME = "vendor.dolby.dms.IDms/default"
        private const val DESCRIPTOR = "vendor.dolby.dms.IDms"

        private const val TRANSACTION_SEND_COMMAND = IBinder.FIRST_CALL_TRANSACTION + 0 // 1 (oneway)
        private const val TRANSACTION_HANDLE_REQUEST = IBinder.FIRST_CALL_TRANSACTION + 1 // 2
        private const val TRANSACTION_GET_INTERFACE_VERSION = 16777215

        // FourCC sendCommand IDs (vendor.dolby.dms.service)
        const val CMD_SDAP = 0x53444150 // 'SDAP' - setDapParam
        const val CMD_SINT = 0x53494e54 // 'SINT' - setIntParams
        const val CMD_SSTD = 0x53535444 // 'SSTD' - setSelectedTuningDevice
        const val CMD_SADV = 0x53414456 // 'SADV' - setActiveDevice
        const val CMD_SCHC = 0x53434843 // 'SCHC' - setChannelCount

        // FourCC handleRequest IDs (vendor.dolby.dms.service)
        const val REQ_GDAP = 0x47444150 // 'GDAP' - getDapParam
        const val REQ_GINT = 0x47494e54 // 'GINT' - getIntParam
        const val REQ_GADV = 0x47414456 // 'GADV' - getActiveDevice
        const val REQ_GPFN = 0x4750464e // 'GPFN' - getProfileName
        const val REQ_GIPN = 0x4749504e // 'GIPN' - getIeqPresetName
        const val REQ_GATD = 0x47415444 // 'GATD' - getAvailableTuningDevices
        const val REQ_GSTD = 0x47535444 // 'GSTD' - getSelectedTuningDevice

        // FourCC keys for REQ_GINT
        const val INT_PARAM_DAP_ON = 0x4441504f           // 'DAPO' - getDsOn()
        const val INT_PARAM_PROFILE_MOBILITY = 0x41504d4f // 'APMO' - selectedProfile->isMobility()
        const val INT_PARAM_ACTIVE_DEVICE = 0x41444556    // 'ADEV'
        const val INT_PARAM_DIALOG_ENHANCER = 0x4445414d  // 'DEAM'

        // SDAP / EFFECT_PARAM_CPDP_VALUES packet opcodes (EventHandler::parseAndSendParam)
        const val DAP_OP_SET_DS_ON = 0x00000000
        const val DAP_OP_PROFILE_PARAM = 0x01000000
        const val DAP_OP_PROFILE_PORT_PARAM = 0x02000000
        const val DAP_OP_SET_PROFILE = 0x0a000000
        const val DAP_OP_RESET_PROFILE = 0x0c000000

        // Verified ParameterAdapter IDs in libdlbdsservice.so (indices 100..116)
        const val DAP_PARAM_HEADPHONE_VIRTUALIZER = 101      // virtualizer_enable (ep 3)
        const val DAP_PARAM_SPEAKER_VIRTUALIZER = 102        // virtualizer_enable (ep 0 / speaker)
        const val DAP_PARAM_VOLUME_LEVELER_ENABLE = 103      // volume_leveler_enable (0..1)
        const val DAP_PARAM_IEQ_ID = 104                     // ieq_id
        const val DAP_PARAM_DIALOG_ENHANCER_ENABLE = 105     // dialog_enhancer_enable (0..1)
        const val DAP_PARAM_GRAPHIC_EQUALIZER_ENABLE = 106   // graphic_equalizer_enable (0..1)
        const val DAP_PARAM_IEQ_AMOUNT = 107                 // ieq_amount (0..16)
        const val DAP_PARAM_DIALOG_ENHANCER_AMOUNT = 108     // dialog_enhancer_amount (0..16)
        const val DAP_PARAM_DIALOG_ENHANCER_DUCKING = 109    // dialog_enhancer_ducking (0..16)
        const val DAP_PARAM_GEQ_BAND_GAINS = 110             // geq_gain (-576..576, 20 bands)
        const val DAP_PARAM_BASS_ENHANCER_ENABLE = 111       // bass_enhancer_enable (0..1; mirrored to 112)
        const val DAP_PARAM_VIRTUAL_BASS_ENABLE = 112        // virtual_bass_enable (0..1)
        const val DAP_PARAM_STEREO_WIDENING = 113            // headphone_virtualizer_steerer_source_distance (4..160)
        const val DAP_PARAM_REVERB_SUPPRESSION_ENABLE = 114  // reverb_suppression_enable (0..1)
        const val DAP_PARAM_REVERB_SUPPRESSION_AMOUNT = 115  // reverb_suppression_amount (0..9)
        const val DAP_PARAM_VOLUME_LEVELER_AMOUNT = 116      // volume_leveler_amount (0..10)

        fun intsToLeBytes(vararg values: Int): ByteArray {
            val buf = ByteBuffer.allocate(values.size * 4).order(ByteOrder.LITTLE_ENDIAN)
            for (v in values) buf.putInt(v)
            return buf.array()
        }

        fun buildSetDsOnPacket(enabled: Boolean): ByteArray =
            intsToLeBytes(DAP_OP_SET_DS_ON, 1, if (enabled) 1 else 0)

        fun buildSetProfilePacket(profileId: Int): ByteArray =
            intsToLeBytes(DAP_OP_SET_PROFILE, 1, profileId)

        fun buildResetProfilePacket(profileId: Int): ByteArray =
            intsToLeBytes(DAP_OP_RESET_PROFILE, 1, profileId)

        fun buildProfileParamPacket(profileId: Int, paramId: Int, values: IntArray): ByteArray {
            val buf = ByteBuffer.allocate((4 + values.size) * 4).order(ByteOrder.LITTLE_ENDIAN)
            buf.putInt(DAP_OP_PROFILE_PARAM)
            buf.putInt(values.size + 1)
            buf.putInt(profileId)
            buf.putInt(paramId)
            for (v in values) buf.putInt(v)
            return buf.array()
        }

        fun buildProfilePortParamPacket(
            profileId: Int,
            port: Int,
            paramId: Int,
            values: IntArray
        ): ByteArray {
            val buf = ByteBuffer.allocate((5 + values.size) * 4).order(ByteOrder.LITTLE_ENDIAN)
            buf.putInt(DAP_OP_PROFILE_PORT_PARAM)
            buf.putInt(values.size + 1)
            buf.putInt(profileId)
            buf.putInt(port)
            buf.putInt(paramId)
            for (v in values) buf.putInt(v)
            return buf.array()
        }
    }

    private val binderLock = Any()
    @Volatile
    private var cachedBinder: IBinder? = null

    private fun getBinder(forceRefresh: Boolean = false): IBinder? = synchronized(binderLock) {
        if (!forceRefresh) {
            val existing = cachedBinder
            if (existing != null && existing.isBinderAlive) {
                return existing
            }
        }
        val binder = try {
            val sm = Class.forName("android.os.ServiceManager")
            val getService = sm.getMethod("getService", String::class.java)
            getService.invoke(null, SERVICE_NAME) as? IBinder
        } catch (e: Exception) {
            Log.e(TAG, "Failed to look up $SERVICE_NAME", e)
            null
        }
        if (binder != null) {
            try {
                binder.linkToDeath({
                    synchronized(binderLock) {
                        if (cachedBinder === binder) cachedBinder = null
                    }
                }, 0)
            } catch (_: Exception) {
            }
            cachedBinder = binder
        } else {
            cachedBinder = null
        }
        binder
    }

    private fun invalidateBinder(stale: IBinder) {
        synchronized(binderLock) {
            if (cachedBinder === stale) {
                cachedBinder = null
            }
        }
    }

    fun isAvailable(): Boolean = getBinder() != null

    fun getInterfaceVersion(): Int? {
        val binder = getBinder() ?: return null
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(DESCRIPTOR)
            val ok = binder.transact(TRANSACTION_GET_INTERFACE_VERSION, data, reply, 0)
            if (!ok) return null
            reply.readException()
            reply.readInt()
        } catch (e: Exception) {
            Log.w(TAG, "getInterfaceVersion failed: ${e.message}")
            null
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    fun sendCommand(commandId: Int, payload: ByteArray): Boolean {
        for (attempt in 0..1) {
            val binder = getBinder(forceRefresh = attempt > 0) ?: return false
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(DESCRIPTOR)
                data.writeInt(commandId)
                data.writeByteArray(payload)
                if (binder.transact(TRANSACTION_SEND_COMMAND, data, reply, IBinder.FLAG_ONEWAY)) {
                    return true
                }
                invalidateBinder(binder)
            } catch (e: android.os.DeadObjectException) {
                invalidateBinder(binder)
                if (attempt == 1) {
                    Log.e(TAG, "sendCommand(0x${commandId.toString(16)}) dead binder", e)
                }
            } catch (e: Exception) {
                Log.e(TAG, "sendCommand(0x${commandId.toString(16)}) failed", e)
                return false
            } finally {
                data.recycle()
                reply.recycle()
            }
        }
        return false
    }

    fun handleRequest(requestId: Int, inData: ByteArray = ByteArray(0)): ByteArray? {
        for (attempt in 0..1) {
            val binder = getBinder(forceRefresh = attempt > 0) ?: return null
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(DESCRIPTOR)
                data.writeInt(requestId)
                data.writeByteArray(inData)
                if (!binder.transact(TRANSACTION_HANDLE_REQUEST, data, reply, 0)) {
                    invalidateBinder(binder)
                    continue
                }
                reply.readException()
                return reply.createByteArray()
            } catch (e: android.os.DeadObjectException) {
                invalidateBinder(binder)
                if (attempt == 1) {
                    Log.e(TAG, "handleRequest(0x${requestId.toString(16)}) dead binder", e)
                }
            } catch (e: Exception) {
                Log.e(TAG, "handleRequest(0x${requestId.toString(16)}) failed", e)
                return null
            } finally {
                data.recycle()
                reply.recycle()
            }
        }
        return null
    }

    fun setDsOn(enabled: Boolean): Boolean =
        sendCommand(CMD_SDAP, buildSetDsOnPacket(enabled))

    fun setSelectedProfile(profileId: Int): Boolean =
        sendCommand(CMD_SDAP, buildSetProfilePacket(profileId))

    fun resetProfile(profileId: Int): Boolean =
        sendCommand(CMD_SDAP, buildResetProfilePacket(profileId))

    fun setProfileParam(profileId: Int, paramId: Int, values: IntArray): Boolean =
        sendCommand(CMD_SDAP, buildProfileParamPacket(profileId, paramId, values))

    fun setProfilePortParam(profileId: Int, port: Int, paramId: Int, values: IntArray): Boolean =
        sendCommand(CMD_SDAP, buildProfilePortParamPacket(profileId, port, paramId, values))

    fun setActiveDevice(audioDeviceMask: Int): Boolean {
        val inParcel = Parcel.obtain()
        val inBytes = try {
            inParcel.writeInt(audioDeviceMask)
            inParcel.marshall()
        } finally {
            inParcel.recycle()
        }
        return sendCommand(CMD_SADV, inBytes)
    }

    fun getIntParam(fourcc: Int): Long? {
        val inParcel = Parcel.obtain()
        val inBytes = try {
            inParcel.writeIntArray(intArrayOf(fourcc))
            inParcel.marshall()
        } finally {
            inParcel.recycle()
        }
        val reply = handleRequest(REQ_GINT, inBytes) ?: return null
        if (reply.size < 12) return null
        val outParcel = Parcel.obtain()
        return try {
            outParcel.unmarshall(reply, 0, reply.size)
            outParcel.setDataPosition(0)
            val longs = outParcel.createLongArray()
            longs?.firstOrNull()
        } catch (_: Exception) {
            null
        } finally {
            outParcel.recycle()
        }
    }

    private fun parseGdapInt32Reply(reply: ByteArray?): Int? {
        if (reply == null || reply.size < 8) return null
        val parcel = Parcel.obtain()
        return try {
            parcel.unmarshall(reply, 0, reply.size)
            parcel.setDataPosition(0)
            val status = parcel.readInt()
            val outBytes = parcel.createByteArray()
            if (status == 0 && outBytes != null && outBytes.size >= 4) {
                ByteBuffer.wrap(outBytes, 0, 4).order(ByteOrder.LITTLE_ENDIAN).int
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            parcel.recycle()
        }
    }

    fun getSelectedProfile(): Int? =
        parseGdapInt32Reply(handleRequest(REQ_GDAP, intsToLeBytes(DAP_OP_SET_PROFILE, 0)))

    fun getDapOn(): Boolean? =
        parseGdapInt32Reply(handleRequest(REQ_GDAP, intsToLeBytes(DAP_OP_SET_DS_ON, 0)))?.let { it != 0 }

    fun getActiveDevice(): Int? {
        val reply = handleRequest(REQ_GADV) ?: return null
        if (reply.size < 4) return null
        val parcel = Parcel.obtain()
        return try {
            parcel.unmarshall(reply, 0, reply.size)
            parcel.setDataPosition(0)
            parcel.readInt()
        } catch (_: Exception) {
            null
        } finally {
            parcel.recycle()
        }
    }

    private fun parseUtf8ParcelReply(reply: ByteArray?): String? {
        if (reply == null || reply.isEmpty()) return null
        val parcel = Parcel.obtain()
        return try {
            parcel.unmarshall(reply, 0, reply.size)
            parcel.setDataPosition(0)
            val len = parcel.readInt()
            if (len in 1..1024 && reply.size >= 4 + len) {
                String(reply, 4, len, Charsets.UTF_8).trimEnd('\u0000')
            } else {
                String(reply, Charsets.UTF_8).trimEnd('\u0000')
            }
        } catch (_: Exception) {
            String(reply, Charsets.UTF_8).trimEnd('\u0000')
        } finally {
            parcel.recycle()
        }
    }

    private fun intParcelBytes(value: Int): ByteArray {
        val inParcel = Parcel.obtain()
        return try {
            inParcel.writeInt(value)
            inParcel.marshall()
        } finally {
            inParcel.recycle()
        }
    }

    fun getProfileName(profileId: Int): String? =
        parseUtf8ParcelReply(handleRequest(REQ_GPFN, intParcelBytes(profileId)))

    fun getAvailableTuningDevices(port: Int = 0): List<String> {
        val raw = parseUtf8ParcelReply(handleRequest(REQ_GATD, intParcelBytes(port))) ?: return emptyList()
        return raw.split(' ').map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun getSelectedTuningDevice(port: Int = 0): String? =
        parseUtf8ParcelReply(handleRequest(REQ_GSTD, intParcelBytes(port)))?.trim()?.takeIf { it.isNotEmpty() }

    fun setSelectedTuningDevice(port: Int = 0, deviceName: String): Boolean {
        if (deviceName.isBlank()) return false
        val inParcel = Parcel.obtain()
        val inBytes = try {
            inParcel.writeInt(port)
            inParcel.writeByteArray(deviceName.toByteArray(Charsets.UTF_8))
            inParcel.marshall()
        } finally {
            inParcel.recycle()
        }
        return sendCommand(CMD_SSTD, inBytes)
    }

    fun getProfileIntParam(profileId: Int, paramId: Int): Int? =
        parseGdapInt32Reply(handleRequest(REQ_GDAP, intsToLeBytes(DAP_OP_PROFILE_PARAM, paramId, profileId)))

    fun probeDiagnosticReport(): String = buildString {
        val binder = getBinder()
        if (binder == null) {
            appendLine("IDms binder: NULL ($SERVICE_NAME unreachable - check SELinux/service)")
            return@buildString
        }
        appendLine("IDms binder: CONNECTED (alive=${binder.isBinderAlive}, ping=${binder.pingBinder()}, desc=${binder.interfaceDescriptor})")
        appendLine("IDms interfaceVersion: ${getInterfaceVersion()}")
        appendLine("IDms activeDevice (GADV): 0x${(getActiveDevice() ?: -1).toString(16)}")
        appendLine("IDms dapOn (GINT DAPO): ${getIntParam(INT_PARAM_DAP_ON)} (GDAP=${getDapOn()})")
        appendLine("IDms selectedProfile (GDAP 0x0a): ${getSelectedProfile()}")
        appendLine("IDms selectedProfileMobility (GINT APMO): ${getIntParam(INT_PARAM_PROFILE_MOBILITY)}")
        appendLine("IDms speakerTuning (GSTD): ${getSelectedTuningDevice(0)} / available=${getAvailableTuningDevices(0)}")
        appendLine("IDms profileNames[0..8]: 0=${getProfileName(0)}, 1=${getProfileName(1)}, 2=${getProfileName(2)}, 3=${getProfileName(3)}, 4=${getProfileName(4)}, 5=${getProfileName(5)}, 6=${getProfileName(6)}, 7=${getProfileName(7)}, 8=${getProfileName(8)}")
    }
}
