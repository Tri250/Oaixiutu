// Static data for Alcedo Studio Android showcase

export type ScreenId =
  | "gallery"
  | "ai"
  | "editor"
  | "color"
  | "film"
  | "export"
  | "settings";

export interface ScreenMeta {
  id: ScreenId;
  name: string;
  shortName: string;
  title: string;
  tagline: string;
  desc: string;
  sourceFiles: string[];
  features: string[];
  techStack: { label: string; tag: "cpp" | "kt" | "gpu" | "data" | "ai" }[];
  metrics: { label: string; value: string }[];
}

export const SCREENS: ScreenMeta[] = [
  {
    id: "gallery",
    name: "相册",
    shortName: "GALLERY",
    title: "智能图库",
    tagline: "Sleeve · 端侧资产管理系统",
    desc: "基于自研 Sleeve inode 式文件系统的极速图库，支持 60+ RAW 格式浏览与 AI 自动标签筛选。",
    sourceFiles: [
      "MainScreen.kt",
      "sleeve_manager.cpp",
      "SleeveFilterService.kt",
      "AlbumViewModel.kt",
      "filter_combo.cpp",
    ],
    features: [
      "AI 自动标签筛选 (100+ 语义类目)",
      "星级评级与色彩标签",
      "海量 RAW 浏览 · 80ms 加载",
      "SQL 语义过滤编译器",
    ],
    techStack: [
      { label: "Sleeve C++", tag: "cpp" },
      { label: "Room + SQLCipher", tag: "data" },
      { label: "metadata-extractor", tag: "kt" },
      { label: "Compose LazyGrid", tag: "kt" },
    ],
    metrics: [
      { label: "加载延迟", value: "80ms" },
      { label: "RAW 格式", value: "60+" },
      { label: "标签类目", value: "100+" },
    ],
  },
  {
    id: "ai",
    name: "AI 搜索",
    shortName: "AI",
    title: "自然语言搜索",
    tagline: "端侧 CLIP + HNSW 向量检索",
    desc: "用日常语言搜索照片，端侧 ONNX Runtime 推理，无需上云，跨图库语义匹配毫秒级响应。",
    sourceFiles: [
      "AiService.kt",
      "AiNdkBridge.kt",
      "clip_inference.cpp",
      "hnsw_index.cpp",
      "embedding_utils.cpp",
      "ai_description.cpp",
    ],
    features: [
      "CLIP 图文联合嵌入 (512/1152 dim)",
      "HNSW 索引近似最近邻检索",
      "零样本分类 (zero-shot)",
      "AI 自动评分 (曝光/对比/饱和)",
    ],
    techStack: [
      { label: "ONNX Runtime", tag: "ai" },
      { label: "CLIP", tag: "ai" },
      { label: "HNSW C++", tag: "cpp" },
      { label: "Coroutines", tag: "kt" },
    ],
    metrics: [
      { label: "检索延迟", value: "~120ms" },
      { label: "嵌入维度", value: "512/1152" },
      { label: "模型", value: "3 个端侧" },
    ],
  },
  {
    id: "editor",
    name: "编辑器",
    shortName: "EDITOR",
    title: "专业照片编辑器",
    tagline: "38 操作符 · GPU 实时管线",
    desc: "实时预览的 RAW 编辑管线，C++20 线程池调度 + GLES Compute 加速，1.5 亿像素仍流畅响应。",
    sourceFiles: [
      "EditorViewModel.kt",
      "RenderService.kt",
      "pipeline_service.cpp",
      "pipeline_scheduler.cpp",
      "operator_factory.cpp",
      "gles_pipeline.cpp",
    ],
    features: [
      "38 个调整操作符",
      "GLES Compute 25 着色器加速",
      "PipelineSnapshot 历史/版本",
      "RGB 直方图实时分析",
    ],
    techStack: [
      { label: "GLES 3.2 Compute", tag: "gpu" },
      { label: "Vulkan", tag: "gpu" },
      { label: "C++20 Thread Pool", tag: "cpp" },
      { label: "Compose Canvas", tag: "kt" },
    ],
    metrics: [
      { label: "操作符", value: "38" },
      { label: "GPU 着色器", value: "25" },
      { label: "目标像素", value: "150MP" },
    ],
  },
  {
    id: "color",
    name: "色彩",
    shortName: "COLOR",
    title: "双引擎色彩科学",
    tagline: "ACES 2.0 · OpenDRT",
    desc: "ACES 与 OpenDRT 双色彩渲染管线切换，从真实还原到艺术化影调全覆盖，11 个内置 ICC 配置文件。",
    sourceFiles: [
      "color_science.cpp",
      "cst_op.cpp",
      "odt_op.cpp",
      "color_wheel_op.cpp",
      "hsl_op.cpp",
      "channel_mixer_op.cpp",
      "tone_curve_op.cpp",
      "tone_region_op.cpp",
    ],
    features: [
      "ACES 2.0 / OpenDRT 双引擎",
      "三色轮分区调色 (阴影/中调/高光)",
      "HSL 八色相 + 通道混合器",
      "OKLab + Planckian 白平衡",
    ],
    techStack: [
      { label: "ACES 2.0", tag: "cpp" },
      { label: "OpenDRT", tag: "cpp" },
      { label: "OKLab", tag: "cpp" },
      { label: "11 ICC 内置", tag: "data" },
    ],
    metrics: [
      { label: "渲染管线", value: "2" },
      { label: "ICC 配置", value: "11" },
      { label: "调色工具", value: "12+" },
    ],
  },
  {
    id: "film",
    name: "胶片",
    shortName: "FILM",
    title: "经典胶片模拟",
    tagline: "真实胶片特性数字复刻",
    desc: "基于真实胶片特性复刻的 LUT 预设，不止色彩，更模拟胶片颗粒、Halation 光晕等物理特性。",
    sourceFiles: [
      "lut_op.cpp",
      "film_grain_op.cpp",
      "halation_op.cpp",
      "lut3d.comp",
      "film_grain.comp",
      "halation.comp",
    ],
    features: [
      "6 款经典胶片 LUT (Portra/Velvia/Tri-X...)",
      "胶片颗粒物理模拟",
      "Halation 高光溢出光晕",
      "LUT 三线性插值 GPU 加速",
    ],
    techStack: [
      { label: "LUT 3D", tag: "cpp" },
      { label: "Film Grain", tag: "gpu" },
      { label: "Halation", tag: "gpu" },
      { label: "Trilinear", tag: "cpp" },
    ],
    metrics: [
      { label: "胶片预设", value: "6" },
      { label: "物理特性", value: "3" },
      { label: "GPU 着色器", value: "3" },
    ],
  },
  {
    id: "export",
    name: "导出",
    shortName: "EXPORT",
    title: "专业导出",
    tagline: "HDR · ICC · Ultra HDR Gain Map",
    desc: "支持 JPEG/PNG/TIFF/DNG/Ultra HDR 多格式批量导出，HDR Gain Map 与 ICC 配置文件嵌入确保跨媒介色彩精准。",
    sourceFiles: [
      "ExportService.kt",
      "ExportViewModel.kt",
      "image_writer.cpp",
      "ultra_hdr_writer.cpp",
      "export_icc_profile_resolver.cpp",
    ],
    features: [
      "Ultra HDR Gain Map 导出",
      "ICC 配置文件嵌入 (11 内置)",
      "4 并发批量导出队列",
      "JPEG/PNG/TIFF/DNG 多格式",
    ],
    techStack: [
      { label: "Ultra HDR", tag: "cpp" },
      { label: "ICC Resolver", tag: "cpp" },
      { label: "StateFlow", tag: "kt" },
      { label: "Reinhard TM", tag: "cpp" },
    ],
    metrics: [
      { label: "导出格式", value: "5" },
      { label: "并发数", value: "4" },
      { label: "色彩空间", value: "3" },
    ],
  },
  {
    id: "settings",
    name: "设置",
    shortName: "SETTINGS",
    title: "系统设置",
    tagline: "端侧推理 · GPU 后端 · 安全",
    desc: "AI 模型管理、GPU 后端切换、镜头校准库、隐私与安全证书锁定一应俱全。",
    sourceFiles: [
      "AlcedoApplication.kt",
      "CertificatePinner.kt",
      "PrivacyManager.kt",
      "SecureHttpClient.kt",
      "TempFileManager.kt",
    ],
    features: [
      "AI 模型下载/激活 (3 模型)",
      "GLES Compute / Vulkan 后端切换",
      "证书锁定 + 加密 HTTP 客户端",
      "60+ 镜头校准 XML 内置",
    ],
    techStack: [
      { label: "CertificatePinner", tag: "kt" },
      { label: "SQLCipher", tag: "data" },
      { label: "LensFun XML", tag: "data" },
      { label: "Privacy", tag: "kt" },
    ],
    metrics: [
      { label: "AI 模型", value: "3" },
      { label: "镜头 XML", value: "60+" },
      { label: "ICC", value: "11" },
    ],
  },
];

export const AI_LABELS: { group: string; items: string[] }[] = [
  {
    group: "场景",
    items: ["人像", "风光", "街拍", "建筑", "微距", "夜景", "运动", "婚礼"],
  },
  {
    group: "主体",
    items: ["海边", "山脉", "森林", "城市", "人", "动物", "食物", "车辆"],
  },
  {
    group: "风格",
    items: ["黄金时刻", "蓝色时刻", "逆光", "剪影", "极简", "对称", "对角线"],
  },
  {
    group: "情绪",
    items: ["温暖", "冷峻", "戏剧", "宁静", "怀旧", "神秘", "浪漫", "活力"],
  },
];

export const SEARCH_PRESETS = [
  "海边日落的人像",
  "秋季金黄的树叶",
  "夜间城市灯光",
  "雨后街道倒影",
  "雪山星空银河",
];

export const SEARCH_RESULTS = [
  { id: 1, sim: 0.94, label: "人像 · 日落", grad: "from-amber-500 to-rose-400" },
  { id: 2, sim: 0.91, label: "海边 · 黄金时刻", grad: "from-orange-400 to-amber-200" },
  { id: 3, sim: 0.88, label: "逆光 · 剪影", grad: "from-rose-500 to-amber-300" },
  { id: 4, sim: 0.85, label: "暖调 · 人像", grad: "from-amber-400 to-yellow-200" },
];

export const GALLERY_THUMBS = [
  { stars: 5, grad: "from-amber-500 via-rose-400 to-orange-300", label: "日落" },
  { stars: 4, grad: "from-arctic-400 to-bench-700", label: "海边" },
  { stars: 5, grad: "from-film-portra to-amber-200", label: "人像" },
  { stars: 3, grad: "from-bench-600 to-bench-800", label: "街拍" },
  { stars: 5, grad: "from-film-velvia to-arctic-600", label: "风光" },
  { stars: 4, grad: "from-film-gold to-amber-500", label: "秋叶" },
  { stars: 5, grad: "from-film-trix to-bench-950", label: "黑白" },
  { stars: 4, grad: "from-film-ektar to-rose-500", label: "街景" },
  { stars: 3, grad: "from-arctic-100 to-arctic-400", label: "雪山" },
  { stars: 5, grad: "from-amber-200 to-film-portra", label: "黄金时刻" },
  { stars: 4, grad: "from-bench-700 to-film-hp5", label: "城市" },
  { stars: 2, grad: "from-film-hp5 to-bench-950", label: "夜景" },
];

export const EDITOR_TOOLS = [
  { id: "exposure", name: "曝光", icon: "sun" },
  { id: "contrast", name: "对比度", icon: "circle" },
  { id: "wb", name: "白平衡", icon: "droplet" },
  { id: "hsl", name: "HSL", icon: "palette" },
  { id: "wheel", name: "色轮", icon: "wheel" },
  { id: "curve", name: "曲线", icon: "curve" },
  { id: "geometry", name: "几何", icon: "crop" },
  { id: "lens", name: "镜头校正", icon: "lens" },
  { id: "clarity", name: "清晰度", icon: "aperture" },
  { id: "sharpen", name: "锐化", icon: "triangle" },
  { id: "grain", name: "颗粒", icon: "grain" },
  { id: "hdr", name: "HDR", icon: "layers" },
];

export const TOOL_PARAMS: Record<string, { name: string; min: number; max: number; default: number; unit?: string }[]> = {
  exposure: [
    { name: "曝光", min: -2, max: 2, default: 0, unit: "EV" },
    { name: "高光", min: -100, max: 100, default: -15 },
    { name: "阴影", min: -100, max: 100, default: 22 },
    { name: "白色", min: -100, max: 100, default: 0 },
    { name: "黑色", min: -100, max: 100, default: 8 },
  ],
  contrast: [
    { name: "对比度", min: -100, max: 100, default: 12 },
    { name: "清晰度", min: -100, max: 100, default: 18 },
    { name: "去雾", min: -100, max: 100, default: 0 },
  ],
  wb: [
    { name: "色温", min: 2000, max: 12000, default: 5500, unit: "K" },
    { name: "色调", min: -100, max: 100, default: 0 },
    { name: "自动白平衡", min: 0, max: 1, default: 1 },
  ],
  hsl: [
    { name: "红色色相", min: -180, max: 180, default: 0 },
    { name: "红色饱和", min: -100, max: 100, default: 0 },
    { name: "红色明度", min: -100, max: 100, default: 0 },
  ],
  wheel: [
    { name: "阴影色相", min: 0, max: 360, default: 0 },
    { name: "阴影亮度", min: -100, max: 100, default: 0 },
    { name: "高光色相", min: 0, max: 360, default: 0 },
    { name: "高光亮度", min: -100, max: 100, default: 0 },
  ],
  curve: [
    { name: "曲线点数", min: 2, max: 16, default: 5 },
    { name: "对比度曲线", min: 0, max: 100, default: 30 },
  ],
  geometry: [
    { name: "旋转", min: -45, max: 45, default: 0, unit: "°" },
    { name: "透视垂直", min: -100, max: 100, default: 0 },
    { name: "透视水平", min: -100, max: 100, default: 0 },
  ],
  lens: [
    { name: "畸变校正", min: -100, max: 100, default: 0 },
    { name: "色差去除", min: 0, max: 100, default: 35 },
    { name: "暗角补偿", min: -100, max: 100, default: 0 },
  ],
  clarity: [
    { name: "清晰度", min: -100, max: 100, default: 15 },
    { name: "纹理", min: -100, max: 100, default: 0 },
  ],
  sharpen: [
    { name: "数量", min: 0, max: 100, default: 40 },
    { name: "半径", min: 0.5, max: 3, default: 1 },
    { name: "细节", min: 0, max: 100, default: 25 },
    { name: "蒙版", min: 0, max: 100, default: 0 },
  ],
  grain: [
    { name: "数量", min: 0, max: 100, default: 28 },
    { name: "大小", min: 1, max: 100, default: 25 },
    { name: "粗糙度", min: 0, max: 100, default: 50 },
  ],
  hdr: [
    { name: "HDR 强度", min: 0, max: 100, default: 50 },
    { name: "高光恢复", min: 0, max: 100, default: 65 },
  ],
};

export const COLOR_WHEELS = [
  { id: "shadows", name: "阴影", lum: 18 },
  { id: "midtones", name: "中间调", lum: 50 },
  { id: "highlights", name: "高光", lum: 85 },
];

export const HSL_HUES = [
  { name: "红", color: "#e74c3c" },
  { name: "橙", color: "#e67e22" },
  { name: "黄", color: "#f1c40f" },
  { name: "绿", color: "#2ecc71" },
  { name: "青", color: "#1abc9c" },
  { name: "蓝", color: "#3498db" },
  { name: "紫", color: "#9b59b6" },
  { name: "品", color: "#e84393" },
];

export const FILM_PRESETS = [
  {
    id: "portra400",
    name: "Kodak Portra 400",
    iso: 400,
    type: "彩色负片",
    desc: "人像肤色还原典范，暖调细腻",
    grad: "from-amber-200 via-film-portra to-amber-700",
    grain: 28,
    halation: 18,
  },
  {
    id: "velvia50",
    name: "Fujifilm Velvia 50",
    iso: 50,
    type: "彩色反转",
    desc: "高饱和高对比，风光利器",
    grad: "from-arctic-400 via-film-velvia to-bench-950",
    grain: 12,
    halation: 8,
  },
  {
    id: "trix400",
    name: "Kodak Tri-X 400",
    iso: 400,
    type: "黑白负片",
    desc: "经典黑白，颗粒粗犷有韵",
    grad: "from-bench-600 via-film-trix to-bench-950",
    grain: 65,
    halation: 0,
  },
  {
    id: "ektar100",
    name: "Kodak Ektar 100",
    iso: 100,
    type: "彩色负片",
    desc: "鲜艳饱和，扫街首选",
    grad: "from-amber-300 via-film-ektar to-rose-700",
    grain: 15,
    halation: 12,
  },
  {
    id: "gold200",
    name: "Kodak Gold 200",
    iso: 200,
    type: "彩色负片",
    desc: "温暖怀旧，日常记忆",
    grad: "from-amber-100 via-film-gold to-amber-600",
    grain: 35,
    halation: 22,
  },
  {
    id: "hp5",
    name: "Ilford HP5 Plus 400",
    iso: 400,
    type: "黑白负片",
    desc: "中性的黑白，宽容度高",
    grad: "from-bench-500 via-film-hp5 to-bench-950",
    grain: 50,
    halation: 0,
  },
];

export const EXPORT_FORMATS = [
  { id: "jpeg", name: "JPEG", desc: "有损 · 通用" },
  { id: "png", name: "PNG", desc: "无损 · 透明" },
  { id: "tiff", name: "TIFF", desc: "16-bit · 档案" },
  { id: "dng", name: "DNG", desc: "RAW · 保留元数据" },
  { id: "ultrahdr", name: "Ultra HDR", desc: "Gain Map · HDR 显示" },
];

export const COLOR_SPACES = [
  { id: "srgb", name: "sRGB" },
  { id: "p3", name: "Display P3" },
  { id: "rec2020", name: "Rec2020" },
];

export const ICC_PROFILES = [
  "rec709_bt1886.icc",
  "rec709_gamma22.icc",
  "p3_d65_pq.icc",
  "p3_d65_gamma22.icc",
  "p3_d60_gamma26.icc",
  "p3_dci_gamma26.icc",
  "rec2020_pq.icc",
  "rec2020_hlg.icc",
  "xyz_gamma26.icc",
  "upstream_displayp3_compat_v4.icc",
  "upstream_rec2020_v4.icc",
];

export const AI_MODELS = [
  {
    id: "mobileclip-s2",
    name: "MobileCLIP S2",
    desc: "Apple 端侧优化 · 256 输入",
    active: true,
    size: "38 MB",
  },
  {
    id: "jina-clip-v2",
    name: "Jina CLIP v2",
    desc: "多语种 · 标签与搜索",
    active: false,
    size: "92 MB",
  },
  {
    id: "siglip2",
    name: "SigLIP 2",
    desc: "Google · 零样本分类",
    active: false,
    size: "110 MB",
  },
];

export const ARCH_MAPPING = [
  {
    desktop: "CUDA / Metal / OpenCL",
    android: "GLES 3.2 Compute + Vulkan",
    note: "GPU 后端平台等价",
    badge: "gpu",
  },
  {
    desktop: "Rust gRPC sidecar (puerh_mind)",
    android: "端侧 ONNX Runtime + CLIP + HNSW",
    note: "AI 推理架构等价",
    badge: "ai",
  },
  {
    desktop: "DuckDB + duckorm",
    android: "Room + DAO + SQLCipher",
    note: "数据层架构等价",
    badge: "data",
  },
  {
    desktop: "Qt 6 / QML",
    android: "Jetpack Compose",
    note: "UI 框架架构等价",
    badge: "kt",
  },
  {
    desktop: "OpenImageIO / LibRaw / Exiv2",
    android: "自研 C++ Image + metadata-extractor",
    note: "图像 I/O 自包含",
    badge: "cpp",
  },
  {
    desktop: "C++ Merkle 哈希版本控制",
    android: "Room EditHistoryDao + HistoryMgmtService",
    note: "历史系统等价",
    badge: "data",
  },
];

export const KEY_METRICS = [
  { value: "38", label: "调整操作符" },
  { value: "25", label: "GPU 着色器" },
  { value: "11", label: "ICC 配置" },
  { value: "60+", label: "镜头校准 XML" },
  { value: "100%", label: "桌面功能覆盖" },
];
