#include "halation_op.h"
#include <cmath>
#include <algorithm>
#include <vector>

namespace alcedo {

static constexpr float kLumR = 0.2126f;
static constexpr float kLumG = 0.7152f;
static constexpr float kLumB = 0.0722f;

void HalationOperator::box_blur_h(float* src, float* dst, int width, int height, int radius) {
    float scale = 1.0f / (2.0f * radius + 1.0f);
    for (int y = 0; y < height; ++y) {
        float sum = 0.0f;
        for (int kx = -radius; kx <= radius; ++kx) {
            int sx = std::max(0, std::min(width - 1, kx));
            sum += src[y * width + sx];
        }
        dst[y * width] = sum * scale;

        for (int x = 1; x < width; ++x) {
            int left = std::max(0, std::min(width - 1, x - radius - 1));
            int right = std::max(0, std::min(width - 1, x + radius));
            sum += src[y * width + right];
            sum -= src[y * width + left];
            dst[y * width + x] = sum * scale;
        }
    }
}

void HalationOperator::box_blur_v(float* src, float* dst, int width, int height, int radius) {
    float scale = 1.0f / (2.0f * radius + 1.0f);
    for (int x = 0; x < width; ++x) {
        float sum = 0.0f;
        for (int ky = -radius; ky <= radius; ++ky) {
            int sy = std::max(0, std::min(height - 1, ky));
            sum += src[sy * width + x];
        }
        dst[x] = sum * scale;

        for (int y = 1; y < height; ++y) {
            int top = std::max(0, std::min(height - 1, y - radius - 1));
            int bottom = std::max(0, std::min(height - 1, y + radius));
            sum += src[bottom * width + x];
            sum -= src[top * width + x];
            dst[y * width + x] = sum * scale;
        }
    }
}

void HalationOperator::box_blur_rgb(float* src, float* dst, int width, int height, int radius) {
    size_t total = static_cast<size_t>(width) * height;
    std::vector<float> tmp(total);
    // Blur each channel independently
    for (int c = 0; c < 3; ++c) {
        std::vector<float> ch_src(total);
        std::vector<float> ch_tmp(total);
        for (int i = 0; i < total; ++i) {
            ch_src[i] = src[i * 3 + c];
        }
        box_blur_h(ch_src.data(), ch_tmp.data(), width, height, radius);
        box_blur_v(ch_tmp.data(), ch_src.data(), width, height, radius);
        for (int i = 0; i < total; ++i) {
            dst[i * 3 + c] = ch_src[i];
        }
    }
}

void HalationOperator::apply_rgb(float* pixels, int width, int height,
                                  float intensity, float threshold,
                                  float spread, float red_bias) {
    if (intensity <= 0.0f) return;
    size_t total = static_cast<size_t>(width) * height;
    int channels = 3;

    // Step 1: Extract highlights (pixels above threshold)
    std::vector<float> highlight_mask(total, 0.0f);
    for (size_t i = 0; i < total; ++i) {
        size_t idx = i * 3;
        float r = pixels[idx];
        float g = pixels[idx + 1];
        float b = pixels[idx + 2];
        float lum = r * kLumR + g * kLumG + b * kLumB;

        if (lum > threshold) {
            // Smooth transition above threshold
            float denom = 1.0f - threshold + 0.001f;
            float t = (lum - threshold) / denom;
            t = std::min(t, 1.0f); // Clamp to prevent overshoot
            highlight_mask[i] = t * t * lum;
        }
    }

    // Step 2: Create RGB highlight image (red-biased)
    std::vector<float> highlight_rgb(total * channels);
    for (size_t i = 0; i < total; ++i) {
        size_t idx = i * 3;
        float mask = highlight_mask[i];
        // Red bias: boost the red channel relative to green and blue
        highlight_rgb[idx]     = pixels[idx] * mask * (1.0f + red_bias);
        highlight_rgb[idx + 1] = pixels[idx + 1] * mask * (1.0f - red_bias * 0.3f);
        highlight_rgb[idx + 2] = pixels[idx + 2] * mask * (1.0f - red_bias * 0.5f);
    }

    // Step 3: Blur the highlight image
    int radius = std::max(1, static_cast<int>(spread));
    std::vector<float> blurred_highlights(total * channels);
    box_blur_rgb(highlight_rgb.data(), blurred_highlights.data(), width, height, radius);

    // Step 4: Blend the blurred highlights back into the image
    float blend = intensity * 0.5f;
    for (size_t i = 0; i < total; ++i) {
        size_t idx = i * 3;
        pixels[idx]     += blurred_highlights[idx] * blend;
        pixels[idx + 1] += blurred_highlights[idx + 1] * blend;
        pixels[idx + 2] += blurred_highlights[idx + 2] * blend;
        // Clamp to valid range
        pixels[idx]     = std::max(0.0f, std::min(1.0f, pixels[idx]));
        pixels[idx + 1] = std::max(0.0f, std::min(1.0f, pixels[idx + 1]));
        pixels[idx + 2] = std::max(0.0f, std::min(1.0f, pixels[idx + 2]));
    }
}

void HalationOperator::apply_rgba(float* pixels, int width, int height,
                                   float intensity, float threshold,
                                   float spread, float red_bias) {
    if (intensity <= 0.0f) return;
    size_t total = static_cast<size_t>(width) * height;
    int channels = 3;

    // Extract highlights
    std::vector<float> highlight_mask(total, 0.0f);
    for (size_t i = 0; i < total; ++i) {
        size_t idx = i * 4;
        float r = pixels[idx];
        float g = pixels[idx + 1];
        float b = pixels[idx + 2];
        float lum = r * kLumR + g * kLumG + b * kLumB;

        if (lum > threshold) {
            float t = (lum - threshold) / (1.0f - threshold + 0.001f);
            highlight_mask[i] = t * t * lum;
        }
    }

    // Create highlight RGB image
    std::vector<float> highlight_rgb(total * channels);
    for (size_t i = 0; i < total; ++i) {
        size_t idx = i * 3;
        size_t src_idx = i * 4;
        float mask = highlight_mask[i];
        highlight_rgb[idx]     = pixels[src_idx] * mask * (1.0f + red_bias);
        highlight_rgb[idx + 1] = pixels[src_idx + 1] * mask * (1.0f - red_bias * 0.3f);
        highlight_rgb[idx + 2] = pixels[src_idx + 2] * mask * (1.0f - red_bias * 0.5f);
    }

    // Blur
    int radius = std::max(1, static_cast<int>(spread));
    std::vector<float> blurred_highlights(total * channels);
    box_blur_rgb(highlight_rgb.data(), blurred_highlights.data(), width, height, radius);

    // Blend
    float blend = intensity * 0.5f;
    for (size_t i = 0; i < total; ++i) {
        size_t idx = i * 4;
        size_t src_idx = i * 3;
        pixels[idx]     += blurred_highlights[src_idx] * blend;
        pixels[idx + 1] += blurred_highlights[src_idx + 1] * blend;
        pixels[idx + 2] += blurred_highlights[src_idx + 2] * blend;
        // Clamp to valid range
        pixels[idx]     = std::max(0.0f, std::min(1.0f, pixels[idx]));
        pixels[idx + 1] = std::max(0.0f, std::min(1.0f, pixels[idx + 1]));
        pixels[idx + 2] = std::max(0.0f, std::min(1.0f, pixels[idx + 2]));
    }
}

} // namespace alcedo