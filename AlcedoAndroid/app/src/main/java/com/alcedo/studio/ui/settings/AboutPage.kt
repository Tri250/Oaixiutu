package com.alcedo.studio.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.alcedo.studio.BuildConfig
import com.alcedo.studio.i18n.stringRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutPage(navController: NavController) {
    val context = LocalContext.current
    // Prefer compile-time BuildConfig values; fall back to PackageManager lookup
    // (and finally a sane constant) so the version is always shown consistently
    // with SettingsScreen.
    val versionName = remember {
        BuildConfig.VERSION_NAME.ifBlank {
            try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
                    ?: "1.1.5"
            } catch (_: Exception) {
                "1.1.5"
            }
        }
    }
    val versionCode = remember {
        try {
            val pi = context.packageManager.getPackageInfo(context.packageName, 0)
            @Suppress("DEPRECATION")
            if (pi.longVersionCode > 0) pi.longVersionCode.toInt() else BuildConfig.VERSION_CODE
        } catch (_: Exception) {
            BuildConfig.VERSION_CODE
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringRes { about }) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringRes { back })
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            // Logo / Branding
            Surface(
                modifier = Modifier.size(96.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.PhotoCamera,
                        contentDescription = "Alcedo Studio",
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Alcedo Studio",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Professional Photo Editor",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Version Info Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Version Information",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    HorizontalDivider()
                    InfoRow(label = "App Name", value = "Alcedo Studio")
                    InfoRow(label = "Version", value = versionName)
                    InfoRow(label = "Build Number", value = versionCode.toString())
                    InfoRow(label = "Platform", value = "Android")
                    InfoRow(label = "Min SDK", value = "API 28 (Android 9+)")
                    InfoRow(label = "Architecture", value = "ARM64 / x86_64")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // License Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "License",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    HorizontalDivider()
                    InfoRow(label = "License", value = "GPL-3.0")
                    Text(
                        text = "This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Credits Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Credits & Acknowledgments",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("Alcedo Studio Team") },
                        supportingContent = { Text("Core Development") },
                        leadingContent = { Icon(Icons.Default.Groups, contentDescription = null) }
                    )
                    ListItem(
                        headlineContent = { Text("ACES Developer Community") },
                        supportingContent = { Text("Color Science Implementation") },
                        leadingContent = { Icon(Icons.Default.Palette, contentDescription = null) }
                    )
                    ListItem(
                        headlineContent = { Text("LibRaw Contributors") },
                        supportingContent = { Text("RAW Image Decoding") },
                        leadingContent = { Icon(Icons.Default.Image, contentDescription = null) }
                    )
                    ListItem(
                        headlineContent = { Text("OpenCL / Vulkan Community") },
                        supportingContent = { Text("GPU Compute Framework") },
                        leadingContent = { Icon(Icons.Default.Memory, contentDescription = null) }
                    )
                    ListItem(
                        headlineContent = { Text("CLIP / SigLIP Researchers") },
                        supportingContent = { Text("AI Semantic Models") },
                        leadingContent = { Icon(Icons.Default.ModelTraining, contentDescription = null) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Links Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Links",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("GitHub Repository") },
                        supportingContent = { Text("github.com/alcedo-studio/alcedo") },
                        leadingContent = { Icon(Icons.Default.Code, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    ListItem(
                        headlineContent = { Text("Documentation") },
                        supportingContent = { Text("alcedo.studio/docs") },
                        leadingContent = { Icon(Icons.Default.MenuBook, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    ListItem(
                        headlineContent = { Text("Report an Issue") },
                        supportingContent = { Text("github.com/alcedo-studio/alcedo/issues") },
                        leadingContent = { Icon(Icons.Default.BugReport, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Third-Party Licenses
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Third-Party Libraries",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    HorizontalDivider()
                    listOf(
                        "Kotlin" to "Apache 2.0",
                        "Jetpack Compose" to "Apache 2.0",
                        "Jetpack Navigation" to "Apache 2.0",
                        "Room Database" to "Apache 2.0",
                        "Kotlinx Serialization" to "Apache 2.0",
                        "Kotlinx Coroutines" to "Apache 2.0",
                        "ExifInterface" to "Apache 2.0",
                        "LibRaw" to "LGPL-2.1",
                        "OpenCV" to "Apache 2.0",
                        "ONNX Runtime" to "MIT"
                    ).forEach { (name, license) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = license,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Made with \u2764 by Alcedo Studio Team",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
