/*
 * SPDX-FileCopyrightText: 2026 marcmyworld <moviesminister@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package com.puppyfrommars.hellodolby.service

import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import com.puppyfrommars.hellodolby.HelloDolbyApp
import com.puppyfrommars.hellodolby.R
import com.puppyfrommars.hellodolby.audio.AudioEffectManager
import com.puppyfrommars.hellodolby.audio.DolbyProfile
import com.puppyfrommars.hellodolby.audio.EffectMode

class DolbyTileService : TileService() {

    private val audioManager: AudioEffectManager
        get() = try {
            HelloDolbyApp.instance.audioEffectManager
        } catch (e: Exception) {
            AudioEffectManager.getInstance(applicationContext)
        }

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val currentMode = audioManager.getEffectMode()
        val newMode = if (currentMode == EffectMode.DOLBY) {
            EffectMode.MISOUND
        } else {
            EffectMode.DOLBY
        }
        Log.d("DolbyTileService", "Toggling effect mode from $currentMode to $newMode")
        audioManager.setEffectMode(newMode)
        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val currentMode = audioManager.getEffectMode()
        val isDolby = currentMode == EffectMode.DOLBY

        tile.label = getString(R.string.dolby_atmos)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_qs_dolby)
        tile.state = if (isDolby) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = when (currentMode) {
                EffectMode.DOLBY -> when (audioManager.getDolbyProfile()) {
                    DolbyProfile.DYNAMIC -> getString(R.string.profile_dynamic)
                    DolbyProfile.MOVIE -> getString(R.string.profile_movie)
                    DolbyProfile.MUSIC -> getString(R.string.profile_music)
                    DolbyProfile.GAME -> getString(R.string.profile_game)
                    DolbyProfile.VOICE -> getString(R.string.profile_voice)
                    DolbyProfile.CUSTOM -> getString(R.string.profile_custom)
                }
                EffectMode.MISOUND -> getString(R.string.xiaomi_sound)
                EffectMode.OFF -> getString(R.string.effect_off)
            }
        }

        tile.updateTile()
    }
}
