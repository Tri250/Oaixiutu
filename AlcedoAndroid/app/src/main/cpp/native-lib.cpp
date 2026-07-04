#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <cmath>

#define LOG_TAG "AlcedoCore"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

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

    if (pixels == nullptr) {
        LOGE("Failed to get input pixels");
        return nullptr;
    }

    jintArray result = env->NewIntArray(len);
    jint *outPixels = new jint[len];

    // Simple CPU-based pipeline processing
    // Exposure adjustment (scale RGB)
    float exposureScale = 1.0f + exposure * 0.333f;
    exposureScale = fmaxf(0.1f, exposureScale);

    // Contrast
    float contrastScale = 1.0f + contrast;
    float contrastOffset = (-0.5f * contrastScale + 0.5f) * 255.0f;

    // Saturation matrix coefficients
    float sat = 1.0f + saturation;
    float lumR = 0.299f, lumG = 0.587f, lumB = 0.114f;

    // White balance (simplified)
    float tempScale = temperature / 6500.0f;
    float wbR = fminf(fmaxf(tempScale, 0.5f), 2.0f);
    float wbB = fminf(fmaxf(2.0f - tempScale, 0.5f), 2.0f);
    float tintG = tint * 0.01f;

    for (int i = 0; i < len; i++) {
        int pixel = pixels[i];
        int a = (pixel >> 24) & 0xFF;
        int r = (pixel >> 16) & 0xFF;
        int g = (pixel >> 8) & 0xFF;
        int b = pixel & 0xFF;

        // Exposure
        float fr = r * exposureScale;
        float fg = g * exposureScale;
        float fb = b * exposureScale;

        // Contrast
        fr = fr * contrastScale + contrastOffset;
        fg = fg * contrastScale + contrastOffset;
        fb = fb * contrastScale + contrastOffset;

        // Saturation
        float lum = fr * lumR + fg * lumG + fb * lumB;
        fr = lum + (fr - lum) * sat;
        fg = lum + (fg - lum) * sat;
        fb = lum + (fb - lum) * sat;

        // White balance
        fr *= wbR;
        fg += tintG;
        fb *= wbB;

        // Highlights/Shadows (simplified tone mapping)
        if (fr > 128.0f) fr += highlights * 30.0f;
        else fr += shadows * 30.0f;
        if (fg > 128.0f) fg += highlights * 30.0f;
        else fg += shadows * 30.0f;
        if (fb > 128.0f) fb += highlights * 30.0f;
        else fb += shadows * 30.0f;

        // Clamp
        fr = fminf(fmaxf(fr, 0.0f), 255.0f);
        fg = fminf(fmaxf(fg, 0.0f), 255.0f);
        fb = fminf(fmaxf(fb, 0.0f), 255.0f);

        outPixels[i] = (a << 24) | ((int)fr << 16) | ((int)fg << 8) | (int)fb;
    }

    env->ReleaseIntArrayElements(input, pixels, JNI_ABORT);
    env->SetIntArrayRegion(result, 0, len, outPixels);
    delete[] outPixels;

    return result;
}

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

    // Placeholder: real implementation would use libraw or similar
    // For now return empty array as indicator
    jintArray result = env->NewIntArray(0);

    env->ReleaseStringUTFChars(rawPath, path);
    return result;
}

JNIEXPORT jstring JNICALL
Java_com_alcedo_studio_ndk_AlcedoNdkBridge_stringFromJNI(JNIEnv *env, jobject thiz) {
    std::string hello = "Alcedo Studio Core v0.2.6-android (NDK initialized)";
    return env->NewStringUTF(hello.c_str());
}

} // extern "C"
