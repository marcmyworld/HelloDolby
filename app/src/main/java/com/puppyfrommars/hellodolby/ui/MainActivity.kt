/*
 * SPDX-FileCopyrightText: 2026 marcmyworld <moviesminister@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package com.puppyfrommars.hellodolby.ui

import android.app.Activity
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.puppyfrommars.hellodolby.HelloDolbyApp
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val audioManager = (application as HelloDolbyApp).audioEffectManager
        val prefs = getSharedPreferences("hellodolby_prefs", Context.MODE_PRIVATE)

        setContent {
            var themeMode by remember {
                mutableStateOf(
                    AppThemeMode.fromId(prefs.getInt("theme_mode", AppThemeMode.SYSTEM.id))
                )
            }

            val systemInDark = isSystemInDarkTheme()
            val isDark = when (themeMode) {
                AppThemeMode.SYSTEM -> systemInDark
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
            }

            val colorSchemeMode = when (themeMode) {
                AppThemeMode.SYSTEM -> ColorSchemeMode.MonetSystem
                AppThemeMode.LIGHT -> ColorSchemeMode.MonetLight
                AppThemeMode.DARK -> ColorSchemeMode.MonetDark
            }

            val themeController = remember(colorSchemeMode) {
                ThemeController(colorSchemeMode = colorSchemeMode)
            }

            val view = LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    val window = (view.context as Activity).window
                    val insetsController = WindowCompat.getInsetsController(window, view)
                    insetsController.isAppearanceLightStatusBars = !isDark
                    insetsController.isAppearanceLightNavigationBars = !isDark
                }
            }

            MiuixTheme(controller = themeController) {
                MainScreen(
                    audioManager = audioManager,
                    themeMode = themeMode,
                    onThemeChange = { newMode ->
                        themeMode = newMode
                        prefs.edit().putInt("theme_mode", newMode.id).apply()
                    }
                )
            }
        }
    }
}
