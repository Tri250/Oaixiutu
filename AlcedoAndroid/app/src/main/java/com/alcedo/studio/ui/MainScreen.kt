package com.alcedo.studio.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.alcedo.studio.i18n.StringResources
import com.alcedo.studio.i18n.stringRes
import com.alcedo.studio.privacy.PrivacyConsentDialog
import com.alcedo.studio.privacy.PrivacyManager
import com.alcedo.studio.ui.album.AlbumScreen
import com.alcedo.studio.ui.album.StatsView
import com.alcedo.studio.ui.ai.AiModelManagerScreen
import com.alcedo.studio.ui.ai.AiRatingScreen
import com.alcedo.studio.ui.ai.AiSearchScreen
import com.alcedo.studio.ui.common.BackgroundTaskBar
import com.alcedo.studio.ui.common.HapticFeedback
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
    CREATE("ai_search", { navCreate }, Icons.Default.AutoAwesome),
    MINE("settings", { navMine }, Icons.Default.Person)
}

// 顶部 Tab 路由 – 相册/创作/我的
private val tabRoutes: Set<String> = MainDestination.entries.map { it.route }.toSet()

private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch(): Boolean {
    val from = initialState.destination.route
    val to = targetState.destination.route
    return from in tabRoutes && to in tabRoutes
}

// ── NavTransitions 适配器 ──────────────────────────────────────────
// Tab 切换（album/ai_search/settings）使用 fadeTransition()。
// 详情页（editor/export/…）使用 slideInRight() 推入，slideInLeft() 弹出。
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

    val context = LocalContext.current
    val view = LocalView.current

    // 通知权限请求 (Android 13+)
    val notificationPermissionState = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        // 用户决定后无需特殊处理，BackgroundTaskService 会在需要时检查
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionState.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // 后台任务进度浮层
    val taskSnapshots by com.alcedo.studio.di.AppModule.backgroundTaskService.tasks.collectAsStateWithLifecycle()
    val hasActiveTasks = taskSnapshots.any {
        it.status == com.alcedo.studio.domain.service.TaskStatus.RUNNING ||
            it.status == com.alcedo.studio.domain.service.TaskStatus.PENDING ||
            it.status == com.alcedo.studio.domain.service.TaskStatus.RETRYING
    }
    val onTaskCancel: (String) -> Unit = { taskId ->
        com.alcedo.studio.di.AppModule.backgroundTaskService.cancel(taskId)
    }

    // 首次启动隐私同意弹窗 (PIPL / GDPR 合规)
    var showPrivacyDialog by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (PrivacyManager.needsConsentDialog()) {
            showPrivacyDialog = true
        }
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar && navigationType == NavigationType.BOTTOM_NAVIGATION) {
                // 干净的 Material3 底部导航栏 – surfaceContainer 暖色背景,贴合 Hasselblad Dark 旗舰观感
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 0.dp
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
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
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
                    // 液态玻璃侧边导航栏
                    Box(
                        Modifier
                            .padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 6.dp)
                    ) {
                        LiquidGlassToolbar(
                            modifier = Modifier.fillMaxHeight(),
                            shape = RoundedCornerShape(32.dp)
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
                            modifier = Modifier.width(300.dp),
                            drawerContainerColor = Color.Transparent,
                            drawerContentColor = MaterialTheme.colorScheme.onSurface
                        ) {
                            Box(
                                Modifier
                                    .padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 6.dp)
                            ) {
                                LiquidGlassPanel(
                                    modifier = Modifier.fillMaxHeight(),
                                    shape = RoundedCornerShape(32.dp)
                                ) {
                                    Spacer(modifier = Modifier.height(28.dp))
                                    Text(
                                        text = projectName,
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    bottomBarDestinations.forEach { destination ->
                                        val selected = currentRoute == destination.route
                                        NavigationDrawerItem(
                                            selected = selected,
                                            onClick = {
                                                HapticFeedback.click(view)
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
                                                Text(
                                                    stringRes(destination.labelKey),
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.SemiBold
                                                    else androidx.compose.ui.text.font.FontWeight.Medium
                                                )
                                            },
                                            colors = NavigationDrawerItemDefaults.colors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
                                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                            ),
                                            modifier = Modifier.padding(
                                                horizontal = 12.dp,
                                                vertical = 4.dp
                                            ).height(56.dp)
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

        // 后台任务进度条
        if (hasActiveTasks) {
            BackgroundTaskBar(
                activeTasks = taskSnapshots,
                onCancelTask = onTaskCancel,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = if (showBottomBar && navigationType == NavigationType.BOTTOM_NAVIGATION) 80.dp else 8.dp)
            )
        }
        }
    }

    // 首次启动隐私同意弹窗
    if (showPrivacyDialog) {
        PrivacyConsentDialog(
            onDismiss = { showPrivacyDialog = false },
            onAccept = { showPrivacyDialog = false }
        )
    }
}

// ── 液态玻璃风格底部导航项 ───────────
@Composable
private fun RowScope.AlcedoNavItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    labelKey: StringResources.() -> String
) {
    val label = stringRes(labelKey)
    val view = LocalView.current
    NavigationBarItem(
        selected = selected,
        onClick = {
            HapticFeedback.click(view)
            onClick()
        },
        icon = {
            AlcedoNavIcon(
                selected = selected,
                imageVector = icon,
                contentDescription = label
            )
        },
        label = {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.SemiBold
                else androidx.compose.ui.text.font.FontWeight.Medium
            )
        },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
        )
    )
}

// ── 导航栏项（统一样式）────────────
@Composable
private fun AlcedoRailItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    labelKey: StringResources.() -> String
) {
    val label = stringRes(labelKey)
    val view = LocalView.current
    NavigationRailItem(
        selected = selected,
        onClick = {
            HapticFeedback.click(view)
            onClick()
        },
        icon = {
            AlcedoNavIcon(
                selected = selected,
                imageVector = icon,
                contentDescription = label
            )
        },
        label = {
            Text(
                label,
                style = if (selected) MaterialTheme.typography.labelMedium
                else MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.SemiBold
                else androidx.compose.ui.text.font.FontWeight.Medium
            )
        },
        colors = NavigationRailItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
        )
    )
}

// ── 选中时带轻微缩放弹簧动画的图标 ───────────
@Composable
private fun AlcedoNavIcon(
    selected: Boolean,
    imageVector: ImageVector,
    contentDescription: String
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.20f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "navIconScale"
    )
    val iconColor = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant
    Box(modifier = Modifier.scale(scale), contentAlignment = Alignment.Center) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = iconColor,
            modifier = Modifier.size(24.dp)
        )
    }
}

// 导航图 – 注册所有页面路由
private fun NavGraphBuilder.alcedoNavGraph(navController: NavHostController) {
    composable(MainDestination.ALBUM.route) {
        AlbumScreen(navController = navController)
    }
    composable(MainDestination.CREATE.route) {
        AiSearchScreen(navController = navController)
    }
    composable(MainDestination.MINE.route) {
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
    composable("ai_rating") {
        AiRatingScreen(navController = navController)
    }
    composable("about") {
        AboutPage(navController = navController)
    }
    composable("stats") {
        StatsView(navController = navController)
    }
}
