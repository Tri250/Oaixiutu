package com.alcedo.studio.ui.editor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alcedo.studio.data.model.TransferParamGroup

enum class TransferMode(val label: String) {
    PASTE("Paste (Replace)"),
    MERGE("Merge (Combine)")
}

@Composable
fun AdjustmentTransferDialog(
    onDismiss: () -> Unit,
    onApply: (TransferMode, Set<TransferParamGroup>) -> Unit,
    availableImages: List<Pair<Long, String>> = emptyList(),
    modifier: Modifier = Modifier
) {
    var sourceImageId by remember { mutableStateOf<Long?>(null) }
    var transferMode by remember { mutableStateOf(TransferMode.PASTE) }
    val selectedGroups = remember { mutableStateSetOf<TransferParamGroup>() }

    // Initialize with ALL selected
    LaunchedEffect(Unit) {
        if (selectedGroups.isEmpty()) {
            selectedGroups.add(TransferParamGroup.ALL)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text("Transfer Adjustments") },
        icon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Source image selection
                Text("Source Image", style = MaterialTheme.typography.labelLarge)
                if (availableImages.isEmpty()) {
                    OutlinedTextField(
                        value = sourceImageId?.toString() ?: "",
                        onValueChange = { sourceImageId = it.toLongOrNull() },
                        label = { Text("Image ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) }
                    )
                } else {
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it }
                    ) {
                        OutlinedTextField(
                            value = availableImages.find { it.first == sourceImageId }?.second ?: "Select source image",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            availableImages.forEach { (id, name) ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = {
                                        sourceImageId = id
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                HorizontalDivider()

                // Transfer mode
                Text("Transfer Mode", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TransferMode.entries.forEach { mode ->
                        FilterChip(
                            selected = transferMode == mode,
                            onClick = { transferMode = mode },
                            label = { Text(mode.label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                HorizontalDivider()

                // Category checkboxes
                Text("Adjustment Categories", style = MaterialTheme.typography.labelLarge)

                val categoryItems = listOf(
                    TransferParamGroup.ALL to "All",
                    TransferParamGroup.EXPOSURE to "Exposure",
                    TransferParamGroup.COLOR to "Color",
                    TransferParamGroup.TONE_CURVE to "Tone Curve",
                    TransferParamGroup.HSL to "HSL",
                    TransferParamGroup.COLOR_WHEEL to "Color Wheels",
                    TransferParamGroup.WHITE_BALANCE to "White Balance",
                    TransferParamGroup.GEOMETRY to "Geometry",
                    TransferParamGroup.DETAIL to "Detail (Sharpness/Clarity)",
                    TransferParamGroup.TINT to "Split Toning",
                    TransferParamGroup.FILM_GRAIN to "Film Grain",
                    TransferParamGroup.HALATION to "Halation",
                    TransferParamGroup.DISPLAY to "Display Transform",
                    TransferParamGroup.RAW_DECODE to "RAW Decode"
                )

                categoryItems.forEach { (group, label) ->
                    val isAllGroup = group == TransferParamGroup.ALL
                    val isChecked = group in selectedGroups

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { checked ->
                                if (isAllGroup) {
                                    if (checked) {
                                        selectedGroups.clear()
                                        selectedGroups.add(TransferParamGroup.ALL)
                                    } else {
                                        selectedGroups.remove(TransferParamGroup.ALL)
                                    }
                                } else {
                                    selectedGroups.remove(TransferParamGroup.ALL)
                                    if (checked) {
                                        selectedGroups.add(group)
                                    } else {
                                        selectedGroups.remove(group)
                                    }
                                }
                            }
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isAllGroup) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                HorizontalDivider()

                // Preview of adjustments
                Text("Adjustments Preview", style = MaterialTheme.typography.labelLarge)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        if (selectedGroups.contains(TransferParamGroup.ALL)) {
                            Text(
                                "All adjustment categories will be transferred",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else if (selectedGroups.isEmpty()) {
                            Text(
                                "No categories selected",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            selectedGroups.forEach { group ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        categoryItems.find { it.first == group }?.second ?: group.name,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            "Mode: ${transferMode.label}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            if (transferMode == TransferMode.PASTE)
                                "Source adjustments will replace all existing adjustments on target"
                            else
                                "Source adjustments will be merged with existing adjustments on target",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onApply(transferMode, selectedGroups.toSet()) },
                enabled = selectedGroups.isNotEmpty()
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
