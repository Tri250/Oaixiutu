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
| **核心图像管线 (Pipeline)** | 完整，含工厂/注册机制 | PipelineService手动调度，无工厂 | ~65% |
| **编辑操作符 (Operators)** | 40+ 操作符，CPU/GPU双路径 | 19个基础操作符，仅CPU | ~50% |
| **RAW解码 (RAW Decode)** | LibRaw + 自研处理器，多算法 | 基础实现，LibRaw未集成 | ~55% |
| **元数据/缩略图 (Metadata)** | Exiv2 + OpenImageIO | 自研简化版 + metadata-extractor fallback | ~60% |
| **色彩科学 (Color Science)** | ACES 2.0 / OpenDRT / OCIO | ACES + OpenDRT基础移植 | ~70% |
| **资产管理 (Sleeve)** | 完整inode式文件系统 | 仅基础Element/File/Folder | ~30% |
| **数据存储 (Storage)** | DuckDB + 多层Mapper/Service | Room简化替代 | ~35% |
| **应用服务 (App Services)** | 25个完整服务 | Kotlin层有接口，C++层缺失多 | ~40% |
| **GPU计算后端** | CUDA / Metal / OpenCL | GLES Compute / Vulkan | ~45% |
| **AI功能** | Sidecar + 云端 + 本地 | CLIP + ONNX Runtime本地推理 | ~50% |
| **UI界面** | Qt/QML，专业级界面 | Compose框架，主要界面已覆盖 | ~60% |
| **导出/渲染** | 完整导出队列，多格式 | 基础导出实现 | ~40% |
| **历史/版本控制** | Git式分支历史 | 基础History模型 | ~35% |
| **搜索/筛选** | 高级EXIF筛选 + 语义搜索 | 基础筛选 + AI搜索框架 | ~45% |
| **工具/基础设施** | LRU缓存、线程池、SIMD等 | 部分缺失 | ~30% |

---

## 三、详细差异清单

### 3.1 编辑操作符层 (Edit Operators)

#### 已移植到Android (19个)
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

#### 桌面端有但Android缺失 (16个+)
| 操作符 | 说明 | 优先级 |
|--------|------|--------|
| black_op | 黑电平调整 | 高 |
| white_op | 白电平调整 | 高 |
| shadow_op | 独立阴影调整 | 高 |
| highlight_op | 独立高光调整 | 高 |
| crop_rotate_op | 裁剪与旋转 | 高 |
| resize_op | 输出缩放/重采样 | 高 |
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
- **Android端**: PipelineService手动include各操作符.cpp文件，无工厂/注册机制

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
├── sleeve_element.cpp   (空壳)
├── sleeve_file.cpp      (空壳)
└── sleeve_folder.cpp    (空壳)
```
**缺失**: sleeve_base, sleeve_filesystem, sleeve_manager, sleeve_view, storage_service, dentry_cache_manager, path_resolver, element_factory, 全部filter系统

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

#### Android端 (Room简化)
```
data/local/
├── SleeveDatabase.kt
├── SleeveDao.kt
├── DentryCacheManager.kt   (简化版)
├── PathResolver.kt         (简化版)
└── ThumbnailDiskCache.kt
```
**缺失**: 全部mapper层、controller层、service层C++实现、image_pool_manager、pipeline_mapper、history_mapper、semantic/ai controller

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
1. **sleeve_base / sleeve_manager / sleeve_view** - 资产管理系统核心
2. **sleeve_filesystem / storage_service** - 虚拟文件系统与存储
3. **db_controller / 多层mapper** - 数据持久化与ORM
4. **image_pool_manager** - 图像内存池管理
5. **raw_processor** - RAW处理核心封装

### P1 - 高优先级 (影响核心功能)
6. **black_op / white_op / shadow_op / highlight_op** - 独立影调操作符
7. **crop_rotate_op** - 裁剪旋转
8. **resize_op** - 输出缩放
9. **operator_factory + registration** - 操作符工厂与注册机制
10. **path_resolver (完整)** - 路径解析
11. **dentry_cache_manager** - 目录缓存
12. **sleeve_filter系统** - 高级筛选

### P2 - 中优先级 (提升体验)
13. **render_service** - 渲染服务
14. **ai_sidecar_runtime_service** - AI Sidecar
15. **image_analysis_encoder/service** - 图像分析
16. **model_asset_catalog** - 模型资产管理
17. **lens_calib_runtime** - 镜头校准数据库
18. **history_mapper / history_service** - 历史版本完整实现
19. **pipeline_mapper / service** - 管线持久化
20. **LRU Cache / Thread Pool / ID Generator** - 基础设施

### P3 - 低优先级 (锦上添花)
21. **OCIO配置集成** - 色彩管理配置
22. **普朗克轨迹表** - 精确色温
23. **SIMD优化** - ARM NEON加速
24. **Profiler** - 性能分析
25. **OpenCL后端** - 额外GPU后端
26. **UI细节**: ScopePanel, VersioningPanel, LUT Browser

---

## 五、移植策略建议

1. **第一阶段**: 补齐Sleeve核心 + 存储层 (使资产管理可用)
2. **第二阶段**: 补齐缺失操作符 + 操作符工厂 (使编辑功能完整)
3. **第三阶段**: 补齐App Service C++层 + 基础设施 (使架构对齐)
4. **第四阶段**: GPU优化 + UI细节完善 (提升体验)
