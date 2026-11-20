/*
 * SPDX-FileCopyrightText: 2026 marcmyworld <moviesminister@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package com.puppyfrommars.hellodolby.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.puppyfrommars.hellodolby.R
import com.puppyfrommars.hellodolby.audio.AudioEffectManager
import com.puppyfrommars.hellodolby.audio.EffectMode
import com.puppyfrommars.hellodolby.data.autoeq.AutoEqIndexEntry
import com.puppyfrommars.hellodolby.data.autoeq.AutoEqRepository
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val BAND_LABELS = listOf(
    "32 Hz", "64 Hz", "125 Hz", "250 Hz", "500 Hz",
    "1 kHz", "2 kHz", "4 kHz", "8 kHz", "16 kHz"
)

private val BAND_SHORT_LABELS = listOf(
    "32", "64", "125", "250", "500",
    "1k", "2k", "4k", "8k", "16k"
)

data class PresetInfo(
    val name: String,
    val subtitle: String,
    val drawableRes: Int
)

private val PRESET_CARDS = listOf(
    PresetInfo("Flat", "Studio reference balance", R.drawable.preset_bg_flat),
    PresetInfo("Rock", "Punchy kick, boosted highs", R.drawable.preset_bg_rock),
    PresetInfo("Jazz", "Smooth brass & warm mids", R.drawable.preset_bg_jazz),
    PresetInfo("Pop", "Vibrant vocals & synth beat", R.drawable.preset_bg_pop),
    PresetInfo("Classical", "Spacious orchestral strings", R.drawable.preset_bg_classical),
    PresetInfo("Hip Hop", "Deep sub-bass & crisp snare", R.drawable.preset_bg_hiphop),
    PresetInfo("Electronic", "Tight EDM low end & drive", R.drawable.preset_bg_electronic),
    PresetInfo("Vocal Boost", "Forward dialogue presence", R.drawable.preset_bg_vocal),
    PresetInfo("Bass Boost", "Deep visceral sub-bass impact", R.drawable.preset_bg_bass)
)

enum class EqViewMode {
    SLIDERS,
    LIVE_GRAPH
}

@Composable
fun EqualizerScreen(
    audioManager: AudioEffectManager,
    padding: PaddingValues
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val connectedDevice by audioManager.connectedDeviceState.collectAsState()
    val effectMode by audioManager.effectModeState.collectAsState()
    val equalizerEnabled by audioManager.equalizerEnabledState.collectAsState()
    val bandGains by audioManager.equalizerBandsState.collectAsState()
    val activePreset by audioManager.equalizerPresetState.collectAsState()
    val customPresets by audioManager.customPresetsState.collectAsState()
    val autoEqProfileName by audioManager.autoEqProfileNameState.collectAsState()

    val isDolbyActive = effectMode == EffectMode.DOLBY
    val isInteractive = equalizerEnabled && isDolbyActive

    var viewMode by remember { mutableStateOf(EqViewMode.SLIDERS) }
    var viewModeRotationTarget by rememberSaveable { mutableFloatStateOf(0f) }
    val viewModeRotation by animateFloatAsState(
        targetValue = viewModeRotationTarget,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "EqViewModeRotation"
    )

    var showAutoEqDialog by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showManageDialog by remember { mutableStateOf(false) }

    val autoEqRepository = remember { AutoEqRepository(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Connected Device Badge Card (Multi-Device State Memory)
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (connectedDevice.contains("Speaker", ignoreCase = true)) "🔊" else "🎧",
                        fontSize = 18.sp
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = connectedDevice,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Device-specific profile memory active",
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Master Equalizer Switch Card
        Text(
            text = stringResource(R.string.equalizer_title),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 6.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            SwitchPreference(
                title = stringResource(R.string.equalizer_enable),
                summary = stringResource(R.string.equalizer_enable_summary),
                checked = equalizerEnabled && isDolbyActive,
                onCheckedChange = { audioManager.setEqualizerEnabled(it) },
                enabled = isDolbyActive
            )
        }

        // Dolby Engine Dependency Warning Card
        if (!isDolbyActive) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFF9800).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "!",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFFFF9800)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.equalizer_dolby_required_warning),
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MiuixTheme.colorScheme.primary)
                                .clickable {
                                    audioManager.setEffectMode(EffectMode.DOLBY)
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.switch_to_dolby),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MiuixTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // All Equalizer Features: Always visible, greyed out and inoperable when disabled
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (isInteractive) 1f else 0.38f)
        ) {
            // Presets Selection Header with Save and Manage Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.category_presets),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Save Current Preset Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(MiuixTheme.colorScheme.surfaceContainerHigh)
                            .clickable(enabled = isInteractive) { showSaveDialog = true }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.save_preset),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isInteractive) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.outline
                        )
                    }

                    // Manage Presets Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(MiuixTheme.colorScheme.surfaceContainerHigh)
                            .clickable(enabled = isInteractive) { showManageDialog = true }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.manage_presets),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isInteractive) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.outline
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Presets Carousel: Built-in Image Cards + User Custom Cards
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Built-in presets
                items(PRESET_CARDS) { preset ->
                    val isSelected = activePreset == preset.name
                    Box(
                        modifier = Modifier
                            .width(148.dp)
                            .height(96.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .border(
                                width = if (isSelected) 2.5.dp else 1.dp,
                                color = if (isSelected) MiuixTheme.colorScheme.primary else Color.Transparent,
                                shape = RoundedCornerShape(18.dp)
                            )
                            .clickable(enabled = isInteractive) {
                                audioManager.setEqualizerPreset(preset.name)
                            }
                    ) {
                        Image(
                            painter = painterResource(preset.drawableRes),
                            contentDescription = preset.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Dark gradient overlay for text readability
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Black.copy(alpha = 0.15f),
                                            Color.Black.copy(alpha = 0.78f)
                                        )
                                    )
                                )
                        )

                        // Selection Check Badge
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(MiuixTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.Ok,
                                    contentDescription = "Selected",
                                    tint = MiuixTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        // Preset Label and Subtitle
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = preset.name,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(1.dp))
                            Text(
                                text = preset.subtitle,
                                fontSize = 10.sp,
                                color = Color.White.copy(alpha = 0.85f),
                                maxLines = 1
                            )
                        }
                    }
                }

                // Custom user presets
                items(customPresets.keys.toList()) { customName ->
                    val isSelected = activePreset == customName
                    Box(
                        modifier = Modifier
                            .width(148.dp)
                            .height(96.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .border(
                                width = if (isSelected) 2.5.dp else 1.dp,
                                color = if (isSelected) MiuixTheme.colorScheme.primary else Color.Transparent,
                                shape = RoundedCornerShape(18.dp)
                            )
                            .clickable(enabled = isInteractive) {
                                audioManager.setEqualizerPreset(customName)
                            }
                    ) {
                        Image(
                            painter = painterResource(R.drawable.preset_bg_flat),
                            contentDescription = customName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            MiuixTheme.colorScheme.primary.copy(alpha = 0.25f),
                                            Color.Black.copy(alpha = 0.85f)
                                        )
                                    )
                                )
                        )

                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(MiuixTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.Ok,
                                    contentDescription = "Selected",
                                    tint = MiuixTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = customName,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(1.dp))
                            Text(
                                text = "Custom Preset",
                                fontSize = 10.sp,
                                color = MiuixTheme.colorScheme.primary,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Frequency Response Section with Single Animated Rotating Switcher Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.category_bands),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )

                // Single Animated Button with rotating/scaling changing icon
                IconButton(
                    onClick = {
                        if (isInteractive) {
                            viewModeRotationTarget += 180f
                            viewMode = if (viewMode == EqViewMode.SLIDERS) EqViewMode.LIVE_GRAPH else EqViewMode.SLIDERS
                        }
                    },
                    enabled = isInteractive
                ) {
                    AnimatedContent(
                        targetState = viewMode,
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(220)) + scaleIn(initialScale = 0.6f, animationSpec = tween(220)))
                                .togetherWith(
                                    fadeOut(animationSpec = tween(180)) + scaleOut(targetScale = 0.6f, animationSpec = tween(180))
                                )
                        },
                        label = "EqModeIconAnimation"
                    ) { mode ->
                        val iconRes = when (mode) {
                            EqViewMode.SLIDERS -> R.drawable.ic_eq_graph
                            EqViewMode.LIVE_GRAPH -> R.drawable.ic_eq_sliders
                        }
                        Icon(
                            painter = painterResource(iconRes),
                            contentDescription = stringResource(R.string.switch_eq_view_mode),
                            tint = if (isInteractive) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.outline,
                            modifier = Modifier
                                .size(24.dp)
                                .rotate(viewModeRotation)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    if (viewMode == EqViewMode.SLIDERS) {
                        // 10 Vertical Sliders side-by-side: Bass (left) to Treble (right)
                        VerticalEqualizerView(
                            bandGains = bandGains,
                            onGainChange = { index, newGain ->
                                if (isInteractive) {
                                    audioManager.setEqualizerBand(index, newGain)
                                }
                            },
                            enabled = isInteractive
                        )
                    } else {
                        // Live Frequency Response Curve (like hardware_dolby)
                        InteractiveLiveEqGraph(
                            bandGains = bandGains,
                            onBandGainChange = { index, newGain ->
                                if (isInteractive) {
                                    audioManager.setEqualizerBand(index, newGain)
                                }
                            },
                            enabled = isInteractive
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Auto Equalizer Section (Online Profiles)
            Text(
                text = stringResource(R.string.category_autoeq),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 6.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                if (autoEqProfileName != null) {
                    ArrowPreference(
                        title = stringResource(R.string.autoeq_active_label),
                        summary = autoEqProfileName ?: "",
                        onClick = { if (isInteractive) showAutoEqDialog = true }
                    )
                }

                ArrowPreference(
                    title = stringResource(R.string.autoeq_search_button),
                    summary = stringResource(R.string.autoeq_search_summary),
                    onClick = { if (isInteractive) showAutoEqDialog = true }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Dedicated Reset Section - Always visible and active at the bottom
        Text(
            text = stringResource(R.string.category_reset),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 6.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            ArrowPreference(
                title = stringResource(R.string.reset_equalizer_title),
                summary = stringResource(R.string.reset_equalizer_summary),
                onClick = { audioManager.resetEqualizer() }
            )

            ArrowPreference(
                title = stringResource(R.string.reset_dolby_title),
                summary = stringResource(R.string.reset_dolby_summary),
                onClick = { audioManager.resetDolbyDsp() }
            )
        }

        // Extra clearance for floating bottom bar
        Spacer(modifier = Modifier.height(110.dp))
    }

    if (showAutoEqDialog) {
        AutoEqSearchDialog(
            repository = autoEqRepository,
            onDismiss = { showAutoEqDialog = false },
            onProfileSelected = { entry ->
                coroutineScope.launch {
                    val profile = autoEqRepository.fetchProfile(entry.id)
                    if (profile != null) {
                        val bands = autoEqRepository.parseGraphicEqTo10Bands(profile.graphicEq)
                        audioManager.setAutoEqProfile(entry.name, bands)
                    }
                    showAutoEqDialog = false
                }
            }
        )
    }

    if (showSaveDialog) {
        SaveCustomPresetDialog(
            onDismiss = { showSaveDialog = false },
            onSave = { name ->
                audioManager.saveCustomPreset(name, bandGains)
                showSaveDialog = false
            }
        )
    }

    if (showManageDialog) {
        ManageCustomPresetsDialog(
            customPresets = customPresets,
            activePreset = activePreset,
            onDismiss = { showManageDialog = false },
            onApply = { name ->
                audioManager.setEqualizerPreset(name)
                showManageDialog = false
            },
            onRename = { oldName, newName ->
                audioManager.renameCustomPreset(oldName, newName)
            },
            onDelete = { name ->
                audioManager.deleteCustomPreset(name)
            }
        )
    }
}

/**
 * 10 Vertical Equalizer Sliders arranged side-by-side:
 * Bass on the left (32 Hz) -> Treble on the right (16 kHz)
 */
@Composable
fun VerticalEqualizerView(
    bandGains: List<Float>,
    onGainChange: (index: Int, gainDb: Float) -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(210.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BAND_SHORT_LABELS.forEachIndexed { index, label ->
            val gain = bandGains.getOrElse(index) { 0f }
            VerticalSliderColumn(
                frequencyLabel = label,
                gainDb = gain,
                onGainChange = { newGain -> onGainChange(index, newGain) },
                enabled = enabled,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun VerticalSliderColumn(
    frequencyLabel: String,
    gainDb: Float,
    onGainChange: (Float) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val primaryColor = MiuixTheme.colorScheme.primary
    val trackBgColor = MiuixTheme.colorScheme.surfaceContainerHigh
    val textColor = MiuixTheme.colorScheme.onSurface
    val subTextColor = MiuixTheme.colorScheme.onSurfaceVariantSummary

    Column(
        modifier = modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Gain display text at top
        val gainStr = if (gainDb > 0f) "+%.1f".format(gainDb)
        else if (gainDb == 0f) "0"
        else "%.1f".format(gainDb)

        Text(
            text = gainStr,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = if (gainDb != 0f) primaryColor else subTextColor,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Vertical Slider Track with conditional touch input
        val touchModifier = if (enabled) {
            Modifier
                .pointerInput(Unit) {
                    detectVerticalDragGestures { change, _ ->
                        val totalH = size.height.toFloat()
                        if (totalH > 0) {
                            val centerY = totalH / 2f
                            val y = change.position.y.coerceIn(0f, totalH)
                            val normalized = (centerY - y) / (totalH / 2f)
                            val clampedDb = (normalized * 10f).coerceIn(-10f, 10f)
                            val rounded = Math.round(clampedDb * 10f) / 10f
                            onGainChange(rounded)
                        }
                        change.consume()
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val totalH = size.height.toFloat()
                        if (totalH > 0) {
                            val centerY = totalH / 2f
                            val y = offset.y.coerceIn(0f, totalH)
                            val normalized = (centerY - y) / (totalH / 2f)
                            val clampedDb = (normalized * 10f).coerceIn(-10f, 10f)
                            val rounded = Math.round(clampedDb * 10f) / 10f
                            onGainChange(rounded)
                        }
                    }
                }
        } else Modifier

        Box(
            modifier = Modifier
                .width(28.dp)
                .weight(1f)
                .then(touchModifier),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val cy = h / 2f
                val trackWidth = 5.dp.toPx()
                val trackRadius = trackWidth / 2f

                // Background slot
                drawRoundRect(
                    color = trackBgColor,
                    topLeft = Offset((w - trackWidth) / 2f, 0f),
                    size = androidx.compose.ui.geometry.Size(trackWidth, h),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackRadius, trackRadius)
                )

                // 0 dB center reference notch
                drawLine(
                    color = subTextColor.copy(alpha = 0.4f),
                    start = Offset((w - trackWidth * 2.2f) / 2f, cy),
                    end = Offset((w + trackWidth * 2.2f) / 2f, cy),
                    strokeWidth = 2.dp.toPx()
                )

                // Active level indicator
                val thumbY = cy - (gainDb / 10f) * (cy * 0.9f)
                if (gainDb > 0f) {
                    drawRoundRect(
                        color = primaryColor,
                        topLeft = Offset((w - trackWidth) / 2f, thumbY),
                        size = androidx.compose.ui.geometry.Size(trackWidth, cy - thumbY),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackRadius, trackRadius)
                    )
                } else if (gainDb < 0f) {
                    drawRoundRect(
                        color = primaryColor.copy(alpha = 0.75f),
                        topLeft = Offset((w - trackWidth) / 2f, cy),
                        size = androidx.compose.ui.geometry.Size(trackWidth, thumbY - cy),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackRadius, trackRadius)
                    )
                }

                // Tactile thumb handle
                drawCircle(
                    color = primaryColor,
                    radius = 8.dp.toPx(),
                    center = Offset(w / 2f, thumbY)
                )
                drawCircle(
                    color = Color.White,
                    radius = 3.5.dp.toPx(),
                    center = Offset(w / 2f, thumbY)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Frequency label at bottom
        Text(
            text = frequencyLabel,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = textColor,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Interactive Live Equalizer Frequency Response Curve (like hardware_dolby)
 */
@Composable
fun InteractiveLiveEqGraph(
    bandGains: List<Float>,
    onBandGainChange: (index: Int, gainDb: Float) -> Unit,
    enabled: Boolean = true
) {
    val primaryColor = MiuixTheme.colorScheme.primary
    val subTextColor = MiuixTheme.colorScheme.onSurfaceVariantSummary
    val trackBgColor = MiuixTheme.colorScheme.surfaceContainerHigh

    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var localGains by remember { mutableStateOf(bandGains) }

    LaunchedEffect(bandGains) {
        if (draggedIndex == null) {
            localGains = bandGains
        }
    }

    val dragModifier = if (enabled) {
        Modifier.pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { offset ->
                    val width = size.width
                    val stepX = (width - 48f) / 9f
                    var closest = -1
                    var minDist = Float.MAX_VALUE
                    for (i in 0 until 10) {
                        val x = 24f + i * stepX
                        val dist = kotlin.math.abs(offset.x - x)
                        if (dist < minDist && dist < 60f) {
                            minDist = dist
                            closest = i
                        }
                    }
                    draggedIndex = closest
                },
                onDrag = { change, _ ->
                    draggedIndex?.let { index ->
                        val height = size.height
                        val cy = height / 2f
                        val y = change.position.y.coerceIn(10f, height - 10f)
                        val normalized = (cy - y) / (cy * 0.85f)
                        val newDb = (normalized * 10f).coerceIn(-10f, 10f)
                        val rounded = Math.round(newDb * 10f) / 10f
                        localGains = localGains.toMutableList().apply {
                            this[index] = rounded
                        }
                        onBandGainChange(index, rounded)
                        change.consume()
                    }
                },
                onDragEnd = { draggedIndex = null },
                onDragCancel = { draggedIndex = null }
            )
        }
    } else Modifier

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(210.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(trackBgColor.copy(alpha = 0.5f))
                .then(dragModifier)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                val cy = height / 2f
                val stepX = (width - 48f) / 9f

                // Horizontal Grid lines (+10 dB, +5 dB, 0 dB, -5 dB, -10 dB)
                for (db in listOf(10f, 5f, 0f, -5f, -10f)) {
                    val y = cy - (db / 10f) * (cy * 0.85f)
                    drawLine(
                        color = if (db == 0f) primaryColor.copy(alpha = 0.4f) else subTextColor.copy(alpha = 0.15f),
                        start = Offset(0f, y),
                        end = Offset(width, y),
                        strokeWidth = if (db == 0f) 2f else 1f
                    )
                }

                // Vertical Grid lines
                for (i in 0 until 10) {
                    val x = 24f + i * stepX
                    drawLine(
                        color = subTextColor.copy(alpha = 0.1f),
                        start = Offset(x, 0f),
                        end = Offset(x, height),
                        strokeWidth = 1f
                    )
                }

                // Spline curve
                if (localGains.size == 10) {
                    val path = Path()
                    val points = localGains.mapIndexed { index, gain ->
                        val x = 24f + index * stepX
                        val y = cy - (gain / 10f) * (cy * 0.85f)
                        Offset(x, y)
                    }

                    path.moveTo(points[0].x, points[0].y)
                    for (i in 1 until points.size) {
                        val prev = points[i - 1]
                        val curr = points[i]
                        val cpX1 = prev.x + stepX * 0.45f
                        val cpY1 = prev.y
                        val cpX2 = curr.x - stepX * 0.45f
                        val cpY2 = curr.y
                        path.cubicTo(cpX1, cpY1, cpX2, cpY2, curr.x, curr.y)
                    }

                    // Shaded gradient under curve
                    val fillPath = Path().apply {
                        addPath(path)
                        lineTo(points.last().x, height)
                        lineTo(points.first().x, height)
                        close()
                    }

                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                primaryColor.copy(alpha = 0.35f),
                                primaryColor.copy(alpha = 0.02f)
                            )
                        )
                    )

                    // Draw main curve stroke
                    drawPath(
                        path = path,
                        color = primaryColor,
                        style = Stroke(width = 3.5.dp.toPx())
                    )

                    // Control Points
                    points.forEachIndexed { i, pt ->
                        val isDragged = draggedIndex == i
                        if (isDragged) {
                            drawCircle(
                                color = primaryColor.copy(alpha = 0.3f),
                                radius = 18f,
                                center = pt
                            )
                        }
                        drawCircle(
                            color = primaryColor,
                            radius = if (isDragged) 10f else 7f,
                            center = pt
                        )
                        drawCircle(
                            color = Color.White,
                            radius = if (isDragged) 4f else 2.5f,
                            center = pt
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Frequency Labels along bottom
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            BAND_SHORT_LABELS.forEach { label ->
                Text(
                    text = label,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = subTextColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(26.dp)
                )
            }
        }
    }
}

/**
 * Dialog to Save Current 10-Band Gains as a New Custom Preset
 */
@Composable
fun SaveCustomPresetDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = stringResource(R.string.save_preset_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.save_preset_hint),
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MiuixTheme.colorScheme.onSurface,
                        unfocusedTextColor = MiuixTheme.colorScheme.onSurface,
                        focusedBorderColor = MiuixTheme.colorScheme.primary,
                        unfocusedBorderColor = MiuixTheme.colorScheme.outline,
                        cursorColor = MiuixTheme.colorScheme.primary
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Cancel",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onDismiss() }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "Save",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (name.isNotBlank()) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.outline,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = name.isNotBlank()) {
                                onSave(name.trim())
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

/**
 * Modal Dialog to Manage Custom Presets: Apply, Rename, Delete
 */
@Composable
fun ManageCustomPresetsDialog(
    customPresets: Map<String, List<Float>>,
    activePreset: String,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit
) {
    var renamingTarget by remember { mutableStateOf<String?>(null) }
    var renameInput by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                Text(
                    text = stringResource(R.string.manage_presets_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (renamingTarget != null) {
                    // Rename view
                    Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        Text(
                            text = "Rename \"$renamingTarget\":",
                            fontSize = 14.sp,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = renameInput,
                            onValueChange = { renameInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MiuixTheme.colorScheme.onSurface,
                                unfocusedTextColor = MiuixTheme.colorScheme.onSurface,
                                focusedBorderColor = MiuixTheme.colorScheme.primary,
                                unfocusedBorderColor = MiuixTheme.colorScheme.outline,
                                cursorColor = MiuixTheme.colorScheme.primary
                            ),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Cancel",
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier
                                    .clickable { renamingTarget = null }
                                    .padding(8.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Done",
                                fontWeight = FontWeight.Bold,
                                color = MiuixTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clickable(enabled = renameInput.isNotBlank()) {
                                        renamingTarget?.let { old ->
                                            onRename(old, renameInput.trim())
                                        }
                                        renamingTarget = null
                                    }
                                    .padding(8.dp)
                            )
                        }
                    }
                } else if (customPresets.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.no_custom_presets),
                            fontSize = 14.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(customPresets.keys.toList()) { name ->
                            val isSelected = activePreset == name
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MiuixTheme.colorScheme.surfaceContainerHigh)
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = name,
                                        fontSize = 15.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface
                                    )
                                    if (isSelected) {
                                        Text(
                                            text = "Active Preset",
                                            fontSize = 11.sp,
                                            color = MiuixTheme.colorScheme.primary
                                        )
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (!isSelected) {
                                        Text(
                                            text = "Apply",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MiuixTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .clickable { onApply(name) }
                                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                        )
                                    }

                                    Text(
                                        text = "Rename",
                                        fontSize = 12.sp,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable {
                                                renamingTarget = name
                                                renameInput = name
                                            }
                                            .padding(horizontal = 8.dp, vertical = 6.dp)
                                    )

                                    Text(
                                        text = "Delete",
                                        fontSize = 12.sp,
                                        color = Color(0xFFE53935),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable { onDelete(name) }
                                            .padding(horizontal = 8.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Text(
                        text = "Close",
                        color = MiuixTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onDismiss() }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AutoEqSearchDialog(
    repository: AutoEqRepository,
    onDismiss: () -> Unit,
    onProfileSelected: (AutoEqIndexEntry) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<AutoEqIndexEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isDownloadingProfile by remember { mutableStateOf(false) }

    LaunchedEffect(searchQuery) {
        isLoading = true
        results = repository.search(searchQuery)
        isLoading = false
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.autoeq_dialog_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.autoeq_search_hint),
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MiuixTheme.colorScheme.onSurface,
                        unfocusedTextColor = MiuixTheme.colorScheme.onSurface,
                        focusedBorderColor = MiuixTheme.colorScheme.primary,
                        unfocusedBorderColor = MiuixTheme.colorScheme.outline,
                        cursorColor = MiuixTheme.colorScheme.primary
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (isDownloadingProfile) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = MiuixTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Downloading & applying acoustic profile…",
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                fontSize = 13.sp
                            )
                        }
                    }
                } else if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = MiuixTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.autoeq_loading),
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                fontSize = 13.sp
                            )
                        }
                    }
                } else if (results.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.autoeq_no_results),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(results) { entry ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        isDownloadingProfile = true
                                        onProfileSelected(entry)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = entry.name,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MiuixTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${entry.source} • ${entry.measurementRig}",
                                    fontSize = 12.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Text(
                        text = "Cancel",
                        color = MiuixTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onDismiss() }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}
