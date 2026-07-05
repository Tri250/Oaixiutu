package com.alcedo.studio.ui.album

import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.foundation.lazy.grid.animateItemPlacement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.alcedo.studio.i18n.StringResources
import com.alcedo.studio.i18n.stringRes
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
    val images by viewModel.filteredImages.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedImages by viewModel.selectedImages.collectAsStateWithLifecycle()
    val showSearch by viewModel.showSearch.collectAsStateWithLifecycle()
    val semanticEnabled by viewModel.semanticSearchEnabled.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val sortMode by viewModel.sortMode.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    // 缩略图缓存通过 LruCache 存储，UI 通过版本号触发重组，
    // 实际 bitmap 通过 viewModel.getThumbnail(id) 直接访问
    val thumbnailCacheVersion by viewModel.thumbnailCacheVersion.collectAsStateWithLifecycle()
    val hasMorePages by viewModel.hasMorePages.collectAsStateWithLifecycle()

    var showFilterSheet by remember { mutableStateOf(false) }
    var showFolderSidebar by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var contextMenuImage by remember { mutableStateOf<ImageModel?>(null) }
    var ratingTarget by remember { mutableStateOf<ImageModel?>(null) }
    var showBatchAiTagDialog by remember { mutableStateOf(false) }
    var showBatchRatingDialog by remember { mutableStateOf(false) }
    var showBatchExportDialog by remember { mutableStateOf(false) }
    var showPermissionRationale by remember { mutableStateOf(false) }
    val permissionError by viewModel.permissionError.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // ── 权限管理 ────────────────────────────────────
    val permissionState = rememberPermissionState(
        onResult = { results ->
            val allGranted = results.all { it.value }
            if (allGranted) {
                viewModel.refresh()
            } else {
                // 检查是否应该显示解释
                val shouldShowRationale = PermissionHelper.shouldShowRationale(context)
                if (shouldShowRationale) {
                    showPermissionRationale = true
                } else {
                    // 永久拒绝 — 引导去设置
                    viewModel.setPermissionError("权限被拒绝，请在设置中开启存储访问权限")
                }
            }
        }
    )

    // 首次启动时请求媒体权限
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

    // ── Photo Picker 启动器 ──────────────────────────────────────
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(500)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.importFromPhotoPicker(uris)
        }
    }

    // ── SAF 目录选择器启动器 ──────────────────────────────
    val safDirLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri: Uri? ->
        treeUri?.let {
            viewModel.importFromSafDirectory(it)
        }
    }

    // ── 传统文件选择器启动器（回退）─────────────────────
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
                        onSmartAlbumSelected = { type ->
                            // 智能相册：根据类型应用对应筛选/排序
                            when (type) {
                                "favorites" -> viewModel.applyFilter(FilterState(rating = 4))
                                "recent" -> viewModel.setSortMode(SortMode.DATE)
                                "to_export" -> { /* 待导出：保留入口，筛选逻辑后续扩展 */ }
                            }
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
                thumbnailCacheVersion = thumbnailCacheVersion,
                hasMorePages = hasMorePages,
                onGetThumbnail = viewModel::getThumbnail,
                onLoadMore = viewModel::loadMoreImages,
                onSearchQueryChange = viewModel::onSearchQueryChange,
                onToggleSearch = viewModel::toggleSearch,
                onToggleSemantic = viewModel::toggleSemanticSearch,
                onToggleImageSelection = viewModel::toggleImageSelection,
                onClearSelection = viewModel::clearSelection,
                onDeleteSelected = viewModel::deleteSelected,
                onBatchAiTag = { showBatchAiTagDialog = true },
                onBatchRating = { showBatchRatingDialog = true },
                onBatchExport = { showBatchExportDialog = true },
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
            thumbnailCacheVersion = thumbnailCacheVersion,
            hasMorePages = hasMorePages,
            onGetThumbnail = viewModel::getThumbnail,
            onLoadMore = viewModel::loadMoreImages,
            onSearchQueryChange = viewModel::onSearchQueryChange,
            onToggleSearch = viewModel::toggleSearch,
            onToggleSemantic = viewModel::toggleSemanticSearch,
            onToggleImageSelection = viewModel::toggleImageSelection,
            onClearSelection = viewModel::clearSelection,
            onDeleteSelected = viewModel::deleteSelected,
            onBatchAiTag = { showBatchAiTagDialog = true },
            onBatchRating = { showBatchRatingDialog = true },
            onBatchExport = { showBatchExportDialog = true },
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

    // 筛选底部表单
    if (showFilterSheet) {
        FilterBottomSheet(
            viewModel = viewModel,
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

    // 导入对话框
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

    // 图片上下文菜单
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
                ratingTarget = image
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

    // 评分选择对话框（替代之前硬编码的评分值）
    ratingTarget?.let { image ->
        RatingPickerDialog(
            imageName = image.imageName,
            onDismiss = { ratingTarget = null },
            onPick = { rating ->
                viewModel.setRating(image.imageId, rating)
                ratingTarget = null
            }
        )
    }

    // 批量 AI 标签对话框
    if (showBatchAiTagDialog) {
        AlertDialog(
            onDismissRequest = { showBatchAiTagDialog = false },
            title = { Text("批量 AI 标签") },
            text = { Text("为选中的 ${selectedImages.size} 张图片生成 AI 标签？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.generateLabelsForImages(selectedImages.toList())
                    showBatchAiTagDialog = false
                }) { Text(stringRes { confirm }) }
            },
            dismissButton = {
                TextButton(onClick = { showBatchAiTagDialog = false }) { Text(stringRes { cancel }) }
            }
        )
    }

    // 批量评分对话框（复用 RatingPickerDialog）
    if (showBatchRatingDialog) {
        RatingPickerDialog(
            imageName = "${selectedImages.size} 张图片",
            onDismiss = { showBatchRatingDialog = false },
            onPick = { rating ->
                viewModel.rateImages(selectedImages.toList(), rating)
                showBatchRatingDialog = false
            }
        )
    }

    // 批量导出对话框
    if (showBatchExportDialog) {
        val selectedImageModels = images.filter { it.imageId in selectedImages }
        AlbumExportDialog(
            images = selectedImageModels,
            onDismiss = { showBatchExportDialog = false },
            onExport = { ids, settings ->
                viewModel.exportBatchByIds(ids, settings)
                showBatchExportDialog = false
            }
        )
    }

    // 权限解释对话框
    if (showPermissionRationale) {
        AlertDialog(
            onDismissRequest = { showPermissionRationale = false },
            title = { Text("需要存储权限") },
            text = { Text("Alcedo Studio 需要访问您的照片来浏览和编辑图片。请在接下来弹出的权限请求中点击\"允许\"。") },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionRationale = false
                    permissionState.requestMediaAccess()
                }) { Text("再次请求") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPermissionRationale = false
                    // 跳转系统设置
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                }) { Text("去设置") }
            }
        )
    }

    // 永久拒绝权限时显示提示
    permissionError?.let { message ->
        AlertDialog(
            onDismissRequest = { viewModel.clearPermissionError() },
            title = { Text("权限被拒绝") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearPermissionError()
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                }) { Text("去设置") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.clearPermissionError() }) { Text(stringRes { cancel }) }
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
    selectedImages: Set<Long>,
    showSearch: Boolean,
    semanticEnabled: Boolean,
    sortMode: SortMode,
    columns: Int,
    thumbnailCacheVersion: Int,
    hasMorePages: Boolean,
    onGetThumbnail: (Long) -> android.graphics.Bitmap?,
    onLoadMore: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onToggleSearch: () -> Unit,
    onToggleSemantic: () -> Unit,
    onToggleImageSelection: (Long) -> Unit,
    onClearSelection: () -> Unit,
    onDeleteSelected: () -> Unit,
    onBatchAiTag: () -> Unit,
    onBatchRating: () -> Unit,
    onBatchExport: () -> Unit,
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
            // 使用 Box 叠加两个 TopAppBar，让退出动画能够正常播放
            Box {
                // 普通模式顶栏
                AnimatedVisibility(
                    visible = selectedImages.isEmpty(),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    TopAppBar(
                        title = {
                            if (showSearch) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = onSearchQueryChange,
                                    placeholder = { Text(stringRes { albumSearchHint }) },
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
                                    contentDescription = stringRes { search }
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
                                Icon(Icons.Default.FilterList, contentDescription = stringRes { filter })
                            }
                            IconButton(onClick = onOpenFolderSidebar) {
                                Icon(Icons.Default.Folder, contentDescription = stringRes { folders })
                            }
                            IconButton(onClick = onSettings) {
                                Icon(Icons.Default.Settings, contentDescription = stringRes { settings })
                            }
                        },
                        scrollBehavior = scrollBehavior
                    )
                }
                // 选择模式顶栏（带动画）
                AnimatedVisibility(
                    visible = selectedImages.isNotEmpty(),
                    enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
                ) {
                    TopAppBar(
                        title = { Text(stringRes { albumSelectedCount }.format(selectedImages.size)) },
                        navigationIcon = {
                            IconButton(onClick = onClearSelection) {
                                Icon(Icons.Default.Close, contentDescription = stringRes { clear })
                            }
                        },
                        actions = {
                            // 批量 AI 标签
                            IconButton(onClick = onBatchAiTag) {
                                Icon(Icons.Default.Label, contentDescription = stringRes { aiTag },
                                    tint = MaterialTheme.colorScheme.onSurface)
                            }
                            // 批量评分
                            IconButton(onClick = onBatchRating) {
                                Icon(Icons.Default.Star, contentDescription = stringRes { aiRatingTitle },
                                    tint = MaterialTheme.colorScheme.onSurface)
                            }
                            // 批量导出选中图片
                            IconButton(onClick = onBatchExport) {
                                Icon(Icons.Default.Share, contentDescription = stringRes { export },
                                    tint = MaterialTheme.colorScheme.onSurface)
                            }
                            // 删除
                            IconButton(onClick = onDeleteSelected) {
                                Icon(Icons.Default.Delete, contentDescription = stringRes { delete },
                                    tint = MaterialTheme.colorScheme.error)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                        )
                    )
                }
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
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = stringRes { importText })
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
                            title = stringRes { albumNoImages },
                            message = stringRes { albumNoImagesDesc },
                            action = {
                                Button(onClick = onImport) {
                                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null,
                                        modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringRes { albumImportPhotos })
                                }
                            }
                        )
                    }
                }
                else -> {
                    Column {
                        // 排序/筛选栏
                        SortFilterBar(
                            sortMode = sortMode,
                            onSortModeChange = onSortModeChange,
                            imageCount = images.size
                        )

                        // 分页加载：当剩余可见项不足阈值时自动请求下一页
                        val gridState = rememberLazyGridState()
                        val shouldLoadMore by remember {
                            derivedStateOf {
                                if (!hasMorePages || images.isEmpty()) return@derivedStateOf false
                                val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
                                    ?: return@derivedStateOf false
                                lastVisible >= images.size - 10
                            }
                        }
                        LaunchedEffect(shouldLoadMore) {
                            if (shouldLoadMore) onLoadMore()
                        }

                        // 缩略图网格
                        LazyVerticalGrid(
                            state = gridState,
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
                                    isSelected = image.imageId in selectedImages,
                                    thumbnailBitmap = onGetThumbnail(image.imageId),
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
                                    },
                                    modifier = Modifier.animateItemPlacement()
                                )
                            }
                            if (hasMorePages) {
                                item(span = { GridItemSpan(columns) }) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                    }
                                }
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
            stringRes { albumImagesCount }.format(imageCount),
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
                    label = { Text(stringRes(mode.labelKey), style = MaterialTheme.typography.labelSmall) },
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
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
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
        modifier = modifier
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
            // 缩略图或占位符
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
                    // 首字母占位符
                    Text(
                        image.imageName.take(1).uppercase(),
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                }
            }

            // 选择指示器（带动画）
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

            // 评分浮层
            val rating = image.exifDisplay.rating
            if (rating > 0) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    repeat(rating.coerceIn(1, 5)) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            modifier = Modifier.size(10.dp),
                            tint = Color(0xFFFFD700)
                        )
                    }
                }
            }

            // AI 标签 / RAW 指示器
            val isRaw = image.imageName.endsWith(".raw", true) ||
                image.imageName.endsWith(".dng", true) ||
                image.imageName.endsWith(".cr2", true) ||
                image.imageName.endsWith(".nef", true) ||
                image.imageName.endsWith(".arw", true)
            if (isRaw) {
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

            // 图片名称浮层
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
    onSmartAlbumSelected: (String) -> Unit,
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
            Text(stringRes { albumFolders }, style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = stringRes { close })
            }
        }
        HorizontalDivider()

        LazyColumn {
            // ── 智能相册分区 ──
            item {
                Text(
                    "智能相册",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("收藏夹") },
                    leadingContent = {
                        Icon(Icons.Default.Favorite, contentDescription = null)
                    },
                    modifier = Modifier.clickable { onSmartAlbumSelected("favorites") }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("最近查看") },
                    leadingContent = {
                        Icon(Icons.Default.Schedule, contentDescription = null)
                    },
                    modifier = Modifier.clickable { onSmartAlbumSelected("recent") }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("待导出") },
                    leadingContent = {
                        Icon(Icons.Default.FileDownload, contentDescription = null)
                    },
                    modifier = Modifier.clickable { onSmartAlbumSelected("to_export") }
                )
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp))
            }

            // ── 文件夹分区 ──
            item {
                Text(
                    stringRes { albumFolders },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringRes { albumAllImages }) },
                    leadingContent = {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                    },
                    modifier = Modifier.clickable { onFolderSelected(0L) }
                )
            }
            items(folders, key = { it.elementId }) { folder: SleeveFolder ->
                ListItem(
                    headlineContent = { Text(folder.elementName) },
                    supportingContent = { Text(stringRes { albumFilesCount }.format(folder.fileCount)) },
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
        title = { Text(stringRes { albumImportTitle }) },
        icon = { Icon(Icons.Default.AddPhotoAlternate, contentDescription = null) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // 选择文件按钮 – 启动 Photo Picker 或文件选择器
                FilledTonalButton(
                    onClick = onSelectFiles,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringRes { albumSelectFiles })
                }

                // 选择目录按钮 – 启动 SAF 目录选择器
                OutlinedButton(
                    onClick = onSelectDirectory,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringRes { albumSelectDirectory })
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text(
                    stringRes { albumDragDrop },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringRes { cancel }) }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterBottomSheet(
    viewModel: AlbumViewModel,
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
            viewModel = viewModel,
            onApply = onApply,
            onReset = onReset
        )
    }
}

@Composable
private fun FilterSheetContent(
    viewModel: AlbumViewModel,
    onApply: (FilterState) -> Unit,
    onReset: () -> Unit
) {
    val allImages by viewModel.images.collectAsStateWithLifecycle()
    val currentFilter by viewModel.currentFilter.collectAsStateWithLifecycle()

    var cameraMakes by remember { mutableStateOf(currentFilter?.cameraMakes?.toSet() ?: emptySet()) }
    var cameraModels by remember { mutableStateOf(currentFilter?.cameraModels?.toSet() ?: emptySet()) }
    var lensModel by remember { mutableStateOf(currentFilter?.lensModel ?: "") }
    var startDate by remember { mutableStateOf(currentFilter?.startDate ?: "") }
    var endDate by remember { mutableStateOf(currentFilter?.endDate ?: "") }
    var selectedRating by remember { mutableIntStateOf(currentFilter?.rating ?: 0) }
    var selectedFileTypes by remember { mutableStateOf(currentFilter?.fileTypes?.toSet() ?: emptySet()) }
    var selectedAiLabels by remember { mutableStateOf(currentFilter?.aiLabels?.toSet() ?: emptySet()) }

    val realCameraMakes = allImages.mapNotNull { it.exifDisplay.cameraMake.takeIf { it.isNotEmpty() } }.distinct()
    val realCameraModels = allImages.mapNotNull { it.exifDisplay.cameraModel.takeIf { it.isNotEmpty() } }.distinct()
    val realFileTypes = allImages.map {
        it.imageName.substringAfterLast(".", "").uppercase()
    }.filter { it.isNotEmpty() }.distinct()
    val realAiLabels = emptyList<String>()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 头部
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringRes { albumFilterTitle }, style = MaterialTheme.typography.titleLarge)
            Row {
                TextButton(onClick = onReset) {
                    Text(stringRes { albumFilterReset })
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
                    Text(stringRes { albumFilterApply })
                }
            }
        }

        // 相机品牌
        Text(stringRes { albumFilterCameraMake }, style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            realCameraMakes.forEach { make ->
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

        // 相机型号
        Text(stringRes { albumFilterCameraModel }, style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            realCameraModels.forEach { model ->
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

        // 镜头
        Text(stringRes { albumFilterLensModel }, style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(
            value = lensModel,
            onValueChange = { lensModel = it },
            placeholder = { Text(stringRes { albumFilterLensPlaceholder }) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Camera, contentDescription = null, modifier = Modifier.size(18.dp)) }
        )

        // 日期范围
        Text(stringRes { albumFilterDateRange }, style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = startDate,
                onValueChange = { startDate = it },
                placeholder = { Text(stringRes { albumFilterStart }) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
            OutlinedTextField(
                value = endDate,
                onValueChange = { endDate = it },
                placeholder = { Text(stringRes { albumFilterEnd }) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }

        // 评分
        Text(stringRes { albumFilterRating }, style = MaterialTheme.typography.labelLarge)
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
                        contentDescription = stringRes { starRating }.format(star),
                        tint = if (star <= selectedRating)
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            if (selectedRating > 0) {
                Text(stringRes { albumFilterAndUp }, style = MaterialTheme.typography.bodySmall)
            }
        }

        // 文件类型
        Text(stringRes { albumFilterFileType }, style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            realFileTypes.forEach { type ->
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

        // AI 标签
        Text(stringRes { albumFilterAiLabels }, style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            realAiLabels.forEach { label ->
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

enum class SortMode(val labelKey: StringResources.() -> String) {
    DATE({ albumSortDate }),
    NAME({ albumSortName }),
    RATING({ albumSortRating }),
    TYPE({ albumSortType })
}

/**
 * Star rating picker dialog. Replaces the previous hardcoded rating value (3)
 * wired into the context menu's "Rate" action.
 */
@Composable
private fun RatingPickerDialog(
    imageName: String,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit
) {
    var selectedRating by remember { mutableIntStateOf(0) }
    val view = androidx.compose.ui.platform.LocalView.current

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Star, contentDescription = null) },
        title = {
            Text(
                stringRes { aiRatingTitle }.format(imageName),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    (1..5).forEach { star ->
                        IconButton(
                            onClick = {
                                selectedRating = if (selectedRating == star) 0 else star
                                com.alcedo.studio.ui.common.HapticFeedback.click(view)
                            },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                if (star <= selectedRating) Icons.Default.Star else Icons.Default.StarOutline,
                                contentDescription = "$star star${if (star != 1) "s" else ""}",
                                tint = if (star <= selectedRating) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (selectedRating == 0) {
                    Text(
                        "Tap a star to rate, or pick 0 stars to clear",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        "$selectedRating star${if (selectedRating != 1) "s" else ""}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onPick(selectedRating) },
                enabled = true
            ) { Text(stringRes { apply }) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    onPick(0)
                }) { Text(stringRes { clear }) }
                TextButton(onClick = onDismiss) { Text(stringRes { cancel }) }
            }
        }
    )
}

// 将 Bitmap 转换为 Compose 使用的 ImageBitmap 的辅助函数
private fun android.graphics.Bitmap.asImageBitmap(): androidx.compose.ui.graphics.ImageBitmap {
    return androidx.compose.ui.graphics.asImageBitmap()
}
