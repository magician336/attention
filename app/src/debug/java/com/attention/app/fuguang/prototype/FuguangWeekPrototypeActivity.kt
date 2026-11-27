package com.attention.app.fuguang.prototype

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.attention.app.ui.theme.AttentionTheme

class FuguangWeekPrototypeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AttentionTheme(darkTheme = false) {
                Surface(modifier = Modifier.fillMaxSize(), color = FuguangPrototypeColors.paper2) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(FuguangPrototypeColors.paper2)
                            .windowInsetsPadding(WindowInsets.safeDrawing),
                    ) {
                        FuguangWeekPrototypeApp()
                    }
                }
            }
        }
    }
}
