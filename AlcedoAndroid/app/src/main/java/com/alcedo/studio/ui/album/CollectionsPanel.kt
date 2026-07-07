package com.alcedo.studio.ui.album

import android.graphics.Bitmap
import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.alcedo.studio.i18n.stringRes

data class CollectionUiModel(
    val collectionId: Long,
    val name: String,
    val description: String = "",
    val imageCount: Int = 0,
    val coverImageId: Long? = null
)

data class CollectionImageUiModel(
    val imageId: Long,
    val imageName: String,
    val imagePath: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionsPanel(
    collections: List<CollectionUiModel>,
    selectedCollection: CollectionUiModel?,
    collectionImages: List<CollectionImageUiModel>,
    onCreateCollection: (String, String) -> Unit,
    onRenameCollection: (Long, String) -> Unit,
    onDeleteCollection: (Long) -> Unit,
    onSelectCollection: (Long) -> Unit,
    onAddImageToCollection: (Long, Long) -> Unit,
    onRemoveImageFromCollection: (Long, Long) -> Unit,
    onImageClick: (Long) -> Unit,
    onPickImages: () -> Unit = {},
    getThumbnail: (Long) -> Bitmap? = { null },
    modifier: Modifier = Modifier
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf<CollectionUiModel?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<CollectionUiModel?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringRes { this.collections }, style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = stringRes { newCollection })
            }
        }

        HorizontalDivider()

        if (selectedCollection == null) {
            // Collection list
            if (collections.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Collections,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            stringRes { noCollectionsYet },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(onClick = { showCreateDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringRes { createCollection })
                        }
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(collections, key = { it.collectionId }) { collection ->
                        CollectionItem(
                            collection = collection,
                            isSelected = selectedCollection?.collectionId == collection.collectionId,
                            coverBitmap = collection.coverImageId?.let { getThumbnail(it) },
                            onClick = { onSelectCollection(collection.collectionId) },
                            onRename = { showRenameDialog = collection },
                            onDelete = { showDeleteConfirm = collection }
                        )
                    }
                }
            }
        } else {
            // Collection detail view
            CollectionDetailView(
                collection = selectedCollection,
                images = collectionImages,
                onBack = { onSelectCollection(-1L) },
                onAddImage = { imageId -> onAddImageToCollection(selectedCollection.collectionId, imageId) },
                onRemoveImage = { imageId -> onRemoveImageFromCollection(selectedCollection.collectionId, imageId) },
                onImageClick = onImageClick,
                onPickImages = onPickImages
            )
        }
    }

    // Create dialog
    if (showCreateDialog) {
        CreateCollectionDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, desc ->
                onCreateCollection(name, desc)
                showCreateDialog = false
            }
        )
    }

    // Rename dialog
    showRenameDialog?.let { collection ->
        RenameCollectionDialog(
            currentName = collection.name,
            onDismiss = { showRenameDialog = null },
            onRename = { newName ->
                onRenameCollection(collection.collectionId, newName)
                showRenameDialog = null
            }
        )
    }

    // Delete confirm
    showDeleteConfirm?.let { collection ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringRes { deleteCollection }) },
            text = { Text(stringRes { deleteCollectionMessage }.format(collection.name)) },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteCollection(collection.collectionId)
                        showDeleteConfirm = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text(stringRes { delete }) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) { Text(stringRes { cancel }) }
            }
        )
    }
}

@Composable
private fun CollectionItem(
    collection: CollectionUiModel,
    isSelected: Boolean,
    coverBitmap: Bitmap?,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    ListItem(
        headlineContent = { Text(collection.name) },
        supportingContent = {
            Text(
                if (collection.description.isNotEmpty()) collection.description
                else stringRes { albumImagesCount }.format(collection.imageCount)
            )
        },
        leadingContent = {
            if (coverBitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = coverBitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = MaterialTheme.shapes.small,
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Collections,
                            contentDescription = null,
                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        trailingContent = {
            Row {
                IconButton(onClick = onRename) {
                    Icon(Icons.Default.Edit, contentDescription = stringRes { rename }, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = stringRes { delete }, modifier = Modifier.size(18.dp))
                }
            }
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CollectionDetailView(
    collection: CollectionUiModel,
    images: List<CollectionImageUiModel>,
    onBack: () -> Unit,
    onAddImage: (Long) -> Unit,
    onRemoveImage: (Long) -> Unit,
    onImageClick: (Long) -> Unit,
    onPickImages: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Header with back
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = stringRes { back })
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(collection.name, style = MaterialTheme.typography.titleMedium)
                if (collection.description.isNotEmpty()) {
                    Text(
                        collection.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onPickImages) {
                Icon(Icons.Default.AddPhotoAlternate, contentDescription = stringRes { addImages })
            }
        }

        HorizontalDivider()

        if (images.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Image,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringRes { noImagesInCollection },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = onPickImages) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringRes { addImages })
                    }
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(images, key = { it.imageId }) { image ->
                    ListItem(
                        headlineContent = { Text(image.imageName) },
                        leadingContent = {
                            Surface(
                                modifier = Modifier.size(40.dp),
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        image.imageName.take(1).uppercase(),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        trailingContent = {
                            IconButton(onClick = { onRemoveImage(image.imageId) }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringRes { remove },
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        modifier = Modifier.clickable { onImageClick(image.imageId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CreateCollectionDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes { newCollection }) },
        icon = { Icon(Icons.Default.Collections, contentDescription = null) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringRes { name }) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringRes { descriptionOptional }) },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name.trim(), description.trim()) },
                enabled = name.isNotBlank()
            ) { Text(stringRes { create }) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringRes { cancel }) }
        }
    )
}

@Composable
private fun RenameCollectionDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit
) {
    var newName by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes { renameCollection }) },
        text = {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text(stringRes { name }) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onRename(newName.trim()) },
                enabled = newName.isNotBlank()
            ) { Text(stringRes { rename }) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringRes { cancel }) }
        }
    )
}
