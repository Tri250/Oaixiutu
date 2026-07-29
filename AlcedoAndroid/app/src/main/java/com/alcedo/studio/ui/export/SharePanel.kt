package com.alcedo.studio.ui.export

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Share
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import com.alcedo.studio.i18n.Strings
import com.alcedo.studio.ui.theme.AlcedoColors
import com.alcedo.studio.ui.theme.DesignTokens
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun SharePanel(
    outputPath: String,
    onDismiss: () -> Unit,
) {
    val s = Strings.res
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val file = remember(outputPath) { File(outputPath) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.exportComplete, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AlcedoColors.TextPrimary,
                )
                Text(
                    text = outputPath,
                    style = MaterialTheme.typography.bodySmall,
                    color = AlcedoColors.TextTertiary,
                )
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingXs),
            ) {
                // Copy path to clipboard
                OutlinedButton(onClick = {
                    runCatching {
                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(outputPath))
                    }.onFailure {
                        Toast.makeText(context, s.couldNotCopyPath, Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null, tint = AlcedoColors.AccentBlue)
                    Text(s.copyPath, color = AlcedoColors.AccentBlue)
                }
                // Save to folder
                OutlinedButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            runCatching { openInFiles(context, file) }
                                .onFailure {
                                    Toast.makeText(context, s.noAppToOpenFile, Toast.LENGTH_SHORT).show()
                                }
                        }
                    }
                }) {
                    Icon(Icons.Outlined.Folder, contentDescription = null, tint = AlcedoColors.AccentBlue)
                    Text(s.outputDirectory, color = AlcedoColors.AccentBlue)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = {
                scope.launch {
                    withContext(Dispatchers.IO) {
                        runCatching { shareFile(context, file) }
                            .onFailure {
                                Toast.makeText(context, s.couldNotShareFile, Toast.LENGTH_SHORT).show()
                            }
                    }
                }
            }) {
                Icon(Icons.Outlined.Share, contentDescription = null, tint = AlcedoColors.AccentBlue)
                Text(s.exportButton, color = AlcedoColors.AccentBlue)
            }
        },
    )
}

private fun shareFile(context: android.content.Context, file: File) {
    if (!file.exists()) {
        Toast.makeText(context, Strings.res.fileNoLongerExists, Toast.LENGTH_SHORT).show()
        return
    }
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/*"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, Strings.res.share))
}

private fun openInFiles(context: android.content.Context, file: File) {
    if (!file.exists()) {
        Toast.makeText(context, Strings.res.fileNoLongerExists, Toast.LENGTH_SHORT).show()
        return
    }
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "image/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(intent)
}
