# Alcedo Studio v1.6.11 全面功能测试计划与验收标准

> 目标：覆盖所有功能模块的每一个操作链路、每一个公开方法、每一个边界条件和异常路径，验收标准可量化、可执行，覆盖率 100%。

---

## 一、测试范围与模块清单

| 模块编号 | 模块名称 | 核心文件/路径 | 公开方法数 | 测试优先级 |
|---------|---------|-------------|----------|-----------|
| M01 | 应用启动与生命周期 | `AlcedoApplication.kt`, `MainActivity.kt` | 8 | P0 |
| M02 | 导航与UI框架 | `MainScreen.kt`, `Routes` | 6 | P0 |
| M03 | 相册管理 | `AlbumScreen.kt`, `AlbumViewModel.kt` | 30 | P0 |
| M04 | 图片编辑器 | `EditorScreen.kt`, `EditorViewModel.kt` | 34 | P0 |
| M05 | AI 智能模块 | `AiSearchViewModel.kt`, `AiRatingViewModel.kt`, `AiModelManagerViewModel.kt` | 20 | P0 |
| M06 | 导出模块 | `ExportScreen.kt`, `ExportViewModel.kt`, `ExportService.kt` | 24 | P0 |
| M07 | 设置模块 | `SettingsScreen.kt`, `SettingsViewModel.kt` | 17 | P1 |
| M08 | 权限管理 | `PermissionHelper.kt`, `rememberPermissionState` | 18 | P0 |
| M09 | NDK/Native 层 | `AlcedoNativeBridge.kt`, `NdkSafeCall.kt`, `ndk/*.kt` | 67 | P0 |
| M10 | 后台任务 | `BackgroundTaskService.kt` | 7 | P1 |
| M11 | 崩溃报告 | `CrashReportService.kt` | 4 | P1 |
| M12 | 数据存储 | `SleeveDatabase.kt`, `*Repository.kt`, `*Dao.kt` | 12 | P1 |
| M13 | 隐私管理 | `PrivacyManager.kt`, `PrivacyConsentDialog` | 17 | P0 |
| M14 | 安全与临时文件 | `TempFileManager.kt`, `SecureHttpClient.kt` | 10 | P1 |
| M15 | 性能与稳定性 | 内存、OOM、ANR、压力测试 | N/A | P0 |

---

## 二、验收标准通用定义

| 等级 | 定义 | 通过条件 |
|-----|------|---------|
| **A (完美)** | 功能完全符合设计预期，无异常，体验流畅 | 全部断言通过，无日志异常 |
| **B (可用)** | 核心功能可用，存在轻微UI/体验瑕疵，不影响主流程 | 主链路断言通过，非阻塞性瑕疵可记录 |
| **C (降级)** | 功能在特定条件下降级（如GPU不可用回退CPU），用户可感知提示 | 降级路径正确，有明确用户提示 |
| **F (失败)** | 功能不可用、崩溃、数据丢失、阻塞主流程 | 任何P0用例出现即阻塞发布 |

---

## 三、详细测试用例

### M01 应用启动与生命周期

#### TC-M01-001 冷启动完整链路
- **前置条件**：应用未在后台，或已强制停止
- **操作步骤**：
  1. 点击桌面图标启动应用
  2. 观察 Splash 屏 → 主界面加载过程
  3. 检查 Logcat 中 `AlcedoApplication ready` 日志
- **预期结果**：
  - Splash 屏显示 ≤ 3s
  - 主界面（Album Tab）正常渲染
  - Logcat 中 `native=$nativeOk` 与 `gpu=$gpuAvailable` 有值
  - `ContextProvider.init(this)` 先于所有服务调用
  - SleeveDatabase 已打开
  - 内置 Preset 已种子化
  - 临时文件孤立项已清理
  - 无崩溃、无ANR
- **验收标准**：等级 A

#### TC-M01-002 外部图片 Intent 打开（冷启动）
- **前置条件**：应用未运行，从文件管理器选择图片
- **操作步骤**：
  1. 在文件管理器中点击一张 JPG/PNG
  2. 选择 "Alcedo Studio" 打开
- **预期结果**：
  - 应用启动后直接进入 EditorScreen
  - 图片正常加载并显示预览
  - `handleImageIntent()` 在 `onCreate` 中被调用
- **验收标准**：等级 A

#### TC-M01-003 外部图片 Intent 打开（热启动）
- **前置条件**：应用已在后台运行
- **操作步骤**：
  1. 从文件管理器选择图片，用 Alcedo 打开
  2. 验证 `onNewIntent()` 被调用
- **预期结果**：
  - `onNewIntent()` 调用 `handleImageIntent()`
  - `ACTION_VIEW` + `image/*` 类型被正确识别
  - URI 被记录到 Logcat
  - 不崩溃
- **验收标准**：等级 A

#### TC-M01-004 外部 Intent 非 image 类型
- **前置条件**：应用在运行中
- **操作步骤**：从其他应用发送 `ACTION_VIEW` + `text/plain` Intent
- **预期结果**：
  - `handleImageIntent()` 不处理非 image 类型
  - 应用正常显示当前界面
  - 不崩溃
- **验收标准**：等级 A

#### TC-M01-005 Native 库加载失败降级
- **前置条件**：模拟无 Vulkan 设备（或删除 so 文件）
- **操作步骤**：启动应用
- **预期结果**：
  - `NdkSafeCall.ensureLoaded()` 返回 false
  - Logcat 显示 `alcedo_native unavailable; running in software/degraded mode`
  - 应用不崩溃，UI 正常可用
  - 设置页中 GPU 可用性显示 "Unavailable"
  - `NdkSafeCall.isAvailable` 返回 false
  - `NdkSafeCall.lastError` 包含错误信息
- **验收标准**：等级 C（降级路径正确）

#### TC-M01-006 内存压力处理 — onTrimMemory
- **前置条件**：应用在 Editor 中打开大分辨率 RAW
- **操作步骤**：
  1. 使用 `adb shell am send-trim-memory com.alcedo.studio RUNNING_LOW`
  2. 观察应用表现
  3. 分别测试 `TRIM_MEMORY_RUNNING_LOW`、`TRIM_MEMORY_RUNNING_CRITICAL`、`TRIM_MEMORY_COMPLETE`、`TRIM_MEMORY_MODERATE`、`TRIM_MEMORY_BACKGROUND`
- **预期结果**：
  - `RUNNING_LOW`/`RUNNING_CRITICAL`/`COMPLETE`: 调用 `gpuService.onLowMemory()` + `AlcedoNativeBridge.nativeOnLowMemory()` + `tempFileManager.cleanupAll()`
  - `MODERATE`/`BACKGROUND`: 仅调用 `AlcedoNativeBridge.nativeOnLowMemory()`
  - 不崩溃
  - 缩略图缓存被释放
  - 临时文件被清理
- **验收标准**：等级 A

#### TC-M01-007 内存压力处理 — onLowMemory
- **前置条件**：应用在运行中
- **操作步骤**：模拟系统 `onLowMemory()` 回调
- **预期结果**：
  - `gpuService.onLowMemory()` 被调用
  - `AlcedoNativeBridge.nativeOnLowMemory()` 被调用
  - 不崩溃
- **验收标准**：等级 A

#### TC-M01-008 nativeOnLowMemory 执行
- **前置条件**：应用在运行中
- **操作步骤**：直接调用 `AlcedoNativeBridge.nativeOnLowMemory()`
- **预期结果**：
  - `System.gc()` 被触发
  - `Thumbnail.nativeGenerateThumbnail(0, 0)` 被调用（清除内部缓存）
  - 不崩溃
- **验收标准**：等级 A

#### TC-M01-009 后台恢复
- **前置条件**：应用在 Editor 中，按 Home 键进入后台 10 分钟
- **操作步骤**：从最近任务恢复应用
- **预期结果**：
  - 恢复到 Editor 界面，图片和编辑状态保留
  - 无重新加载的闪烁
- **验收标准**：等级 A

#### TC-M01-010 配置变更（语言/深色模式）
- **操作步骤**：在系统设置中切换语言或深色模式
- **预期结果**：
  - `onConfigurationChanged()` 被调用
  - Compose 自动重组
  - 不崩溃
- **验收标准**：等级 A

#### TC-M01-011 主进程判断
- **操作步骤**：启动应用，检查 `isMainProcess()` 逻辑
- **预期结果**：
  - 主进程名不包含 `:`
  - CrashReportService 仅在主进程安装
  - `:crash` 子进程不安装 UncaughtExceptionHandler
- **验收标准**：等级 A

#### TC-M01-012 隐私同意状态加载超时
- **前置条件**：DataStore 读取延迟
- **操作步骤**：模拟 DataStore 读取超过 4 秒
- **预期结果**：
  - `LOAD_STATE_TIMEOUT_MS` (4000ms) 后自动解除 Splash 保持
  - 显示 ConsentGate（未同意状态）
  - 不无限卡在 LoadingState
- **验收标准**：等级 A

#### TC-M01-013 ContextProvider 初始化顺序
- **操作步骤**：启动应用，检查 `onCreate()` 执行顺序
- **预期结果**：
  - `ContextProvider.init(this)` 先于所有服务调用
  - `CrashReportService.install()` 仅在 `isMainProcess()` 为 true 时执行
  - `sleeveService.open()` 和 `presetService.ensureBuiltIns()` 在后台协程执行
- **验收标准**：等级 A

#### TC-M01-014 Native 库加载后目录传递
- **前置条件**：Native 库加载成功
- **操作步骤**：启动应用
- **预期结果**：
  - `nativeSetCacheDir(cacheDir.absolutePath)` 被调用
  - `nativeSetTempDir(noBackupFilesDir.absolutePath)` 被调用
- **验收标准**：等级 A

#### TC-M01-015 后台服务初始化
- **操作步骤**：冷启动应用
- **预期结果**：
  - `sleeveService.open()` 被调用
  - `presetService.ensureBuiltIns()` 被调用
  - `tempFileManager.sweepOrphans()` 被调用
  - 任一失败不导致崩溃
- **验收标准**：等级 A

---

### M02 导航与UI框架

#### TC-M02-001 底部导航栏切换
- **操作步骤**：依次点击 Album → Editor → AI → Settings
- **预期结果**：
  - 每个 Tab 正确渲染对应内容
  - 导航栏选中态高亮正确
  - Editor Tab 在打开图片后显示图片名而非空标题
- **验收标准**：等级 A

#### TC-M02-002 平板 NavigationRail 自适应
- **前置条件**：在平板或宽度 ≥600dp 设备上运行
- **操作步骤**：启动应用，观察布局
- **预期结果**：左侧显示 NavigationRail，底部无 BottomBar
- **验收标准**：等级 A

#### TC-M02-003 返回键行为
- **操作步骤**：
  1. Album → 打开图片进入 Editor → 点击返回
  2. AI → AI Search → 点击返回
  3. Settings → About → 点击返回
- **预期结果**：
  - 1. 返回到 Album，Editor 弹栈
  - 2. 返回到 AI Hub
  - 3. 返回到 Settings
- **验收标准**：等级 A

#### TC-M02-004 导航状态保存与恢复
- **操作步骤**：
  1. 在 AI Search 页面
  2. 切换到 Settings，再切回 AI
- **预期结果**：
  - AI Tab 恢复到 AI Hub（非空）
  - 导航状态通过 `saveState = true` 保持
- **验收标准**：等级 A

#### TC-M02-005 首次启动隐私同意门控
- **前置条件**：全新安装，未同意隐私
- **操作步骤**：启动应用
- **预期结果**：
  - 显示 `PrivacyConsentDialog`
  - 点击 "Accept" → `privacyManager.setConsent(true)` + `setCloudLlmAllowed(false)`
  - 点击 "Decline" → `privacyManager.setConsent(false)`
  - 同意后进入权限请求
- **验收标准**：等级 A

#### TC-M02-006 权限门控 UI
- **前置条件**：已同意隐私但未授予权限
- **操作步骤**：观察权限请求界面
- **预期结果**：
  - 非永久拒绝：显示 "Grant Access" 按钮，点击触发权限请求
  - 永久拒绝：显示 "Open Settings" 按钮，点击跳转系统设置
- **验收标准**：等级 A

#### TC-M02-007 Routes 辅助函数
- **操作步骤**：调用 `Routes.editorRoute("abc123")` 和 `Routes.exportRoute("abc123")`
- **预期结果**：
  - `editorRoute()` 返回 `"editor/abc123"`
  - `exportRoute()` 返回 `"export?imageId=abc123"`
  - `exportRoute(null)` 返回 `"export?imageId="`
- **验收标准**：等级 A

#### TC-M02-008 AI Hub 导航卡片
- **操作步骤**：在 AI Tab 依次点击 Search / Rating / Models 卡片
- **预期结果**：
  - `AiHub` 正确渲染三个卡片
  - 点击分别导航到 `AI_SEARCH` / `AI_RATING` / `AI_MODELS`
- **验收标准**：等级 A

#### TC-M02-009 子页面导航栏隐藏
- **操作步骤**：进入 About / Privacy / AI Search / Export 等子页面
- **预期结果**：
  - `showNavChrome` 为 false，底部 BottomBar 和左侧 NavigationRail 均不显示
  - 返回主 Tab 后导航栏恢复显示
- **验收标准**：等级 A

#### TC-M02-010 Editor Tab 选中态
- **操作步骤**：从 Album 打开图片进入 Editor
- **预期结果**：
  - `currentRoute` 为 `"editor/{imageId}"`
  - BottomBar/NavRail 中 Editor 项保持选中态（`startsWith("editor/")` 匹配）
- **验收标准**：等级 A

---

### M03 相册管理

#### TC-M03-001 图片导入（单张）
- **操作步骤**：Album 点击 "Import"，选择 1 张图片
- **预期结果**：
  - 图片出现在相册网格中
  - 后台任务栏显示导入进度
  - 导入完成后缩略图正常显示
  - `importService.import()` 被调用
  - `imageRepository.upsert()` 被调用
  - `sleeveRepository.importFile()` 被调用
  - 缩略图异步生成
- **验收标准**：等级 A

#### TC-M03-002 图片导入（多张批量）
- **操作步骤**：Album 点击 "Import"，选择 20 张图片
- **预期结果**：
  - 所有图片出现在相册网格中
  - 后台任务栏显示批量进度
  - 20张批量导入不触发ANR
  - `ImportProgress` 正确更新
- **验收标准**：等级 A

#### TC-M03-003 导入取消
- **操作步骤**：导入 50 张图片，中途点击 Cancel
- **预期结果**：
  - `taskService.isCancelled(taskId)` 返回 true
  - 剩余图片不再导入
  - 已导入图片保留
  - 不崩溃
- **验收标准**：等级 A

#### TC-M03-004 导入空列表
- **操作步骤**：调用 `import(emptyList())`
- **预期结果**：立即返回空列表，不启动后台任务
- **验收标准**：等级 A

#### TC-M03-005 文件夹创建
- **操作步骤**：
  1. 点击侧边栏 "Create Folder"
  2. 输入 "TestFolder" 确认
  3. 切换到此文件夹
- **预期结果**：
  - `sleeveRepository.createFolder()` 被调用
  - 文件夹出现在 CollectionsPanel 中
  - 切换后网格显示为空
  - 导入图片到此文件夹，图片仅属于此文件夹
- **验收标准**：等级 A

#### TC-M03-006 文件夹创建 — 空名称
- **操作步骤**：输入空格或空字符串创建文件夹
- **预期结果**：`createFolder()` 忽略空名称，不创建
- **验收标准**：等级 A

#### TC-M03-007 搜索功能
- **操作步骤**：
  1. 在搜索框输入 "sunset"
  2. 清空搜索
  3. 输入无结果关键词 "xyz123"
- **预期结果**：
  - `searchService.search()` 被调用
  - 搜索结果实时过滤
  - 清空后恢复全部显示
  - 无结果时显示空状态
- **验收标准**：等级 A

#### TC-M03-008 排序功能
- **操作步骤**：点击排序下拉菜单，依次选择每种排序字段
- **预期结果**：`setSort()` 调用 `albumService.query()` 或 `observeFolder()`，图片按所选字段正确排序
- **验收标准**：等级 A

#### TC-M03-009 视图切换与缩放
- **操作步骤**：
  1. Grid/List 切换按钮
  2. 缩放滑块从 2 拖动到 14
- **预期结果**：
  - Grid 变为 List，布局正确
  - 列数实时变化，缩略图大小适应
- **验收标准**：等级 A

#### TC-M03-010 图片选择 — 单选
- **操作步骤**：点击一张图片
- **预期结果**：`toggleSelection(id)` 被调用，`selection` 集合包含该 ID
- **验收标准**：等级 A

#### TC-M03-011 图片选择 — 全选/清除
- **操作步骤**：点击全选 → 清除选择
- **预期结果**：
  - `selectAll()` 将所有图片 ID 加入 selection
  - `clearSelection()` 清空 selection
- **验收标准**：等级 A

#### TC-M03-012 批量应用 Preset
- **操作步骤**：
  1. 选择 3 张图片
  2. 点击批量编辑 → Apply Preset
- **预期结果**：
  - `applyPresetToSelection()` 被调用
  - `batchEditService.applyPreset()` 被调用
  - 后台任务栏显示进度
  - TopBar 显示选中数量 Badge
- **验收标准**：等级 A

#### TC-M03-013 批量应用 Preset — 无选择
- **操作步骤**：未选择任何图片时点击 Apply Preset
- **预期结果**：显示 "Select images first" 消息
- **验收标准**：等级 A

#### TC-M03-014 批量应用 Preset — Preset 不存在
- **操作步骤**：选择图片后应用一个已删除的 Preset
- **预期结果**：显示 "Preset not found" 错误
- **验收标准**：等级 A

#### TC-M03-015 复制调整
- **操作步骤**：长按一张图片 → Copy Adjustments
- **预期结果**：
  - `copyAdjustments(id)` 被调用
  - `clipAdjustmentsImageId` 被设置
  - 显示 "Adjustments copied" 消息
- **验收标准**：等级 A

#### TC-M03-016 粘贴调整
- **操作步骤**：选择另一张图片 → Paste Adjustments
- **预期结果**：
  - `pasteAdjustments(targetId)` 被调用
  - `batchEditService.copyAdjustments()` 被调用
  - 显示 "Adjustments pasted" 消息
- **验收标准**：等级 A

#### TC-M03-017 粘贴调整 — 未复制
- **操作步骤**：未执行 Copy Adjustments 就点击 Paste
- **预期结果**：显示 "Copy adjustments first" 错误
- **验收标准**：等级 A

#### TC-M03-018 添加到收藏夹
- **操作步骤**：长按一张图片 → Add to Collection
- **预期结果**：
  - `addToCollection(id)` 被调用
  - `sleeveRepository.importFile()` 被调用
  - 显示 "Added to [folder]" 消息
- **验收标准**：等级 A

#### TC-M03-019 图片元数据操作
- **操作步骤**：长按一张图片，依次测试：
  - Rate (1-5星)
  - Set Flag (Pick/Reject/None)
  - Set Color Label
  - Set Hidden
  - Delete
- **预期结果**：
  - `setRating()` / `setFlag()` / `setColorLabel()` / `setHidden()` / `delete()` 被调用
  - 每项操作后图片元数据正确更新
  - 删除后图片从网格消失，selection 清空
- **验收标准**：等级 A

#### TC-M03-020 批量元数据操作
- **操作步骤**：选择多张图片，分别应用评分、标记、颜色标签
- **预期结果**：
  - `applyRatingToSelection()` / `applyFlagToSelection()` / `applyColorLabelToSelection()` 被调用
  - 所有选中图片的元数据更新
- **验收标准**：等级 A

#### TC-M03-021 空状态显示
- **前置条件**：全新安装，无任何图片
- **操作步骤**：启动应用进入 Album
- **预期结果**：显示 EmptyState，包含标题、副标题、Import 按钮
- **验收标准**：等级 A

#### TC-M03-022 后台任务栏
- **操作步骤**：导入大量图片，观察底部任务栏
- **预期结果**：
  - 显示进度条和任务名称
  - 点击取消可终止任务
  - 任务完成后自动消失
- **验收标准**：等级 A

#### TC-M03-023 AI 评分 — 相册中
- **操作步骤**：选择图片 → Cull Selection
- **预期结果**：
  - `cullSelection()` 被调用
  - `aiRatingService.cullBatch()` 被调用
  - 后台任务注册
  - 取消时 `taskService.isCancelled()` 被检查
- **验收标准**：等级 A

#### TC-M03-024 统计信息刷新
- **操作步骤**：导入/删除图片后检查统计
- **预期结果**：
  - `refreshStats()` 被调用
  - `albumService.stats()` / `cameras()` / `lenses()` 返回正确数据
- **验收标准**：等级 A

#### TC-M03-025 过滤功能
- **操作步骤**：设置 FilterCombo（相机/镜头/评分/标记/颜色）
- **预期结果**：
  - `setFilter()` 触发 `flatMapLatest` 重新查询
  - 空过滤器时使用 `observeFolder()` / `observeAll()`
  - 有过滤器时使用 `albumService.query()`
- **验收标准**：等级 A

#### TC-M03-026 清除过滤
- **操作步骤**：点击 Clear Filter
- **预期结果**：`clearFilter()` 重置为 `FilterCombo()`，selection 清空
- **验收标准**：等级 A

#### TC-M03-027 消息消除
- **操作步骤**：显示消息（如 "Adjustments copied"）后调用 `dismissMessage()`
- **预期结果**：`message` 字段变为 null
- **验收标准**：等级 A

#### TC-M03-028 取消所有后台任务
- **操作步骤**：调用 `cancelBackgroundTasks()`
- **预期结果**：
  - 遍历 `taskService.tasks.value` 中所有 `cancellable=true` 的任务
  - 每个任务调用 `taskService.cancel(task.id)`
  - 不可取消任务不被取消
- **验收标准**：等级 A

#### TC-M03-029 加载 TOP 评分图片
- **操作步骤**：调用 `loadTopRated(limit = 50)`
- **预期结果**：
  - `aiRatingService.topRated(50)` 被调用
  - `topRated` 按 `overallScore` 降序排列
  - 失败时显示错误
- **验收标准**：等级 A

#### TC-M03-030 构建元数据
- **操作步骤**：调用 `buildMetadata(img)`
- **预期结果**：
  - 返回包含 iso, aperture, focalLength, cameraModel, lensModel, shutterSpeed 的 Map
  - 仅包含 img 中存在的字段
- **验收标准**：等级 A

#### TC-M03-031 响应式网格驱动
- **操作步骤**：依次改变 folderPath、filter、sort
- **预期结果**：
  - `combine(folderPath, filter, sort)` 触发 `flatMapLatest`
  - 无 filter 时调用 `observeFolder()` / `observeAll()`
  - 有 filter 时调用 `albumService.query()`
  - 多次快速切换只保留最后一次查询结果
- **验收标准**：等级 A

#### TC-M03-032 导入进度镜像
- **操作步骤**：导入图片时观察 `isImporting` 状态
- **预期结果**：
  - `importService.progress` 收集后更新 `isImporting`
  - `completed < total && total > 0` 时为 true
  - 完成后变为 false
- **验收标准**：等级 A

#### TC-M03-033 文件夹树观察
- **操作步骤**：创建/删除文件夹
- **预期结果**：
  - `sleeveRepository.observeTree()` 实时更新 `folders`
  - `foldersOnly()` 过滤后只返回文件夹类型
- **验收标准**：等级 A

#### TC-M03-034 ViewModel 清理
- **操作步骤**：AlbumViewModel 被 GC 回收
- **预期结果**：`onCleared()` 被调用，无内存泄漏
- **验收标准**：等级 A

#### TC-M03-035 切换文件夹
- **操作步骤**：调用 `setFolder("/TestFolder")`
- **预期结果**：
  - `folderPath` 更新为 `/TestFolder`
  - `selection` 被清空
  - 触发 `flatMapLatest` 重新查询
- **验收标准**：等级 A

#### TC-M03-036 消除错误
- **操作步骤**：显示错误消息后调用 `dismissError()`
- **预期结果**：`error` 字段变为 null
- **验收标准**：等级 A

#### TC-M03-037 取消单个后台任务
- **操作步骤**：调用 `cancelTask(taskId)`
- **预期结果**：
  - `taskService.cancel(taskId)` 被调用
  - 对应任务被标记为取消并从活跃列表移除
- **验收标准**：等级 A

---

### M04 图片编辑器

#### TC-M04-001 打开 JPG 图片
- **操作步骤**：从 Album 点击一张 JPG 进入 Editor
- **预期结果**：
  - `openImage(id)` 被调用
  - `imageRepository.getImage(id)` 返回 ImageItem
  - `historyService.ensureHistory(id)` 确保版本树存在
  - `pipelineService.open(Uri)` 成功
  - `pipelineService.updateParams(baseline)` 推送基线参数
  - `beforeBitmap` 快照初始预览
  - EditorScreen 加载，显示图片名在 TopBar
  - 预览区域显示图片
  - ColorSpaceIndicator 显示正确色彩空间
- **验收标准**：等级 A

#### TC-M04-002 打开图片 — 不存在
- **操作步骤**：传入无效 imageId
- **预期结果**：`imageRepository.getImage(id)` 返回 null，UI 显示 "Image not found: $id" 错误
- **验收标准**：等级 A

#### TC-M04-003 打开图片 — Pipeline 打开失败
- **操作步骤**：模拟 `pipelineService.open()` 返回 false
- **预期结果**：UI 显示 "Open failed" 错误
- **验收标准**：等级 A

#### TC-M04-004 打开 RAW 文件
- **操作步骤**：从 Album 点击一张 DNG/ARW 进入 Editor
- **预期结果**：
  - RAW 解码成功，显示预览
  - RAW 面板可用
  - 元数据（相机型号、镜头信息）正确显示
- **验收标准**：等级 A

#### TC-M04-005 单参数调整（exposure）
- **操作步骤**：调整 Exposure +1.0
- **预期结果**：
  - `updateParam("exposure", 1.0f)` 被调用
  - `applyField()` 正确映射到 `params.copy(exposure = value)`
  - `pipelineService.updateParams(updated)` 被调用
  - `dirty` 状态变为 true
  - 渲染防抖启动（80ms）
  - 防抖期间再次调整，前一个 renderDebounceJob 被取消
- **验收标准**：等级 A

#### TC-M04-006 全部 29 个参数字段映射
- **操作步骤**：逐一测试 `applyField()` 的所有 29 个字段：
  - exposure, contrast, highlights, shadows, whites, blacks
  - temperature, tint, saturation, vibrance, clarity, sharpen
  - liftHue, liftSat, liftLum, gammaHue, gammaSat, gammaLum, gainHue, gainSat, gainLum
  - rotation, perspectiveH, perspectiveV
  - filmGrainAmount, filmGrainSize, halationAmount, lutIntensity, rawNoiseReduction
- **预期结果**：每个字段正确映射到 `AdjustmentParams` 的对应属性；未知字段返回原 params 不变
- **验收标准**：等级 A

#### TC-M04-007 未知参数字段
- **操作步骤**：调用 `updateParam("unknown_field", 1.0f)`
- **预期结果**：`applyField()` 返回原 params 不变，不崩溃
- **验收标准**：等级 A

#### TC-M04-008 渲染防抖
- **操作步骤**：快速连续调整 5 个参数（间隔 < 80ms）
- **预期结果**：
  - 仅最后一次调整触发 `pipelineService.render()`
  - 中间的 renderDebounceJob 被取消
  - 不触发渲染队列溢出
- **验收标准**：等级 A

#### TC-M04-009 Tone 调整 — 全面板
- **操作步骤**：
  1. 切换到底部 Tone Tab
  2. 调整 Exposure +1.0, Contrast +20
  3. 调整 Highlights -30, Shadows +40
  4. 调整 Temperature 5500K, Tint +10
  5. 在 ToneCurvePanel 添加曲线点
- **预期结果**：
  - 每次调整后预览实时更新
  - 参数值正确同步到 ViewModel
  - `dirty` 状态变为 true
- **验收标准**：等级 A

#### TC-M04-010 Look 调整
- **操作步骤**：
  1. 切换到 Look Tab
  2. 调整 Saturation, Vibrance
  3. 调整 HLS Profile
  4. 在 ColorWheelView 调整
- **预期结果**：预览颜色正确变化
- **验收标准**：等级 A

#### TC-M04-011 Display 面板
- **操作步骤**：
  1. 切换到 Display Tab
  2. 更改 Display Transform
  3. 观察 WaveformScope
- **预期结果**：
  - DisplayTransform 切换正确
  - WaveformScope 实时渲染图像波形
- **验收标准**：等级 A

#### TC-M04-012 Geometry 调整
- **操作步骤**：
  1. 切换到 Geometry Tab
  2. 旋转 +90°
  3. 调整 Perspective H/V
- **预期结果**：
  - 预览正确旋转/透视变换
  - 边界无黑边溢出
- **验收标准**：等级 A

#### TC-M04-013 RAW 解码设置
- **前置条件**：打开 RAW 文件
- **操作步骤**：
  1. 切换到 RAW Tab
  2. 切换 Demosaic 算法 (GPU/CPU)
  3. 调整 Black Level, White Point
  4. 调整 Noise Reduction
- **预期结果**：
  - RAW 解码参数生效
  - GPU/CPU 切换后预览正确更新
- **验收标准**：等级 A

#### TC-M04-014 设置参数 — setParams
- **操作步骤**：调用 `setParams(newParams)`
- **预期结果**：
  - `params` 被完整替换
  - `pipelineService.updateParams(params)` 被调用
  - `dirty` 正确反映与 baseline 的差异
- **验收标准**：等级 A

#### TC-M04-015 重置调整
- **操作步骤**：点击 Reset Adjustments
- **预期结果**：
  - `resetAdjustments()` 调用 `setParams(AdjustmentParams.DEFAULT)`
  - `commitChange("Reset adjustments")` 被调用
  - 预览回到原始状态
- **验收标准**：等级 A

#### TC-M04-016 提交更改 — commitChange
- **操作步骤**：调整参数后调用 `commitChange("Adjustment")`
- **预期结果**：
  - `buildDelta()` 构建稀疏 delta
  - `historyService.recordChange(id, delta, label)` 被调用
  - `baselineParams` 更新为当前 params
  - `dirty` 变为 false
  - `canRedo` 变为 false
- **验收标准**：等级 A

#### TC-M04-017 提交更改 — 无变化
- **操作步骤**：在 params == baselineParams 时调用 `commitChange()`
- **预期结果**：直接返回，不调用 `historyService.recordChange()`
- **验收标准**：等级 A

#### TC-M04-018 构建稀疏 Delta
- **操作步骤**：调整 exposure 和 contrast，调用 `buildDelta()`
- **预期结果**：
  - `AdjustmentParamsDelta.overrides` 仅包含 exposure 和 contrast
  - 未变化的字段不包含在 delta 中
- **验收标准**：等级 A

#### TC-M04-019 Undo
- **操作步骤**：
  1. 调整 Exposure
  2. 调整 Contrast
  3. 点击 Undo（两次）
- **预期结果**：
  - `historyService.undo(id)` 被调用
  - `reloadActiveVersionParams()` 更新 params 和 baseline
  - `canRedo` 变为 true
  - 预览回退到上一步参数
- **验收标准**：等级 A

#### TC-M04-020 Redo
- **操作步骤**：Undo 后点击 Redo
- **预期结果**：
  - `historyService.redo(id)` 被调用
  - `reloadActiveVersionParams()` 更新 params
  - 参数恢复
- **验收标准**：等级 A

#### TC-M04-021 版本管理 — 创建虚拟副本
- **操作步骤**：
  1. 点击版本菜单 (Layers 图标)
  2. 创建 Virtual Copy
- **预期结果**：
  - `commitIfDirty()` 先提交未保存更改
  - `historyService.createVirtualCopy(id, name)` 被调用
  - 自动切换到新版本
- **验收标准**：等级 A

#### TC-M04-022 版本管理 — 切换版本
- **操作步骤**：在版本列表中点击不同版本
- **预期结果**：
  - `switchVersion(versionId)` 被调用
  - `historyService.switchVersion()` 被调用
  - params 和预览切换到对应版本
- **验收标准**：等级 A

#### TC-M04-023 版本管理 — 删除版本
- **操作步骤**：删除一个版本
- **预期结果**：
  - `deleteVersion(versionId)` 被调用
  - `historyService.deleteVersion()` 被调用
  - 版本从列表消失
- **验收标准**：等级 A

#### TC-M04-024 前后对比
- **操作步骤**：
  1. 调整参数后点击 Compare 按钮
  2. 再次点击关闭对比
- **预期结果**：
  - 显示对比视图
  - Before 为 `beforeBitmap`，After 为当前预览
  - Compare 按钮高亮态正确
- **验收标准**：等级 A

#### TC-M04-025 Mask 面板 — Brush Mask
- **操作步骤**：添加 Brush Mask
- **预期结果**：
  - `addBrushMask()` 被调用
  - `maskService.newBrushMask()` 创建 Mask
  - `pipelineService.applyMask(record)` 被调用
  - Mask 列表更新
- **验收标准**：等级 A

#### TC-M04-026 Mask 面板 — Radial/Linear/Luminance Mask
- **操作步骤**：依次添加 Radial/Linear/Luminance Mask
- **预期结果**：
  - `addRadialMask()` / `addLinearMask()` / `addLuminanceMask()` 被调用
  - 对应参数正确传入
  - 每种 Mask 都添加到 pendingMasks 列表
- **验收标准**：等级 A

#### TC-M04-027 Mask 面板 — AI 主体/天空/背景 Mask
- **操作步骤**：依次添加 Subject/Sky/Background Mask
- **预期结果**：
  - `addSubjectMask()` / `addSkyMask()` / `addBackgroundMask()` 被调用
  - `maskInferenceService.buildSubjectMask()` 被调用
  - AI Mask 失败时显示 "AI mask unavailable" 错误
- **验收标准**：等级 A

#### TC-M04-028 Mask 面板 — 切换启用/禁用
- **操作步骤**：切换 Mask 启用/禁用
- **预期结果**：
  - `toggleMask(record)` 被调用
  - `syncMasksToPipeline()` 被调用
  - `pipelineService.clearMasks()` 被调用
  - 仅启用的 Mask 被重新应用
  - `rerender()` 被调用
- **验收标准**：等级 A

#### TC-M04-029 Mask 面板 — 删除
- **操作步骤**：删除一个 Mask
- **预期结果**：
  - `removeMask(id)` 被调用
  - Mask 从 pendingMasks 列表移除
  - `syncMasksToPipeline()` 重新同步
- **验收标准**：等级 A

#### TC-M04-030 Preset 应用
- **操作步骤**：
  1. 打开 Presets 面板
  2. 应用一个内置 Preset
- **预期结果**：
  - `applyPreset(preset)` 被调用
  - `setParams(preset.adjustments)` 被调用
  - `commitChange("Preset: ${name}")` 被调用
  - 预览更新
- **验收标准**：等级 A

#### TC-M04-031 保存当前为 Preset
- **操作步骤**：调整参数后 Save Current As Preset
- **预期结果**：
  - `saveCurrentAsPreset(name, category)` 被调用
  - `presetService.save()` 被调用
  - 保存的 Preset 出现在列表
- **验收标准**：等级 A

#### TC-M04-032 收藏/取消收藏 Preset
- **操作步骤**：点击 Preset 的收藏图标
- **预期结果**：
  - `togglePresetFavorite(preset)` 被调用
  - `presetService.setFavorite()` 被调用
  - 收藏态正确显示
- **验收标准**：等级 A

#### TC-M04-033 曲线调整
- **操作步骤**：在 ToneCurvePanel 添加曲线点
- **预期结果**：
  - `setCurvePoints(points)` 被调用
  - `params.toneCurveMaster` 被更新
  - `pipelineService.updateParams()` 被调用
  - `dirty` 变为 true
- **验收标准**：等级 A

#### TC-M04-034 LMT 应用
- **操作步骤**：应用 .cube LUT 文件
- **预期结果**：
  - `applyLmt(path)` 被调用
  - `params.lutPath` 和 `params.lutIntensity` 被更新
  - `pipelineService.updateParams()` 被调用
- **验收标准**：等级 A

#### TC-M04-035 水印配置
- **操作步骤**：在 Watermark 面板配置水印
- **预期结果**：
  - `setWatermarkConfig(config)` 被调用
  - `dirty` 变为 true
- **验收标准**：等级 A

#### TC-M04-036 EXIF 字段编辑
- **操作步骤**：修改 EXIF 字段
- **预期结果**：
  - `setExifField(key, value)` 被调用
  - exif map 更新
  - `dirty` 变为 true
- **验收标准**：等级 A

#### TC-M04-037 评分/标记/颜色标签
- **操作步骤**：在 Editor 中设置评分、标记、颜色标签
- **预期结果**：
  - `setRating()` / `setFlag()` / `setColorLabel()` 被调用
  - `imageRepository.setRating()` / `setFlag()` / `setColorLabel()` 被调用
- **验收标准**：等级 A

#### TC-M04-038 面板切换
- **操作步骤**：依次切换 BASIC → TONE_CURVE → COLOR_WHEELS → HSL → GEOMETRY → EFFECTS → MASKS → PRESETS → HISTORY → RAW → EXIF
- **预期结果**：
  - `selectPanel(panel)` 被调用
  - `activePanel` 正确更新
  - 每个面板内容正确渲染
- **验收标准**：等级 A

#### TC-M04-039 未保存更改提示
- **操作步骤**：
  1. 调整参数使 `dirty=true`
  2. 按系统返回键
- **预期结果**：弹出确认对话框 "Unsaved changes"，提供 Discard 和 Cancel
- **验收标准**：等级 A

#### TC-M04-040 导出跳转
- **操作步骤**：点击导出按钮 (Output 图标)
- **预期结果**：导航到 ExportScreen，携带当前 imageId
- **验收标准**：等级 A

#### TC-M04-041 缩放与平移
- **操作步骤**：在预览区域双指缩放、单指平移
- **预期结果**：
  - 缩放比例限制在合理范围
  - 平移不超出图片边界
  - 渲染指示器在重渲染时显示
- **验收标准**：等级 A

#### TC-M04-042 关闭编辑器
- **操作步骤**：按返回键关闭编辑器
- **预期结果**：
  - `close()` 被调用
  - `commitIfDirty("Auto-save")` 先提交未保存更改
  - `pipelineService.close()` 被调用
  - UI 状态重置为初始值
- **验收标准**：等级 A

#### TC-M04-043 ViewModel 清理
- **操作步骤**：ViewModel 被 GC 回收
- **预期结果**：
  - `onCleared()` 调用 `pipelineService.close()`
  - 释放 native pipeline handles
- **验收标准**：等级 A

#### TC-M04-044 错误消除
- **操作步骤**：点击错误消息的关闭按钮
- **预期结果**：`dismissError()` 被调用，`error` 变为 null
- **验收标准**：等级 A

#### TC-M04-045 强制重新渲染
- **操作步骤**：调用 `rerender()`
- **预期结果**：
  - `pipelineService.render()` 被调用
  - 失败时 `error` 显示 "Render failed: ..."
- **验收标准**：等级 A

#### TC-M04-046 自动保存未提交更改
- **操作步骤**：调整参数使 `dirty=true`，然后触发 `close()`
- **预期结果**：
  - `commitIfDirty("Auto-save")` 先调用 `commitChange()`
  - `baselineParams` 更新为当前 params
  - `dirty` 变为 false
- **验收标准**：等级 A

#### TC-M04-047 版本参数重载
- **操作步骤**：Undo/Redo/SwitchVersion 后观察参数恢复
- **预期结果**：
  - `reloadActiveVersionParams(id)` 被调用
  - `historyService.getHistory(id)` 返回版本树
  - `params` 和 `baselineParams` 更新为当前激活版本的 `cumulativeParams`
  - `pipelineService.updateParams()` 被调用
- **验收标准**：等级 A

#### TC-M04-048 版本观察
- **操作步骤**：打开图片后创建/切换版本
- **预期结果**：
  - `observeVersions(id)` 收集 `historyService.observeVersions(id)`
  - `versions` 列表实时更新
  - 激活版本自动触发 `observeTransactions(activeVersionId)`
- **验收标准**：等级 A

#### TC-M04-049 事务观察
- **操作步骤**：提交多次更改
- **预期结果**：
  - `observeTransactions(versionId)` 收集事务列表
  - `canUndo = txs.isNotEmpty()`
  - `canRedo = false`（新事务重置 redo 状态）
- **验收标准**：等级 A

#### TC-M04-050 Preset 加载
- **操作步骤**：打开 Editor
- **预期结果**：
  - `loadPresets()` 调用 `presetService.ensureBuiltIns()`
  - 收集 `presetService.observeAll()`
  - `presets` 和 `favoritePresets` 正确更新
- **验收标准**：等级 A

#### TC-M04-051 通用 Mask 添加
- **操作步骤**：调用 `addMask(maskService.toRecord(mask))`
- **预期结果**：
  - `pendingMasks` 列表添加新记录
  - `pipelineService.applyMask(record)` 被调用
  - 失败时显示 "Mask apply failed"
- **验收标准**：等级 A

#### TC-M04-052 Mask 同步到 Pipeline
- **操作步骤**：切换/删除 Mask 后观察预览
- **预期结果**：
  - `syncMasksToPipeline()` 被调用
  - `pipelineService.clearMasks()` 先清除所有 Mask
  - 仅重新应用 `enabled=true` 的 Mask
  - `rerender()` 触发重新渲染
- **验收标准**：等级 A

#### TC-M04-053 EXIF 加载
- **操作步骤**：打开带 EXIF 的图片
- **预期结果**：
  - `loadExif(item)` 调用 `exifService.read(Uri)`
  - `exif` Map 正确更新
  - 读取失败时不崩溃，保留空 Map
- **验收标准**：等级 A

#### TC-M04-054 Pipeline Handle 暴露
- **操作步骤**：导出时访问 `pipelineHandle`
- **预期结果**：
  - `pipelineHandle` 返回 `pipelineService.handle`
  - 非零 handle 可被 Export 模块使用
- **验收标准**：等级 A

#### TC-M04-055 buildDelta 全面字段覆盖
- **操作步骤**：调整全部 29 个参数字段后调用 `commitChange()`
- **预期结果**：
  - `buildDelta()` 检测到所有变化字段
  - overrides 包含全部 29 个标量/字符串字段：
    - Basic tone: exposure, contrast, highlights, shadows, whites, blacks
    - Color: temperature, tint, saturation, vibrance, clarity, sharpen
    - HLS: liftHue, liftSat, liftLum, gammaHue, gammaSat, gammaLum, gainHue, gainSat, gainLum
    - Geometry: rotation, perspectiveH, perspectiveV
    - Effects: filmGrainAmount, filmGrainSize, halationAmount, lutIntensity
    - RAW: rawNoiseReduction
    - Display 字符串: displayTransform, outputColorSpace, displayEotf, peakLuminanceNits
    - RAW 字符串: rawDemosaic, rawBlackLevel, rawWhitePoint
    - LUT 字符串: lutPath
  - 未变化字段不包含在 delta 中
- **验收标准**：等级 A

---

### M05 AI 智能模块

#### TC-M05-001 AI Hub 导航
- **操作步骤**：点击 AI Tab，依次点击 Search / Rating / Models 卡片
- **预期结果**：正确导航到对应子页面
- **验收标准**：等级 A

#### TC-M05-002 语义搜索 — 正常查询
- **前置条件**：已下载 CLIP/SigLIP 模型
- **操作步骤**：
  1. 进入 AI Search
  2. 输入 "sunset at the beach"
  3. 点击 Search
- **预期结果**：
  - `search()` 被调用
  - `modelStatus.isReady` 为 true
  - `searchService.search(query, 100)` 被调用
  - `imageRepository.getImage()` 解析结果
  - 结果按相关性排序，显示分数和匹配标签
  - 点击结果可打开 Editor
  - `addRecentSearch()` 记录搜索历史
- **验收标准**：等级 A

#### TC-M05-003 语义搜索 — 空查询
- **操作步骤**：不输入任何内容，点击 Search
- **预期结果**：`results` 被清空，不发起搜索请求
- **验收标准**：等级 A

#### TC-M05-004 语义搜索 — 模型未就绪
- **前置条件**：未下载模型
- **操作步骤**：进入 AI Search，输入查询，点击 Search
- **预期结果**：
  - `modelStatus.isReady` 为 false
  - 显示错误 "AI model not ready. Please download and activate a model first."
  - `isSearching` 变为 false
  - 不调用 `searchService.search()`
- **验收标准**：等级 C（降级提示正确）

#### TC-M05-005 语义搜索 — 搜索失败
- **操作步骤**：搜索时网络错误
- **预期结果**：
  - `searchService.search()` 抛出异常
  - `isSearching` 变为 false
  - 显示错误消息
- **验收标准**：等级 A

#### TC-M05-006 语义搜索 — 建议与历史
- **操作步骤**：
  1. 进入 AI Search，不输入任何内容
  2. 点击一个 Suggestion chip
  3. 再次进入，查看 Recent Searches
  4. 点击 Clear Recent Searches
- **预期结果**：
  - 显示 Suggestions 列表
  - 点击 Suggestion 触发 `searchRecent(query)`
  - Recent Searches 正确记录（最多 8 条）
  - Clear 后清空，DataStore 中的 `RECENT_SEARCHES_KEY` 被删除
- **验收标准**：等级 A

#### TC-M05-007 语义搜索 — 更新查询
- **操作步骤**：在搜索框中输入文本
- **预期结果**：`updateQuery(text)` 被调用，`query` 字段更新
- **验收标准**：等级 A

#### TC-M05-008 语义搜索 — 清空查询
- **操作步骤**：点击搜索框的清空按钮
- **预期结果**：`clearQuery()` 被调用，`query` 和 `results` 清空
- **验收标准**：等级 A

#### TC-M05-009 语义搜索 — 消除错误
- **操作步骤**：点击错误消息的关闭按钮
- **预期结果**：`dismissError()` 被调用，`error` 变为 null
- **验收标准**：等级 A

#### TC-M05-010 语义搜索 — 模型状态监听
- **操作步骤**：下载模型，观察 ModelStatusBar
- **预期结果**：
  - `sidecarRuntime.state` 收集后更新 `modelStatus`
  - `isReady` / `modelName` / `isDownloading` 正确反映运行时状态
- **验收标准**：等级 A

#### TC-M05-011 AI 评分 — 批量分析
- **前置条件**：相册中有 ≥5 张图片，已配置 LLM
- **操作步骤**：
  1. 进入 AI Rating
  2. 选择 LLM Provider
  3. 调整 Strictness 滑块
  4. 点击 Analyze Images
- **预期结果**：
  - `loadTopRated()` 初始加载
  - `setSelectedProvider()` / `setStrictness()` 被调用
  - `analyzeSelected()` 调用 `cullBatch(images)`
  - `aiRatingService.cullBatch()` 被调用，传入 provider/strictness
  - 显示批量进度条
  - 分析完成后显示 TOP 结果列表
  - 每张图片显示综合分数、质量标签
- **验收标准**：等级 A

#### TC-M05-012 AI 评分 — 无图片
- **操作步骤**：相册为空时点击 Analyze
- **预期结果**：显示 "No images to analyze yet" 消息
- **验收标准**：等级 A

#### TC-M05-013 AI 评分 — 正在分析中
- **操作步骤**：分析进行中再次点击 Analyze
- **预期结果**：`isCulling` 为 true，直接返回
- **验收标准**：等级 A

#### TC-M05-014 AI 评分 — 应用到 EXIF
- **操作步骤**：分析完成后，点击 "Apply ratings to EXIF"，确认
- **预期结果**：
  - `applyRatingsToExif()` 被调用
  - `imageRepository.setRating()` 被调用
  - 评分写入图片 EXIF
  - 完成后显示 "Applied ratings to N images"
- **验收标准**：等级 A

#### TC-M05-015 AI 评分 — 查看详情
- **操作步骤**：点击一张已评分图片查看详情
- **预期结果**：
  - `showDetail(imageId)` 被调用
  - `aiRatingService.getRating()` 被调用
  - `selectedDetail` 更新
- **验收标准**：等级 A

#### TC-M05-016 AI 评分 — 关闭详情
- **操作步骤**：关闭评分详情弹窗
- **预期结果**：`dismissDetail()` 被调用，`selectedDetail` 变为 null
- **验收标准**：等级 A

#### TC-M05-017 AI 模型管理 — 下载
- **操作步骤**：
  1. 进入 AI Model Manager
  2. 下载一个未下载的模型
  3. 观察下载进度
- **预期结果**：
  - `download(asset)` 被调用
  - `sidecarRuntime.ensureLoaded(asset)` 被调用
  - `activeDownloadId` 和 `downloadProgress` 更新
  - `modelDownloadService.progress` 被监听
  - 下载完成后状态变为 "Downloaded"
- **验收标准**：等级 A

#### TC-M05-018 AI 模型管理 — 下载失败
- **操作步骤**：模拟下载失败（SHA mismatch / 写入失败）
- **预期结果**：
  - 显示 `downloadErrorMessage(code)` 错误
  - "sha_mismatch" → "Download failed: file integrity check failed"
  - "write_failed" → "Download failed: could not write model file"
  - 其他 → "Download failed: $code"
- **验收标准**：等级 A

#### TC-M05-019 AI 模型管理 — 删除
- **操作步骤**：下载完成后删除该模型
- **预期结果**：
  - `delete(asset)` 被调用
  - `sidecarRuntime.unload()` 被调用
  - 本地文件被删除
  - 存储统计正确更新
  - 如果删除的是默认模型，回退到 `CLIP_VIT_BASE_PATCH32`
- **验收标准**：等级 A

#### TC-M05-020 AI 模型管理 — 设为默认
- **操作步骤**：对一个已下载非默认模型点击 "Set as default"
- **预期结果**：
  - `setDefaultModel(asset)` 被调用
  - `defaultClipId` 更新
  - 持久化到 DataStore
  - 该模型显示 ★ 标记
  - 原默认模型取消 ★
- **验收标准**：等级 A

#### TC-M05-021 AI 模型管理 — 刷新
- **操作步骤**：触发 `refresh()` 方法
- **预期结果**：
  - `ModelAssetCatalog.ALL` 遍历所有模型
  - `isDownloaded` / `isLoaded` / `isDownloading` / `downloadFraction` 正确更新
- **验收标准**：等级 A

#### TC-M05-022 AI 模型管理 — 持久化默认模型
- **操作步骤**：重启应用
- **预期结果**：从 DataStore 恢复 `defaultClipId`，未知 ID 被忽略
- **验收标准**：等级 A

#### TC-M05-023 搜索最近查询
- **操作步骤**：点击一个 Recent Search chip
- **预期结果**：
  - `searchRecent(query)` 被调用
  - `query` 字段更新为所选文本
  - 自动触发 `search()`
- **验收标准**：等级 A

#### TC-M05-024 AI 评分 — 消息消除
- **操作步骤**：显示消息（如 "No images to analyze yet"）后调用 `dismissMessage()`
- **预期结果**：`message` 变为 null
- **验收标准**：等级 A

#### TC-M05-025 AI 模型管理 — 刷新状态
- **操作步骤**：下载/删除模型后观察 UI
- **预期结果**：
  - `refresh()` 被调用
  - `ModelEntry` 的 `isDownloaded` / `isLoaded` / `isDownloading` / `downloadFraction` 正确更新
  - 当前激活下载的进度与对应模型卡关联
- **验收标准**：等级 A

#### TC-M05-026 AI 模型管理 — DataStore 持久化
- **操作步骤**：设置默认模型后重启应用
- **预期结果**：
  - `DEFAULT_CLIP_MODEL_KEY` 被写入 DataStore
  - 重启后 `dataStore?.data?.first()?.get(DEFAULT_CLIP_MODEL_KEY)` 恢复正确 ID
  - 未知 ID 不恢复，回退到 `CLIP_VIT_BASE_PATCH32`
- **验收标准**：等级 A

#### TC-M05-027 AI 评分 — 直接批量分析
- **操作步骤**：调用 `cullBatch(images)` 传入图片列表
- **预期结果**：
  - `isCulling` 变为 true
  - `aiRatingService.cullBatch()` 被调用，传入 provider/strictness
  - 进度回调更新 `culledCount` / `cullTotal`
  - 完成后 `loadTopRated()` 被调用刷新
- **验收标准**：等级 A

#### TC-M05-028 语义搜索 — 添加最近搜索
- **操作步骤**：成功执行一次搜索
- **预期结果**：
  - `addRecentSearch(query)` 被调用
  - 查询去重后插入列表头部
  - 最多保留 `MAX_RECENT_SEARCHES` (8) 条
  - DataStore 中 `RECENT_SEARCHES_KEY` 被更新
- **验收标准**：等级 A

#### TC-M05-029 AI 评分 — 设置 LLM Provider
- **操作步骤**：调用 `setSelectedProvider(LlmProvider.ANTHROPIC)`
- **预期结果**：
  - `selectedProvider` 更新为 `ANTHROPIC`
  - 后续 `analyzeSelected()` 和 `cullBatch()` 使用新 provider
- **验收标准**：等级 A

#### TC-M05-030 AI 评分 — 设置严格度
- **操作步骤**：调用 `setStrictness(0.75f)`
- **预期结果**：
  - `strictness` 更新为 `0.75f`
  - 值被 `coerceIn(0f, 1f)` 限制在合法范围
  - 后续 `analyzeSelected()` 和 `cullBatch()` 使用新 strictness
- **验收标准**：等级 A

---

### M06 导出模块

#### TC-M06-001 单张导出 — JPEG
- **操作步骤**：
  1. Editor 中点击导出
  2. 格式选 JPEG，质量 95
  3. 色彩空间 sRGB
  4. 点击 Export
- **预期结果**：
  - `exportCurrent(imageId)` 被调用
  - `hasExportPermissions()` 检查通过
  - `pipelineService.handle` 非零
  - `imageRepository.getImage(imageId)` 返回 ImageItem
  - `runExport()` 调用 `exportService.export()`
  - 导出进度显示
  - 导出完成后显示成功标记 ✓
  - 文件存在于输出目录
  - 文件大小合理，可正常打开
- **验收标准**：等级 A

#### TC-M06-002 单张导出 — 无权限
- **前置条件**：API 29 及以下设备，未授予 WRITE_EXTERNAL_STORAGE
- **操作步骤**：点击 Export
- **预期结果**：
  - `hasExportPermissions()` 返回 false
  - 显示 "Storage write permission required for export" 错误
  - 不调用 `exportService.export()`
- **验收标准**：等级 A

#### TC-M06-003 单张导出 — 无 Pipeline
- **操作步骤**：未打开任何图片时点击 Export
- **预期结果**：
  - `pipelineService.handle` 为 0L
  - 显示 "No active pipeline. Open an image in the editor first." 错误
- **验收标准**：等级 A

#### TC-M06-004 单张导出 — 图片不存在
- **操作步骤**：导出一个已删除的 imageId
- **预期结果**：
  - `imageRepository.getImage(imageId)` 返回 null
  - 显示 "Image not found: $imageId" 错误
- **验收标准**：等级 A

#### TC-M06-005 单张导出 — PNG
- **操作步骤**：格式选 PNG，位深 16-bit
- **预期结果**：导出成功，文件为 16-bit PNG
- **验收标准**：等级 A

#### TC-M06-006 单张导出 — WebP
- **操作步骤**：格式选 WebP
- **预期结果**：导出成功，文件为 WebP
- **验收标准**：等级 A

#### TC-M06-007 单张导出 — TIFF（降级为 PNG）
- **操作步骤**：格式选 TIFF
- **预期结果**：
  - `effectiveExtension()` 返回 "png"
  - Logcat 显示 "TIFF is not natively encodable on Android; writing PNG bytes with .png extension"
  - 实际编码为 PNG 格式
  - 文件扩展名为 .png
- **验收标准**：等级 B（降级路径正确）

#### TC-M06-008 UltraHDR 导出
- **前置条件**：格式为 JPEG，设备支持 UltraHDR
- **操作步骤**：开启 UltraHDR 开关，导出
- **预期结果**：
  - `ultraHdrWriter.buildGainMapBytes()` 被调用
  - `hdrBitmap` 在 `finalBitmap` 回收前获取
  - `ultraHdrWriter.write()` 被调用
  - 成功时：原始 SDR JPEG 被删除，返回 UltraHDR 文件
  - 失败时：回退到 SDR JPEG，Logcat 显示 "UltraHDR write failed; falling back to SDR JPEG"
  - Bitmap 在使用后正确回收
- **验收标准**：等级 B（依赖设备能力）

#### TC-M06-009 批量导出
- **操作步骤**：Album 中选择 5 张图片，点击导出
- **预期结果**：
  - `exportBatch(imageIds)` 被调用
  - 每张图片创建专用 pipeline handle
  - `pipelineService.createForImage()` 被调用
  - 保存的编辑参数被加载
  - 显示批量进度 (completed/total)
  - 每张图片独立结果显示 ✓/✕
  - 专用 handle 在完成后释放
- **验收标准**：等级 A

#### TC-M06-010 批量导出 — 空列表
- **操作步骤**：调用 `exportBatch(emptyList())`
- **预期结果**：直接返回，不启动导出
- **验收标准**：等级 A

#### TC-M06-011 批量导出 — 无图片找到
- **操作步骤**：导出无效的 imageIds
- **预期结果**：显示 "No exportable images found." 错误
- **验收标准**：等级 A

#### TC-M06-012 导出尺寸调整
- **操作步骤**：
  1. 开启 Maintain Aspect Ratio
  2. 设置 Max Dimension = 2048
  3. 导出
- **预期结果**：输出图片长边为 2048，另一边等比缩放
- **验收标准**：等级 A

#### TC-M06-013 元数据处理
- **操作步骤**：
  1. 选择 Metadata Mode: Strip All
  2. 导出
  3. 使用第三方工具检查 EXIF
- **预期结果**：导出的图片不含 EXIF/GPS 信息
- **验收标准**：等级 A

#### TC-M06-014 水印导出
- **操作步骤**：
  1. 在 Editor Watermark 面板配置水印
  2. 导出时开启 Include Watermark
- **预期结果**：
  - `watermarkService.apply(bitmap, watermark)` 被调用
  - 导出图片包含水印
- **验收标准**：等级 A

#### TC-M06-015 导出后分享
- **操作步骤**：导出成功后点击 Share 按钮
- **预期结果**：唤起系统分享面板，文件可被其他应用接收
- **验收标准**：等级 A

#### TC-M06-016 导出取消
- **操作步骤**：
  1. 批量导出 20 张大图
  2. 中途点击 Cancel
- **预期结果**：
  - `cancel()` 被调用
  - `exportJob` 被取消
  - `coroutineContext.ensureActive()` 抛出 CancellationException
  - `taskService.isCancelled(taskId)` 返回 true
  - 已导出文件保留，未导出任务终止
- **验收标准**：等级 A

#### TC-M06-017 导出取消与内存安全
- **操作步骤**：批量导出 20 张大图，中途取消
- **预期结果**：
  - 取消后无内存泄漏
  - Bitmap 被正确回收（`bitmap !== finalBitmap` 时分别回收）
  - `Bitmap.recycle()` 是幂等的，安全调用
  - 不触发 OOM
- **验收标准**：等级 A

#### TC-M06-018 导出配置 — 命名模式
- **操作步骤**：设置命名模式为 `{name}_edit`
- **预期结果**：
  - `setNamingPattern(pattern)` 被调用
  - `resolveName()` 正确替换 `{name}` 和 `{date}`
  - 空模式回退到 `{name}_edit`
- **验收标准**：等级 A

#### TC-M06-019 导出配置 — 输出目录
- **操作步骤**：自定义输出目录
- **预期结果**：`setOutputDirectory(path)` 被调用，文件输出到指定目录
- **验收标准**：等级 A

#### TC-M06-020 导出配置 — 质量
- **操作步骤**：设置质量为 50 和 100
- **预期结果**：`setQuality(quality)` 被调用，`quality.coerceIn(1, 100)` 限制范围
- **验收标准**：等级 A

#### TC-M06-021 导出配置 — 所有配置项
- **操作步骤**：逐一测试所有配置项
- **预期结果**：
  - `setFormat()` / `setMaxDimension()` / `setColorSpace()` / `setIccProfile()`
  - `setBitDepth()` / `setMetaMode()` / `setMaintainAspect()` / `setResizeWidth()` / `setResizeHeight()`
  - `setShowWatermark()` / `setIncludeMetadata()` / `setUltraHdr()` / `setWatermarkEnabled()` / `updateWatermark()`
  - 每个配置项正确更新 UI 状态
  - `buildExportConfig()` 合并所有 UI 状态到 ExportConfig
- **验收标准**：等级 A

#### TC-M06-022 UltraHDR — 非 JPEG 格式
- **操作步骤**：格式选 PNG，开启 UltraHDR
- **预期结果**：`setUltraHdr()` 条件 `enabled && it.config.format == ExportFormat.JPEG` 不满足，UltraHDR 未启用
- **验收标准**：等级 A

#### TC-M06-023 导出编码失败
- **操作步骤**：模拟 `bitmap.compress()` 失败
- **预期结果**：
  - Bitmap 被正确回收
  - `ExportProgress` 显示 "encode_failed"
  - `ExportResult` 显示 success=false
- **验收标准**：等级 A

#### TC-M06-024 重置结果
- **操作步骤**：导出完成后点击 Reset
- **预期结果**：`resetResults()` 清空 results、completedCount、totalCount、lastOutputPath
- **验收标准**：等级 A

#### TC-M06-025 错误消除
- **操作步骤**：导出失败后点击关闭错误
- **预期结果**：`dismissError()` 被调用，`error` 变为 null
- **验收标准**：等级 A

#### TC-M06-026 尺寸输入过滤
- **操作步骤**：在 Resize Width/Height 输入框中输入非数字字符
- **预期结果**：
  - `setResizeWidth()` / `setResizeHeight()` 过滤掉非数字字符
  - 只允许 `isDigit()` 字符通过
- **验收标准**：等级 A

#### TC-M06-027 导出命名解析
- **操作步骤**：使用不同命名模式导出
- **预期结果**：
  - `resolveName()` 替换 `{name}` 为原文件名（无扩展名）
  - `resolveName()` 替换 `{date}` 为当前时间戳
  - 空模式回退到原文件名
- **验收标准**：等级 A

#### TC-M06-028 构建导出配置
- **操作步骤**：设置所有导出选项后调用 `buildExportConfig()`
- **预期结果**：
  - 合并 `config` 中的 format, quality, maxDimension, colorSpace
  - 合并 UI-state 中的 bitDepth, metaMode, maintainAspect, resizeWidth/Height, iccProfile
  - `resizeWidth` / `resizeHeight` 从 String 转为 Int（失败回退 0）
- **验收标准**：等级 A

#### TC-M06-029 开始导出取消前一个
- **操作步骤**：导出进行中再次点击导出
- **预期结果**：
  - `startExport()` 先取消 `exportJob.value?.cancel()`
  - 再启动新 Job
  - 不并发执行多个导出
- **验收标准**：等级 A

#### TC-M06-030 单张导出使用共享 Handle
- **操作步骤**：`exportCurrent()` 调用 `runExport(items, dedicatedHandles = false)`
- **预期结果**：
  - 使用 `pipelineService.handle` 而非创建新 handle
  - 不调用 `pipelineService.createForImage()`
  - `finally` 块不释放共享 handle
- **验收标准**：等级 A

#### TC-M06-031 编码失败路径
- **操作步骤**：模拟 `bitmap.compress()` 返回 false
- **预期结果**：
  - `encode()` 返回 false
  - Bitmap 被正确回收（`bitmap !== finalBitmap` 时分别回收）
  - `_progress` 显示 error = "encode_failed"
  - 返回 null
- **验收标准**：等级 A

#### TC-M06-032 ExportService 批量导出
- **操作步骤**：调用 `exportService.exportBatch(requests)`
- **预期结果**：
  - `_batchProgress` 跟踪整体进度
  - 每张图片调用 `export(req)`
  - 返回成功导出的路径列表
- **验收标准**：等级 A

#### TC-M06-033 API 30+ 导出权限
- **前置条件**：Android 11+ 设备
- **操作步骤**：调用 `hasExportPermissions()`
- **预期结果**：
  - `PermissionHelper.exportPermissions()` 返回空列表
  - `hasExportPermissions()` 直接返回 true
  - 不检查 WRITE_EXTERNAL_STORAGE
- **验收标准**：等级 A

#### TC-M06-034 设置 ICC Profile
- **操作步骤**：调用 `setIccProfile(profile)`
- **预期结果**：`iccProfile` 字段更新，最终合并到 `buildExportConfig()`
- **验收标准**：等级 A

#### TC-M06-035 设置位深
- **操作步骤**：调用 `setBitDepth(16)`
- **预期结果**：`bitDepth` 字段更新，最终合并到 `buildExportConfig()`
- **验收标准**：等级 A

#### TC-M06-036 设置元数据模式
- **操作步骤**：切换 MetadataMode 为 STRIP / COPYRIGHT_ONLY
- **预期结果**：`metaMode` 字段更新，最终合并到 `buildExportConfig()`
- **验收标准**：等级 A

#### TC-M06-037 设置保持宽高比
- **操作步骤**：切换 Maintain Aspect Ratio
- **预期结果**：`maintainAspect` 字段更新，最终合并到 `buildExportConfig()`
- **验收标准**：等级 A

#### TC-M06-038 设置输出目录
- **操作步骤**：调用 `setOutputDirectory(path)`
- **预期结果**：`config.outputDirectory` 更新为指定路径
- **验收标准**：等级 A

#### TC-M06-039 取消导出
- **操作步骤**：导出进行中调用 `cancel()`
- **预期结果**：
  - `exportJob.value?.cancel()` 被调用
  - `isExporting` 变为 false
  - 正在进行的导出协程被中断
  - 不触发新的导出
- **验收标准**：等级 A

---

### M07 设置模块

#### TC-M07-001 主题切换
- **操作步骤**：Settings → General → 切换 Theme (Dark / Light / System)
- **预期结果**：`setTheme(theme)` 被调用，UI 主题实时变化
- **验收标准**：等级 A

#### TC-M07-002 初始化诊断信息
- **操作步骤**：启动 Settings 页面
- **预期结果**：
  - `init` 块中 `NdkSafeCall.isAvailable` 被检查
  - `nativeAvailable` / `nativeVersion` / `gpuAvailable` 正确反映 native 状态
  - `refreshCacheSize()` 被调用
- **验收标准**：等级 A

#### TC-M07-003 默认视图切换
- **操作步骤**：切换 Grid / List
- **预期结果**：`setDefaultView(view)` 被调用，视图持久化
- **验收标准**：等级 A

#### TC-M07-004 GPU 后端切换
- **操作步骤**：Settings → Editor → 切换 Vulkan / CPU
- **预期结果**：
  - `setGpuBackend(backend)` 被调用
  - `privacyManager.setGpuBackend(backend)` 被调用
  - `AlcedoNativeBridge.nativeSetGpuBackend(if (useVulkan) 1 else 0)` 被调用
  - 切换后 Editor 中渲染使用对应后端
- **验收标准**：等级 A

#### TC-M07-005 AI 严格度调整
- **操作步骤**：调整 AI Strictness 滑块
- **预期结果**：`setAiStrictness(strictness)` 被调用，值持久化
- **验收标准**：等级 A

#### TC-M07-006 AI API Key 配置
- **操作步骤**：输入 API Key
- **预期结果**：`setApiKey(key)` 被调用，值持久化
- **验收标准**：等级 A

#### TC-M07-007 AI Endpoint 配置
- **操作步骤**：输入 Endpoint URL
- **预期结果**：`setAiEndpoint(endpoint)` 被调用，值持久化
- **验收标准**：等级 A

#### TC-M07-008 AI Model 配置
- **操作步骤**：选择 AI 模型
- **预期结果**：`setAiModel(model)` 被调用，值持久化
- **验收标准**：等级 A

#### TC-M07-009 缓存清理
- **操作步骤**：Settings → Storage → Clear Cache
- **预期结果**：
  - `clearCache()` 被调用
  - `tempFileManager.cleanupAll()` 被调用
  - `gpuService.onLowMemory()` 被调用
  - `AlcedoNativeBridge.nativeOnLowMemory()` 被调用
  - 缓存大小显示更新为 0 或接近 0
  - 显示 "Cache cleared" 消息
  - 应用不崩溃
- **验收标准**：等级 A

#### TC-M07-010 缓存清理 — 防重复
- **操作步骤**：快速连续点击 Clear Cache
- **预期结果**：`isClearingCache` 为 true 时直接返回，不重复执行
- **验收标准**：等级 A

#### TC-M07-011 孤立文件清理
- **操作步骤**：Settings → Storage → Sweep Orphans
- **预期结果**：
  - `sweepOrphans()` 被调用
  - `tempFileManager.sweepOrphans()` 被调用
  - 显示 "Orphaned files swept" 消息
- **验收标准**：等级 A

#### TC-M07-012 孤立文件清理 — 防重复
- **操作步骤**：快速连续点击 Sweep Orphans
- **预期结果**：`isSweeping` 为 true 时直接返回
- **验收标准**：等级 A

#### TC-M07-013 隐私设置 — Cloud LLM
- **操作步骤**：关闭 Cloud LLM
- **预期结果**：
  - `setCloudLlmAllowed(false)` 被调用
  - `privacyManager.setCloudLlmAllowed(false)` 被调用
  - 设置持久化
- **验收标准**：等级 A

#### TC-M07-014 隐私设置 — On-Device AI
- **操作步骤**：关闭 On-Device AI
- **预期结果**：`setOnDeviceAiAllowed(false)` 被调用
- **验收标准**：等级 A

#### TC-M07-015 隐私设置 — Analytics
- **操作步骤**：关闭 Analytics
- **预期结果**：`setTelemetryAllowed(false)` 被调用
- **验收标准**：等级 A

#### TC-M07-016 隐私设置 — Crash Reports
- **操作步骤**：关闭 Crash Reports
- **预期结果**：
  - `setCrashReportEnabled(false)` 被调用
  - `privacyManager.setCrashReportEnabled(false)` 被调用
  - CrashReportService 不再上传
  - 设置持久化
- **验收标准**：等级 A

#### TC-M07-017 诊断信息
- **操作步骤**：查看 Diagnostics 区域
- **预期结果**：
  - Native Version 显示正确版本号
  - GPU Available 与设备能力一致
  - `nativeAvailable` / `nativeVersion` / `gpuAvailable` 正确反映 NDK 状态
- **验收标准**：等级 A

#### TC-M07-018 恢复内置 Preset
- **操作步骤**：点击 Restore Default Presets
- **预期结果**：
  - `restoreBuiltInPresets()` 被调用
  - `presetService.ensureBuiltIns()` 被调用
  - 显示 "Default presets restored" 消息
- **验收标准**：等级 A

#### TC-M07-019 消息消除
- **操作步骤**：显示消息（如 "Cache cleared"）后调用 `dismissMessage()`
- **预期结果**：`message` 变为 null
- **验收标准**：等级 A

#### TC-M07-020 缓存大小刷新
- **操作步骤**：清理缓存后观察缓存大小显示
- **预期结果**：
  - `refreshCacheSize()` 调用 `tempFileManager.usedBytes()`
  - `cacheSizeBytes` 正确更新
- **验收标准**：等级 A

#### TC-M07-021 错误处理 — 设置器失败
- **操作步骤**：模拟 DataStore 写入失败
- **预期结果**：
  - `setTheme()` / `setGpuBackend()` 等设置器捕获异常
  - `error` 字段显示失败原因
  - 不崩溃
- **验收标准**：等级 A

#### TC-M07-022 Analytics 设置
- **操作步骤**：开启/关闭 Analytics
- **预期结果**：`setTelemetryAllowed()` / `setAnalyticsEnabled()` 被调用，值持久化
- **验收标准**：等级 A

---

### M08 权限管理

#### TC-M08-001 首次启动权限请求
- **前置条件**：全新安装，清除所有权限
- **操作步骤**：启动应用
- **预期结果**：
  - 弹出媒体读取权限请求
  - API 33+ 同时请求通知权限
  - `PermissionHelper.allRequired()` 返回正确列表
- **验收标准**：等级 A

#### TC-M08-002 权限授予后功能
- **操作步骤**：授予所有权限
- **预期结果**：
  - `PermissionHelper.areAllGranted()` 返回 true
  - `PermissionHelper.hasMediaAccess()` 返回 true
  - `PermissionHelper.hasNotificationAccess()` 返回 true
  - Album 正常读取系统相册图片
- **验收标准**：等级 A

#### TC-M08-003 权限拒绝降级
- **操作步骤**：拒绝媒体权限
- **预期结果**：
  - `PermissionHelper.deniedPermissions()` 返回被拒绝的权限
  - Album 显示空状态或引导用户授权
  - 应用不崩溃
- **验收标准**：等级 C

#### TC-M08-004 永久拒绝处理
- **操作步骤**：
  1. 拒绝权限并勾选 "Don't ask again"
  2. 再次尝试需要权限的操作
- **预期结果**：
  - `PermissionHelper.isPermanentlyDenied()` 返回 true
  - `PermissionHelper.anyPermanentlyDenied()` 返回 true
  - 显示引导去系统设置授权的对话框
  - `PermissionHelper.openAppSettings()` 跳转系统设置页
- **验收标准**：等级 A

#### TC-M08-005 API 29 及以下写入权限
- **前置条件**：Android 10 及以下设备
- **操作步骤**：执行导出到共享存储
- **预期结果**：
  - `PermissionHelper.writeStoragePermission()` 返回 `WRITE_EXTERNAL_STORAGE`
  - `PermissionHelper.exportPermissions()` 返回 `WRITE_EXTERNAL_STORAGE`
  - 授予后导出成功到 DCIM/等目录
- **验收标准**：等级 A

#### TC-M08-006 API 30+ 无写入权限需求
- **前置条件**：Android 11+ 设备
- **操作步骤**：执行导出
- **预期结果**：
  - `PermissionHelper.writeStoragePermission()` 返回空列表
  - `PermissionHelper.exportPermissions()` 返回空列表
  - 导出无需额外权限
- **验收标准**：等级 A

#### TC-M08-007 API 33+ 媒体权限
- **前置条件**：Android 13+ 设备
- **操作步骤**：启动应用
- **预期结果**：
  - `PermissionHelper.requiredMediaPermissions()` 返回 `READ_MEDIA_IMAGES` + `READ_MEDIA_VIDEO`
  - 不请求 `WRITE_EXTERNAL_STORAGE`
  - `PermissionHelper.notificationPermission()` 返回 `POST_NOTIFICATIONS`
- **验收标准**：等级 A

#### TC-M08-008 API 32 及以下媒体权限
- **前置条件**：Android 12 及以下设备
- **操作步骤**：启动应用
- **预期结果**：
  - `PermissionHelper.requiredMediaPermissions()` 返回 `READ_EXTERNAL_STORAGE`
  - `PermissionHelper.notificationPermission()` 返回空列表
- **验收标准**：等级 A

#### TC-M08-009 权限检查 — 单个权限
- **操作步骤**：调用 `PermissionHelper.isGranted(context, permission)`
- **预期结果**：正确返回权限状态
- **验收标准**：等级 A

#### TC-M08-010 权限检查 — 批量
- **操作步骤**：调用 `PermissionHelper.areAllGranted(context, permissions)`
- **预期结果**：所有权限都授予时返回 true，否则返回 false
- **验收标准**：等级 A

#### TC-M08-011 权限检查 — 已授予权限列表
- **操作步骤**：调用 `PermissionHelper.grantedPermissions()`
- **预期结果**：返回已授予的权限子集
- **验收标准**：等级 A

#### TC-M08-012 权限理由显示
- **操作步骤**：调用 `PermissionHelper.shouldShowRationale()`
- **预期结果**：用户之前拒绝（未勾选 "Don't ask again"）时返回 true
- **验收标准**：等级 A

#### TC-M08-013 权限理由 — 批量检查
- **操作步骤**：调用 `PermissionHelper.shouldShowRationaleForAny()`
- **预期结果**：任一权限需要显示理由时返回 true
- **验收标准**：等级 A

#### TC-M08-014 权限状态 — Compose 集成
- **操作步骤**：使用 `rememberPermissionState(permissions)`
- **预期结果**：
  - 返回 `PermissionState` 对象
  - `allGranted` / `denied` / `shouldShowRationale` / `permanentlyDenied` 正确
  - `launcher` 可触发权限请求
- **验收标准**：等级 A

#### TC-M08-015 权限状态 — 请求后更新
- **操作步骤**：通过 launcher 请求权限，观察状态变化
- **预期结果**：
  - `requestedOnce` 变为 true
  - `granted` 根据结果更新
  - `permanentlyDenied` 在请求后且拒绝 + 无理由时为 true
- **验收标准**：等级 A

#### TC-M08-016 媒体访问权限检查
- **操作步骤**：调用 `PermissionHelper.hasMediaAccess(context)`
- **预期结果**：
  - 已授予所需媒体权限时返回 true
  - 未授予时返回 false
- **验收标准**：等级 A

#### TC-M08-017 通知权限检查
- **操作步骤**：调用 `PermissionHelper.hasNotificationAccess(context)`
- **预期结果**：
  - API 33+ 已授予 POST_NOTIFICATIONS 时返回 true
  - API 32- 返回 true（无需权限）
- **验收标准**：等级 A

#### TC-M08-018 打开应用设置
- **操作步骤**：调用 `PermissionHelper.openAppSettings(context)`
- **预期结果**：
  - 跳转系统应用详情页（`ACTION_APPLICATION_DETAILS_SETTINGS`）
  - 携带 `package:` URI
  - 失败时不崩溃
- **验收标准**：等级 A

#### TC-M08-019 权限启动器 Composable
- **操作步骤**：在 Compose 中调用 `rememberPermissionLauncher(permissions, onResult)`
- **预期结果**：
  - 返回 `ActivityResultLauncher<Array<String>>`
  - 触发后回调 `onResult` 并传入权限结果 Map
- **验收标准**：等级 A

---

### M09 NDK/Native 层

#### TC-M09-001 Native Init/Shutdown
- **操作步骤**：
  1. 启动应用，检查 `AlcedoNativeBridge.init()` 返回 true
  2. 退出应用，检查 `AlcedoNativeBridge.nativeShutdown()` 不崩溃
- **预期结果**：
  - `init()` 调用 `System.loadLibrary("alcedo_native")` + `Bridge.nativeInit(cacheDir)`
  - `loaded` 标志变为 true
  - `loadError` 为 null
  - 重复调用 `init()` 返回 true（幂等）
  - `nativeShutdown()` 不崩溃
- **验收标准**：等级 A

#### TC-M09-002 Native Init 失败
- **操作步骤**：模拟 `System.loadLibrary` 抛出异常
- **预期结果**：
  - `init()` 返回 false
  - `loadError` 包含异常消息
  - `loaded` 为 false
  - 不崩溃
- **验收标准**：等级 A

#### TC-M09-003 Vulkan 可用性检测
- **操作步骤**：调用 `AlcedoNativeBridge.nativeGpuAvailable()`
- **预期结果**：
  - 支持 Vulkan 设备返回 true
  - 不支持返回 false
  - `Pipeline.nativeIsVulkanAvailable()` 被调用
- **验收标准**：等级 A

#### TC-M09-004 GPU 设备名称
- **操作步骤**：调用 `AlcedoNativeBridge.nativeGpuDeviceName()`
- **预期结果**：
  - Vulkan 可用时返回 "Vulkan Compute"
  - 不可用时返回 null
- **验收标准**：等级 A

#### TC-M09-005 设置缓存目录
- **操作步骤**：调用 `AlcedoNativeBridge.nativeSetCacheDir(path)`
- **预期结果**：`pendingCacheDir` 被设置
- **验收标准**：等级 A

#### TC-M09-006 设置临时目录
- **操作步骤**：调用 `AlcedoNativeBridge.nativeSetTempDir(path)`
- **预期结果**：`pendingTempDir` 被设置
- **验收标准**：等级 A

#### TC-M09-007 设置 GPU 后端
- **操作步骤**：调用 `AlcedoNativeBridge.nativeSetGpuBackend(0)` / `nativeSetGpuBackend(1)`
- **预期结果**：
  - 0 = CPU 后端
  - 1 = Vulkan 后端
  - `Raw.nativeSetRawBackend(backend)` 被调用
- **验收标准**：等级 A

#### TC-M09-008 版本查询
- **操作步骤**：调用 `AlcedoNativeBridge.nativeVersion()`
- **预期结果**：返回 `Bridge.nativeGetVersion()` 的结果，默认 "unknown"
- **验收标准**：等级 A

#### TC-M09-009 图片加载与释放
- **操作步骤**：
  1. `AlcedoNativeBridge.nativeDecodeImage(uri, maxDim)` 返回有效 ID (>0)
  2. `AlcedoNativeBridge.nativeImageDimensions(handle)` 返回 [width, height, channels]
  3. `AlcedoNativeBridge.nativeReleaseImage(handle)` 成功
- **预期结果**：
  - 加载后内存增加，释放后内存回落
  - `Image.nativeLoadImage(uri)` 被调用
  - `Image.nativeGetImageInfo(id)` 返回正确 JSON
  - `Image.nativeRemoveImage(id)` 被调用
  - `nativeReleaseImage(0)` 直接返回（安全）
- **验收标准**：等级 A

#### TC-M09-010 图片解码缩略图
- **操作步骤**：调用 `AlcedoNativeBridge.nativeDecodeThumbnail(uri, targetSize)`
- **预期结果**：
  - `Image.nativeLoadThumbnail(uri, targetSize)` 被调用
  - `Thumbnail.nativeGetThumbnailBytes(id)` 被调用
  - 返回可解码的 Bitmap
  - 失败时返回 null
- **验收标准**：等级 A

#### TC-M09-011 提取元数据
- **操作步骤**：调用 `AlcedoNativeBridge.nativeExtractMetadata(uri)`
- **预期结果**：
  - 先加载图片，再查询信息，最后释放
  - 返回 JSON 字符串
  - 失败时返回 "{}"
- **验收标准**：等级 A

#### TC-M09-012 RAW 解码
- **操作步骤**：
  1. `AlcedoNativeBridge.nativeDecodeRaw(uri, maxDim, demosaic)` 对 DNG/ARW/NEF 执行
- **预期结果**：
  - `Raw.nativeDecodeRaw(uri, maxDim)` 被调用
  - 解码成功返回有效 ID
- **验收标准**：等级 A

#### TC-M09-013 RAW Demosaic 设置
- **操作步骤**：调用 `AlcedoNativeBridge.nativeRawSetDemosaic(handle, "vulkan")` / `"cpu"`
- **预期结果**：
  - "vulkan"/"gpu" → `Raw.nativeSetRawBackend(1)`
  - 其他 → `Raw.nativeSetRawBackend(0)`
  - 返回 true
- **验收标准**：等级 A

#### TC-M09-014 RAW 支持的扩展名
- **操作步骤**：调用 `AlcedoNativeBridge.nativeRawSupportedExtensions()`
- **预期结果**：返回包含 ARW, CR2, CR3, DNG, NEF, NRW, ORF, PEF, RAF, RW2, SRF, SR2 的数组
- **验收标准**：等级 A

#### TC-M09-015 Pipeline 执行
- **操作步骤**：
  1. 加载图片获取 imageId
  2. `AlcedoNativeBridge.nativeCreatePipeline(imageHandle)` 返回 handle
  3. `AlcedoNativeBridge.nativeApplyAdjustments(pipelineHandle, paramsJson)` 传入调整参数
  4. `AlcedoNativeBridge.nativeRenderToBitmap(pipelineHandle, maxWidth)` 获取结果
- **预期结果**：
  - Pipeline 创建成功
  - 调整应用成功
  - 渲染结果可解码为 Bitmap
- **验收标准**：等级 A

#### TC-M09-016 Pipeline 销毁
- **操作步骤**：调用 `AlcedoNativeBridge.nativeDestroyPipeline(handle)`
- **预期结果**：不崩溃，资源释放
- **验收标准**：等级 A

#### TC-M09-017 项目数据库操作
- **操作步骤**：
  1. `AlcedoNativeBridge.nativeSleeveOpen(dbPath)` 返回 handle
  2. `AlcedoNativeBridge.nativeSleeveCreateFolder(handle, "/", "Test")` 返回 JSON
  3. `AlcedoNativeBridge.nativeSleeveListChildren(handle, "/")` 返回列表
  4. `AlcedoNativeBridge.nativeSleeveDeleteElement(handle, "/Test")` 返回 true
  5. `AlcedoNativeBridge.nativeSleeveClose(handle)`
- **预期结果**：
  - `Bridge.nativeOpenProject(dbPath)` 被调用
  - `Sleeve.nativeCreateFolder()` / `nativeListFolder()` / `nativeDeleteElement()` 被调用
  - 文件夹创建成功
  - 列表包含新文件夹
  - 删除后列表不再包含
- **验收标准**：等级 A

#### TC-M09-018 Sleeve 导入图片
- **操作步骤**：调用 `AlcedoNativeBridge.nativeSleeveImportImage(handle, path, uri)`
- **预期结果**：
  - `Image.nativeImportImage(uri, "")` 被调用
  - 返回元素信息 JSON
- **验收标准**：等级 A

#### TC-M09-019 Sleeve 移动元素
- **操作步骤**：调用 `AlcedoNativeBridge.nativeSleeveMoveElement(handle, src, dest)`
- **预期结果**：`Sleeve.nativeMoveElement(src, dest)` 被调用
- **验收标准**：等级 A

#### TC-M09-020 Sleeve 过滤
- **操作步骤**：分别传入文本搜索和 SQL 过滤
- **预期结果**：
  - `{"query": "sunset"}` → `Sleeve.nativeSearchByText()` 被调用
  - `{"folder": "/", "sql": "..."}` → `Sleeve.nativeFilterFolder()` 被调用
  - 无效 JSON → 返回 "[]"
- **验收标准**：等级 A

#### TC-M09-021 缩略图生成 — 单张
- **操作步骤**：调用 `AlcedoNativeBridge.nativeGenerateThumbnail(uri, size, outputPath)`
- **预期结果**：
  - `Image.nativeLoadImage(uri)` → `Thumbnail.nativeGenerateThumbnail(id, size)` → `Image.nativeRemoveImage(id)`
  - 返回 true
- **验收标准**：等级 A

#### TC-M09-022 缩略图生成 — 批量
- **操作步骤**：调用 `AlcedoNativeBridge.nativeGenerateThumbnailBatch(uris, size, dir, listener)`
- **预期结果**：
  - 逐一加载、生成、释放
  - `listener.onThumbnailReady()` / `onThumbnailFailed()` / `onBatchProgress()` / `onBatchComplete()` 被调用
  - 返回是否全部成功
- **验收标准**：等级 A

#### TC-M09-023 编辑历史
- **操作步骤**：
  1. `AlcedoNativeBridge.nativeHistoryOpen(dbPath)` 返回 handle
  2. `AlcedoNativeBridge.nativeHistoryCreateVersion(handle, imageId, parentId, name)`
  3. `AlcedoNativeBridge.nativeHistoryGetTree(handle, imageId)`
  4. `AlcedoNativeBridge.nativeHistoryClose(handle)`
- **预期结果**：
  - `History.nativeCreateVersion()` / `nativeGetHistoryJson()` 被调用
  - 版本创建成功
  - JSON 历史树结构正确
- **验收标准**：等级 A

#### TC-M09-024 Scope 分析
- **操作步骤**：
  1. `AlcedoNativeBridge.nativeScopeHistogram(handle, bins)`
  2. `AlcedoNativeBridge.nativeScopeWaveform(handle, w, h)`
  3. `AlcedoNativeBridge.nativeScopeVectorscope(handle, samples)`
  4. `AlcedoNativeBridge.nativeScopeRgbParade(handle, w, h)`
- **预期结果**：
  - `Scope.nativeSubmitFrame()` 被调用，type 参数正确
  - `Scope.nativeGetHistogram()` / `nativeGetWaveform()` / `nativeGetVectorscope()` 被调用
  - 返回非空数组，数据范围合理
- **验收标准**：等级 A

#### TC-M09-025 导出图片
- **操作步骤**：调用 `AlcedoNativeBridge.nativeExportImage(handle, path, format, quality, colorSpace, includeMetadata)`
- **预期结果**：
  - `Export.nativeExportImage()` 被调用
  - 返回 true
- **验收标准**：等级 A

#### TC-M09-026 requireLoaded 检查
- **操作步骤**：在 `loaded=false` 时调用 `AlcedoNativeBridge.requireLoaded()`
- **预期结果**：抛出 `IllegalStateException`，包含 "Native library 'alcedo_native' not loaded"
- **验收标准**：等级 A

#### TC-M09-027 requireHandle 检查
- **操作步骤**：传入 handle=0L 调用 `AlcedoNativeBridge.requireHandle()`
- **预期结果**：抛出 `IllegalArgumentException`，包含 "Invalid native handle: 0"
- **验收标准**：等级 A

#### TC-M09-028 isValidHandle 检查
- **操作步骤**：分别传入 loaded=true+handle=1L 和 loaded=false+handle=0L
- **预期结果**：前者返回 true，后者返回 false
- **验收标准**：等级 A

#### TC-M09-029 applyParams 便捷方法
- **操作步骤**：调用 `AlcedoNativeBridge.applyParams(pipeline, params)`
- **预期结果**：
  - `requireHandle(pipeline)` 被调用
  - `nativeApplyAdjustments(pipeline, paramsToJson(params))` 被调用
  - `paramsToJson()` 正确序列化所有 29 个参数字段
- **验收标准**：等级 A

#### TC-M09-030 NdkSafeCall.call — 正常调用
- **操作步骤**：调用 `NdkSafeCall.call(default = 0) { 42 }`
- **预期结果**：返回 42，`lastError` 为 null
- **验收标准**：等级 A

#### TC-M09-031 NdkSafeCall.call — 库未加载
- **操作步骤**：在 `loaded=false` 时调用 `NdkSafeCall.call(default = 0) { 42 }`
- **预期结果**：返回 0，`lastError` 为 "Native bridge not loaded"
- **验收标准**：等级 A

#### TC-M09-032 NdkSafeCall.call — UnsatisfiedLinkError
- **操作步骤**：模拟 `UnsatisfiedLinkError`
- **预期结果**：返回 default，`lastError` 包含 "UnsatisfiedLinkError"
- **验收标准**：等级 A

#### TC-M09-033 NdkSafeCall.call — 通用异常
- **操作步骤**：模拟 `RuntimeException`
- **预期结果**：返回 default，`lastError` 包含 "Native call failed"
- **验收标准**：等级 A

#### TC-M09-034 NdkSafeCall.result — 结构化返回
- **操作步骤**：调用 `NdkSafeCall.result { "ok" }`
- **预期结果**：返回 `NdkResult(success=true, value="ok", error=null)`
- **验收标准**：等级 A

#### TC-M09-035 NdkSafeCall.result — 失败
- **操作步骤**：模拟异常
- **预期结果**：返回 `NdkResult(success=false, value=null, error="...")`
- **验收标准**：等级 A

#### TC-M09-036 NdkSafeCall.run — 正常
- **操作步骤**：调用 `NdkSafeCall.run { /* no-op */ }`
- **预期结果**：不崩溃，`lastError` 为 null
- **验收标准**：等级 A

#### TC-M09-037 NdkSafeCall.run — 异常
- **操作步骤**：模拟异常
- **预期结果**：不崩溃，`lastError` 更新
- **验收标准**：等级 A

#### TC-M09-038 NdkSafeCall.callOrNull
- **操作步骤**：调用 `NdkSafeCall.callOrNull { "value" }`
- **预期结果**：成功返回 "value"，失败返回 null
- **验收标准**：等级 A

#### TC-M09-039 NdkSafeCall.handle
- **操作步骤**：调用 `NdkSafeCall.handle { 42L }`
- **预期结果**：成功返回 42L，失败返回 0L
- **验收标准**：等级 A

#### TC-M09-040 NdkSafeCall.ensureLoaded
- **操作步骤**：调用 `NdkSafeCall.ensureLoaded()`
- **预期结果**：已加载返回 true，未加载时尝试 `AlcedoNativeBridge.init()`
- **验收标准**：等级 A

#### TC-M09-041 NdkSafeCall.release
- **操作步骤**：调用 `NdkSafeCall.release(0L) { /* no-op */ }`
- **预期结果**：handle=0L 时直接返回，不调用 release 回调
- **验收标准**：等级 A

#### TC-M09-042 JNI 符号完整性
- **操作步骤**：运行 `nm -D libalcedo_native.so | grep Java_com_alcedo`
- **预期结果**：
  - 所有 Kotlin `external` 方法在 so 中均有对应符号
  - 无未定义符号
  - Bridge/Raw/Pipeline/Sleeve/Image/Ai/Export/Scope/History/Thumbnail 类的 JNI 方法全部存在
- **验收标准**：等级 A（阻塞性）

#### TC-M09-043 AI ONNX 模型加载
- **操作步骤**：调用 `AlcedoNativeBridge.nativeAiLoadOnnxModel(modelPath, deviceId)`
- **预期结果**：
  - ONNX 模型加载当前由 Kotlin OnnxModelManager 管理
  - 返回 0L（JNI 层不直接处理）
  - 不崩溃
- **验收标准**：等级 A

#### TC-M09-044 AI CLIP 文本推理
- **操作步骤**：调用 `AlcedoNativeBridge.nativeAiRunClipText(handle, text)`
- **预期结果**：
  - CLIP 文本推理由 Kotlin OnnxModelManager 管理
  - 返回空 FloatArray
  - 不崩溃
- **验收标准**：等级 A

#### TC-M09-045 AI CLIP 图像推理
- **操作步骤**：调用 `AlcedoNativeBridge.nativeAiRunClipImage(handle, imageHandle)`
- **预期结果**：
  - CLIP 图像推理由 Kotlin OnnxModelManager 管理
  - 返回空 FloatArray
  - 不崩溃
- **验收标准**：等级 A

#### TC-M09-046 AI 分割推理
- **操作步骤**：调用 `AlcedoNativeBridge.nativeAiRunSegmentation(handle, imageHandle)`
- **预期结果**：
  - 分割由 Kotlin OnnxModelManager 管理
  - 返回 null
  - 不崩溃
- **验收标准**：等级 A

#### TC-M09-047 AI 模型释放
- **操作步骤**：调用 `AlcedoNativeBridge.nativeAiReleaseModel(handle)`
- **预期结果**：
  - ONNX 模型生命周期由 OnnxModelManager 管理
  - 不崩溃
- **验收标准**：等级 A

#### TC-M09-048 UltraHDR 写入（路径方式）
- **操作步骤**：调用 `AlcedoNativeBridge.nativeWriteUltraHdr(primaryPath, gainmapPath, outputPath)`
- **预期结果**：
  - C++ Export_nativeExportUltraHdr 使用 image ID 而非路径
  - 返回 false（当前不支持路径方式）
  - 不崩溃
- **验收标准**：等级 A

#### TC-M09-049 ICC Profile 嵌入
- **操作步骤**：调用 `AlcedoNativeBridge.nativeEmbedIccProfile(imagePath, profilePath)`
- **预期结果**：
  - 无直接 C++ 入口
  - 返回 false
  - 不崩溃
- **验收标准**：等级 A

#### TC-M09-050 色彩科学 — 应用矩阵
- **操作步骤**：调用 `AlcedoNativeBridge.nativeColorScienceApply(matrix, inputRgba)`
- **预期结果**：
  - 色彩科学在 Pipeline 中处理
  - 返回原 inputRgba
  - 不崩溃
- **验收标准**：等级 A

#### TC-M09-051 色彩科学 — 色彩空间矩阵
- **操作步骤**：调用 `AlcedoNativeBridge.nativeColorScienceMatrix(fromSpace, toSpace)`
- **预期结果**：
  - 矩阵在 Pipeline 操作符中构建
  - 返回空 FloatArray
  - 不崩溃
- **验收标准**：等级 A

#### TC-M09-052 镜头校正 Profile
- **操作步骤**：调用 `AlcedoNativeBridge.nativeLensCorrectionProfile(lensId)`
- **预期结果**：
  - 镜头校正使用 Kotlin 端数据库
  - 返回 null
  - 不崩溃
- **验收标准**：等级 A

#### TC-M09-053 RAW 黑白电平设置
- **操作步骤**：调用 `AlcedoNativeBridge.nativeRawSetBlackWhite(handle, 0, 65535)`
- **预期结果**：
  - 无直接 C++ 入口，参数通过 Pipeline JSON 传递
  - 返回 false
  - 不崩溃
- **验收标准**：等级 A

#### TC-M09-054 RAW 降噪设置
- **操作步骤**：调用 `AlcedoNativeBridge.nativeRawSetNoiseReduction(handle, 0.5f)`
- **预期结果**：
  - 无直接 C++ 入口，参数通过 Pipeline JSON 传递
  - 返回 false
  - 不崩溃
- **验收标准**：等级 A

#### TC-M09-055 RAW CFA Pattern 检测
- **操作步骤**：调用 `AlcedoNativeBridge.nativeRawDetectCfaPattern(uri)`
- **预期结果**：
  - 无直接 C++ 入口
  - 返回 null
  - 不崩溃
- **验收标准**：等级 A

#### TC-M09-056 Sleeve 路径解析
- **操作步骤**：调用 `AlcedoNativeBridge.nativeSleeveResolvePath(handle, logicalPath)`
- **预期结果**：
  - 无直接 C++ 等价方法
  - 返回 null（由 Kotlin 端解析）
  - 不崩溃
- **验收标准**：等级 A

#### TC-M09-057 图片转 Bitmap
- **操作步骤**：调用 `AlcedoNativeBridge.nativeImageToBitmap(handle, maxWidth)`
- **预期结果**：
  - `Thumbnail.nativeGetThumbnailBytes()` 被调用
  - 返回可解码的 Bitmap
  - 失败时返回 null
- **验收标准**：等级 A

#### TC-M09-058 Mask 应用
- **操作步骤**：调用 `AlcedoNativeBridge.nativeApplyMask(pipelineHandle, maskJson, maskBitmap)`
- **预期结果**：
  - Mask 合成由 Kotlin MaskRenderService 处理
  - 返回 false
  - 不崩溃
- **验收标准**：等级 A

#### TC-M09-059 Mask 清除
- **操作步骤**：调用 `AlcedoNativeBridge.nativeClearMasks(pipelineHandle)`
- **预期结果**：
  - 返回 false（当前实现）
  - 不崩溃
- **验收标准**：等级 A

#### TC-M09-060 Pipeline 阶段失效
- **操作步骤**：调用 `AlcedoNativeBridge.nativeInvalidateStage(pipelineHandle, stageName)`
- **预期结果**：
  - No-op：C++ 在每次 apply 时重新执行 Pipeline
  - 不崩溃
- **验收标准**：等级 A

#### TC-M09-061 Pipeline 渲染到缓冲区
- **操作步骤**：调用 `AlcedoNativeBridge.nativeRenderToBuffer(pipelineHandle)`
- **预期结果**：
  - 返回 pipelineHandle（缓冲区即图像本身）
  - 失败时返回 0L
- **验收标准**：等级 A

#### TC-M09-062 获取最终显示帧
- **操作步骤**：调用 `AlcedoNativeBridge.nativeGetFinalDisplayFrame(pipelineHandle)`
- **预期结果**：
  - `Thumbnail.nativeGetThumbnailBytes()` 被调用
  - 返回可解码的 Bitmap
  - 失败时返回 null
- **验收标准**：等级 A

#### TC-M09-063 历史记录添加事务
- **操作步骤**：调用 `AlcedoNativeBridge.nativeHistoryAddTransaction(handle, versionId, deltaJson)`
- **预期结果**：
  - 事务在 C++ Pipeline 中是隐式的
  - 返回空字符串
  - 不崩溃
- **验收标准**：等级 A

#### TC-M09-064 历史记录重放
- **操作步骤**：调用 `AlcedoNativeBridge.nativeHistoryReplay(handle, versionId)`
- **预期结果**：
  - 重放通过重新执行 Pipeline 处理
  - 返回 "{}"
  - 不崩溃
- **验收标准**：等级 A

#### TC-M09-065 ThumbnailListener 接口回调
- **操作步骤**：实现 `ThumbnailListener` 接口的所有方法
- **预期结果**：
  - `onThumbnailReady(index, uri, outputPath)` 被调用
  - `onThumbnailFailed(index, uri, error)` 被调用
  - `onBatchProgress(completed, total)` 被调用
  - `onBatchComplete(success)` 被调用
- **验收标准**：等级 A

#### TC-M09-066 NativeProgressListener 接口
- **操作步骤**：实现 `NativeProgressListener` 接口
- **预期结果**：
  - `onProgress(completed, total)` 返回 false 可取消
  - 返回 true 继续
- **验收标准**：等级 A

#### TC-M09-067 StringArrayResult 数据类
- **操作步骤**：构造 `StringArrayResult(values, ok)`
- **预期结果**：
  - `values` 和 `ok` 字段正确
  - 被 `@Keep` 标注
- **验收标准**：等级 A

---

### M10 后台任务

#### TC-M10-001 任务启动与进度
- **操作步骤**：调用 `taskService.start(type, title, totalItems)`
- **预期结果**：
  - 返回唯一 taskId
  - `active` 集合包含新任务
  - `tasks` StateFlow 包含新任务
  - `startTimes` 记录启动时间
  - `sleeveHandleCounter` 递增
- **验收标准**：等级 A

#### TC-M10-002 任务进度更新
- **操作步骤**：调用 `taskService.update(taskId, completed, total)`
- **预期结果**：
  - `progress` 正确计算
  - `etaMs` 通过 `computeEta()` 计算
  - `indeterminate` 根据 total 更新
- **验收标准**：等级 A

#### TC-M10-003 任务完成
- **操作步骤**：调用 `taskService.complete(taskId)`
- **预期结果**：
  - `progress` 变为 1f
  - 成功任务自动从 `active` 移除
  - 失败任务保留在 `active` 中
- **验收标准**：等级 A

#### TC-M10-004 任务完成 — 带错误
- **操作步骤**：调用 `taskService.complete(taskId, error = "failed")`
- **预期结果**：任务保留在 `active` 中，`error` 为 "failed"
- **验收标准**：等级 A

#### TC-M10-005 任务取消
- **操作步骤**：
  1. 启动批量导出
  2. 在后台任务栏点击 Cancel
- **预期结果**：
  - `cancelled` 集合先添加 taskId
  - 任务标记为 "cancelled"，progress=1f
  - 任务从 `active` 移除
  - `startTimes` 移除
  - `cancelled` 集合清理（不累积）
  - `isCancelled(taskId)` 返回 false（已清理）
- **验收标准**：等级 A

#### TC-M10-006 取消标志清理
- **操作步骤**：取消任务后检查 `cancelled` 集合
- **预期结果**：`cancelled.remove(taskId)` 被调用，集合不累积
- **验收标准**：等级 A

#### TC-M10-007 任务消除
- **操作步骤**：调用 `taskService.dismiss(taskId)`
- **预期结果**：任务从 `active`、`startTimes`、`cancelled` 中移除
- **验收标准**：等级 A

#### TC-M10-008 活跃任务计数
- **操作步骤**：调用 `taskService.activeCount()`
- **预期结果**：返回非错误且 progress < 1f 的任务数
- **验收标准**：等级 A

#### TC-M10-009 ETA 计算
- **操作步骤**：调用 `computeEta(taskId, completed, total)`
- **预期结果**：
  - completed <= 0 或 total <= 0 时返回 null
  - 正常情况下返回估算剩余时间
- **验收标准**：等级 A

#### TC-M10-010 多任务并发
- **操作步骤**：同时启动导入和导出任务
- **预期结果**：
  - 两个任务并行显示进度
  - 不互相干扰
  - 不触发 ANR
  - `tasks` StateFlow 包含两个任务
- **验收标准**：等级 A

---

### M11 崩溃报告

#### TC-M11-001 崩溃捕获
- **前置条件**：开启 Crash Report
- **操作步骤**：通过开发者选项触发一次测试崩溃
- **预期结果**：
  - `writeCrashReport()` 被调用
  - 崩溃报告写入 `filesDir/crashlogs/crash_YYYYMMDD_HHmmss.txt`
  - 报告包含：时间、线程、进程、设备、Android 版本、ABI、堆栈跟踪
  - 敏感信息被脱敏
  - 报告大小不超过 256KB
  - `pruneOldReports()` 保持最多 10 个文件 / 2MB 总量
  - 崩溃后重启应用
  - 若开启遥测，日志上传服务端
- **验收标准**：等级 B（非阻塞）

#### TC-M11-002 隐私开关控制
- **操作步骤**：在设置中关闭 Crash Reports，触发崩溃
- **预期结果**：
  - `CrashReportService.enabled` 为 false
  - `writeCrashReport()` 检查 `!enabled` 后直接返回
  - 崩溃日志不写入
- **验收标准**：等级 B

#### TC-M11-003 敏感信息脱敏
- **操作步骤**：检查崩溃报告中的敏感信息
- **预期结果**：
  - `REDACT_HEADER` 替换 Authorization/API Key 等为 `<redacted>`
  - `REDACT_BEARER` 替换 Bearer 令牌为 `<redacted>`
  - `REDACT_OPENAI` 替换 OpenAI API Key 为 `<redacted>`
- **验收标准**：等级 A

#### TC-M11-004 报告大小限制
- **操作步骤**：触发一个超长堆栈跟踪的崩溃
- **预期结果**：报告被截断到 `MAX_REPORT_CHARS` (256KB)，附加 `...[truncated]`
- **验收标准**：等级 A

#### TC-M11-005 报告清理
- **操作步骤**：调用 `CrashReportService.clearReports(filesDir)`
- **预期结果**：所有崩溃报告文件被删除，返回删除数量
- **验收标准**：等级 A

#### TC-M11-006 报告列表
- **操作步骤**：调用 `CrashReportService.reportFiles(filesDir)`
- **预期结果**：返回所有 .txt 文件，按修改时间排序
- **验收标准**：等级 A

---

### M12 数据存储

#### TC-M12-001 数据库读写
- **操作步骤**：
  1. 创建文件夹
  2. 导入图片
  3. 编辑图片参数
  4. 关闭应用，重新打开
- **预期结果**：
  - 文件夹和图片持久化
  - 编辑参数（版本）持久化
  - `SleeveDatabase.get(context)` 返回单例
- **验收标准**：等级 A

#### TC-M12-002 数据迁移/升级
- **前置条件**：安装旧版本，创建数据，升级到 v1.6.11
- **操作步骤**：启动新版本
- **预期结果**：旧数据完整保留，无丢失
- **验收标准**：等级 A

#### TC-M12-003 数据库重置
- **操作步骤**：调用 `SleeveDatabase.reset(context)`
- **预期结果**：
  - 旧实例关闭
  - 新实例创建
  - 数据清空
- **验收标准**：等级 A

#### TC-M12-004 TypeConverter — ImageFlag
- **操作步骤**：测试 `ImageFlag` ↔ `String` 转换
- **预期结果**：正确序列化/反序列化所有枚举值
- **验收标准**：等级 A

#### TC-M12-005 TypeConverter — ColorLabel
- **操作步骤**：测试 `ColorLabel` ↔ `String` 转换
- **预期结果**：正确序列化/反序列化所有枚举值
- **验收标准**：等级 A

#### TC-M12-006 TypeConverter — FloatArray
- **操作步骤**：测试 `FloatArray` ↔ `String` 转换
- **预期结果**：正确序列化/反序列化
- **验收标准**：等级 A

#### TC-M12-007 Room Entity 完整性
- **操作步骤**：验证所有 10 个 Entity 的表结构
- **预期结果**：
  - ImageEntity, SleeveElementEntity, ProjectEntity, EditVersionEntity, EditTransactionEntity
  - AiEmbeddingEntity, AiRatingEntity, PipelinePresetEntity, AiModelEntity, LensProfileEntity
  - 所有索引正确创建
  - 所有字段类型正确
- **验收标准**：等级 A

#### TC-M12-008 AiEmbeddingEntity — equals/hashCode
- **操作步骤**：测试 `AiEmbeddingEntity` 的 `equals()` 和 `hashCode()`
- **预期结果**：`embeddingBlob` 的内容比较正确
- **验收标准**：等级 A

#### TC-M12-009 DAO 操作
- **操作步骤**：测试 `ImageDao` / `EditHistoryDao` / `AiEmbeddingDao` / `PipelinePresetDao`
- **预期结果**：所有 CRUD 操作正确
- **验收标准**：等级 A

#### TC-M12-010 破坏性迁移
- **操作步骤**：检查 `fallbackToDestructiveMigration()` 配置
- **预期结果**：版本不匹配时清空重建，不崩溃
- **验收标准**：等级 A

#### TC-M12-011 自定义迁移
- **操作步骤**：检查 `DatabaseMigrations.ALL` 是否被注册
- **预期结果**：所有迁移脚本被添加到 Room builder
- **验收标准**：等级 A

#### TC-M12-012 Repository 操作
- **操作步骤**：测试 `ImageRepository` / `SleeveRepository` 的所有公开方法
- **预期结果**：所有操作正确，包括 upsert、delete、getImage、setRating 等
- **验收标准**：等级 A

---

### M13 隐私管理

#### TC-M13-001 隐私同意 — 授予
- **操作步骤**：首次启动时点击 "Accept"
- **预期结果**：
  - `setConsent(true)` 被调用
  - `consentGiven` 为 true
  - `consentTimestamp` 记录当前时间
  - `canCollectData` 为 true
  - `cloudLlmAllowed` 为 false（默认关闭）
- **验收标准**：等级 A

#### TC-M13-002 隐私同意 — 拒绝
- **操作步骤**：首次启动时点击 "Decline"
- **预期结果**：
  - `setConsent(false)` 被调用
  - `consentGiven` 为 false
  - `cloudLlmAllowed` 和 `telemetryAllowed` 被强制设为 false
  - `canCollectData` 为 false
- **验收标准**：等级 A

#### TC-M13-003 隐私同意 — 撤回
- **操作步骤**：同意后再次拒绝
- **预期结果**：
  - `cloudLlmAllowed` 和 `telemetryAllowed` 被强制设为 false
  - 所有数据收集停止
- **验收标准**：等级 A

#### TC-M13-004 requireConsent 检查
- **操作步骤**：未同意时调用 `requireConsent()`
- **预期结果**：抛出 `ConsentRequiredException`
- **验收标准**：等级 A

#### TC-M13-005 canCollectData 检查
- **操作步骤**：同意/拒绝后分别调用 `canCollectData()`
- **预期结果**：同意时返回 true，拒绝时返回 false
- **验收标准**：等级 A

#### TC-M13-006 Cloud LLM 控制
- **操作步骤**：同意后开启 Cloud LLM
- **预期结果**：
  - `setCloudLlmAllowed(true)` 被调用
  - `canUseCloudLlm` 为 true（需 consent + cloudLlm）
  - 未同意时无法开启 Cloud LLM
- **验收标准**：等级 A

#### TC-M13-007 遥测控制
- **操作步骤**：同意后开启遥测
- **预期结果**：
  - `setTelemetryAllowed(true)` 被调用
  - `canCollectTelemetry` 为 true（需 consent + telemetry）
  - 未同意时无法开启遥测
- **验收标准**：等级 A

#### TC-M13-008 On-Device AI 控制
- **操作步骤**：关闭 On-Device AI
- **预期结果**：
  - `setOnDeviceAiAllowed(false)` 被调用
  - `canUseOnDeviceAi` 需 consent + onDeviceAi
- **验收标准**：等级 A

#### TC-M13-009 应用设置 — 持久化
- **操作步骤**：修改所有应用设置，重启应用
- **预期结果**：
  - `appSettings` Flow 正确恢复所有设置
  - theme / defaultView / gpuBackend / aiStrictness / crashReportEnabled / analyticsEnabled / aiApiKey / aiEndpoint / aiModel
- **验收标准**：等级 A

#### TC-M13-010 PrivacyState 计算属性
- **操作步骤**：验证所有计算属性
- **预期结果**：
  - `canUseCloudLlm` = consentGiven && cloudLlmAllowed
  - `canCollectTelemetry` = consentGiven && telemetryAllowed
  - `canUseOnDeviceAi` = consentGiven && onDeviceAiAllowed
  - `canCollectData` = consentGiven
- **验收标准**：等级 A

#### TC-M13-011 CrashReportService 与隐私联动
- **操作步骤**：同意后关闭 Crash Reports
- **预期结果**：
  - `CrashReportService.setConsentAndTelemetry(state.consentGiven && crashReportEnabled)` 被调用
  - 关闭 Crash Reports 后 `enabled` 为 false
- **验收标准**：等级 A

#### TC-M13-012 崩溃报告 — 仅同意+开启
- **操作步骤**：同意但关闭 Crash Reports，触发崩溃
- **预期结果**：`enabled` 为 false，崩溃日志不写入
- **验收标准**：等级 A

#### TC-M13-013 DataStore 持久化
- **操作步骤**：修改隐私设置，重启应用
- **预期结果**：所有隐私设置通过 DataStore 持久化并恢复
- **验收标准**：等级 A

#### TC-M13-014 AppSettings 默认值
- **操作步骤**：首次启动检查默认值
- **预期结果**：
  - theme = ""
  - gpuBackend = ""
  - aiStrictness = 0.5f
  - crashReportEnabled = true
  - analyticsEnabled = false
  - aiApiKey = ""
  - aiEndpoint = ""
  - aiModel = ""
- **验收标准**：等级 A

#### TC-M13-015 Analytics 设置
- **操作步骤**：开启/关闭 Analytics
- **预期结果**：`setAnalyticsEnabled()` 被调用，值持久化
- **验收标准**：等级 A

#### TC-M13-016 PrivacyConsentDialog UI
- **操作步骤**：首次启动时显示同意对话框
- **预期结果**：
  - 包含 "Accept" 和 "Decline" 按钮
  - Accept 触发 `setConsent(true)` + `setCloudLlmAllowed(false)`
  - Decline 触发 `setConsent(false)`
- **验收标准**：等级 A

---

### M14 安全与临时文件

#### TC-M14-001 临时文件创建
- **操作步骤**：调用 `tempFileManager.create(prefix, suffix)`
- **预期结果**：
  - 文件在 `cacheDir/alcedo_temp/` 下创建
  - 文件名包含 UUID
  - `tracked` 集合包含新文件
- **验收标准**：等级 A

#### TC-M14-002 临时目录创建
- **操作步骤**：调用 `tempFileManager.createDir(prefix)`
- **预期结果**：目录创建并被跟踪
- **验收标准**：等级 A

#### TC-M14-003 临时文件释放
- **操作步骤**：调用 `tempFileManager.release(file)`
- **预期结果**：
  - 文件从 `tracked` 移除
  - 文件被删除
  - 目录被递归删除
- **验收标准**：等级 A

#### TC-M14-004 全部清理
- **操作步骤**：调用 `tempFileManager.cleanupAll()`
- **预期结果**：
  - 所有跟踪文件被删除
  - `tracked` 集合清空
- **验收标准**：等级 A

#### TC-M14-005 孤立文件清理
- **操作步骤**：调用 `tempFileManager.sweepOrphans(maxAgeMs)`
- **预期结果**：
  - 未跟踪且超过 maxAgeMs 的文件被删除
  - 跟踪中的文件不受影响
  - 默认 maxAgeMs = 24 小时
- **验收标准**：等级 A

#### TC-M14-006 存储使用量
- **操作步骤**：调用 `tempFileManager.usedBytes()`
- **预期结果**：返回 tempRoot 下所有文件的总字节数
- **验收标准**：等级 A

#### TC-M14-007 描述信息
- **操作步骤**：调用 `tempFileManager.describe()`
- **预期结果**：返回 "N tracked files, X.X MB" 格式字符串
- **验收标准**：等级 A

#### TC-M14-008 SecureHttpClient — 证书固定
- **操作步骤**：检查 `SecureHttpClient` 配置
- **预期结果**：启用证书固定和超时设置
- **验收标准**：等级 A

#### TC-M14-009 临时文件 — 删除失败
- **操作步骤**：模拟文件删除失败
- **预期结果**：不崩溃，Log.w 记录警告
- **验收标准**：等级 A

#### TC-M14-010 PipelineService — 专用 Handle 管理
- **操作步骤**：
  1. `pipelineService.createForImage(uri)` 创建专用 handle
  2. `pipelineService.releaseHandle(handle)` 释放
- **预期结果**：
  - 创建时 `dedicatedHandles` 记录 handle → decodedHandle 映射
  - 释放时 `dedicatedHandles` 移除映射
  - `AlcedoNativeBridge.nativeDestroyPipeline()` 和 `nativeReleaseImage()` 被调用
  - `close()` 时释放所有泄漏的专用 handle
- **验收标准**：等级 A

---

### M15 性能与稳定性

#### TC-M15-001 大图片压力测试
- **前置条件**：准备 100MB+ TIFF 或 60MP RAW
- **操作步骤**：
  1. 导入并打开
  2. 连续调整参数 20 次
  3. 导出全分辨率
- **预期结果**：
  - 不触发 OOM
  - 不 ANR
  - 导出时间可接受（< 60s）
  - `PipelineService.render()` 正确处理大图
- **验收标准**：等级 A

#### TC-M15-002 批量操作压力测试
- **操作步骤**：选择 100 张图片，批量应用 Preset，批量导出
- **预期结果**：
  - 批量编辑不 ANR
  - 批量导出内存曲线平稳，无持续增长
  - 完成后内存回到基线
  - `ExportService` 正确回收 Bitmap
- **验收标准**：等级 A

#### TC-M15-003 长时间运行稳定性
- **操作步骤**：应用保持前台运行 2 小时，期间频繁切换功能
- **预期结果**：
  - 无内存泄漏（堆增长 < 20%）
  - 无崩溃
  - 响应速度无明显下降
- **验收标准**：等级 A

#### TC-M15-004 快速操作压力
- **操作步骤**：在 Editor 中快速连续切换 Tab、调整滑块、Undo/Redo
- **预期结果**：
  - 不触发渲染队列溢出
  - 防抖（80ms）有效
  - 最终状态与最后一次操作一致
  - 无闪退
- **验收标准**：等级 A

#### TC-M15-005 低内存设备测试
- **前置条件**：模拟 2GB RAM 设备或限制内存
- **操作步骤**：执行常规编辑和导出流程
- **预期结果**：
  - 应用在低内存下 graceful degradation
  - `onTrimMemory()` / `onLowMemory()` 被调用
  - 可能降低预览分辨率，但不崩溃
- **验收标准**：等级 C

#### TC-M15-006 Bitmap 内存回收验证
- **操作步骤**：批量导出 20 张大图，监控内存
- **预期结果**：
  - `Bitmap.recycle()` 在使用后立即调用
  - `bitmap !== finalBitmap` 时分别回收
  - `Bitmap.recycle()` 是幂等的
  - UltraHDR 导出时 `finalBitmap` 在 `hdrBitmap` 获取前不被回收
  - 内存曲线呈锯齿状（上升 → 回收 → 下降），不持续增长
- **验收标准**：等级 A

#### TC-M15-007 PipelineService — 专用 Handle 内存
- **操作步骤**：批量导出时创建/释放 50 个专用 handle
- **预期结果**：
  - 每个 handle 在 `finally` 块中释放
  - `dedicatedHandles` 集合不累积
  - 无 native 内存泄漏
- **验收标准**：等级 A

---

## 四、覆盖率矩阵

### 4.1 功能覆盖率

| 模块 | 用例数 | P0 | P1 | 覆盖方法数 | 覆盖率 |
|-----|-------|----|----|----------|-------|
| M01 启动与生命周期 | 15 | 15 | 0 | 8 | 100% |
| M02 导航与UI框架 | 10 | 10 | 0 | 6 | 100% |
| M03 相册管理 | 37 | 37 | 0 | 30 | 100% |
| M04 图片编辑器 | 55 | 55 | 0 | 34 | 100% |
| M05 AI 模块 | 30 | 30 | 0 | 20 | 100% |
| M06 导出模块 | 39 | 39 | 0 | 24 | 100% |
| M07 设置模块 | 22 | 0 | 22 | 17 | 100% |
| M08 权限管理 | 19 | 19 | 0 | 18 | 100% |
| M09 NDK/Native | 67 | 67 | 0 | 67 | 100% |
| M10 后台任务 | 10 | 0 | 10 | 7 | 100% |
| M11 崩溃报告 | 6 | 0 | 6 | 4 | 100% |
| M12 数据存储 | 12 | 0 | 12 | 12 | 100% |
| M13 隐私管理 | 16 | 16 | 0 | 17 | 100% |
| M14 安全与临时文件 | 10 | 0 | 10 | 10 | 100% |
| M15 性能稳定性 | 7 | 7 | 0 | N/A | 100% |
| **合计** | **355** | **295** | **60** | **284** | **100%** |

### 4.2 代码覆盖率目标

| 层级 | 目标覆盖率 | 验证方式 |
|-----|-----------|---------|
| Kotlin UI / ViewModel | ≥ 80% | Jacoco / Android Studio Coverage |
| Kotlin Service / Repository | ≥ 85% | Jacoco / 单元测试 |
| Kotlin NDK Bridge | ≥ 90% | 单元测试 + 集成测试 |
| Kotlin Privacy / Permission | ≥ 90% | 单元测试 |
| Kotlin Security / TempFile | ≥ 85% | 单元测试 |
| C++ JNI / Core | ≥ 70% | GoogleTest / 手动验证 |

### 4.3 设备兼容性矩阵

| 条件 | 必须验证 | 备注 |
|-----|---------|------|
| Android 10 (API 29) | 是 | WRITE_EXTERNAL_STORAGE 路径 |
| Android 11 (API 30) | 是 | Scoped Storage 分水岭 |
| Android 13 (API 33) | 是 | 媒体权限变更 |
| Android 14 (API 34) | 是 | 最新稳定版 |
| 手机 (<600dp) | 是 | BottomBar 布局 |
| 平板 (≥600dp) | 是 | NavigationRail 布局 |
| 支持 Vulkan | 是 | GPU 渲染路径 |
| 不支持 Vulkan | 是 | CPU 降级路径 |
| ARM64 | 是 | 主流架构 |
| 无网络 | 是 | AI 模型下载降级 |

### 4.4 边界条件覆盖

| 类型 | 用例 | 覆盖模块 |
|-----|------|---------|
| 空输入 | TC-M03-004, TC-M03-006, TC-M05-003, TC-M06-010 | M03, M05, M06 |
| 无效输入 | TC-M04-002, TC-M04-007, TC-M06-004, TC-M06-011 | M04, M06 |
| 权限缺失 | TC-M06-002, TC-M08-003, TC-M08-004 | M06, M08 |
| 降级路径 | TC-M01-005, TC-M06-007, TC-M13-005, TC-M15-005 | M01, M06, M13, M15 |
| 并发/竞态 | TC-M04-008, TC-M07-010, TC-M07-012, TC-M10-010 | M04, M07, M10 |
| 内存压力 | TC-M01-006, TC-M01-007, TC-M15-005, TC-M15-006 | M01, M15 |
| 取消操作 | TC-M03-003, TC-M06-016, TC-M10-005 | M03, M06, M10 |
| 数据持久化 | TC-M12-001, TC-M12-003, TC-M13-009, TC-M13-013 | M12, M13 |
| JNI 错误 | TC-M09-031, TC-M09-032, TC-M09-033 | M09 |
| 隐私门控 | TC-M13-004, TC-M13-006, TC-M13-011, TC-M13-012 | M13 |

---

## 五、测试执行检查清单

### 发布前必须通过项（Blockers）

- [x] TC-M01-001 冷启动通过，无 ANR
- [x] TC-M01-005 Native 降级路径不崩溃
- [x] TC-M01-012 隐私状态加载超时不卡死
- [x] TC-M03-001 导入功能正常
- [x] TC-M04-001/004 Editor 打开 JPG/RAW 正常
- [x] TC-M04-008 渲染防抖有效
- [x] TC-M04-019 Undo/Redo 正确
- [x] TC-M04-039 未保存更改提示正确
- [x] TC-M05-004 AI 搜索模型未就绪降级
- [x] TC-M06-001/009 单张/批量导出成功
- [x] TC-M06-002 无权限导出正确拦截
- [x] TC-M06-008 UltraHDR 导出 Bitmap 回收正确
- [x] TC-M06-017 导出取消无 OOM
- [x] TC-M07-004 GPU 后端切换传递到 native 层
- [x] TC-M08-001/002 权限请求与授予正常
- [x] TC-M09-042 JNI 符号 100% 匹配（无 UnsatisfiedLinkError）
- [x] TC-M10-005 任务取消标志正确清理
- [x] TC-M13-002 隐私拒绝后数据收集停止
- [x] TC-M15-001/002 大图和批量压力不崩溃、不 OOM
- [x] TC-M15-006 Bitmap 内存回收验证通过
- [x] TC-M15-003 2小时稳定性无内存泄漏

### 已知限制与降级（Acceptable）

- TIFF 导出在所有设备上降级为 PNG（TC-M06-007 等级 B）
- UltraHDR 导出在部分旧设备上不可用（TC-M06-008 等级 B/C）
- AI 语义搜索需先下载模型（TC-M05-004 等级 C）
- 低内存设备可能降低预览分辨率（TC-M15-005 等级 C）
- 崩溃报告功能依赖用户同意（TC-M11-002 等级 B）

---

## 六、缺陷分级与处理流程

| 级别 | 定义 | 处理时限 | 是否阻塞发布 |
|-----|------|---------|------------|
| **Critical** | 崩溃、数据丢失、安全漏洞、完全无法使用 | 24h | 是 |
| **Major** | 核心功能异常、性能严重下降、权限失效 | 48h | 是 |
| **Minor** | UI 瑕疵、非核心功能异常、文案错误 | 1周内 | 否 |
| **Trivial** | 纯视觉微调、日志级别问题 | 下次迭代 | 否 |

---

## 七、v1.6.11 新增/修复项专项验证

| 变更项 | 对应用例 | 验证要点 |
|--------|---------|---------|
| 渲染防抖 (80ms) | TC-M04-008 | 快速调整不溢出 |
| GPU 后端切换到 native | TC-M07-004 | `nativeSetGpuBackend()` 被调用 |
| AI 搜索模型就绪检查 | TC-M05-004 | 模型未就绪时提示错误 |
| UltraHDR Bitmap 回收顺序 | TC-M06-008 | `finalBitmap` 在 `hdrBitmap` 获取前不被回收 |
| 后台任务取消标志清理 | TC-M10-005/006 | `cancelled` 集合不累积 |
| 导出权限检查 | TC-M06-002/003 | API 29 及以下需 WRITE_EXTERNAL_STORAGE |
| 外部图片 Intent 处理 | TC-M01-002/003/004 | 冷/热启动 + 非 image 类型 |
| nativeOnLowMemory | TC-M01-006/007/008 | GC + 缓存清理 |
| 隐私同意门控 | TC-M02-005/006 | 首次启动流程 |
| 崩溃报告隐私联动 | TC-M13-011/012 | 同意 + 开关双重控制 |

---

*文档版本：v1.6.11*
*生成日期：2026-07-29*
*适用范围：Release v1.6.11 全面验收测试*
*总用例数：355*
*覆盖公开方法数：284*
*覆盖率：100%*
