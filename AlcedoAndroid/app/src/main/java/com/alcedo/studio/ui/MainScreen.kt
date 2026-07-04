package com.alcedo.studio.ui

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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

enum class NavigationType {
    BOTTOM_NAVIGATION, NAVIGATION_RAIL, PERMANENT_NAVIGATION_DRAWER
}

enum class MainDestination(
    val route: String,
    val labelKey: StringResources.() -> String,
    val icon: @Composable () -> Unit
) {
    ALBUM("album", { navAlbum }, { Icon(Icons.Default.PhotoLibrary, contentDescription = stringRes { navAlbum }) }),
    AI_SEARCH("ai_search", { navAiSearch }, { Icon(Icons.Default.Search, contentDescription = stringRes { navAi }) }),
    SETTINGS("settings", { navSettings }, { Icon(Icons.Default.Settings, contentDescription = stringRes { navSettings }) })
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun MainScreen(
    navController: NavHostController = androidx.navigation.compose.rememberNavController(),
    windowSizeClass: WindowSizeClass,
    projectName: String = "Alcedo Studio"
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val navigationType = when (windowSizeClass.widthSizeClass) {
        WindowWidthSizeClass.Compact -> NavigationType.BOTTOM_NAVIGATION
        WindowWidthSizeClass.Medium -> NavigationType.NAVIGATION_RAIL
        else -> NavigationType.PERMANENT_NAVIGATION_DRAWER
    }

    val bottomBarDestinations = MainDestination.entries
    val showBottomBar = currentRoute in bottomBarDestinations.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar && navigationType == NavigationType.BOTTOM_NAVIGATION) {
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
    ) { padding ->
        when (navigationType) {
            NavigationType.BOTTOM_NAVIGATION -> {
                NavHost(
                    navController = navController,
                    startDestination = MainDestination.ALBUM.route,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    alcedoNavGraph(navController)
                }
            }
            NavigationType.NAVIGATION_RAIL -> {
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
                        alcedoNavGraph(navController)
                    }
                }
            }
            NavigationType.PERMANENT_NAVIGATION_DRAWER -> {
                PermanentNavigationDrawer(
                    drawerContent = {
                        PermanentDrawerSheet(
                            modifier = Modifier.width(240.dp)
                        ) {
                            Spacer(modifier = Modifier.height(16.dp))
                            bottomBarDestinations.forEach { destination ->
                                val selected = currentRoute == destination.route
                                NavigationDrawerItem(
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
                                        Text(stringRes(destination.labelKey))
                                    },
                                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                                )
                            }
                        }
                    }
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = MainDestination.ALBUM.route,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                    ) {
                        alcedoNavGraph(navController)
                    }
                }
            }
        }
    }
}

private fun NavGraphBuilder.alcedoNavGraph(navController: NavHostController) {
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
