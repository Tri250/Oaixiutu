#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <cmath>
#include <cstring>
#include <algorithm>

#include "core/edit/pipeline_service.h"
#include "core/image/image_buffer.h"
#include "core/image/metadata_extractor.h"
#include "core/image/raw_decoder.h"
#include "core/image/metadata_decoder.h"
#include "core/image/thumbnail_decoder.h"
#include "core/decoders/decoder_scheduler.h"
#include "core/edit/color_science.h"
#include "core/edit/operators/lut_op.h"
#include "core/edit/operators/operator_factory.h"
#include "core/edit/operators/black_op.h"
#include "core/edit/operators/white_op.h"
#include "core/edit/operators/shadow_op.h"
#include "core/edit/operators/highlight_op.h"
#include "core/edit/operators/crop_rotate_op.h"
#include "core/edit/operators/resize_op.h"
#include "core/sleeve/sleeve_manager.h"
#include "core/sleeve/sleeve_filesystem.h"
#include "core/sleeve/path_resolver.h"
#include "core/utils/id_generator.h"
#include "core/utils/thread_pool.h"
#include "core/utils/time_provider.h"
#include "core/utils/app_logging.h"
#include "core/utils/crash_handler.h"

#define LOG_TAG "AlcedoCore"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define SAFE_PARAM(idx, val) do { \
    if ((idx) < paramsLen) { val = params[(idx)]; } \
    else { LOGE("Param index %d out of bounds (len=%d)", (idx), paramsLen); } \
} while(0)

using namespace alcedo;

extern "C" {

// ============================================================
// Full Pipeline Processing (float32 RGBA)
// ============================================================

JNIEXPORT jfloatArray JNICALL
Java_com_alcedo_studio_domain_service_NativePipelineBridge_nativeApplyPipelineFloat(
        JNIEnv *env,
        jobject thiz,
        jfloatArray input,
        jint width,
        jint height,
        jint channels,
        jfloatArray paramsArray
) {
    jsize len = env->GetArrayLength(input);
    jfloat *pixels = env->GetFloatArrayElements(input, nullptr);
    if (!pixels) { LOGE("Failed to get input pixels"); return nullptr; }

    // Read params
    jfloat *params = env->GetFloatArrayElements(paramsArray, nullptr);
    if (!params) { env->ReleaseFloatArrayElements(input, pixels, JNI_ABORT); return nullptr; }

    jsize paramsLen = env->GetArrayLength(paramsArray);
    if (paramsLen < 47) {  // minimum expected params count
        LOGE("Params array too short: %d < 47", paramsLen);
        env->ReleaseFloatArrayElements(paramsArray, params, JNI_ABORT);
        env->ReleaseFloatArrayElements(input, pixels, JNI_ABORT);
        return nullptr;
    }

    PipelineParams pipeline_params;

    // Parse params array (complex structure, simplified)
    int p_idx = 0;
    SAFE_PARAM(p_idx++, pipeline_params.exposure);
    SAFE_PARAM(p_idx++, pipeline_params.contrast);
    SAFE_PARAM(p_idx++, pipeline_params.saturation);
    SAFE_PARAM(p_idx++, pipeline_params.vibrance);
    SAFE_PARAM(p_idx++, pipeline_params.highlights);
    SAFE_PARAM(p_idx++, pipeline_params.shadows);
    SAFE_PARAM(p_idx++, pipeline_params.midtones);
    SAFE_PARAM(p_idx++, pipeline_params.white_balance_temp);
    SAFE_PARAM(p_idx++, pipeline_params.white_balance_tint);
    SAFE_PARAM(p_idx++, pipeline_params.sharpen);
    SAFE_PARAM(p_idx++, pipeline_params.clarity);
    SAFE_PARAM(p_idx++, pipeline_params.clarity_radius);
    SAFE_PARAM(p_idx++, pipeline_params.film_grain);
    SAFE_PARAM(p_idx++, pipeline_params.halation_intensity);
    SAFE_PARAM(p_idx++, pipeline_params.halation_threshold);
    SAFE_PARAM(p_idx++, pipeline_params.halation_spread);
    SAFE_PARAM(p_idx++, pipeline_params.halation_red_bias);
    SAFE_PARAM(p_idx++, pipeline_params.sigmoid_contrast);

    // Color wheels
    SAFE_PARAM(p_idx++, pipeline_params.color_wheel_lift[0]);
    SAFE_PARAM(p_idx++, pipeline_params.color_wheel_lift[1]);
    SAFE_PARAM(p_idx++, pipeline_params.color_wheel_lift[2]);
    SAFE_PARAM(p_idx++, pipeline_params.color_wheel_gamma[0]);
    SAFE_PARAM(p_idx++, pipeline_params.color_wheel_gamma[1]);
    SAFE_PARAM(p_idx++, pipeline_params.color_wheel_gamma[2]);
    SAFE_PARAM(p_idx++, pipeline_params.color_wheel_gain[0]);
    SAFE_PARAM(p_idx++, pipeline_params.color_wheel_gain[1]);
    SAFE_PARAM(p_idx++, pipeline_params.color_wheel_gain[2]);

    // Tint
    SAFE_PARAM(p_idx++, pipeline_params.tint_highlight_hue);
    SAFE_PARAM(p_idx++, pipeline_params.tint_highlight_strength);
    SAFE_PARAM(p_idx++, pipeline_params.tint_shadow_hue);
    SAFE_PARAM(p_idx++, pipeline_params.tint_shadow_strength);
    SAFE_PARAM(p_idx++, pipeline_params.tint_balance);

    // Display transform
    { int tmp; SAFE_PARAM(p_idx++, tmp); pipeline_params.display_transform.color_science = static_cast<int>(tmp); }
    { int tmp; SAFE_PARAM(p_idx++, tmp); pipeline_params.display_transform.eotf = static_cast<int>(tmp); }
    SAFE_PARAM(p_idx++, pipeline_params.display_transform.peak_luminance);
    { int tmp; SAFE_PARAM(p_idx++, tmp); pipeline_params.display_transform.display_color_space = static_cast<int>(tmp); }

    env->ReleaseFloatArrayElements(paramsArray, params, JNI_ABORT);

    // Create float buffer
    std::vector<float> float_pixels(pixels, pixels + len);
    env->ReleaseFloatArrayElements(input, pixels, JNI_ABORT);

    // Run pipeline
    auto& pipeline = PipelineService::Instance();
    pipeline.process(float_pixels.data(), width, height, channels, pipeline_params);

    // Return result
    jfloatArray result = env->NewFloatArray(len);
    if (!result || env->ExceptionCheck()) {
        LOGE("Failed to create float array result");
        if (env->ExceptionCheck()) { env->ExceptionDescribe(); env->ExceptionClear(); }
        return nullptr;
    }
    env->SetFloatArrayRegion(result, 0, len, float_pixels.data());
    return result;
}

// ============================================================
// Simple Pipeline (Int-based, legacy)
// ============================================================

JNIEXPORT jintArray JNICALL
Java_com_alcedo_studio_domain_service_NativePipelineBridge_nativeApplyPipeline(
        JNIEnv *env,
        jobject thiz,
        jintArray input,
        jint width,
        jint height,
        jfloat exposure,
        jfloat contrast,
        jfloat saturation,
        jfloat highlights,
        jfloat shadows,
        jfloat temperature,
        jfloat tint,
        jfloat sharpen
) {
    jsize len = env->GetArrayLength(input);
    jint *pixels = env->GetIntArrayElements(input, nullptr);
    if (!pixels) { LOGE("Failed to get input pixels"); return nullptr; }

    // Convert to float
    std::vector<float> float_pixels(len * 4);
    for (int i = 0; i < len; ++i) {
        int pixel = pixels[i];
        int idx = i * 4;
        float_pixels[idx]     = ((pixel >> 16) & 0xFF) / 255.0f;
        float_pixels[idx + 1] = ((pixel >> 8) & 0xFF) / 255.0f;
        float_pixels[idx + 2] = (pixel & 0xFF) / 255.0f;
        float_pixels[idx + 3] = ((pixel >> 24) & 0xFF) / 255.0f;
    }
    env->ReleaseIntArrayElements(input, pixels, JNI_ABORT);

    PipelineParams params;
    params.exposure = exposure;
    params.contrast = contrast;
    params.saturation = saturation;
    params.highlights = highlights;
    params.shadows = shadows;
    params.white_balance_temp = temperature;
    params.white_balance_tint = tint;
    params.sharpen = sharpen;

    auto& pipeline = PipelineService::Instance();
    pipeline.process(float_pixels.data(), width, height, 4, params);

    // Convert back to int
    jintArray result = env->NewIntArray(len);
    if (!result || env->ExceptionCheck()) {
        LOGE("Failed to create int array");
        if (env->ExceptionCheck()) { env->ExceptionDescribe(); env->ExceptionClear(); }
        return nullptr;
    }
    std::vector<jint> outPixels(len);
    for (int i = 0; i < len; ++i) {
        int idx = i * 4;
        int a = static_cast<int>(std::max(0.0f, std::min(1.0f, float_pixels[idx + 3])) * 255.0f);
        int r = static_cast<int>(std::max(0.0f, std::min(1.0f, float_pixels[idx])) * 255.0f);
        int g = static_cast<int>(std::max(0.0f, std::min(1.0f, float_pixels[idx + 1])) * 255.0f);
        int b = static_cast<int>(std::max(0.0f, std::min(1.0f, float_pixels[idx + 2])) * 255.0f);
        outPixels[i] = (a << 24) | (r << 16) | (g << 8) | b;
    }
    env->SetIntArrayRegion(result, 0, len, outPixels.data());
    return result;
}

// ============================================================
// RAW Decode
// ============================================================

JNIEXPORT jfloatArray JNICALL
Java_com_alcedo_studio_domain_service_NativePipelineBridge_nativeDecodeRawFloat(
        JNIEnv *env,
        jobject thiz,
        jshortArray rawData,
        jint rawWidth,
        jint rawHeight,
        jint bayerPattern,
        jint whiteLevel,
        jint blackLevel,
        jboolean highlightReconstruction
) {
    jsize len = env->GetArrayLength(rawData);
    jshort *raw = env->GetShortArrayElements(rawData, nullptr);
    if (!raw) { LOGE("Failed to get raw data"); return nullptr; }

    int output_size = rawWidth * rawHeight * 3;
    std::vector<float> output_rgb(output_size);

    RawDecodeParams raw_params;
    raw_params.bayer_pattern = bayerPattern;
    raw_params.white_level = static_cast<uint16_t>(whiteLevel);
    raw_params.black_level = static_cast<uint16_t>(blackLevel);
    raw_params.highlight_reconstruction = highlightReconstruction;
    raw_params.demosaic_algorithm = 0; // RCD

    auto& pipeline = PipelineService::Instance();
    pipeline.decode_raw(
        reinterpret_cast<const uint16_t*>(raw),
        rawWidth, rawHeight,
        output_rgb.data(), rawWidth, rawHeight,
        raw_params);

    env->ReleaseShortArrayElements(rawData, raw, JNI_ABORT);

    jfloatArray result = env->NewFloatArray(output_size);
    if (!result || env->ExceptionCheck()) {
        LOGE("Failed to create float array for raw decode result");
        if (env->ExceptionCheck()) { env->ExceptionDescribe(); env->ExceptionClear(); }
        return nullptr;
    }
    env->SetFloatArrayRegion(result, 0, output_size, output_rgb.data());
    return result;
}

// Legacy RAW decode
JNIEXPORT jintArray JNICALL
Java_com_alcedo_studio_domain_service_NativePipelineBridge_nativeDecodeRaw(
        JNIEnv *env,
        jobject thiz,
        jstring rawPath,
        jint demosaic,
        jboolean highlightReconstruction
) {
    const char *path = env->GetStringUTFChars(rawPath, nullptr);
    if (!path) {
        LOGE("Failed to get raw path string");
        jintArray empty = env->NewIntArray(0);
        if (env->ExceptionCheck()) { env->ExceptionDescribe(); env->ExceptionClear(); }
        return empty ? empty : env->NewIntArray(0);
    }
    LOGI("Decoding RAW: %s (demosaic=%d)", path, demosaic);

    // Integer array placeholder - real implementation would use LibRaw
    jintArray result = env->NewIntArray(0);
    if (env->ExceptionCheck()) { env->ExceptionDescribe(); env->ExceptionClear(); }

    env->ReleaseStringUTFChars(rawPath, path);
    return result;
}

// ============================================================
// Color Science Transforms
// ============================================================

JNIEXPORT jfloatArray JNICALL
Java_com_alcedo_studio_domain_service_NativePipelineBridge_nativeApplyAcesTransform(
        JNIEnv *env,
        jobject thiz,
        jfloatArray input,
        jint width,
        jint height,
        jfloat peakLuminance
) {
    jsize len = env->GetArrayLength(input);
    jfloat *pixels = env->GetFloatArrayElements(input, nullptr);
    if (!pixels) return nullptr;

    std::vector<float> float_pixels(pixels, pixels + len);
    env->ReleaseFloatArrayElements(input, pixels, JNI_ABORT);

    int pixel_count = width * height;
    color_science::convert_color_space_bulk(float_pixels.data(), pixel_count, 3, 0, 3); // sRGB → AP0
    color_science::aces_rrt_bulk(float_pixels.data(), pixel_count, 3, 3);
    color_science::convert_color_space_bulk(float_pixels.data(), pixel_count, 3, 3, 0); // AP0 → sRGB
    color_science::apply_peak_luminance_scale_bulk(float_pixels.data(), pixel_count, 3, peakLuminance);

    for (int i = 0; i < pixel_count; ++i) {
        int idx = i * 3;
        color_science::srgb_eotf_rgb(&float_pixels[idx], &float_pixels[idx+1], &float_pixels[idx+2]);
    }

    jfloatArray result = env->NewFloatArray(len);
    if (!result || env->ExceptionCheck()) {
        LOGE("Failed to create float array for ACES transform result");
        if (env->ExceptionCheck()) { env->ExceptionDescribe(); env->ExceptionClear(); }
        return nullptr;
    }
    env->SetFloatArrayRegion(result, 0, len, float_pixels.data());
    return result;
}

JNIEXPORT jfloatArray JNICALL
Java_com_alcedo_studio_domain_service_NativePipelineBridge_nativeApplyOpenDRTTransform(
        JNIEnv *env,
        jobject thiz,
        jfloatArray input,
        jint width,
        jint height,
        jfloat peakLuminance
) {
    jsize len = env->GetArrayLength(input);
    jfloat *pixels = env->GetFloatArrayElements(input, nullptr);
    if (!pixels) return nullptr;

    std::vector<float> float_pixels(pixels, pixels + len);
    env->ReleaseFloatArrayElements(input, pixels, JNI_ABORT);

    int pixel_count = width * height;
    color_science::opendrt_tone_map_bulk(float_pixels.data(), pixel_count, 3, 3);
    color_science::apply_peak_luminance_scale_bulk(float_pixels.data(), pixel_count, 3, peakLuminance);

    for (int i = 0; i < pixel_count; ++i) {
        int idx = i * 3;
        color_science::srgb_eotf_rgb(&float_pixels[idx], &float_pixels[idx+1], &float_pixels[idx+2]);
    }

    jfloatArray result = env->NewFloatArray(len);
    if (!result || env->ExceptionCheck()) {
        LOGE("Failed to create float array for OpenDRT transform result");
        if (env->ExceptionCheck()) { env->ExceptionDescribe(); env->ExceptionClear(); }
        return nullptr;
    }
    env->SetFloatArrayRegion(result, 0, len, float_pixels.data());
    return result;
}

JNIEXPORT jfloatArray JNICALL
Java_com_alcedo_studio_domain_service_NativePipelineBridge_nativeConvertColorSpace(
        JNIEnv *env,
        jobject thiz,
        jfloatArray input,
        jint width,
        jint height,
        jint srcSpace,
        jint dstSpace
) {
    jsize len = env->GetArrayLength(input);
    jfloat *pixels = env->GetFloatArrayElements(input, nullptr);
    if (!pixels) return nullptr;

    std::vector<float> float_pixels(pixels, pixels + len);
    env->ReleaseFloatArrayElements(input, pixels, JNI_ABORT);

    color_science::convert_color_space_bulk(float_pixels.data(), width * height, 3, srcSpace, dstSpace);

    jfloatArray result = env->NewFloatArray(len);
    if (!result || env->ExceptionCheck()) {
        LOGE("Failed to create float array for color space conversion result");
        if (env->ExceptionCheck()) { env->ExceptionDescribe(); env->ExceptionClear(); }
        return nullptr;
    }
    env->SetFloatArrayRegion(result, 0, len, float_pixels.data());
    return result;
}

JNIEXPORT jfloatArray JNICALL
Java_com_alcedo_studio_domain_service_NativePipelineBridge_nativeApplyEOTF(
        JNIEnv *env,
        jobject thiz,
        jfloatArray input,
        jint width,
        jint height,
        jint eotfType,
        jfloat peakLuminance
) {
    jsize len = env->GetArrayLength(input);
    jfloat *pixels = env->GetFloatArrayElements(input, nullptr);
    if (!pixels) return nullptr;

    std::vector<float> float_pixels(pixels, pixels + len);
    env->ReleaseFloatArrayElements(input, pixels, JNI_ABORT);

    int pixel_count = width * height;
    for (int i = 0; i < pixel_count; ++i) {
        int idx = i * 3;
        float r = float_pixels[idx], g = float_pixels[idx+1], b = float_pixels[idx+2];
        switch (eotfType) {
            case 0: color_science::srgb_eotf_rgb(&r, &g, &b); break;
            case 1: color_science::pq_eotf_rgb(&r, &g, &b, peakLuminance); break;
            case 2: color_science::hlg_oetf_rgb(&r, &g, &b); break;
            case 3: color_science::gamma_eotf_rgb(&r, &g, &b, 2.2f); break;
            case 4: color_science::gamma_eotf_rgb(&r, &g, &b, 2.4f); break;
        }
        float_pixels[idx] = r; float_pixels[idx+1] = g; float_pixels[idx+2] = b;
    }

    jfloatArray result = env->NewFloatArray(len);
    if (!result || env->ExceptionCheck()) {
        LOGE("Failed to create float array for EOTF result");
        if (env->ExceptionCheck()) { env->ExceptionDescribe(); env->ExceptionClear(); }
        return nullptr;
    }
    env->SetFloatArrayRegion(result, 0, len, float_pixels.data());
    return result;
}

// ============================================================
// LUT Application
// ============================================================

JNIEXPORT jboolean JNICALL
Java_com_alcedo_studio_domain_service_NativePipelineBridge_nativeApplyLut(
        JNIEnv *env,
        jobject thiz,
        jfloatArray pixels,
        jint width,
        jint height,
        jstring lutPath
) {
    const char *path = env->GetStringUTFChars(lutPath, nullptr);
    jsize len = env->GetArrayLength(pixels);
    jfloat *pix = env->GetFloatArrayElements(pixels, nullptr);
    if (!pix || !path) {
        if (path) env->ReleaseStringUTFChars(lutPath, path);
        return JNI_FALSE;
    }

    float* lut_data = nullptr;
    int lut_size = 0;
    bool ok = LutOperator::parse_cube_file(path, lut_data, lut_size);
    if (ok) {
        LutOperator::apply_rgb(pix, width, height, lut_data, lut_size);
        LutOperator::free_parsed_lut(lut_data);
    }

    env->ReleaseFloatArrayElements(pixels, pix, 0);
    env->ReleaseStringUTFChars(lutPath, path);
    return ok ? JNI_TRUE : JNI_FALSE;
}

// ============================================================
// Metadata Extraction
// ============================================================

JNIEXPORT jstring JNICALL
Java_com_alcedo_studio_domain_service_NativePipelineBridge_nativeExtractMetadata(
        JNIEnv *env,
        jobject thiz,
        jstring filePath
) {
    const char *path = env->GetStringUTFChars(filePath, nullptr);
    if (!path) return nullptr;

    alcedo::ExifData data;
    bool ok = alcedo::MetadataExtractor::extract_all(path, data);

    std::string result;
    if (ok) {
        char buf[1024];
        snprintf(buf, sizeof(buf),
            "{\"make\":\"%s\",\"model\":\"%s\",\"iso\":%.0f,\"aperture\":%.1f,"
            "\"shutter\":\"1/%.0f\",\"focal\":%.1f,\"lens\":\"%s\","
            "\"width\":%d,\"height\":%d,\"orientation\":%d,"
            "\"gps_lat\":%.6f,\"gps_lon\":%.6f,\"has_gps\":%s}",
            data.make.c_str(), data.model.c_str(), data.iso, data.aperture,
            1.0f / (data.shutter_speed > 0 ? data.shutter_speed : 1.0f),
            data.focal_length, data.lens_model.c_str(),
            data.image_width, data.image_height, data.orientation,
            data.gps_latitude, data.gps_longitude,
            data.has_gps ? "true" : "false");
        result = buf;
    }

    env->ReleaseStringUTFChars(filePath, path);
    return env->NewStringUTF(result.c_str());
}

// ============================================================
// OKLab Conversions
// ============================================================

JNIEXPORT jfloatArray JNICALL
Java_com_alcedo_studio_domain_service_NativePipelineBridge_nativeSrgbToOklab(
        JNIEnv *env,
        jobject thiz,
        jfloat r, jfloat g, jfloat b
) {
    float L, a, bb;
    color_science::linear_srgb_to_oklab(r, g, b, &L, &a, &bb);
    jfloatArray result = env->NewFloatArray(3);
    if (!result || env->ExceptionCheck()) {
        LOGE("Failed to create float array for OKLab result");
        if (env->ExceptionCheck()) { env->ExceptionDescribe(); env->ExceptionClear(); }
        return nullptr;
    }
    float vals[3] = {L, a, bb};
    env->SetFloatArrayRegion(result, 0, 3, vals);
    return result;
}

JNIEXPORT jfloatArray JNICALL
Java_com_alcedo_studio_domain_service_NativePipelineBridge_nativeOklabToSrgb(
        JNIEnv *env,
        jobject thiz,
        jfloat L, jfloat a, jfloat bb
) {
    float r, g, b;
    color_science::oklab_to_linear_srgb(L, a, bb, &r, &g, &b);
    jfloatArray result = env->NewFloatArray(3);
    if (!result || env->ExceptionCheck()) {
        LOGE("Failed to create float array for OKLab-to-sRGB result");
        if (env->ExceptionCheck()) { env->ExceptionDescribe(); env->ExceptionClear(); }
        return nullptr;
    }
    float vals[3] = {r, g, b};
    env->SetFloatArrayRegion(result, 0, 3, vals);
    return result;
}

// ============================================================
// Sigmoid Contrast
// ============================================================

JNIEXPORT jfloatArray JNICALL
Java_com_alcedo_studio_domain_service_NativePipelineBridge_nativeApplySigmoidContrast(
        JNIEnv *env,
        jobject thiz,
        jfloatArray input,
        jint width,
        jint height,
        jfloat contrast,
        jfloat pivot,
        jfloat shoulder
) {
    jsize len = env->GetArrayLength(input);
    jfloat *pixels = env->GetFloatArrayElements(input, nullptr);
    if (!pixels) return nullptr;

    std::vector<float> float_pixels(pixels, pixels + len);
    env->ReleaseFloatArrayElements(input, pixels, JNI_ABORT);

    color_science::sigmoid_contrast_bulk(float_pixels.data(), width * height, 3, contrast, pivot, shoulder);

    jfloatArray result = env->NewFloatArray(len);
    if (!result || env->ExceptionCheck()) {
        LOGE("Failed to create float array for sigmoid contrast result");
        if (env->ExceptionCheck()) { env->ExceptionDescribe(); env->ExceptionClear(); }
        return nullptr;
    }
    env->SetFloatArrayRegion(result, 0, len, float_pixels.data());
    return result;
}

// ============================================================
// Auto Exposure
// ============================================================

JNIEXPORT jfloat JNICALL
Java_com_alcedo_studio_domain_service_NativePipelineBridge_nativeComputeAutoExposure(
        JNIEnv *env,
        jobject thiz,
        jfloatArray input,
        jint width,
        jint height,
        jint channels,
        jfloat targetPercentile,
        jfloat targetLuminance
) {
    jsize len = env->GetArrayLength(input);
    jfloat *pixels = env->GetFloatArrayElements(input, nullptr);
    if (!pixels) return 0.0f;

    float ev = AutoExposureOperator::compute_auto_exposure(
        pixels, width, height, channels, targetPercentile, targetLuminance);

    env->ReleaseFloatArrayElements(input, pixels, JNI_ABORT);
    return ev;
}

JNIEXPORT jfloatArray JNICALL
Java_com_alcedo_studio_domain_service_NativePipelineBridge_nativeApplyAutoExposure(
        JNIEnv *env,
        jobject thiz,
        jfloatArray input,
        jint width,
        jint height,
        jint channels,
        jfloat targetPercentile,
        jfloat targetLuminance
) {
    jsize len = env->GetArrayLength(input);
    jfloat *pixels = env->GetFloatArrayElements(input, nullptr);
    if (!pixels) return nullptr;

    std::vector<float> float_pixels(pixels, pixels + len);
    env->ReleaseFloatArrayElements(input, pixels, JNI_ABORT);

    AutoExposureOperator::apply(float_pixels.data(), width, height, channels,
                                 targetPercentile, targetLuminance);

    jfloatArray result = env->NewFloatArray(len);
    if (!result || env->ExceptionCheck()) {
        LOGE("Failed to create float array for auto exposure result");
        if (env->ExceptionCheck()) { env->ExceptionDescribe(); env->ExceptionClear(); }
        return nullptr;
    }
    env->SetFloatArrayRegion(result, 0, len, float_pixels.data());
    return result;
}

// ============================================================
// Pipeline Snapshot
// ============================================================

// Store snapshots in a map keyed by jlong handle
#include <unordered_map>
#include <mutex>

static std::unordered_map<jlong, std::unique_ptr<PipelineSnapshot>> g_snapshots;
static std::mutex g_snapshot_mutex;
static jlong g_next_snapshot_id = 1;

JNIEXPORT jlong JNICALL
Java_com_alcedo_studio_domain_service_NativePipelineBridge_nativeCreateSnapshot(
        JNIEnv *env,
        jobject thiz,
        jfloatArray input,
        jint width,
        jint height,
        jint channels,
        jfloatArray paramsArray
) {
    jsize len = env->GetArrayLength(input);
    jfloat *pixels = env->GetFloatArrayElements(input, nullptr);
    if (!pixels) return 0;

    // Parse params (simplified - reuse same format as pipeline)
    PipelineParams params;
    if (paramsArray) {
        jfloat *p = env->GetFloatArrayElements(paramsArray, nullptr);
        if (p) {
            int idx = 0;
            params.exposure = p[idx++];
            params.contrast = p[idx++];
            params.saturation = p[idx++];
            env->ReleaseFloatArrayElements(paramsArray, p, JNI_ABORT);
        }
    }

    auto snapshot = PipelineSnapshot::create(pixels, width, height, channels, params);
    env->ReleaseFloatArrayElements(input, pixels, JNI_ABORT);

    if (!snapshot || !snapshot->is_valid()) return 0;

    jlong handle;
    {
        std::lock_guard<std::mutex> lock(g_snapshot_mutex);
        handle = g_next_snapshot_id++;
        g_snapshots[handle] = std::move(snapshot);
    }

    LOGI("PipelineSnapshot created: handle=%lld, %dx%d", (long long)handle, width, height);
    return handle;
}

JNIEXPORT jfloatArray JNICALL
Java_com_alcedo_studio_domain_service_NativePipelineBridge_nativeRenderSnapshot(
        JNIEnv *env,
        jobject thiz,
        jlong snapshotHandle,
        jint width,
        jint height,
        jint channels
) {
    std::lock_guard<std::mutex> lock(g_snapshot_mutex);
    auto it = g_snapshots.find(snapshotHandle);
    if (it == g_snapshots.end()) return nullptr;

    int total = width * height * channels;
    std::vector<float> output(total);
    if (!it->second->render(output.data(), width, height)) return nullptr;

    jfloatArray result = env->NewFloatArray(total);
    if (!result || env->ExceptionCheck()) {
        LOGE("Failed to create float array for snapshot render result");
        if (env->ExceptionCheck()) { env->ExceptionDescribe(); env->ExceptionClear(); }
        return nullptr;
    }
    env->SetFloatArrayRegion(result, 0, total, output.data());
    return result;
}

JNIEXPORT void JNICALL
Java_com_alcedo_studio_domain_service_NativePipelineBridge_nativeReleaseSnapshot(
        JNIEnv *env,
        jobject thiz,
        jlong snapshotHandle
) {
    std::lock_guard<std::mutex> lock(g_snapshot_mutex);
    auto it = g_snapshots.find(snapshotHandle);
    if (it != g_snapshots.end()) {
        it->second->release();
        g_snapshots.erase(it);
        LOGI("PipelineSnapshot released: handle=%lld", (long long)snapshotHandle);
    }
}

// ============================================================
// Planckian Locus Color Temperature
// ============================================================

JNIEXPORT jfloatArray JNICALL
Java_com_alcedo_studio_domain_service_NativePipelineBridge_nativePlanckianWhiteBalance(
        JNIEnv *env,
        jobject thiz,
        jfloatArray input,
        jint width,
        jint height,
        jint channels,
        jfloat temperature,
        jfloat tint
) {
    jsize len = env->GetArrayLength(input);
    jfloat *pixels = env->GetFloatArrayElements(input, nullptr);
    if (!pixels) return nullptr;

    std::vector<float> float_pixels(pixels, pixels + len);
    env->ReleaseFloatArrayElements(input, pixels, JNI_ABORT);

    int pixel_count = width * height;
    color_science::planckian_white_balance_bulk(float_pixels.data(), pixel_count, channels,
                                                 temperature, tint);

    jfloatArray result = env->NewFloatArray(len);
    if (!result || env->ExceptionCheck()) {
        LOGE("Failed to create float array for planckian WB result");
        if (env->ExceptionCheck()) { env->ExceptionDescribe(); env->ExceptionClear(); }
        return nullptr;
    }
    env->SetFloatArrayRegion(result, 0, len, float_pixels.data());
    return result;
}

JNIEXPORT jfloatArray JNICALL
Java_com_alcedo_studio_domain_service_NativePipelineBridge_nativeGetPlanckianMultipliers(
        JNIEnv *env,
        jobject thiz,
        jfloat temperature
) {
    float r, g, b;
    color_science::planckian_locus_rgb(temperature, &r, &g, &b);
    jfloatArray result = env->NewFloatArray(3);
    if (!result || env->ExceptionCheck()) {
        LOGE("Failed to create float array for planckian multipliers result");
        if (env->ExceptionCheck()) { env->ExceptionDescribe(); env->ExceptionClear(); }
        return nullptr;
    }
    float vals[3] = {r, g, b};
    env->SetFloatArrayRegion(result, 0, 3, vals);
    return result;
}

// ============================================================
// AHD / AMAZE Demosaic
// ============================================================

JNIEXPORT jfloatArray JNICALL
Java_com_alcedo_studio_domain_service_NativePipelineBridge_nativeDemosaicAHD(
        JNIEnv *env,
        jobject thiz,
        jshortArray rawData,
        jint width,
        jint height,
        jint bayerPattern,
        jint whiteLevel,
        jint blackLevel
) {
    jsize len = env->GetArrayLength(rawData);
    jshort *raw = env->GetShortArrayElements(rawData, nullptr);
    if (!raw) return nullptr;

    int outputSize = width * height * 3;
    std::vector<float> output_r(width * height);
    std::vector<float> output_g(width * height);
    std::vector<float> output_b(width * height);

    AHDDemosaicOperator::demosaic_uint16(
        reinterpret_cast<const uint16_t*>(raw), width, height, bayerPattern,
        output_r.data(), output_g.data(), output_b.data(),
        static_cast<uint16_t>(whiteLevel), static_cast<uint16_t>(blackLevel));

    env->ReleaseShortArrayElements(rawData, raw, JNI_ABORT);

    // Interleave R, G, B
    std::vector<float> rgb(outputSize);
    int total = width * height;
    for (int i = 0; i < total; ++i) {
        rgb[i * 3]     = output_r[i];
        rgb[i * 3 + 1] = output_g[i];
        rgb[i * 3 + 2] = output_b[i];
    }

    jfloatArray result = env->NewFloatArray(outputSize);
    if (!result || env->ExceptionCheck()) {
        LOGE("Failed to create float array for AHD demosaic result");
        if (env->ExceptionCheck()) { env->ExceptionDescribe(); env->ExceptionClear(); }
        return nullptr;
    }
    env->SetFloatArrayRegion(result, 0, outputSize, rgb.data());
    return result;
}

JNIEXPORT jfloatArray JNICALL
Java_com_alcedo_studio_domain_service_NativePipelineBridge_nativeDemosaicAMAZE(
        JNIEnv *env,
        jobject thiz,
        jshortArray rawData,
        jint width,
        jint height,
        jint bayerPattern,
        jint whiteLevel,
        jint blackLevel
) {
    jsize len = env->GetArrayLength(rawData);
    jshort *raw = env->GetShortArrayElements(rawData, nullptr);
    if (!raw) return nullptr;

    int outputSize = width * height * 3;
    std::vector<float> output_r(width * height);
    std::vector<float> output_g(width * height);
    std::vector<float> output_b(width * height);

    AMAZEDemosaicOperator::demosaic_uint16(
        reinterpret_cast<const uint16_t*>(raw), width, height, bayerPattern,
        output_r.data(), output_g.data(), output_b.data(),
        static_cast<uint16_t>(whiteLevel), static_cast<uint16_t>(blackLevel));

    env->ReleaseShortArrayElements(rawData, raw, JNI_ABORT);

    std::vector<float> rgb(outputSize);
    int total = width * height;
    for (int i = 0; i < total; ++i) {
        rgb[i * 3]     = output_r[i];
        rgb[i * 3 + 1] = output_g[i];
        rgb[i * 3 + 2] = output_b[i];
    }

    jfloatArray result = env->NewFloatArray(outputSize);
    if (!result || env->ExceptionCheck()) {
        LOGE("Failed to create float array for AMAZE demosaic result");
        if (env->ExceptionCheck()) { env->ExceptionDescribe(); env->ExceptionClear(); }
        return nullptr;
    }
    env->SetFloatArrayRegion(result, 0, outputSize, rgb.data());
    return result;
}

// ============================================================
// NDK String (legacy)
// ============================================================

JNIEXPORT jstring JNICALL
Java_com_alcedo_studio_ndk_AlcedoNdkBridge_stringFromJNI(JNIEnv *env, jobject thiz) {
    std::string hello = "Alcedo Studio Core v2.0-android (NDK: ACES 2.0, OpenDRT, RCD)";
    return env->NewStringUTF(hello.c_str());
}

JNIEXPORT void JNICALL
Java_com_alcedo_studio_ndk_AlcedoNdkBridge_nativeInitialize(JNIEnv* env, jobject thiz) {
    alcedo::CrashHandler::Install();
    LOGI("Native crash handler installed");
}

// ============================================================
// DecodeNdkBridge - Format Detection
// ============================================================

JNIEXPORT jstring JNICALL
Java_com_alcedo_studio_ndk_DecodeNdkBridge_nativeDetectFormat(
        JNIEnv *env, jobject thiz, jstring filePath) {
    const char* path = env->GetStringUTFChars(filePath, nullptr);
    if (!path) return env->NewStringUTF("unknown");
    std::string fmt = RawDecoder::detect_format(path);
    env->ReleaseStringUTFChars(filePath, path);
    return env->NewStringUTF(fmt.c_str());
}

JNIEXPORT jboolean JNICALL
Java_com_alcedo_studio_ndk_DecodeNdkBridge_nativeIsRawFormat(
        JNIEnv *env, jobject thiz, jstring filePath) {
    const char* path = env->GetStringUTFChars(filePath, nullptr);
    if (!path) return JNI_FALSE;
    bool is_raw = RawDecoder::is_raw_format(path);
    env->ReleaseStringUTFChars(filePath, path);
    return is_raw ? JNI_TRUE : JNI_FALSE;
}

// ============================================================
// DecodeNdkBridge - RAW Image Info
// ============================================================

JNIEXPORT jobject JNICALL
Java_com_alcedo_studio_ndk_DecodeNdkBridge_nativeReadRawInfo(
        JNIEnv *env, jobject thiz, jstring filePath) {
    const char* path = env->GetStringUTFChars(filePath, nullptr);
    if (!path) return nullptr;

    RawImageInfo info;
    RawDecoder raw;
    bool ok = raw.read_image_info(path, info);
    env->ReleaseStringUTFChars(filePath, path);

    if (!ok) return nullptr;

    // Build NativeRawInfoResult object
    jclass cls = env->FindClass("com/alcedo/studio/domain/service/NativeRawInfoResult");
    if (!cls) return nullptr;

    jmethodID ctor = env->GetMethodID(cls, "<init>",
        "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;IIIIII[F[F[F[F[IILjava/lang/String;Z)V");
    if (!ctor) return nullptr;

    jstring jfmt = env->NewStringUTF(info.format.c_str());
    jstring jmake = env->NewStringUTF(info.make.c_str());
    jstring jmodel = env->NewStringUTF(info.model.c_str());

    jfloatArray camMatrix = env->NewFloatArray(9);
    if (!camMatrix || env->ExceptionCheck()) {
        LOGE("Failed to create camMatrix array");
        if (env->ExceptionCheck()) { env->ExceptionDescribe(); env->ExceptionClear(); }
        return nullptr;
    }
    env->SetFloatArrayRegion(camMatrix, 0, 9, info.color_matrix);

    jfloatArray dayWb = env->NewFloatArray(3);
    if (!dayWb || env->ExceptionCheck()) {
        LOGE("Failed to create dayWb array");
        if (env->ExceptionCheck()) { env->ExceptionDescribe(); env->ExceptionClear(); }
        return nullptr;
    }
    float dayWbArr[3] = {info.daylight_mult[0], info.daylight_mult[1], info.daylight_mult[2]};
    env->SetFloatArrayRegion(dayWb, 0, 3, dayWbArr);

    jfloatArray tungWb = env->NewFloatArray(3);
    if (!tungWb || env->ExceptionCheck()) {
        LOGE("Failed to create tungWb array");
        if (env->ExceptionCheck()) { env->ExceptionDescribe(); env->ExceptionClear(); }
        return nullptr;
    }
    float tungWbArr[3] = {info.tungsten_mult[0], info.tungsten_mult[1], info.tungsten_mult[2]};
    env->SetFloatArrayRegion(tungWb, 0, 3, tungWbArr);

    jfloatArray camWb = env->NewFloatArray(3);
    if (!camWb || env->ExceptionCheck()) {
        LOGE("Failed to create camWb array");
        if (env->ExceptionCheck()) { env->ExceptionDescribe(); env->ExceptionClear(); }
        return nullptr;
    }
    float camWbArr[3] = {info.camera_wb_mult[0], info.camera_wb_mult[1], info.camera_wb_mult[2]};
    env->SetFloatArrayRegion(camWb, 0, 3, camWbArr);

    jintArray cfaPattern = env->NewIntArray(4);
    if (!cfaPattern || env->ExceptionCheck()) {
        LOGE("Failed to create cfaPattern array");
        if (env->ExceptionCheck()) { env->ExceptionDescribe(); env->ExceptionClear(); }
        return nullptr;
    }
    jint cfaArr[4] = {0, 1, 1, 2}; // RGGB default
    env->SetIntArrayRegion(cfaPattern, 0, 4, cfaArr);

    jstring jcomp = env->NewStringUTF(info.compression_type.c_str());

    jobject result = env->NewObject(cls, ctor,
        jfmt, jmake, jmodel,
        info.raw_width, info.raw_height,
        static_cast<int>(info.cfa_pattern),
        static_cast<int>(info.white_level),
        static_cast<int>(info.black_level),
        camMatrix, dayWb, tungWb, camWb, cfaPattern,
        info.bits_per_sample, jcomp,
        info.is_nikon_he ? JNI_TRUE : JNI_FALSE);
    if (env->ExceptionCheck()) {
        LOGE("Exception during NewObject for raw info result");
        env->ExceptionDescribe(); env->ExceptionClear();
        return nullptr;
    }

    env->DeleteLocalRef(jfmt);
    env->DeleteLocalRef(jmake);
    env->DeleteLocalRef(jmodel);
    env->DeleteLocalRef(jcomp);
    env->DeleteLocalRef(camMatrix);
    env->DeleteLocalRef(dayWb);
    env->DeleteLocalRef(tungWb);
    env->DeleteLocalRef(camWb);
    env->DeleteLocalRef(cfaPattern);

    return result;
}

// ============================================================
// DecodeNdkBridge - Full RAW Decode
// ============================================================

JNIEXPORT jobject JNICALL
Java_com_alcedo_studio_ndk_DecodeNdkBridge_nativeDecodeRaw(
        JNIEnv *env, jobject thiz,
        jstring filePath, jint demosaic, jboolean highlightReconstruction,
        jboolean useCameraMatrix, jboolean halfResolution, jboolean outputFloat,
        jboolean extractThumbnail, jboolean extractPreview, jint maxThumbnailDim,
        jint wbIlluminant) {
    const char* path = env->GetStringUTFChars(filePath, nullptr);
    if (!path) return nullptr;

    RawDecodeOptions opts;
    opts.demosaic = static_cast<DemosaicMethod>(demosaic);
    opts.highlight_mode = highlightReconstruction ? HighlightMode::RECONSTRUCT : HighlightMode::CLIP;
    opts.use_camera_matrix = useCameraMatrix;
    opts.half_resolution = halfResolution;
    opts.output_float = outputFloat;
    opts.extract_thumbnail = extractThumbnail;
    opts.extract_preview = extractPreview;
    opts.max_thumbnail_dimension = maxThumbnailDim;
    opts.wb_illuminant = static_cast<WBIlluminant>(wbIlluminant);

    RawDecodeResult result;
    RawDecoder raw;
    bool ok = raw.decode(path, result, opts);
    env->ReleaseStringUTFChars(filePath, path);

    if (!ok || !result.success) return nullptr;

    // Build NativeDecodeResult
    jclass cls = env->FindClass("com/alcedo/studio/domain/service/NativeDecodeResult");
    if (!cls) return nullptr;

    jmethodID ctor = env->GetMethodID(cls, "<init>", "([F[S[SIII[BII[BIILcom/alcedo/studio/domain/service/DecodeService$RawImageInfo;)V");

    // Float RGB data
    jfloatArray jrgb = nullptr;
    if (!result.float_rgb_data.empty()) {
        jrgb = env->NewFloatArray(result.float_rgb_data.size());
        if (!jrgb || env->ExceptionCheck()) {
            LOGE("Failed to create float RGB array");
            if (env->ExceptionCheck()) { env->ExceptionDescribe(); env->ExceptionClear(); }
            return nullptr;
        }
        env->SetFloatArrayRegion(jrgb, 0, result.float_rgb_data.size(), result.float_rgb_data.data());
    }

    // Short RGB data
    jshortArray jrgbShort = nullptr;
    if (!result.rgb_data.empty()) {
        jrgbShort = env->NewShortArray(result.rgb_data.size());
        if (!jrgbShort || env->ExceptionCheck()) {
            LOGE("Failed to create short RGB array");
            if (env->ExceptionCheck()) { env->ExceptionDescribe(); env->ExceptionClear(); }
            return nullptr;
        }
        env->SetShortArrayRegion(jrgbShort, 0, result.rgb_data.size(),
                                 reinterpret_cast<const jshort*>(result.rgb_data.data()));
    }

    // CFA data
    jshortArray jcfa = nullptr;
    if (!result.raw_cfa_data.empty()) {
        jcfa = env->NewShortArray(result.raw_cfa_data.size());
        if (!jcfa || env->ExceptionCheck()) {
            LOGE("Failed to create CFA array");
            if (env->ExceptionCheck()) { env->ExceptionDescribe(); env->ExceptionClear(); }
            return nullptr;
        }
        env->SetShortArrayRegion(jcfa, 0, result.raw_cfa_data.size(),
                                 reinterpret_cast<const jshort*>(result.raw_cfa_data.data()));
    }

    // Thumbnail data
    jbyteArray jthumb = nullptr;
    if (!result.jpeg_thumbnail.empty()) {
        jthumb = env->NewByteArray(result.jpeg_thumbnail.size());
        if (!jthumb || env->ExceptionCheck()) {
            LOGE("Failed to create thumbnail byte array");
            if (env->ExceptionCheck()) { env->ExceptionDescribe(); env->ExceptionClear(); }
            return nullptr;
        }
        env->SetByteArrayRegion(jthumb, 0, result.jpeg_thumbnail.size(),
                                reinterpret_cast<const jbyte*>(result.jpeg_thumbnail.data()));
    }

    // Preview data
    jbyteArray jpreview = nullptr;
    if (!result.jpeg_preview.empty()) {
        jpreview = env->NewByteArray(result.jpeg_preview.size());
        if (!jpreview || env->ExceptionCheck()) {
            LOGE("Failed to create preview byte array");
            if (env->ExceptionCheck()) { env->ExceptionDescribe(); env->ExceptionClear(); }
            return nullptr;
        }
        env->SetByteArrayRegion(jpreview, 0, result.jpeg_preview.size(),
                                reinterpret_cast<const jbyte*>(result.jpeg_preview.data()));
    }

    // Build RawImageInfo
    jclass infoCls = env->FindClass("com/alcedo/studio/domain/service/DecodeService$RawImageInfo");
    jmethodID infoCtor = env->GetMethodID(infoCls, "<init>",
        "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;IIIIII[F[F[F[F[IILjava/lang/String;Z)V");

    jstring jfmt = env->NewStringUTF(result.image_info.format.c_str());
    jstring jmake = env->NewStringUTF(result.image_info.make.c_str());
    jstring jmodel = env->NewStringUTF(result.image_info.model.c_str());
    jfloatArray jcamMat = env->NewFloatArray(9);
    if (!jcamMat || env->ExceptionCheck()) {
        LOGE("Failed to create camMat array in decode raw");
        if (env->ExceptionCheck()) { env->ExceptionDescribe(); env->ExceptionClear(); }
        return nullptr;
    }
    env->SetFloatArrayRegion(jcamMat, 0, 9, result.image_info.color_matrix);
    jfloatArray jdayWb = env->NewFloatArray(3);
    if (!jdayWb || env->ExceptionCheck()) {
        LOGE("Failed to create dayWb array in decode raw");
        if (env->ExceptionCheck()) { env->ExceptionDescribe(); env->ExceptionClear(); }
        return nullptr;
    }
    float dayWb[3] = {result.image_info.daylight_mult[0], result.image_info.daylight_mult[1], result.image_info.daylight_mult[2]};
    env->SetFloatArrayRegion(jdayWb, 0, 3, dayWb);
    jfloatArray jtungWb = env->NewFloatArray(3);
    if (!jtungWb || env->ExceptionCheck()) {
        LOGE("Failed to create tungWb array in decode raw");
        if (env->ExceptionCheck()) { env->ExceptionDescribe(); env->ExceptionClear(); }
        return nullptr;
    }
    float tungWb[3] = {result.image_info.tungsten_mult[0], result.image_info.tungsten_mult[1], result.image_info.tungsten_mult[2]};
    env->SetFloatArrayRegion(jtungWb, 0, 3, tungWb);
    jfloatArray jcamWb = env->NewFloatArray(3);
    if (!jcamWb || env->ExceptionCheck()) {
        LOGE("Failed to create camWb array in decode raw");
        if (env->ExceptionCheck()) { env->ExceptionDescribe(); env->ExceptionClear(); }
        return nullptr;
    }
    float camWb[3] = {result.image_info.camera_wb_mult[0], result.image_info.camera_wb_mult[1], result.image_info.camera_wb_mult[2]};
    env->SetFloatArrayRegion(jcamWb, 0, 3, camWb);
    jintArray jcfaPat = env->NewIntArray(4);
    if (!jcfaPat || env->ExceptionCheck()) {
        LOGE("Failed to create cfaPat array in decode raw");
        if (env->ExceptionCheck()) { env->ExceptionDescribe(); env->ExceptionClear(); }
        return nullptr;
    }
    jint cfaPat[4] = {0, 1, 1, 2};
    env->SetIntArrayRegion(jcfaPat, 0, 4, cfaPat);
    jstring jcomp = env->NewStringUTF(result.image_info.compression_type.c_str());

    jobject jinfo = env->NewObject(infoCls, infoCtor,
        jfmt, jmake, jmodel,
        result.image_info.raw_width, result.image_info.raw_height,
        static_cast<int>(result.image_info.cfa_pattern),
        result.image_info.white_level, result.image_info.black_level,
        jcamMat, jdayWb, jtungWb, jcamWb, jcfaPat,
        result.image_info.bits_per_sample, jcomp,
        result.image_info.is_nikon_he ? JNI_TRUE : JNI_FALSE);
    if (env->ExceptionCheck()) {
        LOGE("Exception during NewObject for RawImageInfo in decode raw");
        env->ExceptionDescribe(); env->ExceptionClear();
        return nullptr;
    }

    jobject jresult = env->NewObject(cls, ctor,
        jrgb, jrgbShort, jcfa,
        result.width, result.height,
        jinfo,
        jthumb, result.thumbnail_width, result.thumbnail_height,
        jpreview, result.preview_width, result.preview_height);
    if (env->ExceptionCheck()) {
        LOGE("Exception during NewObject for decode result");
        env->ExceptionDescribe(); env->ExceptionClear();
        return nullptr;
    }

    // Cleanup
    env->DeleteLocalRef(jrgb); env->DeleteLocalRef(jrgbShort); env->DeleteLocalRef(jcfa);
    env->DeleteLocalRef(jthumb); env->DeleteLocalRef(jpreview); env->DeleteLocalRef(jinfo);
    env->DeleteLocalRef(jfmt); env->DeleteLocalRef(jmake); env->DeleteLocalRef(jmodel);
    env->DeleteLocalRef(jcamMat); env->DeleteLocalRef(jdayWb); env->DeleteLocalRef(jtungWb);
    env->DeleteLocalRef(jcamWb); env->DeleteLocalRef(jcfaPat); env->DeleteLocalRef(jcomp);

    return jresult;
}

JNIEXPORT jobject JNICALL
Java_com_alcedo_studio_ndk_DecodeNdkBridge_nativeDecodeRawFromMemory(
        JNIEnv *env, jobject thiz,
        jbyteArray data, jint demosaic, jboolean highlightReconstruction,
        jboolean useCameraMatrix, jboolean halfResolution, jboolean outputFloat) {
    jsize len = env->GetArrayLength(data);
    jbyte* bytes = env->GetByteArrayElements(data, nullptr);
    if (!bytes) return nullptr;

    RawDecodeOptions opts;
    opts.demosaic = static_cast<DemosaicMethod>(demosaic);
    opts.highlight_mode = highlightReconstruction ? HighlightMode::RECONSTRUCT : HighlightMode::CLIP;
    opts.use_camera_matrix = useCameraMatrix;
    opts.half_resolution = halfResolution;
    opts.output_float = outputFloat;
    opts.extract_thumbnail = false;
    opts.extract_preview = false;

    RawDecodeResult result;
    RawDecoder raw;
    bool ok = raw.decode_from_memory(reinterpret_cast<const uint8_t*>(bytes), len, result, opts);
    env->ReleaseByteArrayElements(data, bytes, JNI_ABORT);

    if (!ok || !result.success) return nullptr;

    jfloatArray jrgb = env->NewFloatArray(result.float_rgb_data.size());
    if (!jrgb || env->ExceptionCheck()) {
        LOGE("Failed to create float RGB array in decode from memory");
        if (env->ExceptionCheck()) { env->ExceptionDescribe(); env->ExceptionClear(); }
        return nullptr;
    }
    env->SetFloatArrayRegion(jrgb, 0, result.float_rgb_data.size(), result.float_rgb_data.data());

    // Simplified return - just the float RGB data
    jclass cls = env->FindClass("com/alcedo/studio/domain/service/NativeDecodeResult");
    jmethodID ctor = env->GetMethodID(cls, "<init>", "([F[S[SIII[BII[BIILcom/alcedo/studio/domain/service/DecodeService$RawImageInfo;)V");

    jobject jresult = env->NewObject(cls, ctor,
        jrgb, nullptr, nullptr, result.width, result.height, nullptr,
        nullptr, 0, 0, nullptr, 0, 0);
    if (env->ExceptionCheck()) {
        LOGE("Exception during NewObject for decode from memory result");
        env->ExceptionDescribe(); env->ExceptionClear();
        return nullptr;
    }
    env->DeleteLocalRef(jrgb);
    return jresult;
}

// ============================================================
// DecodeNdkBridge - Metadata Extraction
// ============================================================

JNIEXPORT jstring JNICALL
Java_com_alcedo_studio_ndk_DecodeNdkBridge_nativeExtractMetadata(
        JNIEnv *env, jobject thiz, jstring filePath) {
    const char* path = env->GetStringUTFChars(filePath, nullptr);
    if (!path) return nullptr;

    DecodedMetadata meta;
    MetadataDecoder decoder;
    bool ok = decoder.decode(path, meta);
    env->ReleaseStringUTFChars(filePath, path);

    if (!ok) return nullptr;
    return env->NewStringUTF(MetadataDecoder::to_json_compact(meta).c_str());
}

JNIEXPORT jstring JNICALL
Java_com_alcedo_studio_ndk_DecodeNdkBridge_nativeExtractMetadataFromMemory(
        JNIEnv *env, jobject thiz, jbyteArray data) {
    jsize len = env->GetArrayLength(data);
    jbyte* bytes = env->GetByteArrayElements(data, nullptr);
    if (!bytes) return nullptr;

    DecodedMetadata meta;
    MetadataDecoder decoder;
    bool ok = decoder.decode_from_memory(reinterpret_cast<const uint8_t*>(bytes), len, meta);
    env->ReleaseByteArrayElements(data, bytes, JNI_ABORT);

    if (!ok) return nullptr;
    return env->NewStringUTF(MetadataDecoder::to_json_compact(meta).c_str());
}

JNIEXPORT jstring JNICALL
Java_com_alcedo_studio_ndk_DecodeNdkBridge_nativeExtractExif(
        JNIEnv *env, jobject thiz, jstring filePath) {
    const char* path = env->GetStringUTFChars(filePath, nullptr);
    if (!path) return nullptr;

    DecodedMetadata meta;
    bool ok = MetadataDecoder::parse_exif(path, meta);
    env->ReleaseStringUTFChars(filePath, path);

    if (!ok) return nullptr;
    return env->NewStringUTF(MetadataDecoder::to_json_compact(meta).c_str());
}

JNIEXPORT jstring JNICALL
Java_com_alcedo_studio_ndk_DecodeNdkBridge_nativeExtractXmp(
        JNIEnv *env, jobject thiz, jstring filePath) {
    const char* path = env->GetStringUTFChars(filePath, nullptr);
    if (!path) return nullptr;

    DecodedMetadata meta;
    bool ok = MetadataDecoder::parse_xmp(path, meta);
    env->ReleaseStringUTFChars(filePath, path);

    if (!ok) return nullptr;
    return env->NewStringUTF(meta.xmp.raw_xml.c_str());
}

JNIEXPORT jbyteArray JNICALL
Java_com_alcedo_studio_ndk_DecodeNdkBridge_nativeExtractIccProfile(
        JNIEnv *env, jobject thiz, jstring filePath) {
    const char* path = env->GetStringUTFChars(filePath, nullptr);
    if (!path) return nullptr;

    DecodedMetadata meta;
    bool ok = MetadataDecoder::parse_icc_profile(path, meta);
    env->ReleaseStringUTFChars(filePath, path);

    if (!ok || meta.icc_profile.raw_data.empty()) return nullptr;

    jbyteArray result = env->NewByteArray(meta.icc_profile.raw_data.size());
    if (!result || env->ExceptionCheck()) {
        LOGE("Failed to create ICC profile byte array");
        if (env->ExceptionCheck()) { env->ExceptionDescribe(); env->ExceptionClear(); }
        return nullptr;
    }
    env->SetByteArrayRegion(result, 0, meta.icc_profile.raw_data.size(),
                            reinterpret_cast<const jbyte*>(meta.icc_profile.raw_data.data()));
    return result;
}

JNIEXPORT jfloatArray JNICALL
Java_com_alcedo_studio_ndk_DecodeNdkBridge_nativeExtractDngColor(
        JNIEnv *env, jobject thiz, jstring filePath) {
    const char* path = env->GetStringUTFChars(filePath, nullptr);
    if (!path) return nullptr;

    DecodedMetadata meta;
    bool ok = MetadataDecoder::parse_dng_color(path, meta);
    env->ReleaseStringUTFChars(filePath, path);

    if (!ok || !meta.dng_color.has_dng_data) return nullptr;

    // Return color_matrix1 + forward_matrix1 + as_shot_neutral (9+9+4 = 22 floats)
    jfloatArray result = env->NewFloatArray(22);
    if (!result || env->ExceptionCheck()) {
        LOGE("Failed to create DNG color float array");
        if (env->ExceptionCheck()) { env->ExceptionDescribe(); env->ExceptionClear(); }
        return nullptr;
    }
    float data[22];
    memcpy(data, meta.dng_color.color_matrix1, 9 * sizeof(float));
    memcpy(data + 9, meta.dng_color.forward_matrix1, 9 * sizeof(float));
    memcpy(data + 18, meta.dng_color.as_shot_neutral, 4 * sizeof(float));
    env->SetFloatArrayRegion(result, 0, 22, data);
    return result;
}

// ============================================================
// DecodeNdkBridge - Thumbnail Generation
// ============================================================

JNIEXPORT jobject JNICALL
Java_com_alcedo_studio_ndk_DecodeNdkBridge_nativeGenerateThumbnail(
        JNIEnv *env, jobject thiz, jstring filePath, jint maxDimension, jboolean useEmbedded) {
    const char* path = env->GetStringUTFChars(filePath, nullptr);
    if (!path) return nullptr;

    ThumbnailOptions opts;
    opts.max_dimension = maxDimension;
    opts.use_embedded = useEmbedded;
    opts.use_memory_cache = false;
    opts.use_disk_cache = false;

    ThumbnailDecoder decoder;
    ThumbnailResult result = decoder.generate(path, opts);
    env->ReleaseStringUTFChars(filePath, path);

    if (!result.success) return nullptr;

    jclass cls = env->FindClass("com/alcedo/studio/domain/service/NativeThumbnailResult");
    jmethodID ctor = env->GetMethodID(cls, "<init>", "([BIIZ)V");

    jbyteArray jdata = env->NewByteArray(result.data.size());
    if (!jdata || env->ExceptionCheck()) {
        LOGE("Failed to create thumbnail byte array");
        if (env->ExceptionCheck()) { env->ExceptionDescribe(); env->ExceptionClear(); }
        return nullptr;
    }
    env->SetByteArrayRegion(jdata, 0, result.data.size(),
                            reinterpret_cast<const jbyte*>(result.data.data()));

    jobject jresult = env->NewObject(cls, ctor, jdata, result.width, result.height,
                                      result.is_embedded ? JNI_TRUE : JNI_FALSE);
    if (env->ExceptionCheck()) {
        LOGE("Exception during NewObject for thumbnail result");
        env->ExceptionDescribe(); env->ExceptionClear();
        return nullptr;
    }
    env->DeleteLocalRef(jdata);
    return jresult;
}

JNIEXPORT jbyteArray JNICALL
Java_com_alcedo_studio_ndk_DecodeNdkBridge_nativeGenerateThumbnailFromRGB(
        JNIEnv *env, jobject thiz, jfloatArray rgbData, jint width, jint height, jint maxDimension) {
    jsize len = env->GetArrayLength(rgbData);
    jfloat* rgb = env->GetFloatArrayElements(rgbData, nullptr);
    if (!rgb) return nullptr;

    ThumbnailResult result;
    ThumbnailDecoder::generate_from_float_rgb(rgb, width, height, maxDimension, result);
    env->ReleaseFloatArrayElements(rgbData, rgb, JNI_ABORT);

    jbyteArray jdata = env->NewByteArray(result.data.size());
    if (!jdata || env->ExceptionCheck()) {
        LOGE("Failed to create thumbnail from RGB byte array");
        if (env->ExceptionCheck()) { env->ExceptionDescribe(); env->ExceptionClear(); }
        return nullptr;
    }
    env->SetByteArrayRegion(jdata, 0, result.data.size(),
                            reinterpret_cast<const jbyte*>(result.data.data()));
    return jdata;
}

JNIEXPORT jbyteArray JNICALL
Java_com_alcedo_studio_ndk_DecodeNdkBridge_nativeExtractEmbeddedThumbnail(
        JNIEnv *env, jobject thiz, jstring filePath) {
    const char* path = env->GetStringUTFChars(filePath, nullptr);
    if (!path) return nullptr;

    std::vector<uint8_t> jpeg;
    int w, h;
    RawDecoder raw;
    bool ok = raw.extract_thumbnail(path, jpeg, w, h);
    env->ReleaseStringUTFChars(filePath, path);

    if (!ok || jpeg.empty()) return nullptr;

    jbyteArray result = env->NewByteArray(jpeg.size());
    if (!result || env->ExceptionCheck()) {
        LOGE("Failed to create embedded thumbnail byte array");
        if (env->ExceptionCheck()) { env->ExceptionDescribe(); env->ExceptionClear(); }
        return nullptr;
    }
    env->SetByteArrayRegion(result, 0, jpeg.size(), reinterpret_cast<const jbyte*>(jpeg.data()));
    return result;
}

JNIEXPORT jbyteArray JNICALL
Java_com_alcedo_studio_ndk_DecodeNdkBridge_nativeExtractEmbeddedPreview(
        JNIEnv *env, jobject thiz, jstring filePath) {
    const char* path = env->GetStringUTFChars(filePath, nullptr);
    if (!path) return nullptr;

    std::vector<uint8_t> jpeg;
    int w, h;
    RawDecoder raw;
    bool ok = raw.extract_preview(path, jpeg, w, h);
    env->ReleaseStringUTFChars(filePath, path);

    if (!ok || jpeg.empty()) return nullptr;

    jbyteArray result = env->NewByteArray(jpeg.size());
    if (!result || env->ExceptionCheck()) {
        LOGE("Failed to create embedded preview byte array");
        if (env->ExceptionCheck()) { env->ExceptionDescribe(); env->ExceptionClear(); }
        return nullptr;
    }
    env->SetByteArrayRegion(result, 0, jpeg.size(), reinterpret_cast<const jbyte*>(jpeg.data()));
    return result;
}

// ============================================================
// DecodeNdkBridge - Demosaic
// ============================================================

JNIEXPORT jfloatArray JNICALL
Java_com_alcedo_studio_ndk_DecodeNdkBridge_nativeDemosaic(
        JNIEnv *env, jobject thiz, jshortArray rawCfaData, jint width, jint height,
        jint bayerPattern, jint whiteLevel, jint blackLevel, jint demosaicMethod) {
    jsize len = env->GetArrayLength(rawCfaData);
    jshort* raw = env->GetShortArrayElements(rawCfaData, nullptr);
    if (!raw) return nullptr;

    int outputSize = width * height * 3;
    std::vector<float> rgb(outputSize);

    const uint16_t* raw16 = reinterpret_cast<const uint16_t*>(raw);
    switch (demosaicMethod) {
        case 0: RawDecoder::demosaic_rcd(raw16, width, height, bayerPattern, whiteLevel, blackLevel, rgb.data()); break;
        case 1: RawDecoder::demosaic_amaze(raw16, width, height, bayerPattern, whiteLevel, blackLevel, rgb.data()); break;
        case 2: RawDecoder::demosaic_dcb(raw16, width, height, bayerPattern, whiteLevel, blackLevel, rgb.data()); break;
        case 3: RawDecoder::demosaic_bilinear(raw16, width, height, bayerPattern, whiteLevel, blackLevel, rgb.data()); break;
        case 4: RawDecoder::demosaic_vng4(raw16, width, height, bayerPattern, whiteLevel, blackLevel, rgb.data()); break;
        default: RawDecoder::demosaic_rcd(raw16, width, height, bayerPattern, whiteLevel, blackLevel, rgb.data()); break;
    }

    env->ReleaseShortArrayElements(rawCfaData, raw, JNI_ABORT);

    jfloatArray result = env->NewFloatArray(outputSize);
    if (!result || env->ExceptionCheck()) {
        LOGE("Failed to create demosaic result float array");
        if (env->ExceptionCheck()) { env->ExceptionDescribe(); env->ExceptionClear(); }
        return nullptr;
    }
    env->SetFloatArrayRegion(result, 0, outputSize, rgb.data());
    return result;
}

// ============================================================
// DecodeNdkBridge - White Balance
// ============================================================

JNIEXPORT void JNICALL
Java_com_alcedo_studio_ndk_DecodeNdkBridge_nativeApplyWhiteBalance(
        JNIEnv *env, jobject thiz, jfloatArray rgbData, jint width, jint height,
        jfloat rMultiplier, jfloat gMultiplier, jfloat bMultiplier) {
    jfloat* rgb = env->GetFloatArrayElements(rgbData, nullptr);
    if (!rgb) return;

    float mult[3] = {rMultiplier, gMultiplier, bMultiplier};
    RawDecoder::apply_white_balance_float(rgb, width, height, mult);

    env->ReleaseFloatArrayElements(rgbData, rgb, 0);
}

// ============================================================
// DecodeNdkBridge - Color Matrix
// ============================================================

JNIEXPORT void JNICALL
Java_com_alcedo_studio_ndk_DecodeNdkBridge_nativeApplyColorMatrix(
        JNIEnv *env, jobject thiz, jfloatArray rgbData, jint width, jint height, jfloatArray matrix) {
    jfloat* rgb = env->GetFloatArrayElements(rgbData, nullptr);
    jfloat* mat = env->GetFloatArrayElements(matrix, nullptr);
    if (!rgb || !mat) {
        if (rgb) env->ReleaseFloatArrayElements(rgbData, rgb, JNI_ABORT);
        if (mat) env->ReleaseFloatArrayElements(matrix, mat, JNI_ABORT);
        return;
    }

    RawDecoder::apply_color_matrix_float(rgb, width * height, mat);

    env->ReleaseFloatArrayElements(rgbData, rgb, 0);
    env->ReleaseFloatArrayElements(matrix, mat, JNI_ABORT);
}

// ============================================================
// DecodeNdkBridge - Highlight Reconstruction
// ============================================================

JNIEXPORT void JNICALL
Java_com_alcedo_studio_ndk_DecodeNdkBridge_nativeReconstructHighlights(
        JNIEnv *env, jobject thiz, jfloatArray rgbData, jint width, jint height,
        jint whiteLevel, jint mode) {
    jfloat* rgb = env->GetFloatArrayElements(rgbData, nullptr);
    if (!rgb) return;

    RawDecoder::reconstruct_highlights(rgb, width, height,
                                        static_cast<uint16_t>(whiteLevel),
                                        static_cast<HighlightMode>(mode));
    env->ReleaseFloatArrayElements(rgbData, rgb, 0);
}

// ============================================================
// DecodeNdkBridge - Black Level
// ============================================================

JNIEXPORT void JNICALL
Java_com_alcedo_studio_ndk_DecodeNdkBridge_nativeSubtractBlackLevel(
        JNIEnv *env, jobject thiz, jshortArray rawData, jint width, jint height,
        jint blackLevelR, jint blackLevelG1, jint blackLevelG2, jint blackLevelB) {
    jshort* raw = env->GetShortArrayElements(rawData, nullptr);
    if (!raw) return;

    int bl[4] = {blackLevelR, blackLevelG1, blackLevelG2, blackLevelB};
    RawDecoder::subtract_black_level(reinterpret_cast<uint16_t*>(raw), width, height, bl, 4);

    env->ReleaseShortArrayElements(rawData, raw, 0);
}

// ============================================================
// DecodeNdkBridge - Cancellation & Scheduler
// ============================================================

static DecoderScheduler g_scheduler;

JNIEXPORT void JNICALL
Java_com_alcedo_studio_ndk_DecodeNdkBridge_nativeCancelDecode(
        JNIEnv *env, jobject thiz, jlong jobId) {
    g_scheduler.cancel_task(static_cast<uint64_t>(jobId));
}

JNIEXPORT void JNICALL
Java_com_alcedo_studio_ndk_DecodeNdkBridge_nativeCancelAllDecodes(
        JNIEnv *env, jobject thiz) {
    g_scheduler.cancel_all();
}

JNIEXPORT void JNICALL
Java_com_alcedo_studio_ndk_DecodeNdkBridge_nativeInitScheduler(
        JNIEnv *env, jobject thiz, jint threadCount) {
    g_scheduler.set_thread_count(threadCount);
    g_scheduler.start();
    LOGI("DecodeNdkBridge: Scheduler initialized with %d threads", threadCount);
}

JNIEXPORT void JNICALL
Java_com_alcedo_studio_ndk_DecodeNdkBridge_nativeShutdownScheduler(
        JNIEnv *env, jobject thiz) {
    g_scheduler.stop();
    LOGI("DecodeNdkBridge: Scheduler shutdown");
}

JNIEXPORT void JNICALL
Java_com_alcedo_studio_ndk_DecodeNdkBridge_nativeSetProgressCallback(
        JNIEnv *env, jobject thiz, jobject callback) {
    if (callback == nullptr) {
        g_scheduler.set_progress_callback(nullptr);
        g_scheduler.set_complete_callback(nullptr);
        return;
    }

    // Create a global reference to the callback
    jobject globalCallback = env->NewGlobalRef(callback);
    JavaVM* jvm;
    env->GetJavaVM(&jvm);

    g_scheduler.set_progress_callback([jvm, globalCallback](uint64_t taskId, float progress, const std::string& stage) {
        JNIEnv* e;
        bool attached = false;
        jint ret = jvm->GetEnv(reinterpret_cast<void**>(&e), JNI_VERSION_1_6);
        if (ret == JNI_EDETACHED) {
            jvm->AttachCurrentThread(&e, nullptr);
            attached = true;
        }

        jclass cls = e->GetObjectClass(globalCallback);
        jmethodID mid = e->GetMethodID(cls, "onProgress", "(JFLjava/lang/String;)V");
        if (mid) {
            jstring jstage = e->NewStringUTF(stage.c_str());
            e->CallVoidMethod(globalCallback, mid, static_cast<jlong>(taskId), progress, jstage);
            e->DeleteLocalRef(jstage);
        }
        e->DeleteLocalRef(cls);

        if (attached) jvm->DetachCurrentThread();
    });

    g_scheduler.set_complete_callback([jvm, globalCallback](const DecodeResult& result) {
        JNIEnv* e;
        bool attached = false;
        jint ret = jvm->GetEnv(reinterpret_cast<void**>(&e), JNI_VERSION_1_6);
        if (ret == JNI_EDETACHED) {
            jvm->AttachCurrentThread(&e, nullptr);
            attached = true;
        }

        jclass cls = e->GetObjectClass(globalCallback);
        jmethodID mid = e->GetMethodID(cls, "onComplete", "(JZLjava/lang/String;)V");
        if (mid) {
            jstring jerr = e->NewStringUTF(result.error_message.c_str());
            e->CallVoidMethod(globalCallback, mid, static_cast<jlong>(result.task_id),
                              result.success ? JNI_TRUE : JNI_FALSE, jerr);
            e->DeleteLocalRef(jerr);
        }
        e->DeleteLocalRef(cls);

        if (attached) jvm->DetachCurrentThread();
    });
}

// ============================================================
// Operator Factory - Individual Operator Application
// ============================================================

JNIEXPORT jfloatArray JNICALL
Java_com_alcedo_studio_domain_service_NativePipelineBridge_nativeApplyBlackOp(
        JNIEnv *env, jobject thiz,
        jfloatArray input, jint width, jint height, jint channels,
        jfloat blackPoint) {
    jsize len = env->GetArrayLength(input);
    jfloat *pixels = env->GetFloatArrayElements(input, nullptr);
    if (!pixels) return nullptr;
    std::vector<float> float_pixels(pixels, pixels + len);
    env->ReleaseFloatArrayElements(input, pixels, JNI_ABORT);
    alcedo::BlackOperator op(blackPoint);
    op.Apply(float_pixels.data(), width, height, channels);
    jfloatArray result = env->NewFloatArray(len);
    if (!result || env->ExceptionCheck()) {
        LOGE("Failed to create BlackOp result array");
        if (env->ExceptionCheck()) { env->ExceptionDescribe(); env->ExceptionClear(); }
        return nullptr;
    }
    env->SetFloatArrayRegion(result, 0, len, float_pixels.data());
    return result;
}

JNIEXPORT jfloatArray JNICALL
Java_com_alcedo_studio_domain_service_NativePipelineBridge_nativeApplyWhiteOp(
        JNIEnv *env, jobject thiz,
        jfloatArray input, jint width, jint height, jint channels,
        jfloat whitePoint) {
    jsize len = env->GetArrayLength(input);
    jfloat *pixels = env->GetFloatArrayElements(input, nullptr);
    if (!pixels) return nullptr;
    std::vector<float> float_pixels(pixels, pixels + len);
    env->ReleaseFloatArrayElements(input, pixels, JNI_ABORT);
    alcedo::WhiteOperator op(whitePoint);
    op.Apply(float_pixels.data(), width, height, channels);
    jfloatArray result = env->NewFloatArray(len);
    if (!result || env->ExceptionCheck()) {
        LOGE("Failed to create WhiteOp result array");
        if (env->ExceptionCheck()) { env->ExceptionDescribe(); env->ExceptionClear(); }
        return nullptr;
    }
    env->SetFloatArrayRegion(result, 0, len, float_pixels.data());
    return result;
}

JNIEXPORT jfloatArray JNICALL
Java_com_alcedo_studio_domain_service_NativePipelineBridge_nativeApplyShadowOp(
        JNIEnv *env, jobject thiz,
        jfloatArray input, jint width, jint height, jint channels,
        jfloat shadowAmount) {
    jsize len = env->GetArrayLength(input);
    jfloat *pixels = env->GetFloatArrayElements(input, nullptr);
    if (!pixels) return nullptr;
    std::vector<float> float_pixels(pixels, pixels + len);
    env->ReleaseFloatArrayElements(input, pixels, JNI_ABORT);
    alcedo::ShadowOperator op(shadowAmount);
    op.Apply(float_pixels.data(), width, height, channels);
    jfloatArray result = env->NewFloatArray(len);
    if (!result || env->ExceptionCheck()) {
        LOGE("Failed to create ShadowOp result array");
        if (env->ExceptionCheck()) { env->ExceptionDescribe(); env->ExceptionClear(); }
        return nullptr;
    }
    env->SetFloatArrayRegion(result, 0, len, float_pixels.data());
    return result;
}

JNIEXPORT jfloatArray JNICALL
Java_com_alcedo_studio_domain_service_NativePipelineBridge_nativeApplyHighlightOp(
        JNIEnv *env, jobject thiz,
        jfloatArray input, jint width, jint height, jint channels,
        jfloat highlightAmount) {
    jsize len = env->GetArrayLength(input);
    jfloat *pixels = env->GetFloatArrayElements(input, nullptr);
    if (!pixels) return nullptr;
    std::vector<float> float_pixels(pixels, pixels + len);
    env->ReleaseFloatArrayElements(input, pixels, JNI_ABORT);
    alcedo::HighlightOperator op(highlightAmount);
    op.Apply(float_pixels.data(), width, height, channels);
    jfloatArray result = env->NewFloatArray(len);
    if (!result || env->ExceptionCheck()) {
        LOGE("Failed to create HighlightOp result array");
        if (env->ExceptionCheck()) { env->ExceptionDescribe(); env->ExceptionClear(); }
        return nullptr;
    }
    env->SetFloatArrayRegion(result, 0, len, float_pixels.data());
    return result;
}

JNIEXPORT jfloatArray JNICALL
Java_com_alcedo_studio_domain_service_NativePipelineBridge_nativeApplyCrop(
        JNIEnv *env, jobject thiz,
        jfloatArray input, jint width, jint height, jint channels,
        jint left, jint top, jint right, jint bottom) {
    jsize len = env->GetArrayLength(input);
    jfloat *pixels = env->GetFloatArrayElements(input, nullptr);
    if (!pixels) return nullptr;
    std::vector<float> float_pixels(pixels, pixels + len);
    env->ReleaseFloatArrayElements(input, pixels, JNI_ABORT);
    int out_w = right - left;
    int out_h = bottom - top;
    if (out_w <= 0 || out_h <= 0 || left < 0 || top < 0 || right > width || bottom > height) {
        LOGE("Invalid crop region: left=%d top=%d right=%d bottom=%d width=%d height=%d",
             left, top, right, bottom, width, height);
        return nullptr;
    }
    std::vector<float> cropped(out_w * out_h * channels);
    alcedo::CropRotateOperator::apply_crop(float_pixels.data(), width, height, channels,
                                           left, top, right, bottom, cropped.data());
    jfloatArray result = env->NewFloatArray(cropped.size());
    if (!result || env->ExceptionCheck()) {
        LOGE("Failed to create crop result array");
        if (env->ExceptionCheck()) { env->ExceptionDescribe(); env->ExceptionClear(); }
        return nullptr;
    }
    env->SetFloatArrayRegion(result, 0, cropped.size(), cropped.data());
    return result;
}

JNIEXPORT jfloatArray JNICALL
Java_com_alcedo_studio_domain_service_NativePipelineBridge_nativeApplyRotate(
        JNIEnv *env, jobject thiz,
        jfloatArray input, jint width, jint height, jint channels,
        jint angle) {
    jsize len = env->GetArrayLength(input);
    jfloat *pixels = env->GetFloatArrayElements(input, nullptr);
    if (!pixels) return nullptr;
    std::vector<float> float_pixels(pixels, pixels + len);
    env->ReleaseFloatArrayElements(input, pixels, JNI_ABORT);
    int out_w = width, out_h = height;
    if (angle == 90 || angle == 270) { out_w = height; out_h = width; }
    std::vector<float> rotated(out_w * out_h * channels);
    alcedo::CropRotateOperator::apply_rotate(float_pixels.data(), width, height, channels,
                                              angle, rotated.data());
    jfloatArray result = env->NewFloatArray(rotated.size());
    if (!result || env->ExceptionCheck()) {
        LOGE("Failed to create rotate result array");
        if (env->ExceptionCheck()) { env->ExceptionDescribe(); env->ExceptionClear(); }
        return nullptr;
    }
    env->SetFloatArrayRegion(result, 0, rotated.size(), rotated.data());
    return result;
}

JNIEXPORT jfloatArray JNICALL
Java_com_alcedo_studio_domain_service_NativePipelineBridge_nativeApplyResize(
        JNIEnv *env, jobject thiz,
        jfloatArray input, jint width, jint height, jint channels,
        jint dstWidth, jint dstHeight, jint method) {
    jsize len = env->GetArrayLength(input);
    jfloat *pixels = env->GetFloatArrayElements(input, nullptr);
    if (!pixels) return nullptr;
    std::vector<float> float_pixels(pixels, pixels + len);
    env->ReleaseFloatArrayElements(input, pixels, JNI_ABORT);
    std::vector<float> resized(dstWidth * dstHeight * channels);
    alcedo::ResizeOperator::resize(float_pixels.data(), width, height,
                                    resized.data(), dstWidth, dstHeight,
                                    channels,
                                    method == 0 ? alcedo::ResizeMethod::NEAREST
                                                : alcedo::ResizeMethod::BILINEAR);
    jfloatArray result = env->NewFloatArray(resized.size());
    if (!result || env->ExceptionCheck()) {
        LOGE("Failed to create resize result array");
        if (env->ExceptionCheck()) { env->ExceptionDescribe(); env->ExceptionClear(); }
        return nullptr;
    }
    env->SetFloatArrayRegion(result, 0, resized.size(), resized.data());
    return result;
}

// ============================================================
// Sleeve System JNI Bridge
// ============================================================

static std::shared_ptr<alcedo::SleeveManager> g_sleeve_manager;

JNIEXPORT void JNICALL
Java_com_alcedo_studio_ndk_SleeveNdkBridge_nativeInitializeSleeve(JNIEnv *env, jobject thiz) {
    g_sleeve_manager = std::make_shared<alcedo::SleeveManager>();
    g_sleeve_manager->Initialize();
    LOGI("SleeveManager initialized");
}

JNIEXPORT jint JNICALL
Java_com_alcedo_studio_ndk_SleeveNdkBridge_nativeCreateFolder(
        JNIEnv *env, jobject thiz, jstring path, jstring name) {
    if (!g_sleeve_manager) return -1;
    const char* cpath = env->GetStringUTFChars(path, nullptr);
    const char* cname = env->GetStringUTFChars(name, nullptr);
    auto fs = g_sleeve_manager->GetFilesystem();
    auto element = fs->Create(alcedo::SleeveElementType::Folder, cpath, cname);
    env->ReleaseStringUTFChars(path, cpath);
    env->ReleaseStringUTFChars(name, cname);
    return element ? static_cast<jint>(element->id) : -1;
}

JNIEXPORT jint JNICALL
Java_com_alcedo_studio_ndk_SleeveNdkBridge_nativeCreateFile(
        JNIEnv *env, jobject thiz, jstring path, jstring name) {
    if (!g_sleeve_manager) return -1;
    const char* cpath = env->GetStringUTFChars(path, nullptr);
    const char* cname = env->GetStringUTFChars(name, nullptr);
    auto fs = g_sleeve_manager->GetFilesystem();
    auto element = fs->Create(alcedo::SleeveElementType::File, cpath, cname);
    env->ReleaseStringUTFChars(path, cpath);
    env->ReleaseStringUTFChars(name, cname);
    return element ? static_cast<jint>(element->id) : -1;
}

JNIEXPORT jboolean JNICALL
Java_com_alcedo_studio_ndk_SleeveNdkBridge_nativeDeleteElement(
        JNIEnv *env, jobject thiz, jstring path) {
    if (!g_sleeve_manager) return JNI_FALSE;
    const char* cpath = env->GetStringUTFChars(path, nullptr);
    auto fs = g_sleeve_manager->GetFilesystem();
    bool ok = fs->Delete(cpath);
    env->ReleaseStringUTFChars(path, cpath);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_alcedo_studio_ndk_SleeveNdkBridge_nativeMoveElement(
        JNIEnv *env, jobject thiz, jstring src, jstring dst) {
    if (!g_sleeve_manager) return JNI_FALSE;
    const char* csrc = env->GetStringUTFChars(src, nullptr);
    const char* cdst = env->GetStringUTFChars(dst, nullptr);
    auto fs = g_sleeve_manager->GetFilesystem();
    bool ok = fs->Move(csrc, cdst);
    env->ReleaseStringUTFChars(src, csrc);
    env->ReleaseStringUTFChars(dst, cdst);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_alcedo_studio_ndk_SleeveNdkBridge_nativeCopyElement(
        JNIEnv *env, jobject thiz, jstring src, jstring dst) {
    if (!g_sleeve_manager) return JNI_FALSE;
    const char* csrc = env->GetStringUTFChars(src, nullptr);
    const char* cdst = env->GetStringUTFChars(dst, nullptr);
    auto fs = g_sleeve_manager->GetFilesystem();
    bool ok = fs->Copy(csrc, cdst);
    env->ReleaseStringUTFChars(src, csrc);
    env->ReleaseStringUTFChars(dst, cdst);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jintArray JNICALL
Java_com_alcedo_studio_ndk_SleeveNdkBridge_nativeListFolder(
        JNIEnv *env, jobject thiz, jstring path) {
    if (!g_sleeve_manager) return nullptr;
    const char* cpath = env->GetStringUTFChars(path, nullptr);
    auto fs = g_sleeve_manager->GetFilesystem();
    auto ids = fs->ListFolderContent(cpath);
    env->ReleaseStringUTFChars(path, cpath);
    jintArray result = env->NewIntArray(ids.size());
    if (!result || env->ExceptionCheck()) {
        LOGE("Failed to create list folder int array");
        if (env->ExceptionCheck()) { env->ExceptionDescribe(); env->ExceptionClear(); }
        return nullptr;
    }
    std::vector<jint> jint_ids(ids.begin(), ids.end());
    env->SetIntArrayRegion(result, 0, ids.size(), jint_ids.data());
    return result;
}

JNIEXPORT jstring JNICALL
Java_com_alcedo_studio_ndk_SleeveNdkBridge_nativeResolvePath(
        JNIEnv *env, jobject thiz, jstring path) {
    if (!g_sleeve_manager) return env->NewStringUTF("");
    const char* cpath = env->GetStringUTFChars(path, nullptr);
    auto fs = g_sleeve_manager->GetFilesystem();
    auto elem = fs->Get(cpath);
    std::string json = "{}";
    if (elem) {
        char buf[512];
        snprintf(buf, sizeof(buf),
            "{\"id\":%u,\"name\":\"%s\",\"type\":%d,\"sync\":%d,\"pinned\":%s}",
            elem->id, elem->name.c_str(), static_cast<int>(elem->type),
            static_cast<int>(elem->sync_flag),
            elem->pinned ? "true" : "false");
        json = buf;
    }
    env->ReleaseStringUTFChars(path, cpath);
    return env->NewStringUTF(json.c_str());
}

// ============================================================
// Infrastructure JNI Bridge
// ============================================================

JNIEXPORT jlong JNICALL
Java_com_alcedo_studio_ndk_AlcedoNdkBridge_nativeGenerateId(JNIEnv *env, jobject thiz) {
    return static_cast<jlong>(alcedo::IDGenerator::GenerateID());
}

JNIEXPORT jlong JNICALL
Java_com_alcedo_studio_ndk_AlcedoNdkBridge_nativeGetTimestampMillis(JNIEnv *env, jobject thiz) {
    return static_cast<jlong>(alcedo::TimeProvider::NowMillis());
}

JNIEXPORT jlong JNICALL
Java_com_alcedo_studio_ndk_AlcedoNdkBridge_nativeGetTimestampMicros(JNIEnv *env, jobject thiz) {
    return static_cast<jlong>(alcedo::TimeProvider::NowMicros());
}

JNIEXPORT void JNICALL
Java_com_alcedo_studio_ndk_AlcedoNdkBridge_nativeSetLogLevel(JNIEnv *env, jobject thiz, jint level) {
    alcedo::set_log_level(static_cast<int>(level));
}

} // extern "C"