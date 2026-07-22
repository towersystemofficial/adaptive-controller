package com.towersys.adaptiveremote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.towersys.adaptiveremote.ui.AdaptiveRemoteApp
import com.towersys.adaptiveremote.ui.theme.AdaptiveRemoteTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AdaptiveRemoteTheme {
                AdaptiveRemoteApp()
            }
        }
    }
}

