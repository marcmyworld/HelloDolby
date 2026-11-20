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
import com.xiaomi.hellodolby.audio.DolbyProfile
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
    var dolbyProfile by remember { mutableStateOf(audioManager.getDolbyProfile()) }
    val spatialEnabled by audioManager.spatialAudioState.collectAsState()
    var surroundEnabled by remember { mutableStateOf(audioManager.isSurroundEnabled()) }
    var surroundStrength by remember { mutableFloatStateOf(audioManager.getSurroundStrength() / 10f) }

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

            // Section 2: Dolby Audio Presets (visible when Dolby is active)
            if (effectMode == EffectMode.DOLBY) {
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.category_dolby_preset),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp)
                )

                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    RadioButtonPreference(
                        title = stringResource(R.string.profile_dynamic),
                        summary = stringResource(R.string.profile_dynamic_summary),
                        selected = dolbyProfile == DolbyProfile.DYNAMIC,
                        onClick = {
                            dolbyProfile = DolbyProfile.DYNAMIC
                            audioManager.setDolbyProfile(DolbyProfile.DYNAMIC)
                        }
                    )
                    RadioButtonPreference(
                        title = stringResource(R.string.profile_movie),
                        summary = stringResource(R.string.profile_movie_summary),
                        selected = dolbyProfile == DolbyProfile.MOVIE,
                        onClick = {
                            dolbyProfile = DolbyProfile.MOVIE
                            audioManager.setDolbyProfile(DolbyProfile.MOVIE)
                        }
                    )
                    RadioButtonPreference(
                        title = stringResource(R.string.profile_music),
                        summary = stringResource(R.string.profile_music_summary),
                        selected = dolbyProfile == DolbyProfile.MUSIC,
                        onClick = {
                            dolbyProfile = DolbyProfile.MUSIC
                            audioManager.setDolbyProfile(DolbyProfile.MUSIC)
                        }
                    )
                    RadioButtonPreference(
                        title = stringResource(R.string.profile_voice),
                        summary = stringResource(R.string.profile_voice_summary),
                        selected = dolbyProfile == DolbyProfile.VOICE,
                        onClick = {
                            dolbyProfile = DolbyProfile.VOICE
                            audioManager.setDolbyProfile(DolbyProfile.VOICE)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section 3: Spatial & Surround Sound Options
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
                        audioManager.setSpatialAudioEnabled(checked)
                    }
                )

                // Virtualizer (Surround Sound)
                SwitchPreference(
                    title = stringResource(R.string.virtualizer),
                    summary = stringResource(R.string.virtualizer_summary),
                    checked = surroundEnabled,
                    onCheckedChange = { checked ->
                        surroundEnabled = checked
                        audioManager.setSurroundEnabled(checked)
                    }
                )

                if (surroundEnabled) {
                    SliderPreference(
                        title = stringResource(R.string.virtualizer_intensity),
                        summary = "${surroundStrength.toInt()}%",
                        value = surroundStrength,
                        onValueChange = { newVal ->
                            surroundStrength = newVal
                            audioManager.setSurroundStrength((newVal * 10).toInt())
                        },
                        valueRange = 0f..100f
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
