/*
 * SPDX-FileCopyrightText: 2026 marcmyworld <moviesminister@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package com.puppyfrommars.hellodolby.service

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.quicksettings.TileService
import android.util.Log
import com.puppyfrommars.hellodolby.audio.AudioEffectManager

/**
 * Restores persisted Dolby/MiSound DSP state on boot (`LOCKED_BOOT_COMPLETED` / `BOOT_COMPLETED`).
 */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            Log.i("HelloDolbyBoot", "Initializing AudioEffectManager on $action")
            val appContext = context.applicationContext
            AudioEffectManager.getInstance(appContext)
            runCatching {
                TileService.requestListeningState(
                    appContext,
                    ComponentName(appContext, DolbyTileService::class.java)
                )
            }
        }
    }
}
