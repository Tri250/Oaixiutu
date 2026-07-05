package com.alcedo.studio

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.alcedo.studio.ui.album.AlbumScreen
import com.alcedo.studio.ui.editor.EditorScreen
import com.alcedo.studio.ui.export.ExportScreen
import com.alcedo.studio.ui.settings.SettingsScreen
import com.alcedo.studio.ui.ai.AiSearchScreen
import com.alcedo.studio.ui.ai.AiModelManagerScreen
import com.alcedo.studio.ui.ai.AiRatingScreen
import com.alcedo.studio.ui.theme.AlcedoTheme
import com.alcedo.studio.i18n.LanguageManager
import com.alcedo.studio.i18n.stringRes
import com.alcedo.studio.ui.theme.ThemeManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
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
        try { ThemeManager.initialize(this) } catch (e: Throwable) { Log.e("MainActivity", "ThemeManager init failed", e) }
        try { LanguageManager.initialize(this) } catch (e: Throwable) { Log.e("MainActivity", "LanguageManager init failed", e) }

        try { enableEdgeToEdge() } catch (e: Throwable) { Log.e("MainActivity", "enableEdgeToEdge failed", e) }
        setContent {
            AlcedoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = navBackStackEntry?.destination?.route
                    val windowSizeClass = calculateWindowSizeClass(this@MainActivity)
                    val isTablet = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact
                    val isExpanded = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded

                    val bottomBarRoutes = listOf("album", "ai_search", "settings")
                    val showBottomBar = currentRoute in bottomBarRoutes

                    Scaffold(
                        bottomBar = {
                            if (showBottomBar && !isTablet) {
                                NavigationBar {
                                    NavigationBarItem(
                                        selected = currentRoute == "album",
                                        onClick = {
                                            navController.navigate("album") {
                                                popUpTo("album") { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        icon = { Icon(Icons.Default.PhotoLibrary, contentDescription = stringRes { navAlbum }) },
                                        label = { Text(stringRes { navAlbum }) }
                                    )
                                    NavigationBarItem(
                                        selected = currentRoute == "ai_search",
                                        onClick = {
                                            navController.navigate("ai_search") {
                                                popUpTo("album") { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        icon = { Icon(Icons.Default.Search, contentDescription = stringRes { navAi }) },
                                        label = { Text(stringRes { navAiSearch }) }
                                    )
                                    NavigationBarItem(
                                        selected = currentRoute == "settings",
                                        onClick = {
                                            navController.navigate("settings") {
                                                popUpTo("album") { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        icon = { Icon(Icons.Default.Settings, contentDescription = stringRes { navSettings }) },
                                        label = { Text(stringRes { navSettings }) }
                                    )
                                }
                            }
                        }
                    ) { padding ->
                        NavHost(
                            navController = navController,
                            startDestination = "album",
                            modifier = Modifier
                                .fillMaxSize()
                                .then(
                                    if (showBottomBar) Modifier.consumeWindowInsets(padding)
                                    else Modifier
                                )
                        ) {
                            composable("album") {
                                AlbumScreen(navController = navController)
                            }
                            composable("ai_search") {
                                AiSearchScreen(navController = navController)
                            }
                            composable("editor/{imageId}") { backStackEntry ->
                                val imageId = backStackEntry.arguments?.getString("imageId") ?: ""
                                EditorScreen(navController = navController, imageId = imageId)
                            }
                            composable("export/{imageId}") { backStackEntry ->
                                val imageId = backStackEntry.arguments?.getString("imageId") ?: ""
                                ExportScreen(navController = navController, imageId = imageId)
                            }
                            composable("settings") {
                                SettingsScreen(navController = navController)
                            }
                            composable("ai_models") {
                                AiModelManagerScreen(navController = navController)
                            }
                            composable("ai_rating/{imageId}") { backStackEntry ->
                                val imageId = backStackEntry.arguments?.getString("imageId") ?: ""
                                AiRatingScreen(navController = navController)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        LanguageManager.onSystemLocaleChanged(this)
    }
}
