package com.alcedo.studio

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.alcedo.studio.ui.MainScreen
import com.alcedo.studio.ui.theme.AlcedoTheme
import com.alcedo.studio.i18n.LanguageManager
import com.alcedo.studio.ui.theme.ThemeManager
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // Validate incoming intent data
        try {
            intent?.data?.let { uri ->
                if (uri.scheme != "content" && uri.scheme != "file") {
                    Log.w("MainActivity", "Ignoring unsafe URI scheme: ${uri.scheme}")
                    intent.data = null
                }
            }
        } catch (e: Throwable) {
            Log.e("MainActivity", "Intent validation failed", e)
        }

        // Initialize managers
        var isReady by mutableStateOf(false)
        splashScreen.setKeepOnScreenCondition { !isReady }
        try { ThemeManager.initialize(this) } catch (e: Throwable) { Log.e("MainActivity", "ThemeManager init failed", e) }
        try { LanguageManager.initialize(this) } catch (e: Throwable) { Log.e("MainActivity", "LanguageManager init failed", e) }
        isReady = true

        try { enableEdgeToEdge() } catch (e: Throwable) { Log.e("MainActivity", "enableEdgeToEdge failed", e) }
        setContent {
            AlcedoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val windowSizeClass = calculateWindowSizeClass(this@MainActivity)
                    MainScreen(windowSizeClass = windowSizeClass)
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        LanguageManager.onSystemLocaleChanged(this)
    }
}
