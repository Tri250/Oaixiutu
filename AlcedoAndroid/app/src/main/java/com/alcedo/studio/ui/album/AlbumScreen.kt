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
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import com.alcedo.studio.ui.theme.AlcedoGlass
import com.alcedo.studio.ui.theme.AlcedoGradient
import com.alcedo.studio.ui.theme.AlcedoStroke
import com.alcedo.studio.ui.theme.AlcedoRadius
import com.alcedo.studio.ui.theme.AlcedoSpacing
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.alcedo.studio.data.model.ImageModel
import com.alcedo.studio.data.model.SleeveFolder
import com.alcedo.studio.i18n.StringResources
import com.alcedo.studio.i18n.stringRes
import com.alcedo.studio.ui.common.*
import com.alcedo.studio.viewmodel.AlbumViewModel
import com.alcedo.studio.permission.PermissionHelper
import com.alcedo.studio.permission.rememberPermissionState
import com.alcedo.studio.storage.PhotoPickerHelper
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ═══════════════════════════════════════════════════════════════════
// PixCake-inspired view model & grid density
// ═══════════════════════════════════════════════════════════════════

enum class AlbumViewMode(val label: String) {
    PHOTO("照片"),
    PROJECT("项目");

    val icon: ImageVector
        get() = when (this) {
            PHOTO -> Icons.Default.PhotoLibrary
            PROJECT -> Icons.Default.FolderCopy
        }
}

enum class GridDensity(val columns: Int, val label: String) {
    COMFORTABLE(3, "舒适"),
    STANDARD(4, "标准"),
    COMPACT(5, "紧凑");

    val icon: ImageVector
        get() = when (this) {
            COMFORTABLE -> Icons.Default.ViewModule
            STANDARD -> Icons.Default.GridView
            COMPACT -> Icons.Default.Apps
        }
}

/**
 * Date-based photo group for PixCake-style date section headers.
 */
data class DateGroup(
    val dateKey: String,        // "2024:03:15" or "unknown"
    val displayLabel: String,   // "2024年3月15日" or "未知日期"
    val relativeLabel: String,  // "今天" / "昨天" / "本周" etc.
    val images: List<ImageModel>
)

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
    val thumbnailCacheVersion by viewModel.thumbnailCacheVersion.collectAsStateWithLifecycle()
    val hasMorePages by viewModel.hasMorePages.collectAsStateWithLifecycle()

    // ── PixCake 视图状态 ──
    var viewMode by remember { mutableStateOf(AlbumViewMode.PHOTO) }
    var gridDensity by remember { mutableStateOf(GridDensity.STANDARD) }
    val collapsedDateGroups = remember { mutableStateOf(setOf<String>()) }

    var showFilterSheet by remember { mutableStateOf(false) }
    var showFolderSidebar by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var contextMenuImage by remember { mutableStateOf<ImageModel?>(null) }
    var ratingTarget by remember { mutableStateOf<ImageModel?>(null) }
    var showBatchAiTagDialog by remember { mutableStateOf(false) }
    var showBatchRatingDialog by remember { mutableStateOf(false) }
    var showBatchExportDialog by remember { mutableStateOf(false) }
    var showBatchEditPanel by remember { mutableStateOf(false) }
    var showPermissionRationale by remember { mutableStateOf(false) }
    // 待删除图片 — 单张删除前需用户确认 (G-5 修复)
    var pendingDeleteImage by remember { mutableStateOf<ImageModel?>(null) }
    // 批量删除前确认 (G-5 修复)
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }
    val permissionError by viewModel.permissionError.collectAsStateWithLifecycle()
    // S-2 修复: 收集 permissionRationale,在 UI 上展示
    val permissionRationale by viewModel.permissionRationale.collectAsStateWithLifecycle()
    // S-8 修复: 收集 batchExportResult,导出完成时弹出提示
    val batchExportResult by viewModel.batchExportResult.collectAsStateWithLifecycle()
    // Import progress for showing top bar during bulk imports
    val importProgress by viewModel.importProgress.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // ── Snackbar 状态 ──
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.snackbarEvent.collect { message ->
            snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
        }
    }

    // ── 权限管理 ────────────────────────────────────
    val permissionState = rememberPermissionState(
        onResult = { results ->
            val allGranted = results.all { it.value }
            if (allGranted) {
                viewModel.refresh()
            } else {
                val shouldShowRationale = PermissionHelper.shouldShowRationale(context)
                if (shouldShowRationale) {
                    showPermissionRationale = true
                } else {
                    viewModel.setPermissionError("权限被拒绝，请在设置中开启存储访问权限")
                }
            }
        }
    )

    LaunchedEffect(Unit) {
        if (!PermissionHelper.hasReadMediaAccess(context)) {
            permissionState.requestMediaAccess()
        }
    }

    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp >= 600
    val baseColumns = when {
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

    // ── 传统文件选择器回退启动器 (当 Photo Picker 不可用时使用) ──
    val fallbackFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.importFromPhotoPicker(uris)
        }
    }

    // ── 单文件选择回退启动器 (当 GetMultipleContents 也不可用时使用) ──
    val singleFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.importFromPhotoPicker(listOf(it)) }
    }

    // ── SAF 目录选择器启动器 ──────────────────────────────
    val safDirLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri: Uri? ->
        treeUri?.let {
            viewModel.importFromSafDirectory(it)
        }
    }

    // ── 侧边栏内容提取为独立 Composable,供 Modal/Permanent Drawer 复用 ──
    val sidebarContent: @Composable () -> Unit = {
        FolderSidebar(
            folders = folders,
            onFolderSelected = { folderId ->
                // P12 修复: null 表示"全部图片"(根目录),0L 不再作为虚拟根目录
                viewModel.navigateToFolder(folderId)
                if (!isWideScreen) {
                    scope.launch { drawerState.close() }
                }
            },
            onSmartAlbumSelected = { type ->
                when (type) {
                    "favorites" -> viewModel.applyFilter(FilterState(rating = 4))
                    "recent" -> viewModel.setSortMode(SortMode.DATE)
                    "to_export" -> viewModel.applyFilter(FilterState(rating = 3))
                }
                if (!isWideScreen) {
                    scope.launch { drawerState.close() }
                }
            },
            onClose = {
                if (!isWideScreen) {
                    scope.launch { drawerState.close() }
                }
                showFolderSidebar = false
            }
        )
    }

    if (isWideScreen) {
        // ── 宽屏: 永久侧边栏 ──
        PermanentNavigationDrawer(
            drawerContent = {
                PermanentDrawerSheet(
                    modifier = Modifier.width(280.dp)
                ) {
                    sidebarContent()
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
                baseColumns = baseColumns,
                thumbnailCacheVersion = thumbnailCacheVersion,
                hasMorePages = hasMorePages,
                viewMode = viewMode,
                gridDensity = gridDensity,
                collapsedDateGroups = collapsedDateGroups,
                folders = folders,
                importProgress = importProgress,
                snackbarHostState = snackbarHostState,
                onGetThumbnail = viewModel::getThumbnail,
                onLoadMore = viewModel::loadMoreImages,
                onSearchQueryChange = viewModel::onSearchQueryChange,
                onToggleSearch = viewModel::toggleSearch,
                onToggleSemantic = viewModel::toggleSemanticSearch,
                onToggleImageSelection = viewModel::toggleImageSelection,
                onClearSelection = viewModel::clearSelection,
                onSelectAll = viewModel::selectAll,
                onDeleteSelected = { showBatchDeleteConfirm = true },
                onBatchAiTag = { showBatchAiTagDialog = true },
                onBatchRating = { showBatchRatingDialog = true },
                onBatchExport = { showBatchExportDialog = true },
                onBatchEdit = { showBatchEditPanel = true },
                onRefresh = viewModel::refresh,
                onSortModeChange = viewModel::setSortMode,
                onOpenFilter = { showFilterSheet = true },
                onOpenFolderSidebar = { /* 宽屏下侧边栏已常驻,无需操作 */ },
                onImport = { showImport = true },
                onSettings = { navController.navigate("settings") },
                onImageContextMenu = { contextMenuImage = it },
                onLoadThumbnail = viewModel::loadThumbnail,
                onViewModeChange = { viewMode = it },
                onGridDensityChange = { gridDensity = it },
                onToggleDateGroup = { dateKey ->
                    collapsedDateGroups.value = if (dateKey in collapsedDateGroups.value) {
                        collapsedDateGroups.value - dateKey
                    } else {
                        collapsedDateGroups.value + dateKey
                    }
                },
                onNavigateToFolder = { folderId ->
                    viewModel.navigateToFolder(folderId)
                    viewMode = AlbumViewMode.PHOTO
                }
            )
        }
    } else if (showFolderSidebar) {
        // ── 窄屏: 模态侧边栏 ──
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    sidebarContent()
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
                baseColumns = baseColumns,
                thumbnailCacheVersion = thumbnailCacheVersion,
                hasMorePages = hasMorePages,
                viewMode = viewMode,
                gridDensity = gridDensity,
                collapsedDateGroups = collapsedDateGroups,
                folders = folders,
                importProgress = importProgress,
                snackbarHostState = snackbarHostState,
                onGetThumbnail = viewModel::getThumbnail,
                onLoadMore = viewModel::loadMoreImages,
                onSearchQueryChange = viewModel::onSearchQueryChange,
                onToggleSearch = viewModel::toggleSearch,
                onToggleSemantic = viewModel::toggleSemanticSearch,
                onToggleImageSelection = viewModel::toggleImageSelection,
                onClearSelection = viewModel::clearSelection,
                onSelectAll = viewModel::selectAll,
                // G-5 修复: 批量删除前需用户确认,避免误操作
                onDeleteSelected = { showBatchDeleteConfirm = true },
                onBatchAiTag = { showBatchAiTagDialog = true },
                onBatchRating = { showBatchRatingDialog = true },
                onBatchExport = { showBatchExportDialog = true },
                onBatchEdit = { showBatchEditPanel = true },
                onRefresh = viewModel::refresh,
                onSortModeChange = viewModel::setSortMode,
                onOpenFilter = { showFilterSheet = true },
                onOpenFolderSidebar = {
                    scope.launch { drawerState.open() }
                },
                onImport = { showImport = true },
                onSettings = { navController.navigate("settings") },
                onImageContextMenu = { contextMenuImage = it },
                onLoadThumbnail = viewModel::loadThumbnail,
                onViewModeChange = { viewMode = it },
                onGridDensityChange = { gridDensity = it },
                onToggleDateGroup = { dateKey ->
                    collapsedDateGroups.value = if (dateKey in collapsedDateGroups.value) {
                        collapsedDateGroups.value - dateKey
                    } else {
                        collapsedDateGroups.value + dateKey
                    }
                },
                onNavigateToFolder = { folderId ->
                    viewModel.navigateToFolder(folderId)
                    viewMode = AlbumViewMode.PHOTO
                }
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
            baseColumns = baseColumns,
            thumbnailCacheVersion = thumbnailCacheVersion,
            hasMorePages = hasMorePages,
            viewMode = viewMode,
            gridDensity = gridDensity,
            collapsedDateGroups = collapsedDateGroups,
            folders = folders,
            importProgress = importProgress,
            snackbarHostState = snackbarHostState,
            onGetThumbnail = viewModel::getThumbnail,
            onLoadMore = viewModel::loadMoreImages,
            onSearchQueryChange = viewModel::onSearchQueryChange,
            onToggleSearch = viewModel::toggleSearch,
            onToggleSemantic = viewModel::toggleSemanticSearch,
            onToggleImageSelection = viewModel::toggleImageSelection,
            onClearSelection = viewModel::clearSelection,
            onSelectAll = viewModel::selectAll,
            // G-5 修复: 批量删除前需用户确认,避免误操作 (与 showFolderSidebar 分支保持一致)
            onDeleteSelected = { showBatchDeleteConfirm = true },
            onBatchAiTag = { showBatchAiTagDialog = true },
            onBatchRating = { showBatchRatingDialog = true },
            onBatchExport = { showBatchExportDialog = true },
            onBatchEdit = { showBatchEditPanel = true },
            onRefresh = viewModel::refresh,
            onSortModeChange = viewModel::setSortMode,
            onOpenFilter = { showFilterSheet = true },
            onOpenFolderSidebar = { showFolderSidebar = true },
            onImport = { showImport = true },
            onSettings = { navController.navigate("settings") },
            onImageContextMenu = { contextMenuImage = it },
            onLoadThumbnail = viewModel::loadThumbnail,
            onViewModeChange = { viewMode = it },
            onGridDensityChange = { gridDensity = it },
            onToggleDateGroup = { dateKey ->
                collapsedDateGroups.value = if (dateKey in collapsedDateGroups.value) {
                    collapsedDateGroups.value - dateKey
                } else {
                    collapsedDateGroups.value + dateKey
                }
            },
            onNavigateToFolder = { folderId ->
                viewModel.navigateToFolder(folderId)
                viewMode = AlbumViewMode.PHOTO
            }
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
                // P-1 修复: 检测 Photo Picker 可用性，不可用时回退到 ACTION_GET_CONTENT
                // P3-5 修复: 所有回退层级均需 try-catch 保护,避免 ActivityNotFoundException 崩溃
                if (PhotoPickerHelper.isAvailable(context)) {
                    try {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    } catch (e: Exception) {
                        // 即使检测通过，启动仍可能失败（如 GMS 被禁用），回退到传统选择器
                        try {
                            fallbackFileLauncher.launch("image/*")
                        } catch (e2: Exception) {
                            try {
                                singleFileLauncher.launch("image/*")
                            } catch (_: Exception) {
                                viewModel.setPermissionError("设备不支持图片选择，请检查系统组件")
                            }
                        }
                    }
                } else {
                    try {
                        fallbackFileLauncher.launch("image/*")
                    } catch (e: Exception) {
                        try {
                            singleFileLauncher.launch("image/*")
                        } catch (_: Exception) {
                            viewModel.setPermissionError("设备不支持图片选择，请检查系统组件")
                        }
                    }
                }
            },
            onSelectDirectory = {
                showImport = false
                try {
                    safDirLauncher.launch(null)
                } catch (e: Exception) {
                    // OpenDocumentTree 不被所有设备/文档提供者支持,
                    // 回退到文件选择 (多选模式)
                    try {
                        fallbackFileLauncher.launch("image/*")
                    } catch (e2: Exception) {
                        try {
                            singleFileLauncher.launch("image/*")
                        } catch (_: Exception) {
                            // 全部回退失败时通过 ViewModel 提示用户
                            viewModel.setPermissionError("设备不支持目录选择，请使用文件选择导入")
                        }
                    }
                }
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
                // F-1 修复: 上下文菜单"删除"应执行真正的删除,而非切换选中态
                // G-5 修复: 删除前弹出确认对话框
                contextMenuImage = null
                pendingDeleteImage = image
            },
            onAnalyzeAi = {
                contextMenuImage = null
                viewModel.generateLabelsForImage(image.imageId)
            }
        )
    }

    // 评分选择对话框
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

    // 批量评分对话框
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

    // 批量同步调整面板 (复制/粘贴/选择性粘贴/应用预设/重置)
    if (showBatchEditPanel) {
        BatchEditPanel(
            viewModel = viewModel,
            onDismiss = { showBatchEditPanel = false }
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

    // S-2 修复: 观察 ViewModel 抛出的 permissionRationale (例如: 目录无图片/导入失败等)
    permissionRationale?.let { message ->
        AlertDialog(
            onDismissRequest = { viewModel.clearPermissionRationale() },
            title = { Text("提示") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearPermissionRationale() }) {
                    Text(stringRes { confirm })
                }
            }
        )
    }

    // G-5 修复: 单张图片删除确认对话框
    pendingDeleteImage?.let { image ->
        AlertDialog(
            onDismissRequest = { pendingDeleteImage = null },
            title = { Text(stringRes { delete }) },
            text = {
                Text("确定要删除 \"${image.imageName}\" 吗?此操作不可撤销。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteImage(image.imageId)
                        pendingDeleteImage = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text(stringRes { delete }) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteImage = null }) {
                    Text(stringRes { cancel })
                }
            }
        )
    }

    // G-5 修复: 批量删除确认对话框
    if (showBatchDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteConfirm = false },
            title = { Text(stringRes { delete }) },
            text = {
                Text("确定要删除选中的 ${selectedImages.size} 张图片吗?此操作不可撤销。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSelected()
                        showBatchDeleteConfirm = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text(stringRes { delete }) }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteConfirm = false }) {
                    Text(stringRes { cancel })
                }
            }
        )
    }

    // S-8 修复: 批量导出结果反馈 — 导出完成后弹出汇总信息
    batchExportResult?.let { result ->
        AlertDialog(
            onDismissRequest = { viewModel.clearBatchExportResult() },
            title = { Text(stringRes { export }) },
            text = {
                Column {
                    Text("导出完成")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "成功: ${result.successCount} / ${result.totalItems}",
                        color = if (result.errorCount == 0)
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error
                    )
                    if (result.errorCount > 0) {
                        Text(
                            "失败: ${result.errorCount}",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.clearBatchExportResult() }) {
                    Text(stringRes { confirm })
                }
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
    baseColumns: Int,
    thumbnailCacheVersion: Int,
    hasMorePages: Boolean,
    viewMode: AlbumViewMode,
    gridDensity: GridDensity,
    collapsedDateGroups: State<Set<String>>,
    folders: List<SleeveFolder>,
    importProgress: com.alcedo.studio.viewmodel.ImportProgress?,
    snackbarHostState: SnackbarHostState,
    onGetThumbnail: (Long) -> android.graphics.Bitmap?,
    onLoadMore: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onToggleSearch: () -> Unit,
    onToggleSemantic: () -> Unit,
    onToggleImageSelection: (Long) -> Unit,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onDeleteSelected: () -> Unit,
    onBatchAiTag: () -> Unit,
    onBatchRating: () -> Unit,
    onBatchExport: () -> Unit,
    onBatchEdit: () -> Unit,
    onRefresh: () -> Unit,
    onSortModeChange: (SortMode) -> Unit,
    onOpenFilter: () -> Unit,
    onOpenFolderSidebar: () -> Unit,
    onImport: () -> Unit,
    onSettings: () -> Unit,
    onImageContextMenu: (ImageModel) -> Unit,
    onLoadThumbnail: (Long) -> Unit,
    onViewModeChange: (AlbumViewMode) -> Unit,
    onGridDensityChange: (GridDensity) -> Unit,
    onToggleDateGroup: (String) -> Unit,
    onNavigateToFolder: (Long) -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            val topBarBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = AlcedoGlass.borderAlpha)
            Box {
                // 普通模式顶栏 – 深空黑+青墨绿风格
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
                                    shape = RoundedCornerShape(AlcedoRadius.full),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = Color.Transparent,
                                        focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
                                        unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                    leadingIcon = {
                                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp))
                                    },
                                    trailingIcon = {
                                        if (searchQuery.isNotEmpty()) {
                                            IconButton(
                                                onClick = { onSearchQueryChange("") },
                                                modifier = Modifier.size(48.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Close,
                                                    contentDescription = stringRes { clear },
                                                    modifier = Modifier.size(18.dp),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                )
                            } else {
                                Text(
                                    "Alcedo",
                                    style = MaterialTheme.typography.displaySmall,
                                    fontWeight = FontWeight.Light,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        actions = {
                            // 搜索按钮 – 轻量图标
                            IconButton(onClick = onToggleSearch) {
                                Icon(
                                    if (showSearch) Icons.Default.Close else Icons.Default.Search,
                                    contentDescription = stringRes { search },
                                    tint = if (showSearch) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            if (showSearch) {
                                FilterChip(
                                    selected = semanticEnabled,
                                    onClick = onToggleSemantic,
                                    label = {
                                        Text(
                                            "AI",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = if (semanticEnabled) FontWeight.SemiBold
                                            else FontWeight.Medium
                                        )
                                    },
                                    shape = RoundedCornerShape(AlcedoRadius.full),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    modifier = Modifier.padding(end = AlcedoSpacing.xs)
                                )
                            }
                            // 筛选按钮
                            IconButton(onClick = onOpenFilter) {
                                Icon(
                                    Icons.Default.FilterList,
                                    contentDescription = stringRes { filter },
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            // 导入按钮 – 青墨绿主色调
                            IconButton(onClick = onImport) {
                                Icon(
                                    Icons.Default.AddPhotoAlternate,
                                    contentDescription = stringRes { importText },
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = AlcedoGlass.toolbarOpacity)
                        ),
                        modifier = Modifier.drawWithContent {
                            // 玻璃态顶部高光
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = com.alcedo.studio.ui.theme.AlcedoGradient.glassHighlightVertical
                                ),
                                size = size
                            )
                            drawContent()
                            // 玻璃态底部边框
                            drawLine(
                                color = topBarBorderColor,
                                start = Offset(0f, size.height),
                                end = Offset(size.width, size.height),
                                strokeWidth = AlcedoStroke.thin.toPx()
                            )
                        },
                        scrollBehavior = scrollBehavior
                    )
                }
                // 选择模式顶栏
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
                            // P13 修复: 添加"全选"按钮,之前 selectAll() 已实现但无 UI 入口
                            IconButton(onClick = onSelectAll) {
                                Icon(Icons.Default.SelectAll, contentDescription = stringRes { albumAllImages },
                                    tint = MaterialTheme.colorScheme.onSurface)
                            }
                            IconButton(onClick = onBatchEdit) {
                                Icon(Icons.Default.AutoFixHigh, contentDescription = stringRes { batchCopyAdjustments },
                                    tint = MaterialTheme.colorScheme.onSurface)
                            }
                            IconButton(onClick = onBatchAiTag) {
                                Icon(Icons.Default.Label, contentDescription = stringRes { aiTag },
                                    tint = MaterialTheme.colorScheme.onSurface)
                            }
                            IconButton(onClick = onBatchRating) {
                                Icon(Icons.Default.Star, contentDescription = stringRes { aiRatingTitle },
                                    tint = MaterialTheme.colorScheme.onSurface)
                            }
                            IconButton(onClick = onBatchExport) {
                                Icon(Icons.Default.Share, contentDescription = stringRes { export },
                                    tint = MaterialTheme.colorScheme.onSurface)
                            }
                            IconButton(onClick = onDeleteSelected) {
                                Icon(Icons.Default.Delete, contentDescription = stringRes { delete },
                                    tint = MaterialTheme.colorScheme.error)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = AlcedoGlass.toolbarOpacity)
                        ),
                        modifier = Modifier.drawWithContent {
                            drawContent()
                            drawLine(
                                color = topBarBorderColor,
                                start = Offset(0f, size.height),
                                end = Offset(size.width, size.height),
                                strokeWidth = AlcedoStroke.thin.toPx()
                            )
                        }
                    )
                }
            }
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = selectedImages.isEmpty() && (importProgress == null || importProgress.phase == "completed"),
                enter = scaleIn(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
                exit = scaleOut(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeOut()
            ) {
                FloatingActionButton(
                    onClick = onImport,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(20.dp),
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 6.dp,
                        pressedElevation = 10.dp
                    )
                ) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = stringRes { importText })
                }
            }
        }
    ) { padding ->
        // Import progress bar shown during bulk imports
        importProgress?.let { progress ->
            if (progress.phase != "completed") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = padding.calculateTopPadding())
                ) {
                    if (progress.phase == "scanning" || progress.total <= 0) {
                        // 扫描阶段或目录递归阶段 — 不确定进度
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = "正在扫描目录...",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                        )
                    } else {
                        LinearProgressIndicator(
                            progress = { progress.current.toFloat() / progress.total.toFloat() },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = "正在导入 ${progress.current}/${progress.total}...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                    )
                }
            }
        }
        }
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
                        columns = baseColumns,
                        itemCount = 12,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                images.isEmpty() && viewMode == AlbumViewMode.PHOTO -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyState(
                            icon = Icons.Default.PhotoLibrary,
                            title = stringRes { albumNoImages },
                            message = stringRes { albumNoImagesDesc },
                            action = {
                                Button(
                                    onClick = onImport,
                                    enabled = importProgress == null || importProgress.phase == "completed",
                                    shape = RoundedCornerShape(28.dp),
                                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                                ) {
                                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null,
                                        modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        stringRes { albumImportPhotos },
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                        )
                    }
                }
                else -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // ── PixCake 视图切换栏 ──
                        ViewModeTabBar(
                            viewMode = viewMode,
                            onViewModeChange = onViewModeChange,
                            gridDensity = gridDensity,
                            onGridDensityChange = onGridDensityChange,
                            imageCount = images.size,
                            folderCount = folders.size
                        )

                        when (viewMode) {
                            AlbumViewMode.PHOTO -> {
                                // 排序/筛选栏
                                SortFilterBar(
                                    sortMode = sortMode,
                                    onSortModeChange = onSortModeChange,
                                    imageCount = images.size
                                )
                                PhotoGridSection(
                                    images = images,
                                    selectedImages = selectedImages,
                                    gridDensity = gridDensity,
                                    collapsedDateGroups = collapsedDateGroups,
                                    hasMorePages = hasMorePages,
                                    onGetThumbnail = onGetThumbnail,
                                    onLoadMore = onLoadMore,
                                    onLoadThumbnail = onLoadThumbnail,
                                    onImageClick = { image ->
                                        if (selectedImages.isNotEmpty()) {
                                            onToggleImageSelection(image.imageId)
                                        } else {
                                            navController.navigate("editor/${image.imageId}")
                                        }
                                    },
                                    onImageLongClick = { image ->
                                        if (selectedImages.isEmpty()) {
                                            onImageContextMenu(image)
                                        } else {
                                            onToggleImageSelection(image.imageId)
                                        }
                                    },
                                    onToggleDateGroup = onToggleDateGroup,
                                    // G-9 修复: 双指捏合调整列数后,反向同步到 gridDensity 状态
                                    onGridDensityChange = onGridDensityChange
                                )
                            }
                            AlbumViewMode.PROJECT -> {
                                ProjectGridSection(
                                    folders = folders,
                                    onFolderClick = onNavigateToFolder
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// PixCake View Mode Tab Bar – segmented control with grid density
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun ViewModeTabBar(
    viewMode: AlbumViewMode,
    onViewModeChange: (AlbumViewMode) -> Unit,
    gridDensity: GridDensity,
    onGridDensityChange: (GridDensity) -> Unit,
    imageCount: Int,
    folderCount: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                        MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.85f)
                    )
                )
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Segmented control – Photo | Project
        SegmentedControl(
            selected = viewMode,
            onSelect = onViewModeChange
        )

        // Right side: grid density (photo mode) or count summary
        if (viewMode == AlbumViewMode.PHOTO) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                GridDensityCycleButton(
                    density = gridDensity,
                    onChange = onGridDensityChange
                )
            }
        } else {
            Text(
                text = "$folderCount 个项目",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun SegmentedControl(
    selected: AlbumViewMode,
    onSelect: (AlbumViewMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = androidx.compose.foundation.BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(modifier = Modifier.padding(4.dp)) {
            AlbumViewMode.entries.forEach { option ->
                val isSelected = option == selected
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .clickable { onSelect(option) },
                    shape = RoundedCornerShape(24.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = option.icon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                   else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GridDensityCycleButton(
    density: GridDensity,
    onChange: (GridDensity) -> Unit
) {
    val view = LocalView.current
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .clickable {
                val next = when (density) {
                    GridDensity.COMFORTABLE -> GridDensity.STANDARD
                    GridDensity.STANDARD -> GridDensity.COMPACT
                    GridDensity.COMPACT -> GridDensity.COMFORTABLE
                }
                onChange(next)
                HapticFeedback.click(view)
            },
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = androidx.compose.foundation.BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = density.icon,
                contentDescription = density.label,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = density.columns.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Photo Grid Section – date-grouped grid with pinch-to-zoom
// ═══════════════════════════════════════════════════════════════════

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoGridSection(
    images: List<ImageModel>,
    selectedImages: Set<Long>,
    gridDensity: GridDensity,
    collapsedDateGroups: State<Set<String>>,
    hasMorePages: Boolean,
    onGetThumbnail: (Long) -> android.graphics.Bitmap?,
    onLoadMore: () -> Unit,
    onLoadThumbnail: (Long) -> Unit,
    onImageClick: (ImageModel) -> Unit,
    onImageLongClick: (ImageModel) -> Unit,
    onToggleDateGroup: (String) -> Unit,
    // G-9 修复: 双指捏合调整列数后,反向同步到 gridDensity 状态
    onGridDensityChange: (GridDensity) -> Unit
) {
    val dateGroups = remember(images) { groupImagesByDate(images) }
    val density = LocalDensity.current
    var columns by remember(gridDensity) { mutableStateOf(gridDensity.columns) }

    // 同步密度切换
    LaunchedEffect(gridDensity) {
        columns = gridDensity.columns
    }

    // G-9 修复: 双指捏合调整列数时,反向同步到 gridDensity
    // 这样 GridDensityCycleButton 显示的图标/列数与实际渲染一致
    fun syncDensityFromColumns(col: Int) {
        val matched = GridDensity.entries.firstOrNull { it.columns == col }
        if (matched != null && matched != gridDensity) {
            onGridDensityChange(matched)
        }
    }

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

    // 缩略图预加载: 通过 ThumbnailCard 内部的 LaunchedEffect 触发,此处无需手动索引映射

    Box(
        modifier = Modifier
            .fillMaxSize()
            // 双指捏合调整列数 (PixCake 风格 3-6 张/行)
            // 仅在双指时响应,不拦截单指滚动
            .pointerInput(Unit) {
                awaitEachGesture {
                    // 等待至少两个指针按下才处理,单指事件交给 LazyGrid 滚动
                    do {
                        val event = awaitPointerEvent()
                        val pointers = event.changes.filter { it.pressed }
                        if (pointers.size >= 2) {
                            val zoom = event.calculateZoom()
                            if (zoom != 1f) {
                                val target = (columns.toFloat() / zoom).roundToInt().coerceIn(2, 6)
                                if (target != columns) {
                                    columns = target
                                    // G-9 修复: 同步回 gridDensity,使密度按钮显示与实际列数一致
                                    syncDensityFromColumns(target)
                                }
                            }
                            // 双指时消费事件,防止触发滚动
                            pointers.forEach { it.consume() }
                        }
                    } while (pointers.isNotEmpty())
                }
            }
    ) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            dateGroups.forEach { group ->
                // 日期分组头部 – 跨整行
                item(span = { GridItemSpan(maxLineSpan) }, key = "header-${group.dateKey}") {
                    DateSectionHeader(
                        group = group,
                        isCollapsed = group.dateKey in collapsedDateGroups.value,
                        onToggle = { onToggleDateGroup(group.dateKey) }
                    )
                }
                // 折叠时跳过照片项
                if (group.dateKey !in collapsedDateGroups.value) {
                    items(group.images, key = { "img-${it.imageId}" }) { image ->
                        // 交错淡入动画 – staggered fade-in
                        val index = group.images.indexOf(image)
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(
                                animationSpec = tween(
                                    durationMillis = 300,
                                    delayMillis = (index % 12) * 30
                                )
                            ) + expandIn(
                                animationSpec = tween(
                                    durationMillis = 250,
                                    delayMillis = (index % 12) * 30
                                ),
                                initialSize = { androidx.compose.ui.unit.IntSize(1, 1) }
                            ),
                        ) {
                            ThumbnailCard(
                                image = image,
                                isSelected = image.imageId in selectedImages,
                                thumbnailBitmap = onGetThumbnail(image.imageId),
                                onClick = { onImageClick(image) },
                                onLongClick = { onImageLongClick(image) },
                                onLoadThumbnail = { onLoadThumbnail(image.imageId) },
                                modifier = Modifier.animateItemPlacement()
                            )
                        }
                    }
                }
            }
            if (hasMorePages) {
                item(span = { GridItemSpan(maxLineSpan) }) {
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

@Composable
private fun DateSectionHeader(
    group: DateGroup,
    isCollapsed: Boolean,
    onToggle: () -> Unit
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (isCollapsed) -90f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "chevronRotation"
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 日期标题
                Column {
                    Text(
                        text = group.displayLabel,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (group.relativeLabel.isNotEmpty()) {
                        Text(
                            text = group.relativeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 照片数量胶囊
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                ) {
                    Text(
                        text = "${group.images.size}",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                // 折叠按钮 (◀)
                Icon(
                    imageVector = Icons.Default.ChevronLeft,
                    contentDescription = if (isCollapsed) "展开" else "折叠",
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(chevronRotation),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Project Grid Section – 2-column project cards (PixCake style)
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun ProjectGridSection(
    folders: List<SleeveFolder>,
    onFolderClick: (Long) -> Unit
) {
    if (folders.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            EmptyState(
                icon = Icons.Default.FolderCopy,
                title = "暂无项目",
                message = "导入图片后将自动按文件夹创建项目"
            )
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(folders, key = { it.elementId }) { folder ->
            ProjectCard(
                folder = folder,
                onClick = { onFolderClick(folder.elementId) }
            )
        }
    }
}

@Composable
private fun ProjectCard(
    folder: SleeveFolder,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = 1f,
        label = "projectScale"
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 项目图标区 – 渐变背景
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                            ),
                            start = Offset(0f, 0f),
                            end = Offset.Infinite
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            // 项目名称
            Text(
                text = folder.elementName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // 照片数量
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoLibrary,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringRes { albumFilesCount }.format(folder.fileCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Date grouping helper
// ═══════════════════════════════════════════════════════════════════

private fun groupImagesByDate(images: List<ImageModel>): List<DateGroup> {
    if (images.isEmpty()) return emptyList()
    return images
        .groupBy { image ->
            val cd = image.exifDisplay.captureDate
            if (cd.length >= 10) cd.substring(0, 10) else "unknown"
        }
        .map { (dateKey, imgs) ->
            DateGroup(
                dateKey = dateKey,
                displayLabel = formatDateLabel(dateKey),
                relativeLabel = computeRelativeLabel(dateKey),
                images = imgs
            )
        }
        .sortedWith(
            compareByDescending<DateGroup> { it.dateKey }
                .thenBy { it.dateKey == "unknown" }
        )
}

private fun formatDateLabel(dateKey: String): String {
    if (dateKey == "unknown") return "未知日期"
    return try {
        // dateKey format: "yyyy:MM:dd"
        val parts = dateKey.split(":")
        if (parts.size == 3) {
            "${parts[0]}年${parts[1].toInt()}月${parts[2].toInt()}日"
        } else {
            dateKey
        }
    } catch (_: Exception) {
        dateKey
    }
}

private fun computeRelativeLabel(dateKey: String): String {
    if (dateKey == "unknown") return ""
    return try {
        val parts = dateKey.split(":")
        if (parts.size != 3) return ""
        val cal = java.util.Calendar.getInstance()
        val today = java.util.Calendar.getInstance()
        cal.clear()
        cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())

        val dayDiff = (today.timeInMillis - cal.timeInMillis) / (24 * 60 * 60 * 1000)
        when {
            dayDiff <= 0 -> "今天"
            dayDiff == 1L -> "昨天"
            dayDiff <= 7 -> "本周"
            dayDiff <= 30 -> "${dayDiff}天前"
            dayDiff <= 365 -> "${dayDiff / 30}个月前"
            else -> "${dayDiff / 365}年前"
        }
    } catch (_: Exception) {
        ""
    }
}

// ═══════════════════════════════════════════════════════════════════
// Thumbnail Card – premium PixCake aesthetics
// ═══════════════════════════════════════════════════════════════════

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ThumbnailCard(
    image: ImageModel,
    isSelected: Boolean,
    thumbnailBitmap: android.graphics.Bitmap?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onLoadThumbnail: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 缩略图未缓存时触发异步加载
    LaunchedEffect(image.imageId) {
        if (thumbnailBitmap == null) onLoadThumbnail()
    }
    // 按压缩放效果 – 通过 interactionSource 感知按下状态
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = when {
            isSelected -> 0.94f
            isPressed -> 0.96f
            else -> 1f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "thumbnailScale"
    )
    val elevation by animateDpAsState(
        targetValue = if (isSelected) 8.dp else if (isPressed) 4.dp else 2.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "cardElevation"
    )

    Card(
        modifier = modifier
            .graphicsLayer(scaleX = pressScale, scaleY = pressScale)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(
            1.5.dp, MaterialTheme.colorScheme.primary
        ) else null
    ) {
        Box {
            // 缩略图或占位符
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                        else MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (thumbnailBitmap != null) {
                    androidx.compose.foundation.Image(
                        bitmap = thumbnailBitmap.asImageBitmap(),
                        contentDescription = image.imageName,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    // 首字母占位符 – 暖色调
                    Text(
                        image.imageName.take(1).uppercase(),
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                        fontWeight = FontWeight.Light
                    )
                }
            }

            // 选择指示器
            this@Card.AnimatedVisibility(
                visible = isSelected,
                enter = scaleIn(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
                exit = scaleOut(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeOut()
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                    )
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        shadowElevation = 4.dp
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier
                                .size(20.dp)
                                .padding(3.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }

            // 评分浮层 – 暖金色
            val rating = image.exifDisplay.rating
            if (rating > 0) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    repeat(rating.coerceIn(1, 5)) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            modifier = Modifier.size(11.dp),
                            tint = MaterialTheme.colorScheme.tertiary
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
                        .padding(6.dp),
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f)
                ) {
                    Text(
                        "RAW",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // 图片名称浮层 – 暖色渐变
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f)),
                            startY = 0f,
                            endY = 28.dp.value
                        )
                    )
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                Text(
                    image.imageName,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Sort Filter Bar
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun SortFilterBar(
    sortMode: SortMode,
    onSortModeChange: (SortMode) -> Unit,
    imageCount: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stringRes { albumImagesCount }.format(imageCount),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            SortMode.entries.forEach { mode ->
                val selected = sortMode == mode
                FilterChip(
                    selected = selected,
                    onClick = { onSortModeChange(mode) },
                    label = {
                        Text(
                            stringRes(mode.labelKey),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.SemiBold
                            else FontWeight.Medium
                        )
                    },
                    shape = RoundedCornerShape(50),
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selected,
                        borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        selectedBorderColor = Color.Transparent,
                        borderWidth = 0.5.dp,
                        selectedBorderWidth = 0.dp
                    )
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Folder Sidebar – PixCake premium feel
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun FolderSidebar(
    folders: List<SleeveFolder>,
    onFolderSelected: (Long?) -> Unit,
    onSmartAlbumSelected: (String) -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(300.dp)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surfaceContainerLow
                    )
                )
            )
    ) {
        // 头部
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    stringRes { albumFolders },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            IconButton(onClick = onClose) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringRes { close },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        LazyColumn {
            // ── 智能相册分区 ──
            item {
                SectionLabel("智能相册")
            }
            item {
                SidebarItem(
                    icon = Icons.Default.Favorite,
                    label = "收藏夹",
                    onClick = { onSmartAlbumSelected("favorites") }
                )
            }
            item {
                SidebarItem(
                    icon = Icons.Default.Schedule,
                    label = "最近查看",
                    onClick = { onSmartAlbumSelected("recent") }
                )
            }
            item {
                SidebarItem(
                    icon = Icons.Default.FileDownload,
                    label = "待导出",
                    onClick = { onSmartAlbumSelected("to_export") }
                )
            }

            item {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                )
            }

            // ── 文件夹分区 ──
            item {
                SectionLabel(stringRes { albumFolders })
            }
            item {
                SidebarItem(
                    icon = Icons.Default.PhotoLibrary,
                    label = stringRes { albumAllImages },
                    onClick = { onFolderSelected(null) }
                )
            }
            items(folders, key = { it.elementId }) { folder: SleeveFolder ->
                SidebarItem(
                    icon = Icons.Default.Folder,
                    label = folder.elementName,
                    supportingText = stringRes { albumFilesCount }.format(folder.fileCount),
                    onClick = { onFolderSelected(folder.elementId) }
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
    )
}

@Composable
private fun SidebarItem(
    icon: ImageVector,
    label: String,
    supportingText: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (supportingText != null) {
                Text(
                    supportingText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Import Dialog – PixCake premium design
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun ImportDialog(
    onDismiss: () -> Unit,
    onSelectFiles: () -> Unit,
    onSelectDirectory: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 0.dp,
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 顶部 hero 图标 – 渐变背景
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                ),
                                start = Offset(0f, 0f),
                                end = Offset.Infinite
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.AddPhotoAlternate,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }

                // 标题与说明
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringRes { albumImportTitle },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "选择单张照片或整个目录导入到相册",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 选择文件按钮 – primary
                Button(
                    onClick = onSelectFiles,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        stringRes { albumSelectFiles },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // 选择目录按钮 – outlined
                OutlinedButton(
                    onClick = onSelectDirectory,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    )
                ) {
                    Icon(
                        Icons.Default.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        stringRes { albumSelectDirectory },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 2.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )

                Text(
                    stringRes { albumDragDrop },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text(stringRes { cancel }) }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Filter Bottom Sheet
// ═══════════════════════════════════════════════════════════════════

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

@OptIn(ExperimentalLayoutApi::class)
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
    var startDate by remember { mutableStateOf(currentFilter?.startDate?.toString() ?: "") }
    var endDate by remember { mutableStateOf(currentFilter?.endDate?.toString() ?: "") }
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
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
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
                            startDate = startDate.toLongOrNull() ?: 0L,
                            endDate = endDate.toLongOrNull() ?: Long.MAX_VALUE,
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

        Text(stringRes { albumFilterLensModel }, style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(
            value = lensModel,
            onValueChange = { lensModel = it },
            placeholder = { Text(stringRes { albumFilterLensPlaceholder }) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Camera, contentDescription = null, modifier = Modifier.size(18.dp)) }
        )

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
 * Album filter state. All fields default to "no filter" so callers can
 * construct a partial FilterState (e.g. `FilterState(rating = 4)` for
 * the "favorites" smart album).
 */
data class FilterState(
    val cameraMakes: List<String> = emptyList(),
    val cameraModels: List<String> = emptyList(),
    val lensModel: String = "",
    val startDate: Long = 0L,
    val endDate: Long = Long.MAX_VALUE,
    val rating: Int = 0,
    val fileTypes: List<String> = emptyList(),
    val aiLabels: List<String> = emptyList()
)

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
    val view = LocalView.current

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Star, contentDescription = null) },
        title = {
            Text(
                stringRes { aiRatingTitle }.format(imageName),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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
                                HapticFeedback.click(view)
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
