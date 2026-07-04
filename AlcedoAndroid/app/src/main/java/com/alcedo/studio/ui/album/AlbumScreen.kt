package com.alcedo.studio.ui.album

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.staggeredgrid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.alcedo.studio.data.model.ImageModel
import com.alcedo.studio.ui.common.*
import com.alcedo.studio.viewmodel.AlbumViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AlbumScreen(
    navController: NavController,
    viewModel: AlbumViewModel = hiltViewModel()
) {
    val images by viewModel.images.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedImages = viewModel.selectedImages
    val showSearch = viewModel.showSearch.value
    val semanticEnabled = viewModel.semanticSearchEnabled.value
    val folders by viewModel.folders.collectAsState()
    val sortMode by viewModel.sortMode.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    var showFilterSheet by remember { mutableStateOf(false) }
    var showFolderSidebar by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val configuration = LocalConfiguration.current
    val columns = when {
        configuration.screenWidthDp >= 840 -> 5
        configuration.screenWidthDp >= 600 -> 4
        else -> 3
    }

    if (showFolderSidebar) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    FolderSidebar(
                        folders = folders,
                        onFolderSelected = { folderId ->
                            viewModel.selectFolder(folderId)
                            scope.launch { drawerState.close() }
                        },
                        onClose = {
                            scope.launch { drawerState.close() }
                            showFolderSidebar = false
                        }
                    )
                }
            }
        ) {
            AlbumContent(
                navController = navController,
                images = images,
                isLoading = isLoading,
                isRefreshing = isRefreshing,
                searchQuery = searchQuery,
                selectedImages = selectedImages,
                showSearch = showSearch,
                semanticEnabled = semanticEnabled,
                sortMode = sortMode,
                columns = columns,
                onSearchQueryChange = viewModel::onSearchQueryChange,
                onToggleSearch = viewModel::toggleSearch,
                onToggleSemantic = viewModel::toggleSemanticSearch,
                onToggleImageSelection = viewModel::toggleImageSelection,
                onClearSelection = viewModel::clearSelection,
                onDeleteSelected = viewModel::deleteSelected,
                onRefresh = viewModel::refresh,
                onSortModeChange = viewModel::setSortMode,
                onOpenFilter = { showFilterSheet = true },
                onOpenFolderSidebar = {
                    showFolderSidebar = true
                    scope.launch { drawerState.open() }
                },
                onImport = { showImport = true },
                onSettings = { navController.navigate("settings") }
            )
        }
    } else {
        AlbumContent(
            navController = navController,
            images = images,
            isLoading = isLoading,
            isRefreshing = isRefreshing,
            searchQuery = searchQuery,
            selectedImages = selectedImages,
            showSearch = showSearch,
            semanticEnabled = semanticEnabled,
            sortMode = sortMode,
            columns = columns,
            onSearchQueryChange = viewModel::onSearchQueryChange,
            onToggleSearch = viewModel::toggleSearch,
            onToggleSemantic = viewModel::toggleSemanticSearch,
            onToggleImageSelection = viewModel::toggleImageSelection,
            onClearSelection = viewModel::clearSelection,
            onDeleteSelected = viewModel::deleteSelected,
            onRefresh = viewModel::refresh,
            onSortModeChange = viewModel::setSortMode,
            onOpenFilter = { showFilterSheet = true },
            onOpenFolderSidebar = { showFolderSidebar = true },
            onImport = { showImport = true },
            onSettings = { navController.navigate("settings") }
        )
    }

    // Filter bottom sheet
    if (showFilterSheet) {
        FilterSheet(
            onDismiss = { showFilterSheet = false },
            onApply = { filter ->
                viewModel.applyFilter(filter)
                showFilterSheet = false
            },
            onReset = {
                viewModel.resetFilters()
                showFilterSheet = false
            }
        )
    }

    // Import dialog
    if (showImport) {
        ImportDialog(
            onDismiss = { showImport = false },
            onImportFiles = { uris ->
                uris.forEach { viewModel.importImage(it) }
                showImport = false
            },
            onImportDirectory = { path ->
                viewModel.importDirectory(path)
                showImport = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun AlbumContent(
    navController: NavController,
    images: List<ImageModel>,
    isLoading: Boolean,
    isRefreshing: Boolean,
    searchQuery: String,
    selectedImages: List<UInt>,
    showSearch: Boolean,
    semanticEnabled: Boolean,
    sortMode: SortMode,
    columns: Int,
    onSearchQueryChange: (String) -> Unit,
    onToggleSearch: () -> Unit,
    onToggleSemantic: () -> Unit,
    onToggleImageSelection: (UInt) -> Unit,
    onClearSelection: () -> Unit,
    onDeleteSelected: () -> Unit,
    onRefresh: () -> Unit,
    onSortModeChange: (SortMode) -> Unit,
    onOpenFilter: () -> Unit,
    onOpenFolderSidebar: () -> Unit,
    onImport: () -> Unit,
    onSettings: () -> Unit
) {
    Scaffold(
        topBar = {
            if (selectedImages.isNotEmpty()) {
                // Selection mode top bar
                AnimatedVisibility(
                    visible = selectedImages.isNotEmpty(),
                    enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
                ) {
                    TopAppBar(
                        title = { Text("${selectedImages.size} selected") },
                        navigationIcon = {
                            IconButton(onClick = onClearSelection) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        },
                        actions = {
                            IconButton(onClick = onDeleteSelected) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.error)
                            }
                            IconButton(onClick = { /* Share */ }) {
                                Icon(Icons.Default.Share, contentDescription = "Share")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            } else {
                TopAppBar(
                    title = {
                        if (showSearch) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = onSearchQueryChange,
                                placeholder = { Text("Search images...") },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Text("Alcedo Studio")
                        }
                    },
                    actions = {
                        IconButton(onClick = onToggleSearch) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                        if (showSearch) {
                            FilterChip(
                                selected = semanticEnabled,
                                onClick = onToggleSemantic,
                                label = { Text("AI", style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.padding(horizontal = 2.dp)
                            )
                        }
                        IconButton(onClick = onOpenFilter) {
                            Icon(Icons.Default.FilterList, contentDescription = "Filter")
                        }
                        IconButton(onClick = onOpenFolderSidebar) {
                            Icon(Icons.Default.Folder, contentDescription = "Folders")
                        }
                        IconButton(onClick = onSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onImport,
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Import")
            }
        }
    ) { padding ->
        AlcedoPullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                isLoading -> {
                    SkeletonAlbumGrid(
                        columns = columns,
                        itemCount = 12,
                        modifier = Modifier.padding(8.dp)
                    )
                }
                images.isEmpty() -> {
                    EmptyState(
                        icon = Icons.Default.PhotoLibrary,
                        title = "No images yet",
                        message = "Import photos to get started",
                        modifier = Modifier.align(Alignment.Center),
                        action = {
                            Button(onClick = onImport) {
                                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null,
                                    modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Import Photos")
                            }
                        }
                    )
                }
                else -> {
                    Column {
                        // Sort/filter bar
                        SortFilterBar(
                            sortMode = sortMode,
                            onSortModeChange = onSortModeChange,
                            imageCount = images.size
                        )

                        // Thumbnail grid
                        LazyVerticalStaggeredGrid(
                            columns = StaggeredGridCells.Fixed(columns),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalItemSpacing = 6.dp
                        ) {
                            items(images, key = { it.imageId }) { image ->
                                ThumbnailCard(
                                    image = image,
                                    isSelected = selectedImages.contains(image.imageId),
                                    onClick = {
                                        if (selectedImages.isNotEmpty()) {
                                            onToggleImageSelection(image.imageId)
                                        } else {
                                            navController.navigate("editor/${image.imageId}")
                                        }
                                    },
                                    onLongClick = { onToggleImageSelection(image.imageId) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SortFilterBar(
    sortMode: SortMode,
    onSortModeChange: (SortMode) -> Unit,
    imageCount: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "${imageCount} images",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            SortMode.entries.forEach { mode ->
                AssistChip(
                    onClick = { onSortModeChange(mode) },
                    label = { Text(mode.label, style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.height(28.dp),
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (sortMode == mode)
                            MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ThumbnailCard(
    image: ImageModel,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 0.9f else 1f,
        label = "selectScale"
    )
    Card(
        modifier = Modifier
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 4.dp else 0.dp
        )
    ) {
        Box {
            // Thumbnail placeholder
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    image.imageName.take(1).uppercase(),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                )
            }

            // Selection indicator
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                )
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier
                            .size(18.dp)
                            .padding(2.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            // Rating overlay
            if (image.imageId.toInt() % 5 == 0) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp)
                        .background(
                            Color.Black.copy(alpha = 0.5f),
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    repeat(3) { i ->
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            modifier = Modifier.size(10.dp),
                            tint = Color(0xFFFFD700)
                        )
                    }
                }
            }

            // AI label chips
            if (image.hasExif) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFF6B8E6B).copy(alpha = 0.8f)
                ) {
                    Text(
                        "RAW",
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontSize = androidx.compose.ui.unit.sp(10)
                    )
                }
            }

            // Image name
            Text(
                image.imageName,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = androidx.compose.ui.unit.sp(10)
            )
        }
    }
}

@Composable
private fun FolderSidebar(
    folders: List<com.alcedo.studio.data.model.SleeveFolder>,
    onFolderSelected: (UInt) -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(280.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Folders", style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        }
        HorizontalDivider()

        LazyColumn {
            item {
                ListItem(
                    headlineContent = { Text("All Images") },
                    leadingContent = {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                    },
                    modifier = Modifier.clickable { onFolderSelected(0u) }
                )
            }
            items(folders) { folder ->
                ListItem(
                    headlineContent = { Text(folder.elementName) },
                    supportingContent = { Text("${folder.fileCount} files") },
                    leadingContent = {
                        Icon(Icons.Default.Folder, contentDescription = null)
                    },
                    modifier = Modifier.clickable {
                        onFolderSelected(folder.elementId)
                    }
                )
            }
        }
    }
}

@Composable
private fun ImportDialog(
    onDismiss: () -> Unit,
    onImportFiles: (List<android.net.Uri>) -> Unit,
    onImportDirectory: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import Images") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { /* Open file picker */ },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Select Files")
                }
                OutlinedButton(
                    onClick = { /* Open directory picker */ },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Select Directory")
                }
                Text(
                    "Drag and drop images here",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

enum class SortMode(val label: String) {
    DATE("Date"),
    NAME("Name"),
    RATING("Rating"),
    TYPE("Type")
}