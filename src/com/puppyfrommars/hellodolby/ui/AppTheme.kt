/*
 * SPDX-FileCopyrightText: 2026 marcmyworld <moviesminister@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package com.puppyfrommars.hellodolby.ui

import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

enum class AppThemeMode(val id: Int) {
    SYSTEM(0),
    LIGHT(1),
    DARK(2);

    fun next(): AppThemeMode = when (this) {
        SYSTEM -> LIGHT
        LIGHT -> DARK
        DARK -> SYSTEM
    }

    companion object {
        fun fromId(id: Int): AppThemeMode = entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}

enum class AppAccentColor(
    val id: String,
    val label: String,
    val keyColor: Color?
) {
    AUTO("auto", "Auto", null),
    DOLBY_TEAL("teal", "Teal", Color(0xFF00BFA5)),
    OCEAN_BLUE("ocean", "Ocean", Color(0xFF2196F3)),
    AMETHYST("violet", "Violet", Color(0xFF7C4DFF)),
    XIAOMI_AMBER("amber", "Amber", Color(0xFFFF9800)),
    RUBY("ruby", "Ruby", Color(0xFFE53935)),
    ROSE("rose", "Rose", Color(0xFFEC407A));

    companion object {
        fun fromId(id: String?): AppAccentColor = entries.firstOrNull { it.id == id } ?: AUTO
    }
}

enum class AppPaletteStyle(
    val id: String,
    val label: String,
    val style: ThemePaletteStyle?
) {
    AUTO("auto", "Auto", null),
    TONAL_SPOT("tonal_spot", "Tonal", ThemePaletteStyle.TonalSpot),
    VIBRANT("vibrant", "Vibrant", ThemePaletteStyle.Vibrant),
    EXPRESSIVE("expressive", "Expressive", ThemePaletteStyle.Expressive),
    NEUTRAL("neutral", "Neutral", ThemePaletteStyle.Neutral),
    MONOCHROME("monochrome", "Mono", ThemePaletteStyle.Monochrome);

    companion object {
        fun fromId(id: String?): AppPaletteStyle = entries.firstOrNull { it.id == id } ?: AUTO
    }
}
