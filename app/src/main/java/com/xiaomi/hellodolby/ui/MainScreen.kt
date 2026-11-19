package com.xiaomi.hellodolby.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaomi.hellodolby.R
import com.xiaomi.hellodolby.audio.AudioEffectManager
import com.xiaomi.hellodolby.audio.EffectMode
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun MainScreen(audioManager: AudioEffectManager) {
    var effectMode by remember { mutableStateOf(audioManager.getEffectMode()) }
    var spatialEnabled by remember { mutableStateOf(audioManager.isSpatialAudioEnabled()) }
    var virtualizerEnabled by remember { mutableStateOf(audioManager.isVirtualizerEnabled()) }
    var virtualizerStrength by remember { mutableFloatStateOf(audioManager.getVirtualizerStrength() / 10f) }

    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(R.string.app_name)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Section 1: Effect Engine Selection
            Text(
                text = stringResource(R.string.category_processing),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                RadioButtonPreference(
                    title = stringResource(R.string.dolby_atmos),
                    summary = stringResource(R.string.dolby_summary),
                    selected = effectMode == EffectMode.DOLBY,
                    onClick = {
                        effectMode = EffectMode.DOLBY
                        audioManager.setEffectMode(EffectMode.DOLBY)
                    }
                )
                RadioButtonPreference(
                    title = stringResource(R.string.xiaomi_sound),
                    summary = stringResource(R.string.xiaomi_sound_summary),
                    selected = effectMode == EffectMode.MISOUND,
                    onClick = {
                        effectMode = EffectMode.MISOUND
                        audioManager.setEffectMode(EffectMode.MISOUND)
                    }
                )
                RadioButtonPreference(
                    title = stringResource(R.string.effect_off),
                    summary = stringResource(R.string.effect_off_summary),
                    selected = effectMode == EffectMode.OFF,
                    onClick = {
                        effectMode = EffectMode.OFF
                        audioManager.setEffectMode(EffectMode.OFF)
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section 2: Spatial & Virtualizer Options
            Text(
                text = stringResource(R.string.category_spatial),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Immersive sound (AOSP Spatializer)
                SwitchPreference(
                    title = stringResource(R.string.immersive_sound),
                    summary = stringResource(R.string.immersive_sound_summary),
                    checked = spatialEnabled,
                    onCheckedChange = { checked ->
                        spatialEnabled = checked
                        audioManager.setSpatialAudioEnabled(checked)
                    }
                )

                // Virtualizer (Surround Sound)
                SwitchPreference(
                    title = stringResource(R.string.virtualizer),
                    summary = stringResource(R.string.virtualizer_summary),
                    checked = virtualizerEnabled,
                    onCheckedChange = { checked ->
                        virtualizerEnabled = checked
                        audioManager.setVirtualizerEnabled(checked)
                    }
                )

                if (virtualizerEnabled) {
                    SliderPreference(
                        title = stringResource(R.string.virtualizer_intensity),
                        summary = "${virtualizerStrength.toInt()}%",
                        value = virtualizerStrength,
                        onValueChange = { newVal ->
                            virtualizerStrength = newVal
                            audioManager.setVirtualizerStrength((newVal * 10).toInt())
                        },
                        valueRange = 0f..100f
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
