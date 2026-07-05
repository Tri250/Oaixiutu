package com.alcedo.studio.ui.album

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.alcedo.studio.data.model.ImageModel
import com.alcedo.studio.data.model.SleeveFolder
import com.alcedo.studio.storage.PhotoPickerHelper
import com.alcedo.studio.ui.common.*
import com.alcedo.studio.viewmodel.AlbumViewModel
import com.alcedo.studio.permission.PermissionHelper
import com.alcedo.studio.permission.rememberPermissionState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AlbumScreen(
    navController: NavController,
    viewModel: AlbumViewModel = viewModel()
) {
    val images by viewModel.filteredImages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedImages = viewModel.selectedImages
    val showSearch = viewModel.showSearch.value
    val semanticEnabled = viewModel.semanticSearchEnabled.value
    val folders by viewModel.folders.collectAsState()
    val sortMode by viewModel.sortMode.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val thumbnailCache by viewModel.thumbnailCache.collectAsState()

    var showFilterSheet by remember { mutableStateOf(false) }
    var showFolderSidebar by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var contextMenuImage by remember { mutableStateOf<ImageModel?>(null) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // ── Permission management ────────────────────────────────────
    val permissionState = rememberPermissionState(
        onResult = { results ->
            val allGranted = results.all { it.value }
            if (allGranted) {
                viewModel.refresh()
            }
        }
    )

    // Request media permissions on first launch
    LaunchedEffect(Unit) {
        if (!PermissionHelper.hasReadMediaAccess(context)) {
            permissionState.requestMediaAccess()
        }
    }

    val configuration = LocalConfiguration.current
    val columns = when {
        configuration.screenWidthDp >= 840 -> 5
        configuration.screenWidthDp >= 600 -> 4
        else -> 3
    }

    // ── Photo Picker launcher ──────────────────────────────────────
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(50)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.importFromPhotoPicker(uris)
        }
    }

    // ── SAF directory picker launcher ──────────────────────────────
    val safDirLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri: Uri? ->
        treeUri?.let {
            viewModel.importFromSafDirectory(it)
        }
    }

    // ── Legacy file picker launcher (fallback) ─────────────────────
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.importFromPhotoPicker(uris)
        }
    }

    if (showFolderSidebar) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    FolderSidebar(
                        folders = folders,
                        onFolderSelected = { folderId ->
                            viewModel.navigateToFolder(folderId)
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
                thumbnailCache = thumbnailCache,
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
                onSettings = { navController.navigate("settings") },
                onImageContextMenu = { contextMenuImage = it },
                onLoadThumbnail = viewModel::loadThumbnail
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
            thumbnailCache = thumbnailCache,
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
            onSettings = { navController.navigate("settings") },
            onImageContextMenu = { contextMenuImage = it },
            onLoadThumbnail = viewModel::loadThumbnail
        )
    }

    // Filter bottom sheet
    if (showFilterSheet) {
        FilterBottomSheet(
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
            onSelectFiles = {
                showImport = false
                if (PhotoPickerHelper.isAvailable()) {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                } else {
                    filePickerLauncher.launch(arrayOf("image/*"))
                }
            },
            onSelectDirectory = {
                showImport = false
                safDirLauncher.launch(null)
            }
        )
    }

    // Image context menu
    contextMenuImage?.let { image ->
        ImageContextMenu(
            imageName = image.imageName,
            onDismiss = { contextMenuImage = null },
            onEdit = {
                contextMenuImage = null
                navController.navigate("editor/${image.imageId}")
            },
            onRate = {
                contextMenuImage = null
                viewModel.setRating(image.imageId, 3)
            },
            onExport = {
                contextMenuImage = null
                navController.navigate("export/${image.imageId}")
            },
            onDelete = {
                contextMenuImage = null
                viewModel.toggleImageSelection(image.imageId)
            },
            onAnalyzeAi = {
                contextMenuImage = null
                viewModel.generateLabelsForImage(image.imageId)
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
    selectedImages: List<Long>,
    showSearch: Boolean,
    semanticEnabled: Boolean,
    sortMode: SortMode,
    columns: Int,
    thumbnailCache: Map<Long, android.graphics.Bitmap>,
    onSearchQueryChange: (String) -> Unit,
    onToggleSearch: () -> Unit,
    onToggleSemantic: () -> Unit,
    onToggleImageSelection: (Long) -> Unit,
    onClearSelection: () -> Unit,
    onDeleteSelected: () -> Unit,
    onRefresh: () -> Unit,
    onSortModeChange: (SortMode) -> Unit,
    onOpenFilter: () -> Unit,
    onOpenFolderSidebar: () -> Unit,
    onImport: () -> Unit,
    onSettings: () -> Unit,
    onImageContextMenu: (ImageModel) -> Unit,
    onLoadThumbnail: (Long) -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            if (selectedImages.isNotEmpty()) {
                // Selection mode top bar with animation
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
                            IconButton(onClick = { /* Share selected */ }) {
                                Icon(Icons.Default.Share, contentDescription = "Share")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
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
                                modifier = Modifier.fillMaxWidth(),
                                leadingIcon = {
                                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                            )
                        } else {
                            Text("Alcedo Studio")
                        }
                    },
                    actions = {
                        IconButton(onClick = onToggleSearch) {
                            Icon(
                                if (showSearch) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = "Search"
                            )
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
                    },
                    scrollBehavior = scrollBehavior
                )
            }
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = selectedImages.isEmpty(),
                enter = scaleIn(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
                exit = scaleOut(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeOut()
            ) {
                FloatingActionButton(
                    onClick = onImport,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Import")
                }
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
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyState(
                            icon = Icons.Default.PhotoLibrary,
                            title = "No images yet",
                            message = "Import photos to get started",
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
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columns),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 8.dp, end = 8.dp, top = 4.dp, bottom = 88.dp
                            ),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(images, key = { it.imageId }) { image ->
                                ThumbnailCard(
                                    image = image,
                                    isSelected = selectedImages.contains(image.imageId),
                                    thumbnailBitmap = thumbnailCache[image.imageId],
                                    onClick = {
                                        if (selectedImages.isNotEmpty()) {
                                            onToggleImageSelection(image.imageId)
                                        } else {
                                            navController.navigate("editor/${image.imageId}")
                                        }
                                    },
                                    onLongClick = {
                                        if (selectedImages.isEmpty()) {
                                            onImageContextMenu(image)
                                        } else {
                                            onToggleImageSelection(image.imageId)
                                        }
                                    }
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
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "${imageCount} images",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
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
    thumbnailBitmap: android.graphics.Bitmap?,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "selectScale"
    )
    val elevation by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (isSelected) 6.dp else 1.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "cardElevation"
    )

    Card(
        modifier = Modifier
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation)
    ) {
        Box {
            // Thumbnail image or placeholder
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
                if (thumbnailBitmap != null) {
                    androidx.compose.foundation.Image(
                        bitmap = thumbnailBitmap.asImageBitmap(),
                        contentDescription = image.imageName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    // Placeholder with first letter
                    Text(
                        image.imageName.take(1).uppercase(),
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                }
            }

            // Selection indicator with animation
            AnimatedVisibility(
                visible = isSelected,
                enter = scaleIn(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
                exit = scaleOut(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeOut()
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
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

            // AI label / RAW indicator
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
                        fontSize = 9.sp
                    )
                }
            }

            // Image name overlay
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)),
                            startY = 0f,
                            endY = 24.dp.value
                        )
                    )
                    .padding(horizontal = 4.dp, vertical = 3.dp)
            ) {
                Text(
                    image.imageName,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 9.sp
                )
            }
        }
    }
}

@Composable
private fun FolderSidebar(
    folders: List<SleeveFolder>,
    onFolderSelected: (Long) -> Unit,
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
                    modifier = Modifier.clickable { onFolderSelected(0L) }
                )
            }
            items(folders) { folder: SleeveFolder ->
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
    onSelectFiles: () -> Unit,
    onSelectDirectory: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import Images") },
        icon = { Icon(Icons.Default.AddPhotoAlternate, contentDescription = null) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Select Files button – launches Photo Picker or file picker
                FilledTonalButton(
                    onClick = onSelectFiles,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Select Photos")
                }

                // Select Directory button – launches SAF directory picker
                OutlinedButton(
                    onClick = onSelectDirectory,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Select Directory")
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text(
                    "Tip: Long press a photo to open the context menu for editing, rating, and more.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterBottomSheet(
    onDismiss: () -> Unit,
    onApply: (FilterState) -> Unit,
    onReset: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        FilterSheetContent(
            onApply = onApply,
            onReset = onReset
        )
    }
}

@Composable
private fun FilterSheetContent(
    onApply: (FilterState) -> Unit,
    onReset: () -> Unit
) {
    var cameraMakes by remember { mutableStateOf(setOf<String>()) }
    var cameraModels by remember { mutableStateOf(setOf<String>()) }
    var lensModel by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf("") }
    var endDate by remember { mutableStateOf("") }
    var selectedRating by remember { mutableIntStateOf(0) }
    var selectedFileTypes by remember { mutableStateOf(setOf<String>()) }
    var selectedAiLabels by remember { mutableStateOf(setOf<String>()) }

    val sampleCameraMakes = listOf("Sony", "Canon", "Nikon", "Fujifilm", "Leica", "Panasonic", "Olympus")
    val sampleCameraModels = listOf("α7R V", "EOS R5", "Z8", "X-T5", "M11", "S5 II", "OM-1")
    val sampleFileTypes = listOf("JPEG", "PNG", "TIFF", "RAW", "DNG")
    val sampleAiLabels = listOf("Portrait", "Landscape", "Night", "City", "Nature", "Food", "Street", "Abstract")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Filters", style = MaterialTheme.typography.titleLarge)
            Row {
                TextButton(onClick = onReset) {
                    Text("Reset")
                }
                TextButton(onClick = {
                    onApply(
                        FilterState(
                            cameraMakes = cameraMakes.toList(),
                            cameraModels = cameraModels.toList(),
                            lensModel = lensModel,
                            startDate = startDate,
                            endDate = endDate,
                            rating = selectedRating,
                            fileTypes = selectedFileTypes.toList(),
                            aiLabels = selectedAiLabels.toList()
                        )
                    )
                }) {
                    Text("Apply")
                }
            }
        }

        // Camera Make
        Text("Camera Make", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            sampleCameraMakes.forEach { make ->
                FilterChip(
                    selected = cameraMakes.contains(make),
                    onClick = {
                        cameraMakes = if (cameraMakes.contains(make))
                            cameraMakes - make
                        else cameraMakes + make
                    },
                    label = { Text(make, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        // Camera Model
        Text("Camera Model", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            sampleCameraModels.forEach { model ->
                FilterChip(
                    selected = cameraModels.contains(model),
                    onClick = {
                        cameraModels = if (cameraModels.contains(model))
                            cameraModels - model
                        else cameraModels + model
                    },
                    label = { Text(model, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        // Lens
        Text("Lens Model", style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(
            value = lensModel,
            onValueChange = { lensModel = it },
            placeholder = { Text("Any lens...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Camera, contentDescription = null, modifier = Modifier.size(18.dp)) }
        )

        // Date Range
        Text("Date Range", style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = startDate,
                onValueChange = { startDate = it },
                placeholder = { Text("Start") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
            OutlinedTextField(
                value = endDate,
                onValueChange = { endDate = it },
                placeholder = { Text("End") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }

        // Rating
        Text("Rating", style = MaterialTheme.typography.labelLarge)
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            (1..5).forEach { star ->
                IconButton(
                    onClick = { selectedRating = if (selectedRating == star) 0 else star },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        if (star <= selectedRating) Icons.Default.Star else Icons.Default.StarOutline,
                        contentDescription = "$star stars",
                        tint = if (star <= selectedRating)
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            if (selectedRating > 0) {
                Text("& up", style = MaterialTheme.typography.bodySmall)
            }
        }

        // File Type
        Text("File Type", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            sampleFileTypes.forEach { type ->
                FilterChip(
                    selected = selectedFileTypes.contains(type),
                    onClick = {
                        selectedFileTypes = if (selectedFileTypes.contains(type))
                            selectedFileTypes - type
                        else selectedFileTypes + type
                    },
                    label = { Text(type, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        // AI Labels
        Text("AI Labels", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            sampleAiLabels.forEach { label ->
                FilterChip(
                    selected = selectedAiLabels.contains(label),
                    onClick = {
                        selectedAiLabels = if (selectedAiLabels.contains(label))
                            selectedAiLabels - label
                        else selectedAiLabels + label
                    },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }
    }
}

enum class SortMode(val label: String) {
    DATE("Date"),
    NAME("Name"),
    RATING("Rating"),
    TYPE("Type")
}

// Helper to convert Bitmap to ImageBitmap for Compose
private fun android.graphics.Bitmap.asImageBitmap(): androidx.compose.ui.graphics.ImageBitmap {
    return androidx.compose.ui.graphics.asImageBitmap()
}
