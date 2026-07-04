package com.alcedo.studio.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.alcedo.studio.data.model.AiModelProfile
import com.alcedo.studio.di.AppModule

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    val aiService = AppModule.aiService
    val models = remember { aiService.getModelCatalog() }
    var selectedTheme by remember { mutableStateOf("System") }
    var autoAnalyze by remember { mutableStateOf(true) }
    var cacheSize by remember { mutableStateOf("1.2 GB") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            ListItem(
                headlineContent = { Text("Appearance") },
                supportingContent = { Text("Theme: $selectedTheme") }
            )
            ListItem(
                headlineContent = { Text("Storage") },
                supportingContent = { Text("Cache: $cacheSize") },
                trailingContent = {
                    TextButton(onClick = { /* clear cache */ }) {
                        Text("Clear")
                    }
                }
            )
            ListItem(
                headlineContent = { Text("AI Auto-Analysis") },
                supportingContent = { Text("Automatically tag new images") },
                trailingContent = {
                    Switch(checked = autoAnalyze, onCheckedChange = { autoAnalyze = it })
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text(
                "AI Models",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            models.forEach { model ->
                AiModelItem(model = model)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text(
                "About",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            ListItem(
                headlineContent = { Text("Version") },
                supportingContent = { Text("0.2.6-android") }
            )
            ListItem(
                headlineContent = { Text("Credits") },
                supportingContent = { Text("Alcedo Studio Team") }
            )
        }
    }
}

@Composable
private fun AiModelItem(model: AiModelProfile) {
    ListItem(
        headlineContent = { Text(model.modelName) },
        supportingContent = { Text(model.description) },
        trailingContent = {
            if (model.isDownloaded) {
                if (model.isActive) {
                    Badge { Text("Active") }
                } else {
                    TextButton(onClick = { }) { Text("Activate") }
                }
            } else {
                TextButton(onClick = { }) { Text("Download") }
            }
        }
    )
}
