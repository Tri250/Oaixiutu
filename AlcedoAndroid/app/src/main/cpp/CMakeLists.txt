cmake_minimum_required(VERSION 3.22.1)
project("alcedo_core")

# Strip debug symbols in release builds
if(NOT CMAKE_BUILD_TYPE STREQUAL "Debug")
    set(CMAKE_C_FLAGS_RELEASE "${CMAKE_C_FLAGS_RELEASE} -fvisibility=hidden -ffunction-sections -fdata-sections")
    set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} -fvisibility=hidden -ffunction-sections -fdata-sections")
    set(CMAKE_SHARED_LINKER_FLAGS_RELEASE "${CMAKE_SHARED_LINKER_FLAGS_RELEASE} -Wl,--gc-sections -Wl,--strip-all")
endif()

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# Android NDK specifics
find_library(android-log-lib log)
find_library(android-lib android)
find_library(egl-lib EGL)
find_library(gles-lib GLESv3)

# Core library
add_library(
    alcedo_core
    SHARED
    native-lib.cpp
    core/sleeve/sleeve_base.cpp
    core/sleeve/sleeve_manager.cpp
    core/sleeve/sleeve_filesystem.cpp
    core/sleeve/path_resolver.cpp
    core/sleeve/dentry_cache_manager.cpp
    core/sleeve/storage_service.cpp
    core/sleeve/sleeve_filter.cpp
    core/image/image_buffer.cpp
    core/image/image_pool.cpp
    core/image/raw_decoder.cpp
    core/image/metadata_decoder.cpp
    core/image/thumbnail_decoder.cpp
    core/image/metadata_extractor.cpp
    core/decoders/decoder_scheduler.cpp
    core/edit/pipeline_service.cpp
    core/edit/color_science.cpp
    core/edit/color_matrix.cpp
    core/edit/operators/exposure_op.cpp
    core/edit/operators/contrast_op.cpp
    core/edit/operators/saturation_op.cpp
    core/edit/operators/white_balance_op.cpp
    core/edit/operators/tone_curve_op.cpp
    core/edit/operators/sharpen_op.cpp
    core/edit/operators/film_grain_op.cpp
    core/edit/operators/vibrance_op.cpp
    core/edit/operators/tint_op.cpp
    core/edit/operators/clarity_op.cpp
    core/edit/operators/tone_region_op.cpp
    core/edit/operators/hsl_op.cpp
    core/edit/operators/color_wheel_op.cpp
    core/edit/operators/channel_mixer_op.cpp
    core/edit/operators/halation_op.cpp
    core/edit/operators/lut_op.cpp
    core/edit/operators/highlight_reconstruction_op.cpp
    core/edit/operators/rcd_demosaic_op.cpp
    core/edit/operators/ahd_demosaic_op.cpp
    core/edit/operators/amaze_demosaic_op.cpp
    core/edit/operators/auto_exposure_op.cpp
    core/edit/operators/lens_correction_op.cpp
    core/edit/operators/geometry_op.cpp
    core/edit/operators/operator_factory.cpp
    core/edit/operators/black_op.cpp
    core/edit/operators/white_op.cpp
    core/edit/operators/shadow_op.cpp
    core/edit/operators/highlight_op.cpp
    core/edit/operators/crop_rotate_op.cpp
    core/edit/operators/resize_op.cpp
    core/edit/operators/color_temp_op.cpp
    core/edit/operators/hls_op.cpp
    core/edit/operators/oklab_cvt.cpp
    core/edit/operators/curve_op.cpp
    core/edit/operators/cst_op.cpp
    core/edit/operators/odt_op.cpp
    core/edit/operators/lmt_op.cpp
    core/edit/operators/cv_cvt_op.cpp
    core/edit/operators/raw_decode_op.cpp
    core/edit/scope/scope_analyzer.cpp
    core/ai/clip_inference.cpp
    core/ai/embedding_utils.cpp
    core/ai/hnsw_index.cpp
    core/ai/ai_description.cpp
    core/ai/ai_rating.cpp
    core/utils/hash.cpp
    core/utils/file_utils.cpp
    core/utils/id_generator.cpp
    core/utils/thread_pool.cpp
    core/utils/time_provider.cpp
    core/utils/app_logging.cpp
    core/utils/crash_handler.cpp
)

target_include_directories(alcedo_core PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/core
    ${CMAKE_CURRENT_SOURCE_DIR}/include
)

target_link_libraries(alcedo_core
    ${android-log-lib}
    ${android-lib}
    ${egl-lib}
    ${gles-lib}
)

# Hide all symbols by default
set_target_properties(alcedo_core PROPERTIES
    CXX_VISIBILITY_PRESET hidden
    C_VISIBILITY_PRESET hidden
    VISIBILITY_INLINES_HIDDEN ON
)

# Compiler flags for Android
if(CMAKE_BUILD_TYPE STREQUAL "Debug")
    target_compile_options(alcedo_core PRIVATE -g -O0)
else()
    target_compile_options(alcedo_core PRIVATE -O3 -DNDEBUG)
endif()

target_compile_options(alcedo_core PRIVATE
    -Wall
    -Wextra
    -fexceptions
    -frtti
)

# ============================================================
# Optional: LibRaw integration (native RAW decoding)
# ============================================================
# To enable LibRaw, compile LibRaw for Android separately and set:
#   -DLIBRAW_INCLUDE_DIR=/path/to/libraw/include
#   -DLIBRAW_LIB_DIR=/path/to/libraw/libs
# if(DEFINED LIBRAW_INCLUDE_DIR AND DEFINED LIBRAW_LIB_DIR)
#     target_include_directories(alcedo_core PRIVATE ${LIBRAW_INCLUDE_DIR})
#     target_link_libraries(alcedo_core ${LIBRAW_LIB_DIR}/libraw.a)
#     target_compile_definitions(alcedo_core PRIVATE HAS_LIBRAW=1)
# endif()

# ============================================================
# Optional: Exiv2 integration (native metadata extraction)
# ============================================================
# To enable Exiv2, compile Exiv2 for Android separately and set:
#   -DEXIV2_INCLUDE_DIR=/path/to/exiv2/include
#   -DEXIV2_LIB_DIR=/path/to/exiv2/libs
# if(DEFINED EXIV2_INCLUDE_DIR AND DEFINED EXIV2_LIB_DIR)
#     target_include_directories(alcedo_core PRIVATE ${EXIV2_INCLUDE_DIR})
#     target_link_libraries(alcedo_core
#         ${EXIV2_LIB_DIR}/libexiv2.a
#         ${EXIV2_LIB_DIR}/libexpat.a
#         z
#     )
#     target_compile_definitions(alcedo_core PRIVATE HAS_EXIV2=1)
# endif()