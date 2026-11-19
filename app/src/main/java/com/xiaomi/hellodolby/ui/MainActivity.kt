package com.xiaomi.hellodolby.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.xiaomi.hellodolby.HelloDolbyApp
import top.yukonga.miuix.kmp.theme.MiuixTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val audioManager = (application as HelloDolbyApp).audioEffectManager

        setContent {
            MiuixTheme {
                MainScreen(audioManager = audioManager)
            }
        }
    }
}
