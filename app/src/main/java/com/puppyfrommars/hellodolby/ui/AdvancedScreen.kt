/*
 * SPDX-FileCopyrightText: 2026 marcmyworld <moviesminister@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package com.puppyfrommars.hellodolby.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.puppyfrommars.hellodolby.R
import com.puppyfrommars.hellodolby.audio.AudioEffectManager
import com.puppyfrommars.hellodolby.audio.EffectMode
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.theme.MiuixTheme

private fun speakerTuningLabel(raw: String): String = when (raw) {
    "default_internal_speaker" -> "Default"
    "small_mode_speaker" -> "Compact"
    "medium_mode_speaker" -> "Balanced"
    "large_mode_speaker" -> "Wide Stage"
    "speaker_portrait" -> "Portrait"
    "speaker_landscape" -> "Landscape"
    else -> raw.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

@Composable
fun AdvancedScreen(
    audioManager: AudioEffectManager,
    padding: PaddingValues,
    preciseSliders: Boolean,
    onPreciseSlidersChange: (Boolean) -> Unit
) {
    val scrollState = rememberScrollState()
    val connectedDevice by audioManager.connectedDeviceState.collectAsState()
    val activeDeviceKey by audioManager.activeDeviceKeyState.collectAsState()
    val savedSnapshots by audioManager.savedDeviceSnapshotsState.collectAsState()
    val effectMode by audioManager.effectModeState.collectAsState()
    val dolbyProfile by audioManager.dolbyProfileState.collectAsState()

    val virtualBassEnabled by audioManager.virtualBassEnabledState.collectAsState()
    val reverbEnabled by audioManager.reverbSuppressionEnabledState.collectAsState()
    val reverbAmount by audioManager.reverbSuppressionAmountState.collectAsState()
    val dialogueDucking by audioManager.dialogueDuckingState.collectAsState()
    val loudnessBoostEnabled by audioManager.loudnessBoostEnabledState.collectAsState()
    val loudnessBoostDb by audioManager.loudnessBoostDbState.collectAsState()
    val speakerTuning by audioManager.speakerTuningDeviceState.collectAsState()
    val availableSpeakerTunings by audioManager.availableSpeakerTuningsState.collectAsState()

    var showDeviceProfilesDialog by remember { mutableStateOf(false) }
    var showRestoreConfirmDialog by remember { mutableStateOf(false) }

    val isDolbyActive = effectMode == EffectMode.DOLBY
    val isSpeakerRoute = activeDeviceKey == "builtin_speaker"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Active Device Status Banner
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(
                            if (isSpeakerRoute) R.drawable.ic_speaker else R.drawable.ic_dolby
                        ),
                        contentDescription = connectedDevice,
                        tint = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = connectedDevice,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Hardware DSP parameters apply per output device",
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.14f))
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = when (effectMode) {
                            EffectMode.DOLBY -> "Dolby"
                            EffectMode.MISOUND -> "MiSound"
                            EffectMode.OFF -> "Bypass"
                        },
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MiuixTheme.colorScheme.primary
                    )
                }
            }
        }

        // Section 1: Extended Dolby DSP Controls (Virtual Bass, Reverb Suppression, Dialogue Ducking)
        AnimatedVisibility(
            visible = isDolbyActive,
            enter = expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(tween(220)),
            exit = shrinkVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut(tween(160))
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.category_dolby_dsp_advanced),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 8.dp)
                )

                Card(modifier = Modifier.fillMaxWidth()) {
                    // Dolby Virtual Bass Toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { audioManager.setVirtualBassEnabled(!virtualBassEnabled) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(
                                    if (virtualBassEnabled) {
                                        MiuixTheme.colorScheme.primary.copy(alpha = 0.16f)
                                    } else {
                                        MiuixTheme.colorScheme.surfaceContainerHigh
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_preset_music),
                                contentDescription = stringResource(R.string.virtual_bass_title),
                                tint = if (virtualBassEnabled) {
                                    MiuixTheme.colorScheme.primary
                                } else {
                                    MiuixTheme.colorScheme.onSurfaceVariantSummary
                                },
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.virtual_bass_title),
                                fontSize = 15.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = MiuixTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.virtual_bass_summary),
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = virtualBassEnabled,
                            onCheckedChange = { audioManager.setVirtualBassEnabled(it) }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    // Reverb Suppression Toggle + Strength Slider
                    EnhancerControlTile(
                        title = stringResource(R.string.reverb_suppression_title),
                        summary = stringResource(R.string.reverb_suppression_summary),
                        intensityLabel = stringResource(R.string.reverb_suppression_amount),
                        iconRes = R.drawable.ic_preset_movie,
                        enabled = reverbEnabled,
                        level = reverbAmount,
                        preciseSliders = preciseSliders,
                        onToggle = { audioManager.setReverbSuppressionEnabled(it) },
                        onLevelChange = { audioManager.setReverbSuppressionAmount(it) }
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    // Dialogue Ducking Slider (always accessible without toggling off)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.dialogue_ducking_title),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = MiuixTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.14f))
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "$dialogueDucking%",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MiuixTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.dialogue_ducking_summary),
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        TactilePercentSlider(
                            label = "Ducking Depth",
                            level = dialogueDucking,
                            preciseSliders = preciseSliders,
                            onLevelChange = { audioManager.setDialogueDucking(it) },
                            stepChips = listOf(0, 25, 50, 75)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Section 2: Speaker Acoustic Calibration (GATD / GSTD / SSTD)
                Text(
                    text = stringResource(R.string.category_speaker_tuning),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 8.dp)
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(if (isSpeakerRoute) 1f else 0.48f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.speaker_tuning_title),
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isSpeakerRoute) {
                                stringResource(R.string.speaker_tuning_summary)
                            } else {
                                "Switch output to Phone Speaker to apply speaker calibration curves"
                            },
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            availableSpeakerTunings.forEach { tuningKey ->
                                val selected = speakerTuning == tuningKey
                                val chipBg by animateColorAsState(
                                    targetValue = if (selected) {
                                        MiuixTheme.colorScheme.primary
                                    } else {
                                        MiuixTheme.colorScheme.surfaceContainerHigh
                                    },
                                    animationSpec = tween(180),
                                    label = "TuningBg_$tuningKey"
                                )
                                val chipFg by animateColorAsState(
                                    targetValue = if (selected) {
                                        MiuixTheme.colorScheme.onPrimary
                                    } else {
                                        MiuixTheme.colorScheme.onSurface
                                    },
                                    animationSpec = tween(180),
                                    label = "TuningFg_$tuningKey"
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(chipBg)
                                        .clickable(enabled = isSpeakerRoute) {
                                            audioManager.setSpeakerTuningDevice(tuningKey)
                                        }
                                        .padding(horizontal = 13.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = speakerTuningLabel(tuningKey),
                                        fontSize = 12.5.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        color = chipFg
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Section 3: Master Output Stage (Loudness Enhancer + Hardware GEQ Gain — active in Dolby & Xiaomi Sound)
        val isDirectDac = effectMode == EffectMode.OFF
        Text(
            text = stringResource(R.string.category_global_gain),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 8.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (isDirectDac) 0.44f else 1f)
        ) {
            EnhancerControlTile(
                title = stringResource(R.string.loudness_boost_title),
                summary = stringResource(R.string.loudness_boost_summary),
                intensityLabel = stringResource(R.string.loudness_boost_db),
                iconRes = R.drawable.ic_direct_dac,
                enabled = !isDirectDac && loudnessBoostEnabled,
                level = loudnessBoostDb,
                preciseSliders = true,
                interactive = !isDirectDac,
                valueRange = 0..10,
                stepChips = emptyList(),
                chipSuffix = " dB",
                onToggle = { audioManager.setLoudnessBoostEnabled(it) },
                onLevelChange = { audioManager.setLoudnessBoostDb(it) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Section 5: Per-Device Profiles & Controls (Migrated Precise Sliders + Device Profile Manager)
        Text(
            text = stringResource(R.string.category_device_management),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 8.dp)
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            // Precise Sliders Toggle (migrated cleanly from About)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPreciseSlidersChange(!preciseSliders) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(
                            if (preciseSliders) {
                                MiuixTheme.colorScheme.primary.copy(alpha = 0.16f)
                            } else {
                                MiuixTheme.colorScheme.surfaceContainerHigh
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = MiuixIcons.Tune,
                        contentDescription = stringResource(R.string.precise_sliders_title),
                        tint = if (preciseSliders) {
                            MiuixTheme.colorScheme.primary
                        } else {
                            MiuixTheme.colorScheme.onSurfaceVariantSummary
                        },
                        modifier = Modifier.size(19.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.precise_sliders_title),
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.precise_sliders_summary),
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = preciseSliders,
                    onCheckedChange = onPreciseSlidersChange
                )
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // Reset Equalizer Entry (migrated cleanly from Fine Tune)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { audioManager.resetEqualizer() }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MiuixTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = MiuixIcons.Refresh,
                        contentDescription = stringResource(R.string.reset_equalizer_title),
                        tint = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.reset_equalizer_title),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.reset_equalizer_summary),
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MiuixTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "0.0 dB",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // Saved Device Profiles Entry
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDeviceProfilesDialog = true }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_speaker),
                        contentDescription = stringResource(R.string.delete_device_profiles_title),
                        tint = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.delete_device_profiles_title),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.delete_device_profiles_summary),
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MiuixTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${savedSnapshots.size} Saved",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MiuixTheme.colorScheme.primary
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // Restore Defaults Entry
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showRestoreConfirmDialog = true }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE53935).copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = MiuixIcons.Delete,
                        contentDescription = stringResource(R.string.reset_dolby_title),
                        tint = Color(0xFFE53935),
                        modifier = Modifier.size(19.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.reset_dolby_title),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.reset_dolby_summary),
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Icon(
                    imageVector = MiuixIcons.ChevronForward,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(110.dp))
    }

    if (showDeviceProfilesDialog) {
        SavedDeviceProfilesDialog(
            snapshots = savedSnapshots,
            activeDeviceKey = activeDeviceKey,
            onDismiss = { showDeviceProfilesDialog = false },
            onDeleteDevice = { audioManager.deleteSavedDeviceProfile(it) },
            onDeleteAll = { audioManager.deleteAllSavedDeviceProfiles() }
        )
    }

    if (showRestoreConfirmDialog) {
        RestoreDefaultsConfirmDialog(
            snapshots = savedSnapshots,
            activeDeviceKey = activeDeviceKey,
            activeDeviceName = connectedDevice,
            onDismiss = { showRestoreConfirmDialog = false },
            onOpenSavedProfiles = {
                showRestoreConfirmDialog = false
                showDeviceProfilesDialog = true
            },
            onConfirmResetDevice = { key ->
                audioManager.resetDeviceDefaults(key)
                showRestoreConfirmDialog = false
            },
            onConfirmResetAll = {
                audioManager.resetAllDevicesDefaults()
                showRestoreConfirmDialog = false
            }
        )
    }
}
