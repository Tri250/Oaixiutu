# AlcedoAndroid CMake Build
# Vulkan Compute backend for Android
cmake_minimum_required(VERSION 3.22.1)
project(alcedo_android LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# Android-specific defines
add_definitions(
    -DANDROID
    -DPUERHLAB_VULKAN_BACKEND
    -DPUERHLAB_MOBILE
    -DNDEBUG
)

# Find Vulkan
find_library(vulkan-lib vulkan)
find_library(log-lib log)
find_library(android-lib android)
find_library(jnigraphics-lib jnigraphics)

# ============================================================
# Core C++ Library: alcedo_core (no Qt, no CUDA, no Metal)
# ============================================================

# Core source files
set(CORE_SOURCES
    # Image layer
    core/image/image.cpp
    core/image/image_buffer.cpp
    core/image/metadata_extractor.cpp
    core/image/vulkan_image.cpp

    # Decoders
    core/decoders/decoder_scheduler.cpp
    core/decoders/metadata_decoder.cpp
    core/decoders/raw_decoder.cpp
    core/decoders/thumbnail_decoder.cpp
    core/decoders/raw_processor.cpp
    core/decoders/raw_processor_vulkan.cpp

    # Edit operators - basic
    core/edit/operators/basic/exposure_op.cpp
    core/edit/operators/basic/contrast_op.cpp
    core/edit/operators/basic/white_op.cpp
    core/edit/operators/basic/black_op.cpp
    core/edit/operators/basic/shadow_op.cpp
    core/edit/operators/basic/highlight_op.cpp
    core/edit/operators/basic/color_temp_op.cpp
    core/edit/operators/basic/highlight_shadow_local_tone_vulkan.cpp

    # Edit operators - color
    core/edit/operators/color/tint_op.cpp
    core/edit/operators/color/saturation_op.cpp
    core/edit/operators/color/vibrance_op.cpp
    core/edit/operators/color/hls_op.cpp
    core/edit/operators/color/conversion/oklab_cvt.cpp

    # Edit operators - curve
    core/edit/operators/curve/curve_op.cpp

    # Edit operators - cst (color space transform)
    core/edit/operators/cst/cst_op.cpp
    core/edit/operators/cst/lmt_op.cpp
    core/edit/operators/cst/odt_op.cpp
    core/edit/operators/cst/film_grain_op.cpp
    core/edit/operators/cst/halation_op.cpp
    core/edit/operators/cst/aces_odt_cpu.cpp
    core/edit/operators/cst/open_drt_cpu.cpp

    # Edit operators - detail
    core/edit/operators/detail/clarity_op.cpp
    core/edit/operators/detail/sharpen_op.cpp

    # Edit operators - geometry
    core/edit/operators/geometry/resize_op.cpp
    core/edit/operators/geometry/crop_rotate_op.cpp
    core/edit/operators/geometry/lens_calib_op.cpp

    # Edit operators - raw
    core/edit/operators/raw/raw_decode_op.cpp

    # Edit operators - wheel
    core/edit/operators/wheel/color_wheel_op.cpp

    # Operator registration
    core/edit/operators/operator_registration.cpp

    # Edit history
    core/edit/history/edit_history.cpp
    core/edit/history/version.cpp
    core/edit/history/edit_transaction.cpp

    # Pipeline
    core/edit/pipeline/pipeline.cpp
    core/edit/pipeline/pipeline_stage.cpp
    core/edit/pipeline/pipeline_vulkan_impl.cpp
    core/edit/pipeline/pipeline_accelerator.cpp

    # Scope
    core/edit/scope/scope_analyzer.cpp
    core/edit/scope/vulkan_scope_analyzer.cpp
    core/edit/scope/final_display_frame_tap.cpp

    # Sleeve filesystem
    core/sleeve/sleeve_base.cpp
    core/sleeve/sleeve_filesystem.cpp
    core/sleeve/sleeve_manager.cpp
    core/sleeve/sleeve_view.cpp
    core/sleeve/path_resolver.cpp
    core/sleeve/dentry_cache_manager.cpp
    core/sleeve/storage_service.cpp
    core/sleeve/sleeve_element/sleeve_element.cpp
    core/sleeve/sleeve_element/sleeve_file.cpp
    core/sleeve/sleeve_element/sleeve_folder.cpp
    core/sleeve/sleeve_element/sleeve_element_factory.cpp
    core/sleeve/sleeve_filter/filter_sql.cpp
    core/sleeve/sleeve_filter/filter_combo.cpp
    core/sleeve/sleeve_filter/filters/datetime_filter.cpp
    core/sleeve/sleeve_filter/filters/exif_filter.cpp

    # Storage / ORM
    core/storage/db_controller.cpp
    core/storage/controller_types.cpp
    core/storage/duckdb_capi.cpp
    core/storage/mapper/image_mapper.cpp
    core/storage/mapper/pipeline_mapper.cpp
    core/storage/mapper/sleeve_mapper.cpp
    core/storage/controller/image_controller.cpp
    core/storage/controller/semantic_controller.cpp
    core/storage/controller/ai_controller.cpp
    core/storage/controller/sleeve_element_controller.cpp
    core/storage/service/image_service.cpp
    core/storage/service/pipeline_service.cpp
    core/storage/service/sleeve_service.cpp
    core/storage/image_pool/image_pool_manager.cpp

    # Application services
    core/app/project_service.cpp
    core/app/import_service.cpp
    core/app/export_service.cpp
    core/app/thumbnail_service.cpp
    core/app/thumbnail_disk_cache_service.cpp
    core/app/history_mgmt_service.cpp
    core/app/pipeline_service.cpp
    core/app/sleeve_filter_service.cpp
    core/app/sleeve_service.cpp
    core/app/image_pool_service.cpp
    core/app/album_browse_service.cpp
    core/app/adjustment_transfer_service.cpp
    core/app/ai_credential_store.cpp
    core/app/ai_provider_profile.cpp
    core/app/model_asset_catalog.cpp
    core/app/model_download_service.cpp
    core/app/semantic_generation_service.cpp
    core/app/image_analysis_encoder.cpp
    core/app/image_analysis_service.cpp
    core/app/search_query_classifier.cpp
    core/app/project_package_service.cpp

    # Renderer
    core/renderer/pipeline_scheduler.cpp

    # Concurrency
    core/concurrency/thread_pool.cpp

    # NN
    core/nn/safetensors.cpp

    # IO
    core/io/image_loader.cpp
    core/io/image_writer.cpp
    core/io/ultra_hdr_writer.cpp
    core/io/export_icc_profile_resolver.cpp

    # Utils
    core/utils/clock/time_provider.cpp
    core/utils/diagnostics/app_logging.cpp
    core/utils/string/converter.cpp

    # AI
    core/ai/ai_description.cpp
    core/ai/ai_rating.cpp
)

# Vulkan backend sources
set(VULKAN_SOURCES
    vulkan/context/vulkan_context.cpp
    vulkan/context/vulkan_device.cpp
    vulkan/pipeline/vulkan_pipeline.cpp
    vulkan/pipeline/vulkan_kernel_stream.cpp
    vulkan/pipeline/vulkan_program_registry.cpp
)

# JNI bridge sources
set(JNI_SOURCES
    jni/jni_bridge.cpp
    jni/jni_image.cpp
    jni/jni_pipeline.cpp
    jni/jni_sleeve.cpp
    jni/jni_editor.cpp
    jni/jni_raw.cpp
    jni/jni_ai.cpp
    jni/jni_export.cpp
    jni/jni_thumbnail.cpp
    jni/jni_history.cpp
    jni/jni_scope.cpp
)

# Vulkan SPIR-V shaders (compiled at build time)
set(VULKAN_SHADERS
    vulkan/shaders/basic.comp
    vulkan/shaders/color.comp
    vulkan/shaders/cst.comp
    vulkan/shaders/detail.comp
    vulkan/shaders/film_grain.comp
    vulkan/shaders/halation.comp
    vulkan/shaders/tone_mapping.comp
    vulkan/shaders/edit_pipeline_fused.comp
    vulkan/shaders/scope_analyzer.comp
    vulkan/shaders/prng.comp
    vulkan/shaders/geometry_utils.comp
)

# ============================================================
# Build native library
# ============================================================

# SQLite amalgamation (backing store for the DuckDB C API shim). Compiled as a
# separate C static library with a tuned feature set for Android.
add_library(alcedo_sqlite STATIC third_party/sqlite/sqlite3.c)
target_include_directories(alcedo_sqlite PUBLIC third_party/sqlite)
target_compile_definitions(alcedo_sqlite PRIVATE
    SQLITE_OMIT_LOAD_EXTENSION        # no dynamic extension loading on Android
    SQLITE_OMIT_DEPRECATED
    SQLITE_THREADSAFE=1              # serialize at the sqlite3 layer (FULLMUTEX)
    SQLITE_DEFAULT_MEMSTATUS=0       # skip memory stats bookkeeping
    SQLITE_DEFAULT_WAL_SYNCHRONOUS=1 # NORMAL sync for WAL (faster, durable enough)
    SQLITE_ENABLE_FTS5               # full-text search (used by AiImageFtsDocument)
    SQLITE_ENABLE_JSON1              # JSON1 functions (always on in 3.45, kept explicit)
)
# The amalgamation is one large TU; compile it with optimisation but without
# LTO to keep build time predictable.
target_compile_options(alcedo_sqlite PRIVATE -O2 -fomit-frame-pointer -ffunction-sections -fdata-sections)

add_library(alcedo_native SHARED
    ${CORE_SOURCES}
    ${VULKAN_SOURCES}
    ${JNI_SOURCES}
)

target_include_directories(alcedo_native PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/core
    ${CMAKE_CURRENT_SOURCE_DIR}/core/include
    ${CMAKE_CURRENT_SOURCE_DIR}/vulkan
    ${CMAKE_CURRENT_SOURCE_DIR}/jni
    ${CMAKE_CURRENT_SOURCE_DIR}/third_party
    ${CMAKE_CURRENT_SOURCE_DIR}/third_party/sqlite
)

target_link_libraries(alcedo_native
    ${log-lib}
    ${android-lib}
    ${vulkan-lib}
    ${jnigraphics-lib}
    EGL
    GLESv3
    alcedo_sqlite
)

# Optimization flags
target_compile_options(alcedo_native PRIVATE
    -O2
    -fvisibility=hidden
    -fvisibility-inlines-hidden
    -fomit-frame-pointer
    -ffunction-sections
    -fdata-sections
    -funroll-loops
)

# Link-time optimization for release
if(CMAKE_BUILD_TYPE STREQUAL "Release")
    target_link_options(alcedo_native PRIVATE
        -Wl,--gc-sections
        -Wl,--exclude-libs,ALL
        -Wl,-O1
        -flto
    )
endif()
