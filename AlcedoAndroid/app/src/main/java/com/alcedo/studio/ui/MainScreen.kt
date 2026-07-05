package com.alcedo.studio.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.alcedo.studio.i18n.StringResources
import com.alcedo.studio.i18n.stringRes
import com.alcedo.studio.ui.album.AlbumScreen
import com.alcedo.studio.ui.album.StatsView
import com.alcedo.studio.ui.ai.AiModelManagerScreen
import com.alcedo.studio.ui.ai.AiSearchScreen
import com.alcedo.studio.ui.common.LiquidGlassPanel
import com.alcedo.studio.ui.common.LiquidGlassToolbar
import com.alcedo.studio.ui.common.NavTransitions
import com.alcedo.studio.ui.editor.EditorScreen
import com.alcedo.studio.ui.export.ExportScreen
import com.alcedo.studio.ui.settings.AboutPage
import com.alcedo.studio.ui.settings.SettingsScreen

enum class NavigationType {
    BOTTOM_NAVIGATION, NAVIGATION_RAIL, PERMANENT_NAVIGATION_DRAWER
}

enum class MainDestination(
    val route: String,
    val labelKey: StringResources.() -> String,
    val icon: ImageVector
) {
    ALBUM("album", { navAlbum }, Icons.Default.PhotoLibrary),
    AI_SEARCH("ai_search", { navAiSearch }, Icons.Default.Search),
    SETTINGS("settings", { navSettings }, Icons.Default.Settings)
}

// Routes rendered as top-level tabs – used to pick fade vs slide transitions.
private val tabRoutes: Set<String> = MainDestination.entries.map { it.route }.toSet()

private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch(): Boolean {
    val from = initialState.destination.route
    val to = targetState.destination.route
    return from in tabRoutes && to in tabRoutes
}

// ── NavTransitions adapters ──────────────────────────────────────────
// Tab switches (album/ai_search/settings) use fadeTransition().
// Detail pages (editor/export/…) use slideInRight() for push, slideInLeft() for pop.
private fun alcedoNavEnter(scope: AnimatedContentTransitionScope<NavBackStackEntry>): EnterTransition =
    if (scope.isTabSwitch()) NavTransitions.fadeTransition().targetContentEnter
    else NavTransitions.slideInRight().targetContentEnter

private fun alcedoNavExit(scope: AnimatedContentTransitionScope<NavBackStackEntry>): ExitTransition =
    if (scope.isTabSwitch()) NavTransitions.fadeTransition().initialContentExit
    else NavTransitions.slideInRight().initialContentExit

private fun alcedoNavPopEnter(scope: AnimatedContentTransitionScope<NavBackStackEntry>): EnterTransition =
    if (scope.isTabSwitch()) NavTransitions.fadeTransition().targetContentEnter
    else NavTransitions.slideInLeft().targetContentEnter

private fun alcedoNavPopExit(scope: AnimatedContentTransitionScope<NavBackStackEntry>): ExitTransition =
    if (scope.isTabSwitch()) NavTransitions.fadeTransition().initialContentExit
    else NavTransitions.slideInLeft().initialContentExit

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
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
                // Floating liquid-glass bottom navigation bar
                Box(
                    Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    LiquidGlassToolbar(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(28.dp)
                    ) {
                        NavigationBar(
                            containerColor = Color.Transparent,
                            tonalElevation = 0.dp,
                            windowInsets = WindowInsets(0, 0, 0, 0)
                        ) {
                            bottomBarDestinations.forEach { destination ->
                                AlcedoNavItem(
                                    selected = currentRoute == destination.route,
                                    onClick = {
                                        navController.navigate(destination.route) {
                                            popUpTo(MainDestination.ALBUM.route) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = destination.icon,
                                    labelKey = destination.labelKey
                                )
                            }
                        }
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
                        .padding(padding),
                    enterTransition = { alcedoNavEnter(this) },
                    exitTransition = { alcedoNavExit(this) },
                    popEnterTransition = { alcedoNavPopEnter(this) },
                    popExitTransition = { alcedoNavPopExit(this) }
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
                    // Floating liquid-glass navigation rail
                    Box(
                        Modifier
                            .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp)
                    ) {
                        LiquidGlassToolbar(
                            modifier = Modifier.fillMaxHeight(),
                            shape = RoundedCornerShape(28.dp)
                        ) {
                            NavigationRail(
                                containerColor = Color.Transparent,
                                windowInsets = WindowInsets(0, 0, 0, 0)
                            ) {
                                Spacer(modifier = Modifier.weight(1f))
                                bottomBarDestinations.forEach { destination ->
                                    AlcedoRailItem(
                                        selected = currentRoute == destination.route,
                                        onClick = {
                                            navController.navigate(destination.route) {
                                                popUpTo(MainDestination.ALBUM.route) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        icon = destination.icon,
                                        labelKey = destination.labelKey
                                    )
                                }
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }

                    NavHost(
                        navController = navController,
                        startDestination = MainDestination.ALBUM.route,
                        modifier = Modifier.fillMaxSize(),
                        enterTransition = { alcedoNavEnter(this) },
                        exitTransition = { alcedoNavExit(this) },
                        popEnterTransition = { alcedoNavPopEnter(this) },
                        popExitTransition = { alcedoNavPopExit(this) }
                    ) {
                        alcedoNavGraph(navController)
                    }
                }
            }
            NavigationType.PERMANENT_NAVIGATION_DRAWER -> {
                PermanentNavigationDrawer(
                    drawerContent = {
                        PermanentDrawerSheet(
                            modifier = Modifier.width(280.dp),
                            drawerContainerColor = Color.Transparent,
                            drawerContentColor = MaterialTheme.colorScheme.onSurface
                        ) {
                            Box(
                                Modifier
                                    .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp)
                            ) {
                                LiquidGlassPanel(
                                    modifier = Modifier.fillMaxHeight(),
                                    shape = RoundedCornerShape(28.dp)
                                ) {
                                    Spacer(modifier = Modifier.height(20.dp))
                                    Text(
                                        text = projectName,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
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
                                            icon = {
                                                AlcedoNavIcon(
                                                    selected = selected,
                                                    imageVector = destination.icon,
                                                    contentDescription = stringRes(destination.labelKey)
                                                )
                                            },
                                            label = {
                                                Text(stringRes(destination.labelKey))
                                            },
                                            colors = NavigationDrawerItemDefaults.colors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                            ),
                                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                                        )
                                    }
                                }
                            }
                        }
                    }
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = MainDestination.ALBUM.route,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        enterTransition = { alcedoNavEnter(this) },
                        exitTransition = { alcedoNavExit(this) },
                        popEnterTransition = { alcedoNavPopEnter(this) },
                        popExitTransition = { alcedoNavPopExit(this) }
                    ) {
                        alcedoNavGraph(navController)
                    }
                }
            }
        }
    }
}

// ── Bottom navigation item with liquid-glass aware styling ───────────
@Composable
private fun RowScope.AlcedoNavItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    labelKey: StringResources.() -> String
) {
    val label = stringRes(labelKey)
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = {
            AlcedoNavIcon(
                selected = selected,
                imageVector = icon,
                contentDescription = label
            )
        },
        label = {
            Text(label, style = MaterialTheme.typography.labelSmall)
        },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        )
    )
}

// ── Navigation rail item with consistent styling ────────────────────
@Composable
private fun AlcedoRailItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    labelKey: StringResources.() -> String
) {
    val label = stringRes(labelKey)
    NavigationRailItem(
        selected = selected,
        onClick = onClick,
        icon = {
            AlcedoNavIcon(
                selected = selected,
                imageVector = icon,
                contentDescription = label
            )
        },
        label = {
            Text(label, style = MaterialTheme.typography.labelSmall)
        },
        colors = NavigationRailItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        )
    )
}

// ── Icon with subtle scale spring animation when selected ───────────
@Composable
private fun AlcedoNavIcon(
    selected: Boolean,
    imageVector: ImageVector,
    contentDescription: String
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.18f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "navIconScale"
    )
    Box(modifier = Modifier.scale(scale)) {
        Icon(imageVector, contentDescription = contentDescription)
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
