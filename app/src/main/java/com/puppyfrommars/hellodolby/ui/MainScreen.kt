/*
 * SPDX-FileCopyrightText: 2026 marcmyworld <moviesminister@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package com.puppyfrommars.hellodolby.ui

import android.content.ComponentName
import android.content.Intent
import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.provider.Settings as AndroidSettings
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.puppyfrommars.hellodolby.R
import com.puppyfrommars.hellodolby.audio.AudioEffectManager
import com.puppyfrommars.hellodolby.audio.DolbyProfile
import com.puppyfrommars.hellodolby.audio.EffectMode
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarDefaults
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.ToolbarPosition
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Music
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class DolbyPresetBanner(
    val profile: DolbyProfile,
    val titleRes: Int,
    val subRes: Int,
    val bannerRes: Int,
    val iconRes: Int
)

private val DOLBY_PRESET_BANNERS = listOf(
    DolbyPresetBanner(
        DolbyProfile.DYNAMIC,
        R.string.profile_dynamic,
        R.string.profile_dynamic_summary,
        R.drawable.preset_banner_dynamic,
        R.drawable.ic_preset_dynamic
    ),
    DolbyPresetBanner(
        DolbyProfile.MOVIE,
        R.string.profile_movie,
        R.string.profile_movie_summary,
        R.drawable.preset_banner_movie,
        R.drawable.ic_preset_movie
    ),
    DolbyPresetBanner(
        DolbyProfile.MUSIC,
        R.string.profile_music,
        R.string.profile_music_summary,
        R.drawable.preset_banner_music,
        R.drawable.ic_preset_music
    ),
    DolbyPresetBanner(
        DolbyProfile.GAME,
        R.string.profile_game,
        R.string.profile_game_summary,
        R.drawable.preset_banner_game,
        R.drawable.ic_preset_game
    ),
    DolbyPresetBanner(
        DolbyProfile.VOICE,
        R.string.profile_voice,
        R.string.profile_voice_summary,
        R.drawable.preset_banner_voice,
        R.drawable.ic_preset_voice
    ),
    DolbyPresetBanner(
        DolbyProfile.CUSTOM,
        R.string.profile_custom,
        R.string.profile_custom_summary,
        R.drawable.preset_banner_custom,
        R.drawable.ic_preset_custom
    )
)

enum class ScreenTab {
    EFFECTS,
    EQUALIZER,
    ADVANCED,
    CONTRIBUTORS
}

@Composable
fun MainScreen(
    audioManager: AudioEffectManager,
    isDark: Boolean,
    themeMode: AppThemeMode,
    onThemeChange: (AppThemeMode) -> Unit,
    accentColor: AppAccentColor,
    onAccentChange: (AppAccentColor) -> Unit,
    paletteStyle: AppPaletteStyle,
    onPaletteStyleChange: (AppPaletteStyle) -> Unit,
    preciseSliders: Boolean,
    onPreciseSlidersChange: (Boolean) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val tabPagerState = rememberPagerState(initialPage = 0, pageCount = { ScreenTab.entries.size })
    val currentTab = ScreenTab.entries[tabPagerState.currentPage]
    var showDebugInfo by rememberSaveable { mutableStateOf(false) }
    var rotationTarget by rememberSaveable { mutableFloatStateOf(0f) }
    val backdropLayer = rememberGraphicsLayer()
    var pagerBoundsInRoot by remember { mutableStateOf(Rect.Zero) }

    BackHandler(enabled = showDebugInfo) {
        showDebugInfo = false
    }

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

    val isOnDebugPage = currentTab == ScreenTab.CONTRIBUTORS && showDebugInfo

    val headerFontFamily = remember {
        FontFamily(
            Font(R.font.space_grotesk_medium, FontWeight.Normal),
            Font(R.font.space_grotesk_semibold, FontWeight.Medium),
            Font(R.font.space_grotesk_bold, FontWeight.Bold)
        )
    }
    val baseTextStyles = MiuixTheme.textStyles
    val headerTextStyles = remember(baseTextStyles, headerFontFamily) {
        baseTextStyles.copy(
            main = baseTextStyles.main.copy(
                fontFamily = headerFontFamily,
                letterSpacing = (-0.5).sp
            )
        )
    }

    Scaffold(
        topBar = {
            MiuixTheme(
                colors = MiuixTheme.colorScheme,
                textStyles = headerTextStyles
            ) {
                TopAppBar(
                title = if (isOnDebugPage) {
                    stringResource(R.string.debug_info_title)
                } else {
                    when (currentTab) {
                        ScreenTab.EFFECTS -> "Audio Engine"
                        ScreenTab.EQUALIZER -> stringResource(R.string.tab_fine_tune)
                        ScreenTab.ADVANCED -> stringResource(R.string.tab_advanced)
                        ScreenTab.CONTRIBUTORS -> stringResource(R.string.tab_about)
                    }
                },
                navigationIcon = {
                    if (isOnDebugPage) {
                        IconButton(onClick = { showDebugInfo = false }) {
                            Icon(
                                imageVector = MiuixIcons.Back,
                                contentDescription = "Back",
                                tint = MiuixTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                actions = {
                    if (!isOnDebugPage) {
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
                                    AppThemeMode.LIGHT -> stringResource(R.string.theme_light)
                                    AppThemeMode.DARK -> stringResource(R.string.theme_dark)
                                    AppThemeMode.SYSTEM -> stringResource(R.string.theme_system)
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
                }
            )
            }
        },
        floatingToolbar = {
            if (!isOnDebugPage) {
                FrostedGlassFloatingNavBar(
                    currentTab = currentTab,
                    isDark = isDark,
                    backdropLayer = backdropLayer,
                    contentBoundsInRoot = pagerBoundsInRoot,
                    onSelectTab = { targetTab ->
                        coroutineScope.launch {
                            tabPagerState.animateScrollToPage(targetTab.ordinal)
                        }
                    }
                )
            }
        },
        floatingToolbarPosition = ToolbarPosition.BottomCenter
    ) { padding ->
        HorizontalPager(
            state = tabPagerState,
            userScrollEnabled = !isOnDebugPage,
            beyondViewportPageCount = 1,
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coords ->
                    pagerBoundsInRoot = coords.boundsInRoot()
                }
                .drawWithContent {
                    backdropLayer.record {
                        this@drawWithContent.drawContent()
                    }
                    drawLayer(backdropLayer)
                }
        ) { page ->
            when (ScreenTab.entries[page]) {
                ScreenTab.EFFECTS -> EffectsTab(
                    audioManager = audioManager,
                    padding = padding,
                    preciseSliders = preciseSliders
                )
                ScreenTab.EQUALIZER -> EqualizerScreen(audioManager, padding, preciseSliders)
                ScreenTab.ADVANCED -> AdvancedScreen(
                    audioManager = audioManager,
                    padding = padding,
                    preciseSliders = preciseSliders,
                    onPreciseSlidersChange = onPreciseSlidersChange
                )
                ScreenTab.CONTRIBUTORS -> {
                    if (showDebugInfo) {
                        DebugInfoScreen(padding)
                    } else {
                        ContributorsScreen(
                            padding = padding,
                            accentColor = accentColor,
                            onAccentChange = onAccentChange,
                            paletteStyle = paletteStyle,
                            onPaletteStyleChange = onPaletteStyleChange,
                            onOpenDebugInfo = { showDebugInfo = true }
                        )
                    }
                }
            }
        }
    }
}

private const val FROSTED_GLASS_LENS_AGSL = """
uniform shader content;
uniform float2 resolution;

float sdCapsulePill(float2 p, float2 halfSize, float radius) {
    float2 q = abs(p) - max(halfSize - float2(radius), float2(0.0));
    return length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - radius;
}

half4 main(float2 fragCoord) {
    float2 halfSize = max(resolution * 0.5, float2(1.0));
    float radius = halfSize.y;
    float2 p = fragCoord - halfSize;

    float d = sdCapsulePill(p, halfSize, radius);
    float rimBand = clamp((-d) / max(radius * 0.72, 1.0), 0.0, 1.0);
    float rimCurve = pow(1.0 - rimBand, 2.2);

    float eps = 1.0;
    float2 grad = float2(
        sdCapsulePill(p + float2(eps, 0.0), halfSize, radius) - sdCapsulePill(p - float2(eps, 0.0), halfSize, radius),
        sdCapsulePill(p + float2(0.0, eps), halfSize, radius) - sdCapsulePill(p - float2(0.0, eps), halfSize, radius)
    );
    float2 normal = grad / max(length(grad), 0.0001);

    // Optical refraction near curved pill meniscus + subtle center dome magnification
    float2 sampleBase = fragCoord - p * (0.025 * (1.0 - rimCurve));
    float bend = rimCurve * radius * 0.18;
    float disp = rimCurve * radius * 0.035;

    float2 maxCoord = max(resolution - float2(1.0), float2(1.0));
    float2 uvR = clamp(sampleBase - normal * (bend + disp), float2(1.0), maxCoord);
    float2 uvG = clamp(sampleBase - normal * bend, float2(1.0), maxCoord);
    float2 uvB = clamp(sampleBase - normal * (bend - disp), float2(1.0), maxCoord);

    half r = content.eval(uvR).r;
    half g = content.eval(uvG).g;
    half b = content.eval(uvB).b;
    half a = content.eval(uvG).a;

    float upperSheen = smoothstep(0.0, -radius * 0.35, p.y) * rimCurve * 0.06;
    float lowerBounce = smoothstep(0.0, radius * 0.35, p.y) * pow(rimCurve, 1.8) * 0.03;
    half3 rgb = half3(r, g, b) + half3(upperSheen + lowerBounce);
    return half4(rgb, a);
}
"""

@Composable
private fun FrostedGlassFloatingNavBar(
    currentTab: ScreenTab,
    isDark: Boolean,
    backdropLayer: GraphicsLayer,
    contentBoundsInRoot: Rect,
    onSelectTab: (ScreenTab) -> Unit
) {
    val navBottomInset = WindowInsets.navigationBars.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()
    val bottomMargin = if (navBottomInset > 0.dp) 26.dp + navBottomInset else 36.dp
    val pillShape = RoundedCornerShape(32.dp)
    var pillBoundsInRoot by remember { mutableStateOf(Rect.Zero) }
    val lensShader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            RuntimeShader(FROSTED_GLASS_LENS_AGSL)
        } else {
            null
        }
    }

    val surfaceTint = MiuixTheme.colorScheme.surfaceContainer
    val tintColor = if (isDark) {
        surfaceTint.copy(alpha = 0.34f)
    } else {
        Color.White.copy(alpha = 0.42f)
    }
    val rimBrush = if (isDark) {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.36f),
                Color.White.copy(alpha = 0.07f),
                Color.White.copy(alpha = 0.16f)
            )
        )
    } else {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.88f),
                Color.White.copy(alpha = 0.22f),
                Color.White.copy(alpha = 0.50f)
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = bottomMargin),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .defaultMinSize(minHeight = 52.dp)
                .clip(pillShape)
                .pointerInput(Unit) {
                    // Absorb any unconsumed taps/drags inside the pill gaps so touches never pass through to cards underneath
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            event.changes.forEach { change ->
                                if (!change.isConsumed) change.consume()
                            }
                        }
                    }
                }
                .onGloballyPositioned { coords ->
                    pillBoundsInRoot = coords.boundsInRoot()
                }
        ) {
            // Optical frosted blur + AGSL liquid lens refraction backdrop surface
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        clip = true
                        shape = pillShape
                        val blur = AndroidRenderEffect.createBlurEffect(22f, 22f, Shader.TileMode.CLAMP)
                        renderEffect = if (
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            lensShader != null &&
                            size.width > 1f &&
                            size.height > 1f
                        ) {
                            lensShader.setFloatUniform("resolution", size.width, size.height)
                            val lens = AndroidRenderEffect.createRuntimeShaderEffect(lensShader, "content")
                            AndroidRenderEffect.createChainEffect(lens, blur).asComposeRenderEffect()
                        } else {
                            blur.asComposeRenderEffect()
                        }
                    }
                    .drawBehind {
                        if (pillBoundsInRoot != Rect.Zero && contentBoundsInRoot != Rect.Zero) {
                            val dx = pillBoundsInRoot.left - contentBoundsInRoot.left
                            val dy = pillBoundsInRoot.top - contentBoundsInRoot.top
                            translate(-dx, -dy) {
                                drawLayer(backdropLayer)
                            }
                        }
                        drawRect(color = tintColor)
                    }
            )

            // Crisp specular frosted glass rim border rendered above the blur pass
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .border(width = 0.9.dp, brush = rimBrush, shape = pillShape)
            )

            // Crisp un-blurred foreground navigation items
            Row(
                modifier = Modifier
                    .defaultMinSize(minHeight = 52.dp)
                    .padding(horizontal = FloatingNavigationBarDefaults.HorizontalPadding, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(FloatingNavigationBarDefaults.ItemSpacing),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FloatingNavigationBarItem(
                    selected = currentTab == ScreenTab.EFFECTS,
                    onClick = { onSelectTab(ScreenTab.EFFECTS) },
                    icon = MiuixIcons.Music,
                    label = stringResource(R.string.tab_audio_engine)
                )
                FloatingNavigationBarItem(
                    selected = currentTab == ScreenTab.EQUALIZER,
                    onClick = { onSelectTab(ScreenTab.EQUALIZER) },
                    icon = MiuixIcons.Tune,
                    label = stringResource(R.string.tab_fine_tune)
                )
                FloatingNavigationBarItem(
                    selected = currentTab == ScreenTab.ADVANCED,
                    onClick = { onSelectTab(ScreenTab.ADVANCED) },
                    icon = MiuixIcons.Settings,
                    label = stringResource(R.string.tab_advanced)
                )
                FloatingNavigationBarItem(
                    selected = currentTab == ScreenTab.CONTRIBUTORS,
                    onClick = { onSelectTab(ScreenTab.CONTRIBUTORS) },
                    icon = MiuixIcons.Info,
                    label = stringResource(R.string.tab_about)
                )
            }
        }
    }
}

@Composable
fun EffectsTab(
    audioManager: AudioEffectManager,
    padding: PaddingValues,
    preciseSliders: Boolean
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val effectMode by audioManager.effectModeState.collectAsState()
    val dolbyProfile by audioManager.dolbyProfileState.collectAsState()
    val spatialEnabled by audioManager.spatialAudioState.collectAsState()
    val aospSpatializerActive by audioManager.aospSpatializerActiveState.collectAsState()
    val surroundStrengthRaw by audioManager.surroundStrengthState.collectAsState()
    val surroundStrength = surroundStrengthRaw / 10f

    DisposableEffect(context) {
        val lifecycle = (context as? LifecycleOwner)?.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                audioManager.refreshAospSpatializerState()
            }
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }

    val bassEnhancerEnabled by audioManager.bassEnhancerEnabledState.collectAsState()
    val bassEnhancerLevel by audioManager.bassEnhancerLevelState.collectAsState()
    val midEnhancerEnabled by audioManager.midEnhancerEnabledState.collectAsState()
    val midEnhancerLevel by audioManager.midEnhancerLevelState.collectAsState()
    val trebleEnhancerEnabled by audioManager.trebleEnhancerEnabledState.collectAsState()
    val trebleEnhancerLevel by audioManager.trebleEnhancerLevelState.collectAsState()
    val dialogueEnhancerEnabled by audioManager.dialogueEnhancerEnabledState.collectAsState()
    val dialogueEnhancerLevel by audioManager.dialogueEnhancerLevelState.collectAsState()
    val volumeLevelerEnabled by audioManager.volumeLevelerEnabledState.collectAsState()
    val volumeLevelerLevel by audioManager.volumeLevelerLevelState.collectAsState()

    val initialPageIndex = DOLBY_PRESET_BANNERS.indexOfFirst { it.profile == dolbyProfile }.coerceAtLeast(0)
    val pagerState = rememberPagerState(
        initialPage = initialPageIndex,
        pageCount = { DOLBY_PRESET_BANNERS.size }
    )
    var isProgrammaticScroll by remember { mutableStateOf(false) }

    // Sync only user-driven swipes back to the audio profile (never snap on intermediate programmatic scroll frames)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage to pagerState.isScrollInProgress }
            .collect { (settled, scrolling) ->
                if (!scrolling && !isProgrammaticScroll) {
                    val settledProfile = DOLBY_PRESET_BANNERS[settled].profile
                    if (dolbyProfile != settledProfile) {
                        audioManager.setDolbyProfile(settledProfile)
                    }
                }
            }
    }

    // Single authoritative animator for external / button preset selections
    LaunchedEffect(dolbyProfile) {
        val targetIdx = DOLBY_PRESET_BANNERS.indexOfFirst { it.profile == dolbyProfile }
        if (targetIdx >= 0 && pagerState.currentPage != targetIdx) {
            isProgrammaticScroll = true
            try {
                pagerState.animateScrollToPage(targetIdx)
            } finally {
                isProgrammaticScroll = false
            }
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

        // Android Spatial Audio Status & Settings Shortcut Card
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            val statusColor = if (aospSpatializerActive) Color(0xFFFF9800) else Color(0xFF00C853)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (aospSpatializerActive) Color(0xFFFF9800).copy(alpha = 0.08f) else Color.Transparent
                    )
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(statusColor.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (aospSpatializerActive) {
                        Text(
                            text = "!",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Black,
                            color = statusColor
                        )
                    } else {
                        Icon(
                            imageVector = MiuixIcons.Ok,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.spatial_aosp_warning_title),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(statusColor.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (aospSpatializerActive) "ENABLED" else "OFF",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (aospSpatializerActive) Color(0xFFE65100) else statusColor
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = stringResource(
                            if (aospSpatializerActive) R.string.spatial_aosp_warning_active
                            else R.string.spatial_aosp_warning_idle
                        ),
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (aospSpatializerActive) Color(0xFFFF9800) else MiuixTheme.colorScheme.primary)
                            .clickable {
                                val spatialDirect = Intent().apply {
                                    component = ComponentName(
                                        "com.android.settings",
                                        "com.android.settings.SubSettings"
                                    )
                                    putExtra(
                                        ":settings:show_fragment",
                                        "com.android.settings.notification.SpatialAudioSettings"
                                    )
                                }
                                val soundHighlighted = Intent(AndroidSettings.ACTION_SOUND_SETTINGS).apply {
                                    putExtra(":settings:fragment_args_key", "spatial_audio_summary")
                                }
                                runCatching { context.startActivity(spatialDirect) }
                                    .onFailure {
                                        runCatching { context.startActivity(soundHighlighted) }
                                    }
                            }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.spatial_aosp_settings_button),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (aospSpatializerActive) Color.White else MiuixTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Section 1: Effect Engine Selector (Segmented Tile Cards + Animated Footer)
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val engineOptions = listOf(
                        Triple(EffectMode.DOLBY, R.string.dolby_atmos, R.drawable.ic_dolby),
                        Triple(EffectMode.MISOUND, R.string.xiaomi_sound, R.drawable.ic_xiaomi_sound),
                        Triple(EffectMode.OFF, R.string.engine_banner_off_title, R.drawable.ic_direct_dac)
                    )
                    engineOptions.forEach { (modeOption, labelRes, iconRes) ->
                        val isSelected = effectMode == modeOption
                        val tileBg by animateColorAsState(
                            targetValue = if (isSelected) {
                                MiuixTheme.colorScheme.primary.copy(alpha = 0.14f)
                            } else {
                                MiuixTheme.colorScheme.surfaceContainerHigh
                            },
                            animationSpec = tween(220),
                            label = "EngineTileBg_${modeOption.id}"
                        )
                        val borderColor by animateColorAsState(
                            targetValue = if (isSelected) {
                                MiuixTheme.colorScheme.primary
                            } else {
                                MiuixTheme.colorScheme.outline.copy(alpha = 0.22f)
                            },
                            animationSpec = tween(220),
                            label = "EngineTileBorder_${modeOption.id}"
                        )
                        val iconCircleBg by animateColorAsState(
                            targetValue = if (isSelected) {
                                MiuixTheme.colorScheme.primary
                            } else {
                                MiuixTheme.colorScheme.surfaceContainer
                            },
                            animationSpec = tween(220),
                            label = "EngineIconBg_${modeOption.id}"
                        )
                        val iconTint by animateColorAsState(
                            targetValue = if (isSelected) {
                                MiuixTheme.colorScheme.onPrimary
                            } else {
                                MiuixTheme.colorScheme.onSurfaceVariantSummary
                            },
                            animationSpec = tween(220),
                            label = "EngineIconTint_${modeOption.id}"
                        )

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(tileBg)
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = borderColor,
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clickable { audioManager.setEffectMode(modeOption) }
                                .padding(horizontal = 8.dp, vertical = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(iconCircleBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(iconRes),
                                    contentDescription = stringResource(labelRes),
                                    tint = iconTint,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(labelRes),
                                fontSize = 12.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) {
                                    MiuixTheme.colorScheme.primary
                                } else {
                                    MiuixTheme.colorScheme.onSurface
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(10.dp))

                AnimatedContent(
                    targetState = effectMode,
                    transitionSpec = { fadeIn(tween(200)).togetherWith(fadeOut(tween(150))) },
                    label = "EngineSummaryTransition"
                ) { mode ->
                    val statusColor = when (mode) {
                        EffectMode.DOLBY -> Color(0xFF00C853)
                        EffectMode.MISOUND -> Color(0xFFFFB300)
                        EffectMode.OFF -> Color(0xFF757575)
                    }
                    val summaryRes = when (mode) {
                        EffectMode.DOLBY -> R.string.dolby_summary
                        EffectMode.MISOUND -> R.string.xiaomi_sound_summary
                        EffectMode.OFF -> R.string.effect_off_summary
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(summaryRes),
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Section 2: Dolby Audio Presets + Creative Audio Enhancers
        AnimatedVisibility(
            visible = effectMode == EffectMode.DOLBY,
            enter = expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(tween(240)),
            exit = shrinkVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut(tween(180))
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.category_dolby_preset),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp)
                )

                // Spotlight Pager Carousel
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

                Spacer(modifier = Modifier.height(10.dp))

                // Complete 3x2 Dolby Preset Selector Grid (zero horizontal overflow)
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DOLBY_PRESET_BANNERS.chunked(3).forEachIndexed { rowIdx, rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowItems.forEachIndexed { colIdx, banner ->
                                    val globalIdx = rowIdx * 3 + colIdx
                                    val isSelected = dolbyProfile == banner.profile
                                    val pillBg by animateColorAsState(
                                        targetValue = if (isSelected) {
                                            MiuixTheme.colorScheme.primary
                                        } else {
                                            MiuixTheme.colorScheme.surfaceContainerHigh
                                        },
                                        animationSpec = tween(200),
                                        label = "PresetGridBg_${banner.profile.name}"
                                    )
                                    val pillFg by animateColorAsState(
                                        targetValue = if (isSelected) {
                                            MiuixTheme.colorScheme.onPrimary
                                        } else {
                                            MiuixTheme.colorScheme.onSurface
                                        },
                                        animationSpec = tween(200),
                                        label = "PresetGridFg_${banner.profile.name}"
                                    )

                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(pillBg)
                                            .clickable {
                                                audioManager.setDolbyProfile(banner.profile)
                                            }
                                            .padding(horizontal = 10.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(banner.iconRes),
                                            contentDescription = stringResource(banner.titleRes),
                                            tint = pillFg,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = stringResource(banner.titleRes),
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = pillFg,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Standalone Audio Enhancers Section with Active Counter Badge & Tactile Stepper Controls
                val activeEnhancersCount = listOf(
                    dialogueEnhancerEnabled,
                    volumeLevelerEnabled,
                    bassEnhancerEnabled
                ).count { it }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 8.dp, top = 4.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.category_enhancers),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )

                    AnimatedVisibility(visible = activeEnhancersCount > 0) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.14f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "$activeEnhancersCount Active",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MiuixTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    EnhancerControlTile(
                        title = stringResource(R.string.dialogue_enhancer),
                        summary = stringResource(R.string.dialogue_enhancer_summary),
                        intensityLabel = "Dialogue Clarity",
                        iconRes = R.drawable.ic_preset_voice,
                        enabled = dialogueEnhancerEnabled,
                        level = dialogueEnhancerLevel,
                        preciseSliders = preciseSliders,
                        onToggle = { audioManager.setDialogueEnhancerEnabled(it) },
                        onLevelChange = { audioManager.setDialogueEnhancerLevel(it) }
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    EnhancerControlTile(
                        title = stringResource(R.string.volume_leveler),
                        summary = stringResource(R.string.volume_leveler_summary),
                        intensityLabel = "Leveling Strength",
                        iconRes = R.drawable.ic_direct_dac,
                        enabled = volumeLevelerEnabled,
                        level = volumeLevelerLevel,
                        preciseSliders = preciseSliders,
                        onToggle = { audioManager.setVolumeLevelerEnabled(it) },
                        onLevelChange = { audioManager.setVolumeLevelerLevel(it) }
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    EnhancerControlTile(
                        title = stringResource(R.string.bass_enhancer),
                        summary = stringResource(R.string.bass_enhancer_summary),
                        intensityLabel = "Sub-Bass Gain",
                        iconRes = R.drawable.ic_preset_music,
                        enabled = bassEnhancerEnabled,
                        level = bassEnhancerLevel,
                        preciseSliders = preciseSliders,
                        onToggle = { audioManager.setBassEnhancerEnabled(it) },
                        onLevelChange = { audioManager.setBassEnhancerLevel(it) }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Separate Tonal Enhancers Section: Mid Enhancer + Treble Enhancer
                val activeTonalCount = listOf(midEnhancerEnabled, trebleEnhancerEnabled).count { it }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 8.dp, top = 4.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.category_tonal_enhancers),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )

                    AnimatedVisibility(visible = activeTonalCount > 0) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.14f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "$activeTonalCount Active",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MiuixTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    EnhancerControlTile(
                        title = stringResource(R.string.mid_enhancer),
                        summary = stringResource(R.string.mid_enhancer_summary),
                        intensityLabel = "Midrange Clarity",
                        iconRes = R.drawable.ic_preset_voice,
                        enabled = midEnhancerEnabled,
                        level = midEnhancerLevel,
                        preciseSliders = preciseSliders,
                        onToggle = { audioManager.setMidEnhancerEnabled(it) },
                        onLevelChange = { audioManager.setMidEnhancerLevel(it) }
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    EnhancerControlTile(
                        title = stringResource(R.string.treble_enhancer),
                        summary = stringResource(R.string.treble_enhancer_summary),
                        intensityLabel = "High-Frequency Air",
                        iconRes = R.drawable.ic_preset_dynamic,
                        enabled = trebleEnhancerEnabled,
                        level = trebleEnhancerLevel,
                        preciseSliders = preciseSliders,
                        onToggle = { audioManager.setTrebleEnhancerEnabled(it) },
                        onLevelChange = { audioManager.setTrebleEnhancerLevel(it) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Section 3: Spatial & Surround Sound Options (Dolby Atmos only — disabled in Xiaomi Sound & Direct DAC)
        val isSpatialSupported = effectMode == EffectMode.DOLBY
        val spatialSummaryRes = when (effectMode) {
            EffectMode.DOLBY -> R.string.immersive_sound_summary
            EffectMode.MISOUND -> R.string.immersive_sound_misound_summary
            EffectMode.OFF -> R.string.immersive_sound_off_summary
        }

        Text(
            text = stringResource(R.string.category_spatial),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = if (isSpatialSupported) 1f else 0.44f }
        ) {
            EnhancerControlTile(
                title = stringResource(R.string.immersive_sound),
                summary = stringResource(spatialSummaryRes),
                intensityLabel = stringResource(R.string.virtualizer_intensity),
                iconRes = R.drawable.ic_xiaomi_sound,
                enabled = isSpatialSupported && spatialEnabled,
                level = surroundStrength.roundToInt(),
                preciseSliders = preciseSliders,
                interactive = isSpatialSupported,
                onToggle = { checked -> audioManager.setSpatialAudioEnabled(checked) },
                onLevelChange = { newLevel ->
                    audioManager.setSurroundStrength(newLevel * 10)
                }
            )
        }

        // Extra clearance for floating bottom bar
        Spacer(modifier = Modifier.height(100.dp))
    }
}

@Composable
internal fun TactilePercentSlider(
    label: String,
    level: Int,
    preciseSliders: Boolean,
    onLevelChange: (Int) -> Unit,
    valueRange: IntRange = 0..100,
    stepChips: List<Int> = listOf(25, 50, 75, 100),
    chipSuffix: String = "%"
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
            if (stepChips.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val tolerance = if (valueRange.last <= 20) 0 else 4
                    stepChips.forEach { step ->
                        val isStepSelected = kotlin.math.abs(level - step) <= tolerance
                        val chipBg by animateColorAsState(
                            targetValue = if (isStepSelected) {
                                MiuixTheme.colorScheme.primary
                            } else {
                                MiuixTheme.colorScheme.surfaceContainerHigh
                            },
                            animationSpec = tween(160),
                            label = "StepBg_${label}_$step"
                        )
                        val chipFg by animateColorAsState(
                            targetValue = if (isStepSelected) {
                                MiuixTheme.colorScheme.onPrimary
                            } else {
                                MiuixTheme.colorScheme.onSurfaceVariantSummary
                            },
                            animationSpec = tween(160),
                            label = "StepFg_${label}_$step"
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(chipBg)
                                .clickable { onLevelChange(step) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (chipSuffix == " dB") "+$step$chipSuffix" else "$step$chipSuffix",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = chipFg
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val view = LocalView.current
        val detentTargets = remember(valueRange) {
            if (valueRange == 0..100) setOf(25, 50, 75) else emptySet()
        }
        val currentLevel by rememberUpdatedState(level)
        val keyPointsList = remember(preciseSliders, valueRange) {
            if (!preciseSliders && valueRange == 0..100) listOf(25f, 50f, 75f) else null
        }

        // Allow direct point tapping along the track without interfering with Slider drags or toggling the parent card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(preciseSliders, valueRange) {
                    detectTapGestures { offset ->
                        val w = size.width.toFloat()
                        val capPad = 14.dp.toPx()
                        val usable = (w - 2f * capPad).coerceAtLeast(1f)
                        val fraction = ((offset.x - capPad) / usable).coerceIn(0f, 1f)
                        val rawVal = valueRange.first + fraction * (valueRange.last - valueRange.first)
                        var target = rawVal.roundToInt().coerceIn(valueRange.first, valueRange.last)
                        if (!preciseSliders && valueRange == 0..100) {
                            val nearestDetent = detentTargets.minByOrNull { kotlin.math.abs(it - target) }
                            if (nearestDetent != null && kotlin.math.abs(nearestDetent - target) <= 3) {
                                target = nearestDetent
                            }
                        }
                        if (target != currentLevel) {
                            if (!preciseSliders && target in detentTargets) {
                                val hapticConst = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                    HapticFeedbackConstants.SEGMENT_TICK
                                } else {
                                    HapticFeedbackConstants.CLOCK_TICK
                                }
                                view.performHapticFeedback(hapticConst)
                            }
                            onLevelChange(target)
                        }
                    }
                }
        ) {
            Slider(
                value = level.toFloat(),
                onValueChange = { rawValue ->
                    val nextLevel = rawValue.roundToInt().coerceIn(valueRange.first, valueRange.last)
                    if (!preciseSliders && nextLevel != currentLevel && nextLevel in detentTargets) {
                        val hapticConst = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            HapticFeedbackConstants.SEGMENT_TICK
                        } else {
                            HapticFeedbackConstants.CLOCK_TICK
                        }
                        view.performHapticFeedback(hapticConst)
                    }
                    onLevelChange(nextLevel)
                },
                valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
                showKeyPoints = keyPointsList != null,
                keyPoints = keyPointsList,
                hapticEffect = if (!preciseSliders) {
                    SliderDefaults.SliderHapticEffect.Step
                } else {
                    SliderDefaults.SliderHapticEffect.None
                },
                magnetThreshold = if (!preciseSliders) 0.032f else 0f,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
internal fun EnhancerControlTile(
    title: String,
    summary: String,
    intensityLabel: String,
    iconRes: Int,
    enabled: Boolean,
    level: Int,
    preciseSliders: Boolean,
    interactive: Boolean = true,
    valueRange: IntRange = 0..100,
    stepChips: List<Int> = listOf(25, 50, 75, 100),
    chipSuffix: String = "%",
    onToggle: (Boolean) -> Unit,
    onLevelChange: (Int) -> Unit
) {
    val iconCircleBg by animateColorAsState(
        targetValue = if (enabled) {
            MiuixTheme.colorScheme.primary.copy(alpha = 0.16f)
        } else {
            MiuixTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = tween(220),
        label = "EnhancerIconBg_$title"
    )
    val iconTint by animateColorAsState(
        targetValue = if (enabled) {
            MiuixTheme.colorScheme.primary
        } else {
            MiuixTheme.colorScheme.onSurfaceVariantSummary
        },
        animationSpec = tween(220),
        label = "EnhancerIconTint_$title"
    )

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Clickable strictly on header Row so tapping/adjusting the Slider never closes the feature
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = interactive) { onToggle(!enabled) }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(iconCircleBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = title,
                    tint = iconTint,
                    modifier = Modifier.size(19.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = title,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (enabled) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.14f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (chipSuffix == " dB") "+$level$chipSuffix" else "$level$chipSuffix",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MiuixTheme.colorScheme.primary
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = summary,
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                enabled = interactive
            )
        }

        AnimatedVisibility(
            visible = enabled,
            enter = expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(tween(200)),
            exit = shrinkVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut(tween(150))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
            ) {
                TactilePercentSlider(
                    label = intensityLabel,
                    level = level,
                    preciseSliders = preciseSliders,
                    onLevelChange = onLevelChange,
                    valueRange = valueRange,
                    stepChips = stepChips,
                    chipSuffix = chipSuffix
                )
            }
        }
    }
}
