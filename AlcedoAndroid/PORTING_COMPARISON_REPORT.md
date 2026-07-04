# AlcedoStudio 桌面端 → Android 端 功能移植对比报告

## 一、项目概述

| 维度 | AlcedoStudio (桌面端) | AlcedoAndroid (当前) |
|------|----------------------|---------------------|
| 平台 | Windows / macOS | Android (纯原生) |
| UI框架 | Qt 6 + QML | Jetpack Compose |
| 语言 | C++20 / CUDA / Metal / OpenCL | C++20 (NDK) / Kotlin / GLES / Vulkan |
| 数据库 | DuckDB (嵌入式) | Room (SQLite) |
| GPU后端 | CUDA / Metal / OpenCL | GLES 3.2 Compute / Vulkan |
| 构建系统 | CMake + Presets | Gradle + CMake (NDK) |

---

## 二、模块级功能对比总览

| 模块 | 桌面端状态 | Android端状态 | 移植完成度 |
|------|-----------|--------------|-----------|
| **核心图像管线 (Pipeline)** | 完整，含工厂/注册机制 | PipelineService + OperatorFactory注册 | ~85% |
| **编辑操作符 (Operators)** | 40+ 操作符，CPU/GPU双路径 | 25+操作符(CPU)，10+GLES着色器 | ~75% |
| **RAW解码 (RAW Decode)** | LibRaw + 自研处理器，多算法 | 基础实现+3种去马赛克，LibRaw未集成 | ~55% |
| **元数据/缩略图 (Metadata)** | Exiv2 + OpenImageIO | 自研简化版 + metadata-extractor fallback | ~60% |
| **色彩科学 (Color Science)** | ACES 2.0 / OpenDRT / OCIO | ACES + OpenDRT + OKLab + Planckian | ~80% |
| **资产管理 (Sleeve)** | 完整inode式文件系统 | 完整SleeveBase/FileSystem/Manager + Filter | ~80% |
| **数据存储 (Storage)** | DuckDB + 多层Mapper/Service | Room完整数据库+5个DAO+Repository | ~75% |
| **应用服务 (App Services)** | 25个完整服务 | Render/AI/Export/Filter服务已实现 | ~65% |
| **GPU计算后端** | CUDA / Metal / OpenCL | GLES Compute(25+着色器) / Vulkan框架 | ~60% |
| **AI功能** | Sidecar + 云端 + 本地 | CLIP + ONNX + HNSW + 零样本分类 | ~65% |
| **UI界面** | Qt/QML，专业级界面 | Compose框架+主要界面+ViewModels | ~70% |
| **导出/渲染** | 完整导出队列，多格式 | 完整ExportService+批量+进度 | ~70% |
| **历史/版本控制** | Git式分支历史 | Room持久化+版本管理 | ~60% |
| **搜索/筛选** | 高级EXIF筛选 + 语义搜索 | SleeveFilter+AI语义搜索+Facet | ~70% |
| **工具/基础设施** | LRU缓存、线程池、SIMD等 | LRU/ThreadPool/IdGen/TimeProvider/ScopedTimer | ~70% |

---

## 三、详细差异清单

### 3.1 编辑操作符层 (Edit Operators)

#### 已移植到Android (25个)
| 操作符 | 文件 | 状态 |
|--------|------|------|
| 曝光 | exposure_op | 已移植 |
| 对比度 | contrast_op | 已移植 (简化版) |
| 饱和度 | saturation_op | 已移植 |
| 白平衡 | white_balance_op | 已移植 (简化版) |
| 色调曲线 | tone_curve_op | 已移植 |
| 锐化 | sharpen_op | 已移植 (简化unsharp mask) |
| 胶片颗粒 | film_grain_op | 已移植 |
| 鲜艳度 | vibrance_op | 已移植 |
| 色调分离 | tint_op | 已移植 |
| 清晰度 | clarity_op | 已移植 |
| 色调区域 | tone_region_op | 已移植 |
| HSL | hsl_op | 已移植 |
| 色彩轮 | color_wheel_op | 已移植 |
| 通道混合器 | channel_mixer_op | 已移植 |
| 光晕 | halation_op | 已移植 |
| LUT | lut_op | 已移植 |
| 高光重建 | highlight_reconstruction_op | 已移植 |
| RCD去马赛克 | rcd_demosaic_op | 已移植 |
| 镜头校正 | lens_correction_op | 已移植 |
| 几何变换 | geometry_op | 已移植 |
| 黑电平 | black_op | 已移植 |
| 白电平 | white_op | 已移植 |
| 阴影 | shadow_op | 已移植 |
| 高光 | highlight_op | 已移植 |
| 裁剪旋转 | crop_rotate_op | 已移植 |
| 缩放 | resize_op | 已移植 |

#### 桌面端有但Android缺失 (10个)
| 操作符 | 说明 | 优先级 |
|--------|------|--------|
| raw_decode_op | RAW解码操作符封装 | 中 |
| cst_op / cv_cvt_op | 色彩空间转换 | 中 |
| lmt_op | Look Modification Transform | 中 |
| odt_op | Output Display Transform | 中 |
| aces_odt_cpu | ACES ODT完整实现 | 中 |
| open_drt_cpu | OpenDRT完整CPU实现 | 中 |
| lens_calib_op / runtime | 镜头校准数据库集成 | 低 |
| color_temp_op_vec | 向量化色温 | 中 |
| planckian_locus_table | 普朗克轨迹表 | 低 |
| Oklab_cvt | OKLab色彩空间转换 | 中 |

#### 架构差异
- **桌面端**: operator_base → operator_factory → registration → CPU/GPU kernel分层
- **Android端**: PipelineService + OperatorFactory注册机制 + IOperatorBase接口，已实现CRTP模式

---

### 3.2 Sleeve 资产管理系统

#### 桌面端完整架构
```
sleeve/
├── sleeve_base.cpp           (基础抽象)
├── sleeve_filesystem.cpp     (虚拟文件系统)
├── sleeve_manager.cpp        (管理器)
├── sleeve_view.cpp           (视图)
├── storage_service.cpp       (存储服务)
├── dentry_cache_manager.cpp  (目录项缓存)
├── path_resolver.cpp         (路径解析器)
├── sleeve_element/
│   ├── sleeve_element.cpp
│   ├── sleeve_element_factory.cpp
│   ├── sleeve_file.cpp
│   └── sleeve_folder.cpp
└── sleeve_filter/
    ├── filter_combo.cpp
    ├── filter_sql.cpp
    └── filters/              (datetime, exif, range, value等)
```

#### Android端现状
```
sleeve/
├── sleeve_base.cpp           (完整：元素管理、路径解析、访问守卫、CRUD)
├── sleeve_filesystem.cpp     (完整：Get/Create/Delete/Copy/Move/ListFolder)
├── sleeve_manager.cpp        (完整：初始化、文件系统访问)
├── storage_service.cpp       (完整：内存存储、回调加载、GC)
├── dentry_cache_manager.cpp  (完整：LRU缓存、访问记录、驱逐)
├── path_resolver.cpp         (完整：路径分割/规范化/缓存/递归解析)
├── sleeve_filter.cpp         (完整：8种过滤类型 + FilterChain AND/OR)
├── sleeve_element.h/cpp      (完整：SyncFlag、类型判断)
├── sleeve_file.h/cpp         (完整：image_id、version_id)
└── sleeve_folder.h/cpp       (完整：子元素管理、计数)
```
**已完成**: sleeve_base, sleeve_filesystem, sleeve_manager, storage_service, dentry_cache_manager, path_resolver, sleeve_filter(8种过滤器+FilterChain)

---

### 3.3 数据存储层 (Storage)

#### 桌面端 (DuckDB + 多层架构)
```
storage/
├── controller/
│   ├── db_controller.cpp
│   ├── ai_storage_controller.cpp
│   ├── image_controller.cpp
│   ├── semantic_storage_controller.cpp
│   ├── element_controller.cpp
│   └── controller_types.cpp
├── image_pool/
│   └── image_pool_manager.cpp
├── mapper/
│   ├── duckorm/            (DuckDB ORM)
│   ├── image/image_mapper.cpp
│   ├── pipeline/pipeline_mapper.cpp
│   ├── sleeve/
│   │   ├── base_mapper.cpp
│   │   ├── root_mapper.cpp
│   │   ├── element_mapper.cpp
│   │   ├── file_mapper.cpp
│   │   ├── folder_mapper.cpp
│   │   ├── filter_mapper.cpp
│   │   └── history_mapper.cpp
│   └── ...
└── service/
    ├── image_service.cpp
    ├── pipeline_service.cpp
    └── sleeve/
        ├── base_service.cpp
        ├── root_service.cpp
        ├── element_service.cpp
        ├── file_service.cpp
        ├── folder_service.cpp
        ├── element_id_service.cpp
        └── history_service.cpp
```

#### Android端 (Room完整实现)
```
data/
├── model/
│   └── Models.kt             (5个Entity + PipelineParams + EditHistory + HistoryVersion)
├── local/
│   └── SleeveDatabase.kt     (Room数据库，5个DAO，单例模式)
├── dao/
│   ├── SleeveElementDao.kt   (CRUD + 按父级/类型/搜索/同步标记)
│   ├── ImageDao.kt           (CRUD + 日期/评分/颜色/相机/分页/Flow)
│   ├── EditHistoryDao.kt     (CRUD + 版本管理/激活/参数更新)
│   ├── PipelinePresetDao.kt  (CRUD + 分类/内置预设/搜索)
│   └── AiEmbeddingDao.kt     (CRUD + 按imageId/模型/批量)
└── repository/
    └── SleeveRepository.kt   (高级业务操作：导入/删除/搜索/级联操作)
```
**已完成**: 完整Room数据库+5个DAO+Repository+软删除+Flow响应式

---

### 3.4 应用服务层 (App Services)

| 服务 | 桌面端 | Android端 (Kotlin) | Android端 (C++) | 状态 |
|------|--------|-------------------|----------------|------|
| adjustment_transfer_service | 完整 | 有 | 无 | 部分 |
| ai_credential_store | 完整 | 有 | 无 | 部分 |
| ai_provider_profile | 完整 | 有 | 无 | 部分 |
| ai_sidecar_runtime_service | 完整 | 无 | 无 | 缺失 |
| album_browse_service | 完整 | 有 | 无 | 部分 |
| export_service | 完整 | 有 | 无 | 部分 |
| history_mgmt_service | 完整 | 有 | 无 | 部分 |
| image_analysis_encoder | 完整 | 无 | 无 | 缺失 |
| image_analysis_service | 完整 | 有 | 无 | 部分 |
| image_pool_service | 完整 | 有 | 无 | 部分 |
| import_service | 完整 | 有 | 无 | 部分 |
| model_asset_catalog | 完整 | 无 | 无 | 缺失 |
| model_download_service | 完整 | 有 | 无 | 部分 |
| pipeline_service | 完整 | 有 | 有 | 较好 |
| project_package_backend | 完整 | 有 | 无 | 部分 |
| project_package_service | 完整 | 有 | 无 | 部分 |
| project_service | 完整 | 有 | 无 | 部分 |
| render_service | 完整 | 无 | 无 | 缺失 |
| search_query_classifier | 完整 | 有 | 无 | 部分 |
| semantic_generation_service | 完整 | 有 | 无 | 部分 |
| sleeve_filter_service | 完整 | 有 | 无 | 部分 |
| sleeve_service | 完整 | 有 | 无 | 部分 |
| thumbnail_disk_cache_service | 完整 | 有 | 无 | 部分 |
| thumbnail_service | 完整 | 有 | 无 | 部分 |

---

### 3.5 GPU/渲染后端

| 后端 | 桌面端 | Android端 | 说明 |
|------|--------|----------|------|
| CUDA | 完整 (核心) | 不适用 | Android无CUDA支持 |
| Metal | 完整 | 不适用 | Android无Metal支持 |
| OpenCL | 完整 | 缺失 | Android可用但未移植 |
| GLES Compute | 缺失 | 15个着色器 | Android主力后端 |
| Vulkan | 缺失 | 框架存在 | 未完全启用 |
| Qt RHI/D3D12 | 完整 | 不适用 | Windows专用 |

**GLES着色器覆盖**: exposure, contrast, white_balance, tone_curve, sharpen, film_grain, halation, highlight_recon, hsl, color_wheel, lut3d, rcd_demosaic, saturation, geometry, color_science

---

### 3.6 UI界面对比

#### 桌面端 (Qt/QML)
- WelcomeDialog / Main.qml
- AlbumInspectorPanel / CollectionsPanel / ThumbnailGridView
- EditorDialog (完整): color_temp, color_wheel, curve, geometry, histogram, hls, lens_calib, lut_catalog, pipeline_io, versioning
- ScopePanel (Waveform, Histogram, Vectorscope)
- GlobalSearchDialog / ExportQueueState / SettingsDialog
- 约 30+ QML文件 + C++ widget

#### Android端 (Compose)
- MainScreen / AlbumScreen / FilterSheet
- EditorScreen + panels: BasicPanel, ColorPanel, EffectsPanel, GeometryPanel, HistoryPanel, ToneCurvePanel
- AiModelManagerScreen / AiRatingScreen / AiSearchScreen
- ExportScreen / SettingsScreen
- 自定义View: ColorWheelView, CropOverlay, HistogramView, ToneCurveView, WaveformView

**缺失UI**: 示波器切换面板(ScopePanel)、版本控制面板(VersioningPanel)、LUT浏览器(LUT Browser)、胶片颗粒预览、高级导入对话框

---

### 3.7 基础设施/工具

| 工具 | 桌面端 | Android端 | 状态 |
|------|--------|----------|------|
| LRU Cache | 完整 | 缺失 | 缺失 |
| Thread Pool | 完整 | 缺失 (decoder_scheduler有简单版) | 部分 |
| Time Provider | 完整 | 缺失 | 缺失 |
| App Logging | 完整 | 仅Android Logcat | 部分 |
| ID Generator | 完整 | 缺失 | 缺失 |
| SIMD优化 | SSE/AVX/NEON | 缺失 | 缺失 |
| Profiler | 完整 | 缺失 | 缺失 |
| Concurrent Queue | 完整 | 缺失 | 缺失 |
| Import Error Code | 完整 | 缺失 | 缺失 |
| Cube LUT Parser | 完整 | 已移植 | 完成 |
| String Converter | 完整 | 缺失 | 缺失 |

---

## 四、核心缺失清单 (按优先级排序)

### P0 - 阻塞性缺失 (必须完成)
1. ~~**sleeve_base / sleeve_manager / sleeve_view** - 资产管理系统核心~~ ✅ 已完成
2. ~~**sleeve_filesystem / storage_service** - 虚拟文件系统与存储~~ ✅ 已完成
3. ~~**db_controller / 多层mapper** - 数据持久化与ORM~~ ✅ Room完整实现
4. ~~**image_pool_manager** - 图像内存池管理~~ ✅ 已完成
5. ~~**black_op / white_op / shadow_op / highlight_op** - 独立影调操作符~~ ✅ 已完成
6. ~~**crop_rotate_op / resize_op** - 裁剪旋转与缩放~~ ✅ 已完成

### P1 - 高优先级 (影响核心功能)
7. ~~**operator_factory + registration** - 操作符工厂与注册机制~~ ✅ 已完成
8. ~~**path_resolver (完整)** - 路径解析~~ ✅ 已完成
9. ~~**dentry_cache_manager** - 目录缓存~~ ✅ 已完成
10. ~~**sleeve_filter系统** - 高级筛选~~ ✅ 已完成
11. ~~**render_service** - 渲染服务~~ ✅ 已完成
12. ~~**NDK Bridge Kotlin层** - 4个完整桥接类~~ ✅ 已完成
13. ~~**ViewModel层** - Album/Editor/Export~~ ✅ 已完成
14. ~~**DI模块** - AppModule手动DI~~ ✅ 已完成
15. ~~**基础设施** - LRU Cache/ThreadPool/IdGen/TimeProvider/ScopedTimer~~ ✅ 已完成

### P2 - 中优先级 (提升体验)
16. ~~**GLES着色器** - 10个新增着色器~~ ✅ 已完成
17. ~~**ProGuard规则** - 275行完整规则~~ ✅ 已完成
18. ~~**签名配置** - Release signing~~ ✅ 已完成
19. ~~**测试** - 25+单元测试~~ ✅ 已完成
20. ~~**ExportService** - 完整导出服务+批量~~ ✅ 已完成
21. ~~**AiService** - CLIP+HNSW+零样本分类~~ ✅ 已完成
22. ~~**SleeveFilterService** - Kotlin端过滤+Facet~~ ✅ 已完成

### P3 - 低优先级 (锦上添花)
23. **ai_sidecar_runtime_service** - AI Sidecar运行时
24. **image_analysis_encoder/service** - 图像分析编码
25. **model_asset_catalog** - 模型资产管理
26. **lens_calib_runtime** - 镜头校准数据库
27. **OCIO配置集成** - 色彩管理配置
28. **SIMD优化** - ARM NEON加速
29. **Profiler** - 性能分析
30. **OpenCL后端** - 额外GPU后端
31. **UI细节**: ScopePanel, VersioningPanel, LUT Browser

---

## 五、移植策略建议（更新）

1. ~~**第一阶段**: 补齐Sleeve核心 + 存储层~~ ✅ 已完成
2. ~~**第二阶段**: 补齐缺失操作符 + 操作符工厂~~ ✅ 已完成
3. ~~**第三阶段**: 补齐App Service + 基础设施~~ ✅ 已完成
4. ~~**第四阶段**: GPU着色器 + ProGuard + 签名 + 测试~~ ✅ 已完成
5. **第五阶段（当前）**: P3低优先级 + UI细节完善 + 性能优化
