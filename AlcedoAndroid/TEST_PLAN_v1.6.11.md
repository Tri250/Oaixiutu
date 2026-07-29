# Alcedo Studio v1.6.11 全面功能测试计划与验收标准

> 目标：覆盖所有功能模块的操作链路，验收标准可量化、可执行，覆盖率 100%。

---

## 一、测试范围与模块清单

| 模块编号 | 模块名称 | 核心文件/路径 | 测试优先级 |
|---------|---------|-------------|-----------|
| M01 | 应用启动与生命周期 | `AlcedoApplication.kt`, `MainActivity.kt` | P0 |
| M02 | 导航与UI框架 | `MainScreen.kt`, `Routes` | P0 |
| M03 | 相册管理 | `AlbumScreen.kt`, `AlbumViewModel.kt` | P0 |
| M04 | 图片编辑器 | `EditorScreen.kt`, `EditorViewModel.kt` | P0 |
| M05 | AI 智能模块 | `AiSearchScreen.kt`, `AiRatingScreen.kt`, `AiModelManagerScreen.kt` | P0 |
| M06 | 导出模块 | `ExportScreen.kt`, `ExportService.kt` | P0 |
| M07 | 设置模块 | `SettingsScreen.kt`, `SettingsViewModel.kt` | P1 |
| M08 | 权限管理 | `PermissionHelper.kt` | P0 |
| M09 | NDK/Native 层 | `AlcedoNativeBridge.kt`, `ndk/*.kt`, `cpp/jni/*.cpp` | P0 |
| M10 | 后台任务 | `BackgroundTaskService.kt` | P1 |
| M11 | 崩溃报告 | `CrashReportService.kt` | P1 |
| M12 | 数据存储 | `SleeveDatabase.kt`, `*Repository.kt`, `*Dao.kt` | P1 |
| M13 | 性能与稳定性 | 内存、OOM、ANR、压力测试 | P0 |

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
  - 无崩溃、无ANR
- **验收标准**：等级 A

#### TC-M01-002 外部图片 Intent 打开
- **前置条件**：应用未运行，从文件管理器选择图片
- **操作步骤**：
  1. 在文件管理器中点击一张 JPG/PNG
  2. 选择 "Alcedo Studio" 打开
- **预期结果**：
  - 应用启动后直接进入 EditorScreen
  - 图片正常加载并显示预览
- **验收标准**：等级 A

#### TC-M01-003 Native 库加载失败降级
- **前置条件**：模拟无 Vulkan 设备（或删除 so 文件）
- **操作步骤**：启动应用
- **预期结果**：
  - Logcat 显示 `alcedo_native unavailable; running in software/degraded mode`
  - 应用不崩溃，UI 正常可用
  - 设置页中 GPU 可用性显示 "Unavailable"
- **验收标准**：等级 C（降级路径正确）

#### TC-M01-004 内存压力处理
- **前置条件**：应用在 Editor 中打开大分辨率 RAW
- **操作步骤**：
  1. 使用 `adb shell am send-trim-memory com.alcedo.studio RUNNING_LOW`
  2. 观察应用表现
- **预期结果**：
  - 不崩溃
  - 缩略图缓存被释放
  - Native 层 `nativeOnLowMemory()` 被调用
  - 临时文件被清理
- **验收标准**：等级 A

#### TC-M01-005 后台恢复
- **前置条件**：应用在 Editor 中，按 Home 键进入后台 10 分钟
- **操作步骤**：从最近任务恢复应用
- **预期结果**：
  - 恢复到 Editor 界面，图片和编辑状态保留
  - 无重新加载的闪烁
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

---

### M03 相册管理

#### TC-M03-001 图片导入（单张/多张）
- **操作步骤**：
  1. Album 点击 "Import" 或空状态按钮
  2. 系统文件选择器选择 1/5/20 张图片
- **预期结果**：
  - 图片出现在相册网格中
  - 后台任务栏显示导入进度
  - 导入完成后缩略图正常显示
  - 20张批量导入不触发ANR
- **验收标准**：等级 A

#### TC-M03-002 文件夹创建与管理
- **操作步骤**：
  1. 点击侧边栏 "Create Folder"
  2. 输入 "TestFolder" 确认
  3. 切换到此文件夹
- **预期结果**：
  - 文件夹出现在 CollectionsPanel 中
  - 切换后网格显示为空
  - 导入图片到此文件夹，图片仅属于此文件夹
- **验收标准**：等级 A

#### TC-M03-003 搜索功能
- **操作步骤**：
  1. 在搜索框输入 "sunset"
  2. 清空搜索
  3. 输入无结果关键词 "xyz123"
- **预期结果**：
  - 搜索结果实时过滤
  - 清空后恢复全部显示
  - 无结果时显示 EmptyState (`noSearchResults`)
- **验收标准**：等级 A

#### TC-M03-004 排序功能
- **操作步骤**：点击排序下拉菜单，依次选择每种排序字段
- **预期结果**：图片按所选字段正确排序（正序/倒序切换）
- **验收标准**：等级 A

#### TC-M03-005 视图切换与缩放
- **操作步骤**：
  1. Grid/List 切换按钮
  2. 缩放滑块从 2 拖动到 14
- **预期结果**：
  - Grid 变为 List，布局正确
  - 列数实时变化，缩略图大小适应
- **验收标准**：等级 A

#### TC-M03-006 图片选择与批量操作
- **操作步骤**：
  1. 长按一张图片进入选择模式
  2. 点击选择 3 张图片
  3. 点击批量编辑 → Apply Preset
  4. 点击导出
- **预期结果**：
  - 选中图片显示选中态
  - TopBar 显示选中数量 Badge
  - 批量应用 Preset 成功
  - 导出页面携带选中图片ID
- **验收标准**：等级 A

#### TC-M03-007 右键菜单操作
- **操作步骤**：长按一张图片，依次测试：
  - Copy Adjustments
  - Paste Adjustments
  - Rate (1-5星)
  - Set Flag (Pick/Reject/None)
  - Set Color Label
  - Add to Collection
  - Delete
- **预期结果**：每项操作后图片元数据正确更新，删除后图片从网格消失
- **验收标准**：等级 A

#### TC-M03-008 空状态显示
- **前置条件**：全新安装，无任何图片
- **操作步骤**：启动应用进入 Album
- **预期结果**：显示 EmptyState，包含标题、副标题、Import 按钮
- **验收标准**：等级 A

#### TC-M03-009 后台任务栏
- **操作步骤**：导入大量图片，观察底部任务栏
- **预期结果**：
  - 显示进度条和任务名称
  - 点击取消可终止任务
  - 任务完成后自动消失
- **验收标准**：等级 A

---

### M04 图片编辑器

#### TC-M04-001 打开图片
- **操作步骤**：从 Album 点击一张 JPG 进入 Editor
- **预期结果**：
  - EditorScreen 加载，显示图片名在 TopBar
  - 预览区域显示图片
  - ColorSpaceIndicator 显示正确色彩空间
- **验收标准**：等级 A

#### TC-M04-002 打开 RAW 文件
- **操作步骤**：从 Album 点击一张 DNG/ARW 进入 Editor
- **预期结果**：
  - RAW 解码成功，显示预览
  - RAW 面板可用
  - 元数据（相机型号、镜头信息）正确显示
- **验收标准**：等级 A

#### TC-M04-003 Tone 调整
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

#### TC-M04-004 Look 调整
- **操作步骤**：
  1. 切换到 Look Tab
  2. 调整 Saturation, Vibrance
  3. 调整 HLS Profile
  4. 在 ColorWheelView 调整
- **预期结果**：预览颜色正确变化
- **验收标准**：等级 A

#### TC-M04-005 Display 面板
- **操作步骤**：
  1. 切换到 Display Tab
  2. 更改 Display Transform
  3. 观察 WaveformScope
- **预期结果**：
  - DisplayTransform 切换正确
  - WaveformScope 实时渲染图像波形
- **验收标准**：等级 A

#### TC-M04-006 Geometry 调整
- **操作步骤**：
  1. 切换到 Geometry Tab
  2. 旋转 +90°
  3. 调整 Perspective H/V
- **预期结果**：
  - 预览正确旋转/透视变换
  - 边界无黑边溢出
- **验收标准**：等级 A

#### TC-M04-007 RAW 解码设置
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

#### TC-M04-008 Undo/Redo
- **操作步骤**：
  1. 调整 Exposure
  2. 调整 Contrast
  3. 点击 Undo（两次）
  4. 点击 Redo
- **预期结果**：
  - Undo 回退到上一步参数
  - Redo 恢复参数
  - Undo 按钮在无可撤销时禁用
- **验收标准**：等级 A

#### TC-M04-009 版本管理
- **操作步骤**：
  1. 点击版本菜单 (Layers 图标)
  2. 创建 Virtual Copy
  3. 在新版本上调整不同参数
  4. 切换回原版本
  5. 删除一个版本
- **预期结果**：
  - 版本列表正确更新
  - 切换版本后参数和预览正确
  - 删除后版本从列表消失
- **验收标准**：等级 A

#### TC-M04-010 前后对比
- **操作步骤**：
  1. 调整参数后点击 Compare 按钮
  2. 再次点击关闭对比
- **预期结果**：
  - 显示左右/滑块对比视图
  - Before 为原始图，After 为调整后
  - Compare 按钮高亮态正确
- **验收标准**：等级 A

#### TC-M04-011 Mask 面板
- **操作步骤**：
  1. 从 Overflow 菜单打开 Masks
  2. 依次添加 Brush/Radial/Linear/Luminance Mask
  3. 切换 Mask 启用/禁用
  4. 删除一个 Mask
- **预期结果**：
  - Mask 列表正确显示
  - 添加后预览更新（如果有绘制）
  - 删除后从列表移除
- **验收标准**：等级 A

#### TC-M04-012 Preset 应用与保存
- **操作步骤**：
  1. 打开 Presets 面板
  2. 应用一个内置 Preset
  3. 调整参数后 Save Current As Preset
  4. 收藏/取消收藏 Preset
- **预期结果**：
  - 应用 Preset 后参数批量更新
  - 保存的 Preset 出现在列表
  - 收藏态正确显示
- **验收标准**：等级 A

#### TC-M04-013 未保存更改提示
- **操作步骤**：
  1. 调整参数使 `dirty=true`
  2. 按系统返回键
- **预期结果**：弹出确认对话框 "Unsaved changes"，提供 Discard 和 Cancel
- **验收标准**：等级 A

#### TC-M04-014 导出跳转
- **操作步骤**：点击导出按钮 (Output 图标)
- **预期结果**：导航到 ExportScreen，携带当前 imageId
- **验收标准**：等级 A

#### TC-M04-015 缩放与平移
- **操作步骤**：在预览区域双指缩放、单指平移
- **预期结果**：
  - 缩放比例限制在合理范围 (min/max)
  - 平移不超出图片边界
  - 渲染指示器在重渲染时显示
- **验收标准**：等级 A

---

### M05 AI 智能模块

#### TC-M05-001 AI Hub 导航
- **操作步骤**：点击 AI Tab，依次点击 Search / Rating / Models 卡片
- **预期结果**：正确导航到对应子页面
- **验收标准**：等级 A

#### TC-M05-002 语义搜索 - 正常查询
- **前置条件**：已下载 CLIP/SigLIP 模型
- **操作步骤**：
  1. 进入 AI Search
  2. 输入 "sunset at the beach"
  3. 点击 Search
- **预期结果**：
  - ModelStatusBar 显示 "Ready"
  - 显示进度指示
  - 结果按相关性排序，显示分数和匹配标签
  - 点击结果可打开 Editor
- **验收标准**：等级 A

#### TC-M05-003 语义搜索 - 建议与历史
- **操作步骤**：
  1. 进入 AI Search，不输入任何内容
  2. 点击一个 Suggestion chip
  3. 再次进入，查看 Recent Searches
  4. 点击 Clear Recent Searches
- **预期结果**：
  - 显示 Suggestions 列表
  - 点击 Suggestion 触发搜索
  - Recent Searches 正确记录
  - Clear 后清空
- **验收标准**：等级 A

#### TC-M05-004 语义搜索 - 模型未就绪
- **前置条件**：未下载模型
- **操作步骤**：进入 AI Search
- **预期结果**：
  - ModelStatusBar 显示 "Not downloaded"（红色点）
  - 点击设置图标可跳转到模型管理
- **验收标准**：等级 C（降级提示正确）

#### TC-M05-005 AI 评分 - 批量分析
- **前置条件**：相册中有 ≥5 张图片，已配置 LLM
- **操作步骤**：
  1. 进入 AI Rating
  2. 选择 LLM Provider
  3. 调整 Strictness 滑块
  4. 点击 Analyze Images
- **预期结果**：
  - 显示批量进度条
  - 分析完成后显示 TOP 结果列表
  - 每张图片显示综合分数、质量标签
- **验收标准**：等级 A

#### TC-M05-006 AI 评分 - 应用到 EXIF
- **操作步骤**：分析完成后，点击 "Apply ratings to EXIF"，确认
- **预期结果**：
  - 评分写入图片 EXIF
  - 完成后显示提示
- **验收标准**：等级 A

#### TC-M05-007 AI 模型管理 - 下载/删除
- **操作步骤**：
  1. 进入 AI Model Manager
  2. 下载一个未下载的模型
  3. 观察下载进度
  4. 下载完成后删除该模型
- **预期结果**：
  - 下载进度条正确更新
  - 下载完成后状态变为 "Downloaded"
  - 删除后状态恢复为未下载
  - 存储统计正确更新
- **验收标准**：等级 A

#### TC-M05-008 AI 模型管理 - 设为默认
- **操作步骤**：对一个已下载非默认模型点击 "Set as default"
- **预期结果**：
  - 该模型显示 ★ 标记
  - 原默认模型取消 ★
- **验收标准**：等级 A

---

### M06 导出模块

#### TC-M06-001 单张导出 - JPEG
- **操作步骤**：
  1. Editor 中点击导出
  2. 格式选 JPEG，质量 95
  3. 色彩空间 sRGB
  4. 点击 Export
- **预期结果**：
  - 导出进度显示
  - 导出完成后显示成功标记 ✓
  - 文件存在于输出目录
  - 文件大小合理，可正常打开
- **验收标准**：等级 A

#### TC-M06-002 单张导出 - PNG 16-bit
- **操作步骤**：格式选 PNG，位深 16-bit
- **预期结果**：导出成功，文件为 16-bit PNG
- **验收标准**：等级 A

#### TC-M06-003 UltraHDR 导出
- **前置条件**：格式为 JPEG，设备支持 UltraHDR
- **操作步骤**：开启 UltraHDR 开关，导出
- **预期结果**：生成 UltraHDR JPEG，在支持设备上显示 HDR 效果
- **验收标准**：等级 B（依赖设备能力）

#### TC-M06-004 批量导出
- **操作步骤**：Album 中选择 5 张图片，点击导出
- **预期结果**：
  - 显示批量进度 (completed/total)
  - 每张图片独立结果显示 ✓/✕
  - 导出过程中可取消
  - 取消后已导出文件保留，未导出任务终止
- **验收标准**：等级 A

#### TC-M06-005 导出尺寸调整
- **操作步骤**：
  1. 开启 Maintain Aspect Ratio
  2. 设置 Max Dimension = 2048
  3. 导出
- **预期结果**：输出图片长边为 2048，另一边等比缩放
- **验收标准**：等级 A

#### TC-M06-006 元数据处理
- **操作步骤**：
  1. 选择 Metadata Mode: Strip All
  2. 导出
  3. 使用第三方工具检查 EXIF
- **预期结果**：导出的图片不含 EXIF/GPS 信息
- **验收标准**：等级 A

#### TC-M06-007 水印导出
- **操作步骤**：
  1. 在 Editor Watermark 面板配置水印
  2. 导出时开启 Include Watermark
- **预期结果**：导出图片包含水印
- **验收标准**：等级 A

#### TC-M06-008 导出后分享
- **操作步骤**：导出成功后点击 Share 按钮
- **预期结果**：唤起系统分享面板，文件可被其他应用接收
- **验收标准**：等级 A

#### TC-M06-009 导出取消与内存安全
- **操作步骤**：
  1. 批量导出 20 张大图
  2. 中途点击 Cancel
- **预期结果**：
  - 取消后无内存泄漏
  - Bitmap 被正确回收
  - 不触发 OOM
- **验收标准**：等级 A

---

### M07 设置模块

#### TC-M07-001 语言切换
- **操作步骤**：Settings → General → 切换 Language
- **预期结果**：应用内所有文本实时切换为所选语言
- **验收标准**：等级 A

#### TC-M07-002 主题切换
- **操作步骤**：切换 Dark / Light / System
- **预期结果**：UI 主题实时变化
- **验收标准**：等级 A

#### TC-M07-003 GPU 后端切换
- **操作步骤**：Settings → Editor → 切换 Vulkan / CPU
- **预期结果**：
  - 切换后 Editor 中渲染使用对应后端
  - Native 层正确响应
- **验收标准**：等级 A

#### TC-M07-004 AI 配置
- **操作步骤**：
  1. 输入 API Key, Endpoint, Model
  2. 调整 Strictness 滑块
  3. 切换 Cloud LLM / On-Device AI
- **预期结果**：
  - 配置持久化
  - AI 功能使用新配置
- **验收标准**：等级 A

#### TC-M07-005 缓存清理
- **操作步骤**：Settings → Storage → Clear Cache
- **预期结果**：
  - 缓存大小显示更新为 0 或接近 0
  - 应用不崩溃
- **验收标准**：等级 A

#### TC-M07-006 隐私设置
- **操作步骤**：
  1. 关闭 Analytics
  2. 关闭 Crash Reports
- **预期结果**：
  - CrashReportService 不再上传
  - 设置持久化
- **验收标准**：等级 A

#### TC-M07-007 诊断信息
- **操作步骤**：查看 Diagnostics 区域
- **预期结果**：
  - Native Version 显示正确版本号
  - GPU Available 与设备能力一致
- **验收标准**：等级 A

---

### M08 权限管理

#### TC-M08-001 首次启动权限请求
- **前置条件**：全新安装，清除所有权限
- **操作步骤**：启动应用
- **预期结果**：
  - 弹出媒体读取权限请求
  - API 33+ 同时请求通知权限
- **验收标准**：等级 A

#### TC-M08-002 权限授予后功能
- **操作步骤**：授予所有权限
- **预期结果**：Album 正常读取系统相册图片
- **验收标准**：等级 A

#### TC-M08-003 权限拒绝降级
- **操作步骤**：拒绝媒体权限
- **预期结果**：
  - Album 显示空状态或引导用户授权
  - 应用不崩溃
- **验收标准**：等级 C

#### TC-M08-004 永久拒绝处理
- **操作步骤**：
  1. 拒绝权限并勾选 "Don't ask again"
  2. 再次尝试需要权限的操作
- **预期结果**：
  - 显示引导去系统设置授权的对话框
  - 点击可跳转系统设置页
- **验收标准**：等级 A

#### TC-M08-005 API 29 及以下写入权限
- **前置条件**：Android 10 及以下设备
- **操作步骤**：执行导出到共享存储
- **预期结果**：
  - 请求 WRITE_EXTERNAL_STORAGE
  - 授予后导出成功到 DCIM/等目录
- **验收标准**：等级 A

#### TC-M08-006 API 33+ 媒体权限
- **前置条件**：Android 13+ 设备
- **操作步骤**：启动应用
- **预期结果**：请求 READ_MEDIA_IMAGES + READ_MEDIA_VIDEO，不请求 WRITE_EXTERNAL_STORAGE
- **验收标准**：等级 A

---

### M09 NDK/Native 层

#### TC-M09-001 Native Init/Shutdown
- **操作步骤**：
  1. 启动应用，检查 `Bridge.nativeInit()` 返回 true
  2. 退出应用，检查 `Bridge.nativeShutdown()` 不崩溃
- **预期结果**：生命周期正常
- **验收标准**：等级 A

#### TC-M09-002 Vulkan 可用性检测
- **操作步骤**：调用 `Pipeline.nativeIsVulkanAvailable()`
- **预期结果**：
  - 支持 Vulkan 设备返回 true
  - 不支持返回 false
- **验收标准**：等级 A

#### TC-M09-003 图片加载与释放
- **操作步骤**：
  1. `Image.nativeLoadImage(uri)` 返回有效 ID (>0)
  2. `Image.nativeGetImageInfo(id)` 返回正确 JSON
  3. `Image.nativeRemoveImage(id)` 成功
- **预期结果**：
  - 加载后内存增加，释放后内存回落
  - 重复加载/释放 100 次无内存泄漏
- **验收标准**：等级 A

#### TC-M09-004 RAW 解码
- **操作步骤**：
  1. `Raw.nativeDecodeRaw(path, maxDim)` 对 DNG/ARW/NEF 执行
  2. `Raw.nativeGetRawMetadata(imageId)` 获取元数据
- **预期结果**：
  - 解码成功返回有效 ID
  - 元数据包含相机型号、ISO、快门、光圈
- **验收标准**：等级 A

#### TC-M09-005 Pipeline 执行
- **操作步骤**：
  1. 加载图片获取 imageId
  2. `Pipeline.nativeExecute(imageId, paramJson)` 传入调整参数
  3. 获取渲染结果
- **预期结果**：
  - 返回有效结果 ID
  - 渲染结果与参数预期一致
- **验收标准**：等级 A

#### TC-M09-006 项目数据库操作
- **操作步骤**：
  1. `Bridge.nativeOpenProject(dbPath)`
  2. `Sleeve.nativeCreateFolder("/", "Test")`
  3. `Sleeve.nativeListFolder("/")`
  4. `Sleeve.nativeDeleteElement("/Test")`
  5. `Bridge.nativeCloseProject()`
- **预期结果**：
  - 文件夹创建成功
  - 列表包含新文件夹
  - 删除后列表不再包含
- **验收标准**：等级 A

#### TC-M09-007 缩略图生成
- **操作步骤**：
  1. `Thumbnail.nativeGenerateThumbnail(imageId, 256)`
  2. `Thumbnail.nativeGetThumbnailBytes(imageId)`
- **预期结果**：返回非空字节数组，可解码为 Bitmap
- **验收标准**：等级 A

#### TC-M09-008 Scope 分析
- **操作步骤**：
  1. `Scope.nativeSubmitFrame(imageId, type, bins, w, h)`
  2. `Scope.nativeGetHistogram()` / `Scope.nativeGetWaveform()` / `Scope.nativeGetVectorscope()`
- **预期结果**：返回非空数组，数据范围合理
- **验收标准**：等级 A

#### TC-M09-009 编辑历史
- **操作步骤**：
  1. `History.nativeCreateVersion(imageId, "v1")`
  2. `History.nativeGetHistoryJson(imageId)`
- **预期结果**：
  - 版本创建成功
  - JSON 历史树结构正确
- **验收标准**：等级 A

#### TC-M09-010 JNI 符号完整性
- **操作步骤**：运行 `nm -D libalcedo_native.so | grep Java_com_alcedo`
- **预期结果**：
  - 所有 Kotlin `external` 方法在 so 中均有对应符号
  - 无未定义符号
- **验收标准**：等级 A（阻塞性）

---

### M10 后台任务

#### TC-M10-001 导入后台任务
- **操作步骤**：导入 50 张图片，切换应用到后台
- **预期结果**：
  - 通知栏显示前台服务通知（如果开启了通知权限）
  - 后台任务继续执行
  - 完成后应用内显示结果
- **验收标准**：等级 A

#### TC-M10-002 任务取消
- **操作步骤**：
  1. 启动批量导出
  2. 在后台任务栏点击 Cancel
- **预期结果**：任务终止，不再消耗资源
- **验收标准**：等级 A

#### TC-M10-003 多任务并发
- **操作步骤**：同时启动导入和导出任务
- **预期结果**：
  - 两个任务并行显示进度
  - 不互相干扰
  - 不触发 ANR
- **验收标准**：等级 A

---

### M11 崩溃报告

#### TC-M11-001 崩溃捕获
- **前置条件**：开启 Crash Report
- **操作步骤**：通过开发者选项触发一次测试崩溃
- **预期结果**：
  - 崩溃后重启应用
  - CrashReportService 写入日志到磁盘
  - 若开启遥测，日志上传服务端
- **验收标准**：等级 B（非阻塞）

#### TC-M11-002 隐私开关控制
- **操作步骤**：在设置中关闭 Crash Reports，触发崩溃
- **预期结果**：崩溃日志不写入或不上传
- **验收标准**：等级 B

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
- **验收标准**：等级 A

#### TC-M12-002 数据迁移/升级
- **前置条件**：安装旧版本，创建数据，升级到 v1.6.11
- **操作步骤**：启动新版本
- **预期结果**：旧数据完整保留，无丢失
- **验收标准**：等级 A

---

### M13 性能与稳定性

#### TC-M13-001 大图片压力测试
- **前置条件**：准备 100MB+ TIFF 或 60MP RAW
- **操作步骤**：
  1. 导入并打开
  2. 连续调整参数 20 次
  3. 导出全分辨率
- **预期结果**：
  - 不触发 OOM
  - 不 ANR
  - 导出时间可接受（< 60s）
- **验收标准**：等级 A

#### TC-M13-002 批量操作压力测试
- **操作步骤**：选择 100 张图片，批量应用 Preset，批量导出
- **预期结果**：
  - 批量编辑不 ANR
  - 批量导出内存曲线平稳，无持续增长
  - 完成后内存回到基线
- **验收标准**：等级 A

#### TC-M13-003 长时间运行稳定性
- **操作步骤**：应用保持前台运行 2 小时，期间频繁切换功能
- **预期结果**：
  - 无内存泄漏（堆增长 < 20%）
  - 无崩溃
  - 响应速度无明显下降
- **验收标准**：等级 A

#### TC-M13-004 快速操作压力
- **操作步骤**：在 Editor 中快速连续切换 Tab、调整滑块、Undo/Redo
- **预期结果**：
  - 不触发渲染队列溢出
  - 最终状态与最后一次操作一致
  - 无闪退
- **验收标准**：等级 A

#### TC-M13-005 低内存设备测试
- **前置条件**：模拟 2GB RAM 设备或限制内存
- **操作步骤**：执行常规编辑和导出流程
- **预期结果**：
  - 应用在低内存下 graceful degradation
  - 可能降低预览分辨率，但不崩溃
- **验收标准**：等级 C

---

## 四、覆盖率矩阵

### 4.1 功能覆盖率

| 模块 | 用例数 | P0 | P1 | 覆盖功能点 | 覆盖率 |
|-----|-------|----|----|----------|-------|
| M01 启动与生命周期 | 5 | 5 | 0 | 冷启动、Intent、降级、内存、恢复 | 100% |
| M02 导航与UI框架 | 4 | 4 | 0 | 底部栏、Rail、返回、状态保存 | 100% |
| M03 相册管理 | 9 | 9 | 0 | 导入、文件夹、搜索、排序、视图、选择、菜单、空态、任务 | 100% |
| M04 图片编辑器 | 15 | 15 | 0 | 打开JPG/RAW、5大面板、Undo/Redo、版本、对比、Mask、Preset、未保存提示、导出跳转、缩放 | 100% |
| M05 AI 模块 | 8 | 8 | 0 | Hub、搜索正常/建议/未就绪、评分/应用、模型下载/删除/默认 | 100% |
| M06 导出模块 | 9 | 9 | 0 | 单张JPEG/PNG、UltraHDR、批量、尺寸、元数据、水印、分享、取消 | 100% |
| M07 设置模块 | 7 | 0 | 7 | 语言、主题、GPU、AI配置、缓存、隐私、诊断 | 100% |
| M08 权限管理 | 6 | 6 | 0 | 首次请求、授予、拒绝、永久拒绝、API29写入、API33媒体 | 100% |
| M09 NDK/Native | 10 | 10 | 0 | Init/Shutdown、Vulkan、图片加载释放、RAW、Pipeline、项目DB、缩略图、Scope、历史、JNI符号 | 100% |
| M10 后台任务 | 3 | 0 | 3 | 导入后台、取消、并发 | 100% |
| M11 崩溃报告 | 2 | 0 | 2 | 捕获、隐私开关 | 100% |
| M12 数据存储 | 2 | 0 | 2 | 读写、升级迁移 | 100% |
| M13 性能稳定性 | 5 | 5 | 0 | 大图压力、批量压力、长时间、快速操作、低内存 | 100% |
| **合计** | **85** | **71** | **14** | **全部功能点** | **100%** |

### 4.2 代码覆盖率目标

| 层级 | 目标覆盖率 | 验证方式 |
|-----|-----------|---------|
| Kotlin UI / ViewModel | ≥ 80% | Jacoco / Android Studio Coverage |
| Kotlin Service / Repository | ≥ 85% | Jacoco / 单元测试 |
| Kotlin NDK Bridge | ≥ 90% | 单元测试 + 集成测试 |
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

---

## 五、测试执行检查清单

### 发布前必须通过项（Blockers）

- [ ] TC-M01-001 冷启动通过，无 ANR
- [ ] TC-M01-003 Native 降级路径不崩溃
- [ ] TC-M03-001 导入功能正常
- [ ] TC-M04-001/002 Editor 打开 JPG/RAW 正常
- [ ] TC-M04-008 Undo/Redo 正确
- [ ] TC-M04-013 未保存更改提示正确
- [ ] TC-M06-001/004 单张/批量导出成功
- [ ] TC-M06-009 导出取消无 OOM
- [ ] TC-M08-001/002 权限请求与授予正常
- [ ] TC-M09-010 JNI 符号 100% 匹配（无 UnsatisfiedLinkError）
- [ ] TC-M13-001/002 大图和批量压力不崩溃、不 OOM
- [ ] TC-M13-003 2小时稳定性无内存泄漏

### 已知限制与降级（Acceptable）

- UltraHDR 导出在部分旧设备上不可用（TC-M06-003 等级 B/C）
- AI 语义搜索需先下载模型（TC-M05-004 等级 C）
- 低内存设备可能降低预览分辨率（TC-M13-005 等级 C）

---

## 六、缺陷分级与处理流程

| 级别 | 定义 | 处理时限 | 是否阻塞发布 |
|-----|------|---------|------------|
| **Critical** | 崩溃、数据丢失、安全漏洞、完全无法使用 | 24h | 是 |
| **Major** | 核心功能异常、性能严重下降、权限失效 | 48h | 是 |
| **Minor** | UI 瑕疵、非核心功能异常、文案错误 | 1周内 | 否 |
| **Trivial** | 纯视觉微调、日志级别问题 | 下次迭代 | 否 |

---

*文档版本：v1.6.11*
*生成日期：2026-07-29*
*适用范围：Release v1.6.11 全面验收测试*
