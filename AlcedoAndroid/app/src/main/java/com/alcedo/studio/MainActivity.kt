package com.alcedo.studio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.alcedo.studio.ui.album.AlbumScreen
import com.alcedo.studio.ui.editor.EditorScreen
import com.alcedo.studio.ui.export.ExportScreen
import com.alcedo.studio.ui.settings.SettingsScreen
import com.alcedo.studio.ui.theme.AlcedoTheme

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
                    NavHost(
                        navController = navController,
                        startDestination = "album"
                    ) {
                        composable("album") {
                            AlbumScreen(navController = navController)
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
                    }
                }
            }
        }
    }
}
