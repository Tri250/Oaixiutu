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

#define LOG_TAG "AlcedoCore"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

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

    PipelineParams pipeline_params;

    // Parse params array (complex structure, simplified)
    int p_idx = 0;
    pipeline_params.exposure = params[p_idx++];
    pipeline_params.contrast = params[p_idx++];
    pipeline_params.saturation = params[p_idx++];
    pipeline_params.vibrance = params[p_idx++];
    pipeline_params.highlights = params[p_idx++];
    pipeline_params.shadows = params[p_idx++];
    pipeline_params.midtones = params[p_idx++];
    pipeline_params.white_balance_temp = params[p_idx++];
    pipeline_params.white_balance_tint = params[p_idx++];
    pipeline_params.sharpen = params[p_idx++];
    pipeline_params.clarity = params[p_idx++];
    pipeline_params.clarity_radius = params[p_idx++];
    pipeline_params.film_grain = params[p_idx++];
    pipeline_params.halation_intensity = params[p_idx++];
    pipeline_params.halation_threshold = params[p_idx++];
    pipeline_params.halation_spread = params[p_idx++];
    pipeline_params.halation_red_bias = params[p_idx++];
    pipeline_params.sigmoid_contrast = params[p_idx++];

    // Color wheels
    pipeline_params.color_wheel_lift[0] = params[p_idx++];
    pipeline_params.color_wheel_lift[1] = params[p_idx++];
    pipeline_params.color_wheel_lift[2] = params[p_idx++];
    pipeline_params.color_wheel_gamma[0] = params[p_idx++];
    pipeline_params.color_wheel_gamma[1] = params[p_idx++];
    pipeline_params.color_wheel_gamma[2] = params[p_idx++];
    pipeline_params.color_wheel_gain[0] = params[p_idx++];
    pipeline_params.color_wheel_gain[1] = params[p_idx++];
    pipeline_params.color_wheel_gain[2] = params[p_idx++];

    // Tint
    pipeline_params.tint_highlight_hue = params[p_idx++];
    pipeline_params.tint_highlight_strength = params[p_idx++];
    pipeline_params.tint_shadow_hue = params[p_idx++];
    pipeline_params.tint_shadow_strength = params[p_idx++];
    pipeline_params.tint_balance = params[p_idx++];

    // Display transform
    pipeline_params.display_transform.color_science = static_cast<int>(params[p_idx++]);
    pipeline_params.display_transform.eotf = static_cast<int>(params[p_idx++]);
    pipeline_params.display_transform.peak_luminance = params[p_idx++];
    pipeline_params.display_transform.display_color_space = static_cast<int>(params[p_idx++]);

    env->ReleaseFloatArrayElements(paramsArray, params, JNI_ABORT);

    // Create float buffer
    std::vector<float> float_pixels(pixels, pixels + len);
    env->ReleaseFloatArrayElements(input, pixels, JNI_ABORT);

    // Run pipeline
    PipelineService pipeline;
    pipeline.process(float_pixels.data(), width, height, channels, pipeline_params);

    // Return result
    jfloatArray result = env->NewFloatArray(len);
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

    PipelineService pipeline;
    pipeline.process(float_pixels.data(), width, height, 4, params);

    // Convert back to int
    jintArray result = env->NewIntArray(len);
    jint *outPixels = new jint[len];
    for (int i = 0; i < len; ++i) {
        int idx = i * 4;
        int a = static_cast<int>(std::max(0.0f, std::min(1.0f, float_pixels[idx + 3])) * 255.0f);
        int r = static_cast<int>(std::max(0.0f, std::min(1.0f, float_pixels[idx])) * 255.0f);
        int g = static_cast<int>(std::max(0.0f, std::min(1.0f, float_pixels[idx + 1])) * 255.0f);
        int b = static_cast<int>(std::max(0.0f, std::min(1.0f, float_pixels[idx + 2])) * 255.0f);
        outPixels[i] = (a << 24) | (r << 16) | (g << 8) | b;
    }
    env->SetIntArrayRegion(result, 0, len, outPixels);
    delete[] outPixels;
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

    PipelineService pipeline;
    pipeline.decode_raw(
        reinterpret_cast<const uint16_t*>(raw),
        rawWidth, rawHeight,
        output_rgb.data(), rawWidth, rawHeight,
        raw_params);

    env->ReleaseShortArrayElements(rawData, raw, JNI_ABORT);

    jfloatArray result = env->NewFloatArray(output_size);
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
    LOGI("Decoding RAW: %s (demosaic=%d)", path, demosaic);

    // Integer array placeholder - real implementation would use LibRaw
    jintArray result = env->NewIntArray(0);

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
    env->SetFloatArrayRegion(result, 0, len, float_pixels.data());
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
    env->SetFloatArrayRegion(camMatrix, 0, 9, info.color_matrix);

    jfloatArray dayWb = env->NewFloatArray(3);
    float dayWbArr[3] = {info.daylight_mult[0], info.daylight_mult[1], info.daylight_mult[2]};
    env->SetFloatArrayRegion(dayWb, 0, 3, dayWbArr);

    jfloatArray tungWb = env->NewFloatArray(3);
    float tungWbArr[3] = {info.tungsten_mult[0], info.tungsten_mult[1], info.tungsten_mult[2]};
    env->SetFloatArrayRegion(tungWb, 0, 3, tungWbArr);

    jfloatArray camWb = env->NewFloatArray(3);
    float camWbArr[3] = {info.camera_wb_mult[0], info.camera_wb_mult[1], info.camera_wb_mult[2]};
    env->SetFloatArrayRegion(camWb, 0, 3, camWbArr);

    jintArray cfaPattern = env->NewIntArray(4);
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
        env->SetFloatArrayRegion(jrgb, 0, result.float_rgb_data.size(), result.float_rgb_data.data());
    }

    // Short RGB data
    jshortArray jrgbShort = nullptr;
    if (!result.rgb_data.empty()) {
        jrgbShort = env->NewShortArray(result.rgb_data.size());
        env->SetShortArrayRegion(jrgbShort, 0, result.rgb_data.size(),
                                 reinterpret_cast<const jshort*>(result.rgb_data.data()));
    }

    // CFA data
    jshortArray jcfa = nullptr;
    if (!result.raw_cfa_data.empty()) {
        jcfa = env->NewShortArray(result.raw_cfa_data.size());
        env->SetShortArrayRegion(jcfa, 0, result.raw_cfa_data.size(),
                                 reinterpret_cast<const jshort*>(result.raw_cfa_data.data()));
    }

    // Thumbnail data
    jbyteArray jthumb = nullptr;
    if (!result.jpeg_thumbnail.empty()) {
        jthumb = env->NewByteArray(result.jpeg_thumbnail.size());
        env->SetByteArrayRegion(jthumb, 0, result.jpeg_thumbnail.size(),
                                reinterpret_cast<const jbyte*>(result.jpeg_thumbnail.data()));
    }

    // Preview data
    jbyteArray jpreview = nullptr;
    if (!result.jpeg_preview.empty()) {
        jpreview = env->NewByteArray(result.jpeg_preview.size());
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
    env->SetFloatArrayRegion(jcamMat, 0, 9, result.image_info.color_matrix);
    jfloatArray jdayWb = env->NewFloatArray(3);
    float dayWb[3] = {result.image_info.daylight_mult[0], result.image_info.daylight_mult[1], result.image_info.daylight_mult[2]};
    env->SetFloatArrayRegion(jdayWb, 0, 3, dayWb);
    jfloatArray jtungWb = env->NewFloatArray(3);
    float tungWb[3] = {result.image_info.tungsten_mult[0], result.image_info.tungsten_mult[1], result.image_info.tungsten_mult[2]};
    env->SetFloatArrayRegion(jtungWb, 0, 3, tungWb);
    jfloatArray jcamWb = env->NewFloatArray(3);
    float camWb[3] = {result.image_info.camera_wb_mult[0], result.image_info.camera_wb_mult[1], result.image_info.camera_wb_mult[2]};
    env->SetFloatArrayRegion(jcamWb, 0, 3, camWb);
    jintArray jcfaPat = env->NewIntArray(4);
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

    jobject jresult = env->NewObject(cls, ctor,
        jrgb, jrgbShort, jcfa,
        result.width, result.height,
        jinfo,
        jthumb, result.thumbnail_width, result.thumbnail_height,
        jpreview, result.preview_width, result.preview_height);

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
    env->SetFloatArrayRegion(jrgb, 0, result.float_rgb_data.size(), result.float_rgb_data.data());

    // Simplified return - just the float RGB data
    jclass cls = env->FindClass("com/alcedo/studio/domain/service/NativeDecodeResult");
    jmethodID ctor = env->GetMethodID(cls, "<init>", "([F[S[SIII[BII[BIILcom/alcedo/studio/domain/service/DecodeService$RawImageInfo;)V");

    jobject jresult = env->NewObject(cls, ctor,
        jrgb, nullptr, nullptr, result.width, result.height, nullptr,
        nullptr, 0, 0, nullptr, 0, 0);
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
    env->SetByteArrayRegion(jdata, 0, result.data.size(),
                            reinterpret_cast<const jbyte*>(result.data.data()));

    jobject jresult = env->NewObject(cls, ctor, jdata, result.width, result.height,
                                      result.is_embedded ? JNI_TRUE : JNI_FALSE);
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

} // extern "C"