package com.example.neodocscanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.neodocscanner.navigation.AppNavigation
import com.example.neodocscanner.ui.theme.NeoDocScannerTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-activity host. Delegates all navigation to [AppNavigation].
 *
 * iOS equivalent: The @main NeoDocsApp struct which bootstraps SwiftUI's
 * scene lifecycle. Here, Android's single-activity model means this class
 * only sets up the Compose content and edge-to-edge rendering.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NeoDocScannerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color    = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}
