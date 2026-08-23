package com.homedesign.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.homedesign.android.core.ui.theme.HdTheme
import com.homedesign.android.core.ui.theme.HomeDesignTheme
import com.homedesign.android.presentation.navigation.HomeNavHost
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HomeDesignTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = HdTheme.colors.paper,
                ) {
                    HomeNavHost()
                }
            }
        }
    }
}
