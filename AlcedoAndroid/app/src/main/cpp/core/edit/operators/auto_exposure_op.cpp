#include "auto_exposure_op.h"
#include <cmath>
#include <algorithm>
#include <android/log.h>

#define LOG_TAG "AlcedoAutoExposure"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace alcedo {

void AutoExposureOperator::compute_histogram(const float* pixels, size_t pixel_count, int channels,
                                              int* histogram, int bins) {
    std::fill(histogram, histogram + bins, 0);

    // Luminance weights (Rec.709)
    const float lumR = 0.2126f;
    const float lumG = 0.7152f;
    const float lumB = 0.0722f;

    for (size_t i = 0; i < pixel_count; ++i) {
        size_t idx = i * channels;
        float r = pixels[idx];
        float g = pixels[idx + 1];
        float b = pixels[idx + 2];

        // Compute luminance
        float lum = r * lumR + g * lumG + b * lumB;
        lum = std::max(0.0f, std::min(1.0f, lum));

        // Map to histogram bin
        int bin = static_cast<int>(lum * (bins - 1));
        bin = std::max(0, std::min(bins - 1, bin));
        histogram[bin]++;
    }
}

float AutoExposureOperator::find_percentile(const int* histogram, int bins, size_t pixel_count,
                                             float percentile) {
    if (pixel_count == 0) return 0.0f;

    float target_count = percentile * static_cast<float>(pixel_count);
    float accumulated = 0.0f;

    for (int i = 0; i < bins; ++i) {
        accumulated += static_cast<float>(histogram[i]);
        if (accumulated >= target_count) {
            // Interpolate within the bin
            float bin_start = static_cast<float>(i) / static_cast<float>(bins - 1);
            float bin_end = static_cast<float>(i + 1) / static_cast<float>(bins - 1);

            // How far into this bin did we reach the percentile?
            float prev_accumulated = accumulated - static_cast<float>(histogram[i]);
            float fraction = (histogram[i] > 0) ?
                (target_count - prev_accumulated) / static_cast<float>(histogram[i]) : 0.0f;
            fraction = std::max(0.0f, std::min(1.0f, fraction));

            return bin_start + fraction * (bin_end - bin_start);
        }
    }

    return 1.0f;
}

float AutoExposureOperator::compute_auto_exposure(const float* pixels, int width, int height,
                                                   int channels,
                                                   float target_percentile,
                                                   float target_luminance) {
    size_t pixel_count = static_cast<size_t>(width) * height;
    if (pixel_count == 0 || !pixels) return 0.0f;

    // Build luminance histogram
    const int HISTOGRAM_BINS = 256;
    int histogram[HISTOGRAM_BINS];
    compute_histogram(pixels, pixel_count, channels, histogram, HISTOGRAM_BINS);

    // Find the luminance value at the target percentile
    float current_luminance = find_percentile(histogram, HISTOGRAM_BINS, pixel_count,
                                               target_percentile);

    if (current_luminance <= 1e-6f) {
        // Image is nearly black, recommend maximum exposure boost
        LOGI("AutoExposure: image very dark, current_lum=%.4f", current_luminance);
        return 5.0f; // +5 stops max
    }

    // Calculate exposure adjustment in stops (EV)
    // target / current = 2^exposure
    // exposure = log2(target / current)
    float exposure = std::log2(target_luminance / current_luminance);

    // Clamp to reasonable range (-5 to +5 stops)
    exposure = std::max(-5.0f, std::min(5.0f, exposure));

    LOGI("AutoExposure: current_lum=%.4f at p%.0f, target=%.4f, exposure=%.2f EV",
         current_luminance, target_percentile * 100.0f, target_luminance, exposure);

    return exposure;
}

void AutoExposureOperator::apply(float* pixels, int width, int height, int channels,
                                  float target_percentile,
                                  float target_luminance) {
    float exposure = compute_auto_exposure(pixels, width, height, channels,
                                            target_percentile, target_luminance);

    if (exposure == 0.0f) return;

    float scale = std::pow(2.0f, exposure);
    size_t pixel_count = static_cast<size_t>(width) * height;

    for (size_t i = 0; i < pixel_count; ++i) {
        size_t idx = i * channels;
        for (int c = 0; c < 3 && c < channels; ++c) {
            pixels[idx + c] = std::max(0.0f, std::min(1.0f, pixels[idx + c] * scale));
        }
    }

    LOGI("AutoExposure applied: %.2f EV (scale=%.3f)", exposure, scale);
}

} // namespace alcedo
