/*
 * SPDX-FileCopyrightText: 2026 marcmyworld <moviesminister@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package com.puppyfrommars.hellodolby.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.puppyfrommars.hellodolby.audio.AudioEffectManager

class BootCompletedReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootCompletedReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "Received boot intent: $action")
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            try {
                // Instantiating AudioEffectManager applies the saved active device state to DSP
                val manager = AudioEffectManager(context.applicationContext)
                Log.d(TAG, "HelloDolby restored audio DSP configuration on boot successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error restoring HelloDolby on boot", e)
            }
        }
    }
}
