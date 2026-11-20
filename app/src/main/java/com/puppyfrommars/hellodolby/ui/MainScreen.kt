/*
 * SPDX-FileCopyrightText: 2026 marcmyworld <moviesminister@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package com.puppyfrommars.hellodolby.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.puppyfrommars.hellodolby.R
import com.puppyfrommars.hellodolby.audio.AudioEffectManager
import com.puppyfrommars.hellodolby.audio.DolbyProfile
import com.puppyfrommars.hellodolby.audio.EffectMode
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
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
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Theme
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class DolbyPresetBanner(
    val profile: DolbyProfile,
    val titleRes: Int,
    val subRes: Int,
    val bannerRes: Int
)

private val DOLBY_PRESET_BANNERS = listOf(
    DolbyPresetBanner(
        DolbyProfile.DYNAMIC,
        R.string.profile_dynamic,
        R.string.profile_dynamic_summary,
        R.drawable.preset_banner_dynamic
    ),
    DolbyPresetBanner(
        DolbyProfile.MOVIE,
        R.string.profile_movie,
        R.string.profile_movie_summary,
        R.drawable.preset_banner_movie
    ),
    DolbyPresetBanner(
        DolbyProfile.MUSIC,
        R.string.profile_music,
        R.string.profile_music_summary,
        R.drawable.preset_banner_music
    ),
    DolbyPresetBanner(
        DolbyProfile.GAME,
        R.string.profile_game,
        R.string.profile_game_summary,
        R.drawable.preset_banner_game
    ),
    DolbyPresetBanner(
        DolbyProfile.VOICE,
        R.string.profile_voice,
        R.string.profile_voice_summary,
        R.drawable.preset_banner_voice
    ),
    DolbyPresetBanner(
        DolbyProfile.CUSTOM,
        R.string.profile_custom,
        R.string.profile_custom_summary,
        R.drawable.preset_banner_custom
    )
)

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
                    ScreenTab.EFFECTS -> stringResource(R.string.tab_audio_engine)
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
                    label = stringResource(R.string.tab_audio_engine)
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
    val coroutineScope = rememberCoroutineScope()
    val effectMode by audioManager.effectModeState.collectAsState()
    var dolbyProfile by remember { mutableStateOf(audioManager.getDolbyProfile()) }
    val spatialEnabled by audioManager.spatialAudioState.collectAsState()
    var surroundEnabled by remember { mutableStateOf(audioManager.isSurroundEnabled()) }
    var surroundStrength by remember { mutableFloatStateOf(audioManager.getSurroundStrength() / 10f) }

    val initialPageIndex = DOLBY_PRESET_BANNERS.indexOfFirst { it.profile == dolbyProfile }.coerceAtLeast(0)
    val pagerState = rememberPagerState(
        initialPage = initialPageIndex,
        pageCount = { DOLBY_PRESET_BANNERS.size }
    )

    // Sync pager settling to audio profile
    LaunchedEffect(pagerState.settledPage) {
        val settledProfile = DOLBY_PRESET_BANNERS[pagerState.settledPage].profile
        if (dolbyProfile != settledProfile) {
            dolbyProfile = settledProfile
            audioManager.setDolbyProfile(settledProfile)
        }
    }

    // Sync external or radio button selection to pager position
    LaunchedEffect(dolbyProfile) {
        val targetIdx = DOLBY_PRESET_BANNERS.indexOfFirst { it.profile == dolbyProfile }
        if (targetIdx >= 0 && pagerState.currentPage != targetIdx) {
            pagerState.animateScrollToPage(targetIdx)
        }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Section 0: 16:9 Audio Engine Banner with Animated Crossfade
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(22.dp))
        ) {
            AnimatedContent(
                targetState = effectMode,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(350)) + scaleIn(initialScale = 0.96f, animationSpec = tween(350)))
                        .togetherWith(
                            fadeOut(animationSpec = tween(280)) + scaleOut(targetScale = 0.96f, animationSpec = tween(280))
                        )
                },
                label = "EngineBannerTransition"
            ) { mode ->
                val bannerDrawable = when (mode) {
                    EffectMode.DOLBY -> R.drawable.banner_engine_dolby
                    EffectMode.MISOUND -> R.drawable.banner_engine_misound
                    EffectMode.OFF -> R.drawable.banner_engine_off
                }
                val title = when (mode) {
                    EffectMode.DOLBY -> stringResource(R.string.engine_banner_dolby_title)
                    EffectMode.MISOUND -> stringResource(R.string.engine_banner_misound_title)
                    EffectMode.OFF -> stringResource(R.string.engine_banner_off_title)
                }
                val subtitle = when (mode) {
                    EffectMode.DOLBY -> stringResource(R.string.engine_banner_dolby_sub)
                    EffectMode.MISOUND -> stringResource(R.string.engine_banner_misound_sub)
                    EffectMode.OFF -> stringResource(R.string.engine_banner_off_sub)
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    Image(
                        painter = painterResource(bannerDrawable),
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.15f),
                                        Color.Black.copy(alpha = 0.85f)
                                    )
                                )
                            )
                    )

                    // Active Engine Badge Chip at top-left
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(14.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                when (mode) {
                                    EffectMode.DOLBY -> Color(0xFF00C853).copy(alpha = 0.9f)
                                    EffectMode.MISOUND -> Color(0xFFFFB300).copy(alpha = 0.9f)
                                    EffectMode.OFF -> Color(0xFF757575).copy(alpha = 0.9f)
                                }
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.active_engine_badge),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 0.8.sp
                        )
                    }

                    // Engine Title & Subtitle at bottom-left
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp)
                    ) {
                        Text(
                            text = title,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = subtitle,
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.85f),
                            maxLines = 2
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Section 1: Effect Engine Selection
        Text(
            text = stringResource(R.string.category_processing),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 8.dp)
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

        // Section 2: Dolby Audio Presets with Spotlight Carousel & Full Profile List
        if (effectMode == EffectMode.DOLBY) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.category_dolby_preset),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp)
            )

            // Spotlight Pager Carousel: Center item is in spotlight & larger; swipe snaps to center
            HorizontalPager(
                state = pagerState,
                contentPadding = PaddingValues(horizontal = 48.dp),
                pageSpacing = 14.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(145.dp)
            ) { page ->
                val banner = DOLBY_PRESET_BANNERS[page]
                val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue
                val scale = 1f - (pageOffset * 0.12f).coerceIn(0f, 0.12f)
                val alpha = 1f - (pageOffset * 0.40f).coerceIn(0f, 0.40f)
                val isCenter = pageOffset < 0.5f

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(135.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            this.alpha = alpha
                        }
                        .clip(RoundedCornerShape(22.dp))
                        .border(
                            width = if (isCenter) 2.5.dp else 1.dp,
                            color = if (isCenter) MiuixTheme.colorScheme.primary else Color.White.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(22.dp)
                        )
                        .clickable {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(page)
                            }
                            dolbyProfile = banner.profile
                            audioManager.setDolbyProfile(banner.profile)
                        }
                ) {
                    Image(
                        painter = painterResource(banner.bannerRes),
                        contentDescription = stringResource(banner.titleRes),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.15f),
                                        Color.Black.copy(alpha = 0.85f)
                                    )
                                )
                            )
                    )

                    // Center Spotlight Active Check Badge
                    if (isCenter) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp)
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(MiuixTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Ok,
                                contentDescription = "Selected",
                                tint = MiuixTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Preset Title & Summary
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = stringResource(banner.titleRes),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(banner.subRes),
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.85f),
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 2-Column Preset Grid without tick space on the left
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val chunkedPresets = DOLBY_PRESET_BANNERS.chunked(2)
                chunkedPresets.forEach { rowPresets ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowPresets.forEach { banner ->
                            val targetIndex = DOLBY_PRESET_BANNERS.indexOf(banner)
                            val isSelected = dolbyProfile == banner.profile
                            val animatedBgColor by animateColorAsState(
                                targetValue = if (isSelected) {
                                    MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)
                                } else {
                                    MiuixTheme.colorScheme.surfaceContainerHigh
                                },
                                animationSpec = tween(220),
                                label = "PresetGridBg_${banner.profile.name}"
                            )
                            val animatedBorderColor by animateColorAsState(
                                targetValue = if (isSelected) {
                                    MiuixTheme.colorScheme.primary
                                } else {
                                    MiuixTheme.colorScheme.outline.copy(alpha = 0.10f)
                                },
                                animationSpec = tween(220),
                                label = "PresetGridBorder_${banner.profile.name}"
                            )

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 64.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(animatedBgColor)
                                    .border(
                                        width = if (isSelected) 1.8.dp else 1.dp,
                                        color = animatedBorderColor,
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .clickable {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(targetIndex)
                                        }
                                        dolbyProfile = banner.profile
                                        audioManager.setDolbyProfile(banner.profile)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = stringResource(banner.titleRes),
                                            fontSize = 15.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                            color = if (isSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (isSelected) {
                                            Box(
                                                modifier = Modifier
                                                    .size(18.dp)
                                                    .clip(CircleShape)
                                                    .background(MiuixTheme.colorScheme.primary),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = MiuixIcons.Ok,
                                                    contentDescription = "Selected",
                                                    tint = MiuixTheme.colorScheme.onPrimary,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = stringResource(banner.subRes),
                                        fontSize = 11.sp,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
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
