package com.alcedo.studio.ui

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.alcedo.studio.ui.album.AlbumScreen
import com.alcedo.studio.ui.album.StatsView
import com.alcedo.studio.ui.editor.EditorScreen
import com.alcedo.studio.ui.export.ExportScreen
import com.alcedo.studio.ui.settings.SettingsScreen
import com.alcedo.studio.ui.settings.AboutPage
import com.alcedo.studio.ui.ai.AiSearchScreen
import com.alcedo.studio.ui.ai.AiModelManagerScreen
import com.alcedo.studio.i18n.StringResources
import com.alcedo.studio.i18n.stringRes

enum class MainDestination(
    val route: String,
    val labelKey: StringResources.() -> String,
    val icon: @Composable () -> Unit
) {
    ALBUM("album", { navAlbum }, { Icon(Icons.Default.PhotoLibrary, contentDescription = stringRes { navAlbum }) }),
    AI_SEARCH("ai_search", { navAiSearch }, { Icon(Icons.Default.Search, contentDescription = stringRes { navAi }) }),
    SETTINGS("settings", { navSettings }, { Icon(Icons.Default.Settings, contentDescription = stringRes { navSettings }) })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    navController: NavHostController = androidx.navigation.compose.rememberNavController(),
    projectName: String = "Alcedo Studio"
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600

    val bottomBarDestinations = MainDestination.entries
    val showBottomBar = currentRoute in bottomBarDestinations.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar && !isTablet) {
                NavigationBar {
                    bottomBarDestinations.forEach { destination ->
                        val selected = currentRoute == destination.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (currentRoute != destination.route) {
                                    navController.navigate(destination.route) {
                                        popUpTo(MainDestination.ALBUM.route) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = destination.icon,
                            label = {
                                Text(
                                    stringRes(destination.labelKey),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        )
                    }
                }
            }
        }
    } { padding ->
        if (isTablet) {
            // Tablet: NavigationRail layout
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                NavigationRail {
                    Spacer(modifier = Modifier.weight(1f))
                    bottomBarDestinations.forEach { destination ->
                        val selected = currentRoute == destination.route
                        NavigationRailItem(
                            selected = selected,
                            onClick = {
                                if (currentRoute != destination.route) {
                                    navController.navigate(destination.route) {
                                        popUpTo(MainDestination.ALBUM.route) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = destination.icon,
                            label = {
                                Text(
                                    stringRes(destination.labelKey),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                }

                NavHost(
                    navController = navController,
                    startDestination = MainDestination.ALBUM.route,
                    modifier = Modifier.fillMaxSize()
                ) {
                    composable(MainDestination.ALBUM.route) {
                        AlbumScreen(navController = navController)
                    }
                    composable(MainDestination.AI_SEARCH.route) {
                        AiSearchScreen(navController = navController)
                    }
                    composable(MainDestination.SETTINGS.route) {
                        SettingsScreen(navController = navController)
                    }
                    composable("editor/{imageId}") { backStackEntry ->
                        val imageId = backStackEntry.arguments?.getString("imageId") ?: ""
                        EditorScreen(navController = navController, imageId = imageId)
                    }
                    composable("export/{imageId}") { backStackEntry ->
                        val imageId = backStackEntry.arguments?.getString("imageId") ?: ""
                        ExportScreen(navController = navController, imageId = imageId)
                    }
                    composable("ai_models") {
                        AiModelManagerScreen(navController = navController)
                    }
                    composable("about") {
                        AboutPage(navController = navController)
                    }
                    composable("stats") {
                        StatsView(navController = navController)
                    }
                }
            }
        } else {
            // Phone: BottomNavigation layout
            NavHost(
                navController = navController,
                startDestination = MainDestination.ALBUM.route,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                composable(MainDestination.ALBUM.route) {
                    AlbumScreen(navController = navController)
                }
                composable(MainDestination.AI_SEARCH.route) {
                    AiSearchScreen(navController = navController)
                }
                composable(MainDestination.SETTINGS.route) {
                    SettingsScreen(navController = navController)
                }
                composable("editor/{imageId}") { backStackEntry ->
                    val imageId = backStackEntry.arguments?.getString("imageId") ?: ""
                    EditorScreen(navController = navController, imageId = imageId)
                }
                composable("export/{imageId}") { backStackEntry ->
                    val imageId = backStackEntry.arguments?.getString("imageId") ?: ""
                    ExportScreen(navController = navController, imageId = imageId)
                }
                composable("ai_models") {
                    AiModelManagerScreen(navController = navController)
                }
                composable("about") {
                    AboutPage(navController = navController)
                }
                composable("stats") {
                    StatsView(navController = navController)
                }
            }
        }
    }
}