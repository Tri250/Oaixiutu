package com.alcedo.studio.ui.editor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.alcedo.studio.domain.service.ExifData
import com.alcedo.studio.domain.service.ExifEditorService
import com.alcedo.studio.i18n.Strings
import com.alcedo.studio.i18n.stringRes
import kotlinx.coroutines.launch

/**
 * A full-screen dialog that lets the user edit the editable EXIF fields of an image
 * and view the read-only capture fields. Writes go through [ExifEditorService].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExifEditorDialog(
    filePath: String,
    onDismiss: () -> Unit
) {
    val service = remember { ExifEditorService() }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var original by remember { mutableStateOf<ExifData?>(null) }
    var editable by remember { mutableStateOf<ExifData?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var gpsCleared by remember { mutableStateOf(false) }

    // Load EXIF on open
    LaunchedEffect(filePath) {
        val data = runCatching { service.readExif(filePath) }.getOrNull()
        if (data != null) {
            original = data
            editable = data
        }
    }

    val current = editable
    val orig = original

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(stringRes { exifEditTitle }) },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = null)
                            }
                        },
                        actions = {
                            TextButton(
                                onClick = {
                                    orig?.let { editable = it; gpsCleared = false }
                                },
                                enabled = orig != null && editable != orig
                            ) {
                                Text(stringRes { exifReset })
                            }
                        }
                    )
                },
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    BottomAppBar(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        Button(
                            onClick = {
                                val toSave = editable ?: return@Button
                                isSaving = true
                                scope.launch {
                                    val ok = service.writeExif(filePath, toSave)
                                    isSaving = false
                                    if (ok) {
                                        original = toSave
                                        snackbarHostState.showSnackbar(Strings.current.exifSaved)
                                    } else {
                                        snackbarHostState.showSnackbar(Strings.current.exifSaveFailed)
                                    }
                                }
                            },
                            enabled = current != null && !isSaving && current != orig,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(Icons.Default.Save, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringRes { exifSave })
                            }
                        }
                    }
                }
            ) { padding ->
                if (current == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                    return@Scaffold
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // ── Editable fields ──
                    SectionLabel("Editable")

                    EditableField(
                        label = stringRes { exifAuthor },
                        value = current.author ?: "",
                        leadingIcon = Icons.Default.Person
                    ) { v ->
                        editable = current.copy(author = v.ifBlank { null })
                    }
                    EditableField(
                        label = stringRes { exifCopyright },
                        value = current.copyright ?: "",
                        leadingIcon = Icons.Default.Copyright
                    ) { v ->
                        editable = current.copy(copyright = v.ifBlank { null })
                    }
                    EditableField(
                        label = stringRes { exifTitle },
                        value = current.title ?: "",
                        leadingIcon = Icons.Default.Title
                    ) { v ->
                        editable = current.copy(title = v.ifBlank { null })
                    }
                    EditableField(
                        label = stringRes { exifComment },
                        value = current.comment ?: "",
                        leadingIcon = Icons.Default.Comment,
                        singleLine = false
                    ) { v ->
                        editable = current.copy(comment = v.ifBlank { null })
                    }
                    EditableField(
                        label = stringRes { exifDateTime },
                        value = current.dateTime ?: "",
                        leadingIcon = Icons.Default.Schedule,
                        placeholder = "yyyy:MM:dd HH:mm:ss"
                    ) { v ->
                        editable = current.copy(dateTime = v.ifBlank { null })
                    }

                    HorizontalDivider()

                    // ── Read-only capture fields ──
                    SectionLabel("Camera")
                    ReadOnlyField(stringRes { exifMake }, current.make)
                    ReadOnlyField(stringRes { exifModel }, current.model)
                    ReadOnlyField(stringRes { exifLensModel }, current.lensModel)
                    ReadOnlyField(stringRes { exifFocalLength }, current.focalLength)
                    ReadOnlyField(stringRes { exifFNumber }, current.fNumber)
                    ReadOnlyField(stringRes { exifIso }, current.iso)
                    ReadOnlyField(stringRes { exifExposureTime }, current.exposureTime)
                    ReadOnlyField(stringRes { exifDateTimeOriginal }, current.dateTimeOriginal)
                    ReadOnlyField("Software", current.software)

                    HorizontalDivider()

                    // ── GPS ──
                    SectionLabel("GPS")
                    ReadOnlyField(stringRes { exifGpsLatitude }, current.gpsLatitude)
                    ReadOnlyField(stringRes { exifGpsLongitude }, current.gpsLongitude)
                    val hasGps = !current.gpsLatitude.isNullOrBlank() || !current.gpsLongitude.isNullOrBlank()
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val ok = service.clearExifFields(
                                    filePath,
                                    listOf(
                                        androidx.exifinterface.media.ExifInterface.TAG_GPS_LATITUDE,
                                        androidx.exifinterface.media.ExifInterface.TAG_GPS_LONGITUDE,
                                        androidx.exifinterface.media.ExifInterface.TAG_GPS_LATITUDE_REF,
                                        androidx.exifinterface.media.ExifInterface.TAG_GPS_LONGITUDE_REF
                                    )
                                )
                                if (ok) {
                                    val cleared = current.copy(
                                        gpsLatitude = null,
                                        gpsLongitude = null
                                    )
                                    editable = cleared
                                    original = cleared
                                    gpsCleared = true
                                    snackbarHostState.showSnackbar(Strings.current.exifGpsCleared)
                                } else {
                                    snackbarHostState.showSnackbar(Strings.current.exifSaveFailed)
                                }
                            }
                        },
                        enabled = hasGps,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.LocationOff, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringRes { exifGpsClear })
                    }

                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun EditableField(
    label: String,
    value: String,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector,
    placeholder: String? = null,
    singleLine: Boolean = true,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        leadingIcon = { Icon(leadingIcon, contentDescription = null) },
        singleLine = singleLine,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
private fun ReadOnlyField(label: String, value: String?) {
    OutlinedTextField(
        value = value ?: "—",
        onValueChange = {},
        label = { Text(label) },
        readOnly = true,
        enabled = false,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    )
}
