package com.alcedo.studio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AlcedoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = navBackStackEntry?.destination?.route
                    val configuration = LocalConfiguration.current
                    val isTablet = configuration.screenWidthDp >= 600

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
                                        icon = { Icon(Icons.Default.PhotoLibrary, contentDescription = "Album") },
                                        label = { Text("Album") }
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
                                        icon = { Icon(Icons.Default.Search, contentDescription = "AI") },
                                        label = { Text("Search") }
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
                                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                                        label = { Text("Settings") }
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
                                AiRatingScreen(navController = navController, imageId = imageId)
                            }
                        }
                    }
                }
            }
        }
    }
}