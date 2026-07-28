package com.alcedo.studio.ui.export

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Share
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
import androidx.compose.ui.platform.LocalContext
import com.alcedo.studio.i18n.Strings
import com.alcedo.studio.ui.theme.AlcedoColors
import com.alcedo.studio.ui.theme.DesignTokens
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Share panel for a completed export. Offers "Share" (system share sheet) and
 * "Show in files" actions for the produced output file. Rendered as a dialog.
 */
@Composable
fun SharePanel(
    outputPath: String,
    onDismiss: () -> Unit,
) {
    val s = Strings.res
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
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
            Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm)) {
                OutlinedButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { openInFiles(context, file) }
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
                    withContext(Dispatchers.IO) { shareFile(context, file) }
                }
            }) {
                Icon(Icons.Outlined.Share, contentDescription = null, tint = AlcedoColors.AccentBlue)
                Text(s.exportButton, color = AlcedoColors.AccentBlue)
            }
        },
    )
}

private fun shareFile(context: android.content.Context, file: File) {
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
    context.startActivity(Intent.createChooser(intent, "Share"))
}

private fun openInFiles(context: android.content.Context, file: File) {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "image/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(intent) }
}
