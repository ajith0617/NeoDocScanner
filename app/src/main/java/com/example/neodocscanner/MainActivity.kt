package com.example.neodocscanner

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import com.example.neodocscanner.core.data.local.preferences.AppPreferencesDataStore
import com.example.neodocscanner.core.domain.model.AppPreferences
import com.example.neodocscanner.navigation.AppNavigation
import com.example.neodocscanner.ui.theme.NeoDocScannerTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Single-activity host. Delegates all navigation to [AppNavigation].
 *
 * iOS equivalent: The @main NeoDocsApp struct which bootstraps SwiftUI's
 * scene lifecycle. Here, Android's single-activity model means this class
 * only sets up the Compose content and edge-to-edge rendering.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var appPreferencesDataStore: AppPreferencesDataStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Do NOT call enableEdgeToEdge() — it forces a transparent status bar on API 29+
        // and makes window.statusBarColor ignored. The coral color is applied by Theme.kt.
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContent {
            val appPreferences by appPreferencesDataStore.preferencesFlow
                .collectAsState(initial = AppPreferences())

            NeoDocScannerTheme(darkTheme = appPreferences.darkThemeEnabled) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color    = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(
                        initialQrPayload = extractQrPayload(intent)
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun extractQrPayload(intent: Intent?): String? {
        val data = intent?.data ?: return null
        if (data.scheme != "neodocs") return null
        val payload = data.getQueryParameter("payload")
        return payload?.takeIf { it.isNotBlank() }
    }
}
