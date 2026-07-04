package com.alcedo.studio.ui.export

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.alcedo.studio.data.model.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    navController: NavController,
    imageId: String
) {
    var format by remember { mutableStateOf(ExportFormat.JPEG) }
    var quality by remember { mutableStateOf(95) }
    var colorSpace by remember { mutableStateOf(ColorSpace.SRGB) }
    var embedIcc by remember { mutableStateOf(true) }
    var includeMetadata by remember { mutableStateOf(true) }
    var isHdr by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Export") },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Format", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExportFormat.entries.forEach { f ->
                    FilterChip(
                        selected = format == f,
                        onClick = { format = f },
                        label = { Text(f.name) }
                    )
                }
            }

            if (format == ExportFormat.JPEG || format == ExportFormat.ULTRA_HDR) {
                Text("Quality: $quality%", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = quality.toFloat(),
                    onValueChange = { quality = it.toInt() },
                    valueRange = 1f..100f
                )
            }

            Text("Color Space", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ColorSpace.entries.forEach { cs ->
                    FilterChip(
                        selected = colorSpace == cs,
                        onClick = { colorSpace = cs },
                        label = { Text(cs.name) }
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = embedIcc, onCheckedChange = { embedIcc = it })
                Text("Embed ICC Profile")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = includeMetadata, onCheckedChange = { includeMetadata = it })
                Text("Include Metadata")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = isHdr, onCheckedChange = { isHdr = it })
                Text("HDR Output")
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    // Trigger export
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Export Image")
            }
        }
    }
}
