/*
 * SPDX-FileCopyrightText: 2026 marcmyworld <moviesminister@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package com.puppyfrommars.hellodolby.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.puppyfrommars.hellodolby.R
import com.puppyfrommars.hellodolby.audio.AudioEffectManager
import com.puppyfrommars.hellodolby.audio.DolbyProfile
import com.puppyfrommars.hellodolby.audio.EffectMode
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ToolbarPosition
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Music
import top.yukonga.miuix.kmp.icon.extended.Theme
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

enum class ScreenTab {
    EFFECTS,
    EQUALIZER,
    CONTRIBUTORS
}

@Composable
fun MainScreen(
    audioManager: AudioEffectManager,
    themeMode: AppThemeMode,
    onThemeChange: (AppThemeMode) -> Unit
) {
    var currentTab by rememberSaveable { mutableStateOf(ScreenTab.EFFECTS) }
    var rotationTarget by rememberSaveable { mutableFloatStateOf(0f) }
    val rotation by animateFloatAsState(
        targetValue = rotationTarget,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "ThemeRotation"
    )

    val handleThemeChange: (AppThemeMode) -> Unit = { newMode ->
        rotationTarget += 180f
        onThemeChange(newMode)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = when (currentTab) {
                    ScreenTab.EFFECTS -> stringResource(R.string.app_name)
                    ScreenTab.EQUALIZER -> stringResource(R.string.tab_fine_tune)
                    ScreenTab.CONTRIBUTORS -> stringResource(R.string.tab_about)
                },
                actions = {
                    IconButton(
                        onClick = {
                            handleThemeChange(themeMode.next())
                        }
                    ) {
                        AnimatedContent(
                            targetState = themeMode,
                            transitionSpec = {
                                (fadeIn(animationSpec = tween(220)) + scaleIn(initialScale = 0.6f, animationSpec = tween(220)))
                                    .togetherWith(
                                        fadeOut(animationSpec = tween(180)) + scaleOut(targetScale = 0.6f, animationSpec = tween(180))
                                    )
                            },
                            label = "ThemeIconAnimation"
                        ) { mode ->
                            val iconRes = when (mode) {
                                AppThemeMode.LIGHT -> R.drawable.ic_theme_light
                                AppThemeMode.DARK -> R.drawable.ic_theme_dark
                                AppThemeMode.SYSTEM -> R.drawable.ic_theme_auto
                            }
                            val desc = when (mode) {
                                AppThemeMode.LIGHT -> stringResource(R.string.theme_mode_light)
                                AppThemeMode.DARK -> stringResource(R.string.theme_mode_dark)
                                AppThemeMode.SYSTEM -> stringResource(R.string.theme_mode_system)
                            }
                            Icon(
                                painter = painterResource(iconRes),
                                contentDescription = desc,
                                tint = MiuixTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .size(24.dp)
                                    .rotate(rotation)
                            )
                        }
                    }
                }
            )
        },
        floatingToolbar = {
            FloatingNavigationBar(
                color = MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.82f),
                shadowElevation = 12.dp,
                cornerRadius = 32.dp,
                horizontalOutSidePadding = 24.dp,
                showDivider = false
            ) {
                FloatingNavigationBarItem(
                    selected = currentTab == ScreenTab.EFFECTS,
                    onClick = { currentTab = ScreenTab.EFFECTS },
                    icon = MiuixIcons.Music,
                    label = stringResource(R.string.tab_effects)
                )
                FloatingNavigationBarItem(
                    selected = currentTab == ScreenTab.EQUALIZER,
                    onClick = { currentTab = ScreenTab.EQUALIZER },
                    icon = MiuixIcons.Tune,
                    label = stringResource(R.string.tab_fine_tune)
                )
                FloatingNavigationBarItem(
                    selected = currentTab == ScreenTab.CONTRIBUTORS,
                    onClick = { currentTab = ScreenTab.CONTRIBUTORS },
                    icon = MiuixIcons.Info,
                    label = stringResource(R.string.tab_about)
                )
            }
        },
        floatingToolbarPosition = ToolbarPosition.BottomCenter
    ) { padding ->
        when (currentTab) {
            ScreenTab.EFFECTS -> EffectsTab(audioManager, padding)
            ScreenTab.EQUALIZER -> EqualizerScreen(audioManager, padding)
            ScreenTab.CONTRIBUTORS -> ContributorsScreen(padding, themeMode, handleThemeChange)
        }
    }
}

@Composable
fun EffectsTab(audioManager: AudioEffectManager, padding: PaddingValues) {
    val effectMode by audioManager.effectModeState.collectAsState()
    var dolbyProfile by remember { mutableStateOf(audioManager.getDolbyProfile()) }
    val spatialEnabled by audioManager.spatialAudioState.collectAsState()
    var surroundEnabled by remember { mutableStateOf(audioManager.isSurroundEnabled()) }
    var surroundStrength by remember { mutableFloatStateOf(audioManager.getSurroundStrength() / 10f) }

    val scrollState = rememberScrollState()

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
                    audioManager.setEffectMode(EffectMode.DOLBY)
                }
            )
            RadioButtonPreference(
                title = stringResource(R.string.xiaomi_sound),
                summary = stringResource(R.string.xiaomi_sound_summary),
                selected = effectMode == EffectMode.MISOUND,
                onClick = {
                    audioManager.setEffectMode(EffectMode.MISOUND)
                }
            )
            RadioButtonPreference(
                title = stringResource(R.string.effect_off),
                summary = stringResource(R.string.effect_off_summary),
                selected = effectMode == EffectMode.OFF,
                onClick = {
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

        // Extra clearance for floating bottom bar
        Spacer(modifier = Modifier.height(100.dp))
    }
}
