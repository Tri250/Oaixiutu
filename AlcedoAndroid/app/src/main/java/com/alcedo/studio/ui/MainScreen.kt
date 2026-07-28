package com.alcedo.studio.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.alcedo.studio.i18n.Strings
import com.alcedo.studio.ui.album.AlbumScreen
import com.alcedo.studio.ui.ai.AiModelManagerScreen
import com.alcedo.studio.ui.ai.AiRatingScreen
import com.alcedo.studio.ui.ai.AiSearchScreen
import com.alcedo.studio.ui.editor.EditorScreen
import com.alcedo.studio.ui.export.ExportScreen
import com.alcedo.studio.ui.onboarding.OnboardingScreen
import com.alcedo.studio.ui.settings.AboutPage
import com.alcedo.studio.ui.settings.PrivacyPolicyScreen
import com.alcedo.studio.ui.settings.SettingsScreen
import com.alcedo.studio.ui.settings.UserAgreementScreen
import com.alcedo.studio.ui.theme.AlcedoColors
import com.alcedo.studio.ui.theme.DesignTokens

/**
 * Centralised route constants. The editor route carries an `imageId` argument
 * so it can be opened for a specific image from the album grid.
 */
object Routes {
    const val ALBUM = "album"
    const val EDITOR = "editor"
    const val AI = "ai"
    const val SETTINGS = "settings"

    const val ONBOARDING = "onboarding"
    const val EXPORT = "export"
    const val ABOUT = "about"
    const val PRIVACY = "privacy"
    const val AGREEMENT = "agreement"
    const val AI_SEARCH = "ai_search"
    const val AI_RATING = "ai_rating"
    const val AI_MODELS = "ai_models"

    /** Editor route with an imageId path segment. */
    const val EDITOR_WITH_IMAGE = "editor/{imageId}"
    fun editorRoute(imageId: String) = "editor/$imageId"

    /** Export route with an optional imageId. */
    const val EXPORT_WITH_IMAGE = "export?imageId={imageId}"
    fun exportRoute(imageId: String? = null) = "export?imageId=${imageId ?: ""}"
}

private data class TopDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

@Composable
private fun topDestinations(): List<TopDestination> {
    val s = Strings.res
    return listOf(
        TopDestination(Routes.ALBUM, s.tabAlbum, Icons.Outlined.Collections),
        TopDestination(Routes.EDITOR, s.tabEditor, Icons.Outlined.Edit),
        TopDestination(Routes.AI, s.tabAi, Icons.Outlined.AutoAwesome),
        TopDestination(Routes.SETTINGS, s.tabSettings, Icons.Outlined.Settings),
    )
}

/**
 * Main navigation host. Renders a bottom navigation bar with the Album, Editor,
 * AI and Settings tabs and a [NavHost] that wires the album, editor (with
 * imageId argument), AI sub-screens, export and settings destinations.
 *
 * The editor/export routes accept an `imageId` so they can be pushed from the
 * album grid; tapping the Editor tab without an image shows the editor's empty
 * state which prompts the user to pick one.
 */
@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    startDestination: String = Routes.ALBUM,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val showBottomBar = currentRoute in setOf(
        Routes.ALBUM, Routes.EDITOR, Routes.AI, Routes.SETTINGS,
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                AlcedoBottomBar(
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        if (route != currentRoute) {
                            navController.navigate(route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            composable(Routes.ALBUM) {
                AlbumScreen(
                    onOpenImage = { imageId -> navController.navigate(Routes.editorRoute(imageId)) },
                    onExportSelected = { ids ->
                        navController.navigate(Routes.exportRoute(ids.firstOrNull()))
                    },
                )
            }
            composable(
                route = Routes.EDITOR_WITH_IMAGE,
                arguments = listOf(navArgument("imageId") { type = NavType.StringType }),
            ) { entry ->
                val imageId = entry.arguments?.getString("imageId").orEmpty()
                EditorScreen(
                    imageId = imageId,
                    onBack = { navController.popBackStack() },
                    onExport = { navController.navigate(Routes.exportRoute(imageId)) },
                )
            }
            composable(Routes.EDITOR) {
                EditorScreen(
                    imageId = null,
                    onBack = { navController.navigate(Routes.ALBUM) { popUpTo(0) } },
                    onExport = {},
                )
            }
            composable(Routes.AI) {
                AiHub(
                    onSearch = { navController.navigate(Routes.AI_SEARCH) },
                    onRating = { navController.navigate(Routes.AI_RATING) },
                    onModels = { navController.navigate(Routes.AI_MODELS) },
                )
            }
            composable(Routes.AI_SEARCH) {
                AiSearchScreen(
                    onBack = { navController.popBackStack() },
                    onOpenImage = { imageId ->
                        navController.navigate(Routes.editorRoute(imageId))
                    },
                )
            }
            composable(Routes.AI_RATING) {
                AiRatingScreen(
                    onBack = { navController.popBackStack() },
                    onOpenImage = { imageId -> navController.navigate(Routes.editorRoute(imageId)) },
                )
            }
            composable(Routes.AI_MODELS) {
                AiModelManagerScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onAbout = { navController.navigate(Routes.ABOUT) },
                    onPrivacy = { navController.navigate(Routes.PRIVACY) },
                    onAgreement = { navController.navigate(Routes.AGREEMENT) },
                    onManageSpace = { navController.navigate(Routes.PRIVACY) },
                )
            }
            composable(
                route = Routes.EXPORT_WITH_IMAGE,
                arguments = listOf(navArgument("imageId") {
                    type = NavType.StringType
                    defaultValue = ""
                    nullable = true
                }),
            ) { entry ->
                val imageId = entry.arguments?.getString("imageId").orEmpty()
                ExportScreen(
                    imageId = imageId.takeIf { it.isNotBlank() },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.ABOUT) { AboutPage(onBack = { navController.popBackStack() }) }
            composable(Routes.PRIVACY) { PrivacyPolicyScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.AGREEMENT) { UserAgreementScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.ONBOARDING) {
                OnboardingScreen(onDone = { navController.navigate(Routes.ALBUM) { popUpTo(0) } })
            }
        }
    }
}

@Composable
private fun AlcedoBottomBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
) {
    val destinations = topDestinations()
    NavigationBar(
        containerColor = AlcedoColors.Graphite,
        contentColor = AlcedoColors.TextSecondary,
    ) {
        destinations.forEach { dest ->
            val selected = currentRoute == dest.route ||
                (dest.route == Routes.EDITOR && currentRoute?.startsWith("editor/") == true)
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(dest.route) },
                icon = { Icon(imageVector = dest.icon, contentDescription = dest.label) },
                label = { Text(text = dest.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = AlcedoColors.AccentBlue,
                    selectedTextColor = AlcedoColors.AccentBlue,
                    indicatorColor = AlcedoColors.SurfaceSelected,
                    unselectedIconColor = AlcedoColors.TextTertiary,
                    unselectedTextColor = AlcedoColors.TextTertiary,
                ),
            )
        }
    }
}

/**
 * Landing hub for the AI tab. Presents the three AI tools (semantic search,
 * culling assist, model management) as a list of navigable cards.
 */
@Composable
private fun AiHub(
    onSearch: () -> Unit,
    onRating: () -> Unit,
    onModels: () -> Unit,
) {
    val s = Strings.res
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = s.aiTitle,
            style = MaterialTheme.typography.headlineMedium,
            color = AlcedoColors.TextPrimary,
            modifier = Modifier.padding(DesignTokens.spacingLg),
        )
        AiToolCard(s.aiSearch, s.searchHint, Icons.Outlined.Search, onSearch)
        AiToolCard(s.aiRating, s.aiCulling, Icons.Outlined.AutoAwesome, onRating)
        AiToolCard(s.aiModels, s.clip + " · " + s.siglip, Icons.Outlined.Download, onModels)
    }
}

@Composable
private fun AiToolCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = DesignTokens.spacingLg, vertical = DesignTokens.spacingXs),
        colors = CardDefaults.cardColors(containerColor = AlcedoColors.SurfaceRaised),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingLg),
            modifier = Modifier.padding(DesignTokens.spacingLg),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AlcedoColors.AccentBlue,
                modifier = Modifier.size(28.dp),
            )
            Column {
                Text(text = title, style = MaterialTheme.typography.titleMedium, color = AlcedoColors.TextPrimary)
                if (subtitle.isNotBlank()) {
                    Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = AlcedoColors.TextTertiary)
                }
            }
        }
    }
}
