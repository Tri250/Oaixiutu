#include "dehaze_op.h"
#include <cmath>
#include <algorithm>
#include <cstring>
#include <vector>

namespace alcedo {

void DehazeOperator::compute_dark_channel(const float* pixels, int width, int height, int channels,
                                           int radius, float* dark_channel) {
    // Step 1: Compute per-pixel min channel: min(R, G, B)
    size_t pixel_count = static_cast<size_t>(width) * height;
    std::vector<float> min_channel(pixel_count);
    for (size_t i = 0; i < pixel_count; ++i) {
        int idx = static_cast<int>(i) * channels;
        min_channel[i] = std::min({pixels[idx], pixels[idx + 1], pixels[idx + 2]});
    }

    // Step 2: Minimum filter over local patch (box min) for dark channel
    // Use separable min filter: horizontal then vertical
    std::vector<float> tmp(pixel_count);
    // Horizontal pass
    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            float min_val = 1.0f;
            int x0 = std::max(0, x - radius);
            int x1 = std::min(width - 1, x + radius);
            for (int kx = x0; kx <= x1; ++kx) {
                min_val = std::min(min_val, min_channel[y * width + kx]);
            }
            tmp[y * width + x] = min_val;
        }
    }
    // Vertical pass
    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            float min_val = 1.0f;
            int y0 = std::max(0, y - radius);
            int y1 = std::min(height - 1, y + radius);
            for (int ky = y0; ky <= y1; ++ky) {
                min_val = std::min(min_val, tmp[ky * width + x]);
            }
            dark_channel[y * width + x] = min_val;
        }
    }
}

void DehazeOperator::estimate_atmospheric_light(const float* pixels, int width, int height, int channels,
                                                 const float* dark_channel,
                                                 float& A_r, float& A_g, float& A_b) {
    // Find top 0.1% brightest pixels in dark channel
    size_t pixel_count = static_cast<size_t>(width) * height;
    size_t top_count = std::max(static_cast<size_t>(1), pixel_count / 1000);

    // Collect dark channel values with indices
    std::vector<std::pair<float, size_t>> indexed(pixel_count);
    for (size_t i = 0; i < pixel_count; ++i) {
        indexed[i] = {dark_channel[i], i};
    }
    // Partial sort to find top 0.1%
    std::nth_element(indexed.begin(), indexed.begin() + static_cast<long>(pixel_count - top_count),
                     indexed.end());
    // Average the pixel values at those positions
    A_r = 0.0f; A_g = 0.0f; A_b = 0.0f;
    for (size_t k = pixel_count - top_count; k < pixel_count; ++k) {
        size_t idx = indexed[k].second * channels;
        A_r += pixels[idx];
        A_g += pixels[idx + 1];
        A_b += pixels[idx + 2];
    }
    A_r /= static_cast<float>(top_count);
    A_g /= static_cast<float>(top_count);
    A_b /= static_cast<float>(top_count);
    // Ensure atmospheric light is at least a small positive value
    A_r = std::max(0.01f, A_r);
    A_g = std::max(0.01f, A_g);
    A_b = std::max(0.01f, A_b);
}

void DehazeOperator::box_blur(const float* src, float* dst, int width, int height, int radius) {
    // Separable box blur: horizontal then vertical
    std::vector<float> tmp(static_cast<size_t>(width) * height);
    float scale = 1.0f / (2.0f * radius + 1.0f);
    // Horizontal pass with sliding window
    for (int y = 0; y < height; ++y) {
        float sum = 0.0f;
        for (int kx = -radius; kx <= radius; ++kx) {
            int sx = std::max(0, std::min(width - 1, kx));
            sum += src[y * width + sx];
        }
        tmp[y * width] = sum * scale;
        for (int x = 1; x < width; ++x) {
            int left = std::max(0, std::min(width - 1, x - radius - 1));
            int right = std::max(0, std::min(width - 1, x + radius));
            sum += src[y * width + right] - src[y * width + left];
            tmp[y * width + x] = sum * scale;
        }
    }
    // Vertical pass with sliding window
    for (int x = 0; x < width; ++x) {
        float sum = 0.0f;
        for (int ky = -radius; ky <= radius; ++ky) {
            int sy = std::max(0, std::min(height - 1, ky));
            sum += tmp[sy * width + x];
        }
        dst[x] = sum * scale;
        for (int y = 1; y < height; ++y) {
            int top = std::max(0, std::min(height - 1, y - radius - 1));
            int bottom = std::max(0, std::min(height - 1, y + radius));
            sum += tmp[bottom * width + x] - tmp[top * width + x];
            dst[y * width + x] = sum * scale;
        }
    }
}

void DehazeOperator::apply_rgb(float* pixels, int width, int height, float amount, int radius) {
    if (amount == 0.0f || width <= 0 || height <= 0) return;
    int channels = 3;
    size_t pixel_count = static_cast<size_t>(width) * height;

    // Compute dark channel
    std::vector<float> dark_channel(pixel_count);
    compute_dark_channel(pixels, width, height, channels, radius, dark_channel.data());

    // Estimate atmospheric light
    float A_r, A_g, A_b;
    estimate_atmospheric_light(pixels, width, height, channels, dark_channel.data(), A_r, A_g, A_b);

    // Estimate transmission: t(x) = 1 - omega * dark_channel(x) / A
    // Use per-channel min of (I/A) for robust estimation
    float omega = 0.95f; // standard dehazing strength factor
    std::vector<float> transmission(pixel_count);
    for (size_t i = 0; i < pixel_count; ++i) {
        int idx = static_cast<int>(i) * channels;
        float min_ratio = std::min({pixels[idx] / A_r, pixels[idx + 1] / A_g, pixels[idx + 2] / A_b});
        transmission[i] = 1.0f - omega * min_ratio;
    }

    // Refine transmission with box filter (simple approximation of guided filter)
    int refine_radius = std::max(1, radius / 2);
    std::vector<float> refined_transmission(pixel_count);
    box_blur(transmission.data(), refined_transmission.data(), width, height, refine_radius);
    // Second pass for smoother result
    box_blur(refined_transmission.data(), transmission.data(), width, height, refine_radius);

    // Apply dehazing: J(x) = (I(x) - A) / max(t(x), t0) + A
    // For negative amounts (add haze): interpolate towards hazy version
    float t0 = 0.1f; // minimum transmission to avoid division by zero

    if (amount > 0.0f) {
        // Remove haze
        float strength = amount; // 0 to 1
        for (size_t i = 0; i < pixel_count; ++i) {
            int idx = static_cast<int>(i) * channels;
            float t = std::max(transmission[i], t0);
            float inv_t = 1.0f / t;
            // Dehazed result
            float J_r = (pixels[idx] - A_r) * inv_t + A_r;
            float J_g = (pixels[idx + 1] - A_g) * inv_t + A_g;
            float J_b = (pixels[idx + 2] - A_b) * inv_t + A_b;
            // Blend original with dehazed based on strength
            pixels[idx]     = std::clamp(pixels[idx]     + (J_r - pixels[idx])     * strength, 0.0f, 1.0f);
            pixels[idx + 1] = std::clamp(pixels[idx + 1] + (J_g - pixels[idx + 1]) * strength, 0.0f, 1.0f);
            pixels[idx + 2] = std::clamp(pixels[idx + 2] + (J_b - pixels[idx + 2]) * strength, 0.0f, 1.0f);
        }
    } else {
        // Add haze (negative amount)
        float strength = -amount; // 0 to 1
        // Hazy version: I_hazy = I * (1 - strength) + A * strength * (1 - t)
        for (size_t i = 0; i < pixel_count; ++i) {
            int idx = static_cast<int>(i) * channels;
            float t = std::max(transmission[i], t0);
            // Simulate adding haze by blending towards hazy version
            float haze_factor = strength * (1.0f - t);
            pixels[idx]     = std::clamp(pixels[idx]     * (1.0f - haze_factor) + A_r * haze_factor, 0.0f, 1.0f);
            pixels[idx + 1] = std::clamp(pixels[idx + 1] * (1.0f - haze_factor) + A_g * haze_factor, 0.0f, 1.0f);
            pixels[idx + 2] = std::clamp(pixels[idx + 2] * (1.0f - haze_factor) + A_b * haze_factor, 0.0f, 1.0f);
        }
    }
}

void DehazeOperator::apply_rgba(float* pixels, int width, int height, float amount, int radius) {
    if (amount == 0.0f || width <= 0 || height <= 0) return;
    int channels = 4;
    size_t pixel_count = static_cast<size_t>(width) * height;

    // Compute dark channel (only from RGB channels)
    std::vector<float> dark_channel(pixel_count);
    compute_dark_channel(pixels, width, height, channels, radius, dark_channel.data());

    // Estimate atmospheric light
    float A_r, A_g, A_b;
    estimate_atmospheric_light(pixels, width, height, channels, dark_channel.data(), A_r, A_g, A_b);

    // Estimate transmission
    float omega = 0.95f;
    std::vector<float> transmission(pixel_count);
    for (size_t i = 0; i < pixel_count; ++i) {
        int idx = static_cast<int>(i) * channels;
        float min_ratio = std::min({pixels[idx] / A_r, pixels[idx + 1] / A_g, pixels[idx + 2] / A_b});
        transmission[i] = 1.0f - omega * min_ratio;
    }

    // Refine transmission
    int refine_radius = std::max(1, radius / 2);
    std::vector<float> refined_transmission(pixel_count);
    box_blur(transmission.data(), refined_transmission.data(), width, height, refine_radius);
    box_blur(refined_transmission.data(), transmission.data(), width, height, refine_radius);

    float t0 = 0.1f;

    if (amount > 0.0f) {
        float strength = amount;
        for (size_t i = 0; i < pixel_count; ++i) {
            int idx = static_cast<int>(i) * channels;
            float t = std::max(transmission[i], t0);
            float inv_t = 1.0f / t;
            float J_r = (pixels[idx] - A_r) * inv_t + A_r;
            float J_g = (pixels[idx + 1] - A_g) * inv_t + A_g;
            float J_b = (pixels[idx + 2] - A_b) * inv_t + A_b;
            pixels[idx]     = std::clamp(pixels[idx]     + (J_r - pixels[idx])     * strength, 0.0f, 1.0f);
            pixels[idx + 1] = std::clamp(pixels[idx + 1] + (J_g - pixels[idx + 1]) * strength, 0.0f, 1.0f);
            pixels[idx + 2] = std::clamp(pixels[idx + 2] + (J_b - pixels[idx + 2]) * strength, 0.0f, 1.0f);
            // Alpha unchanged
        }
    } else {
        float strength = -amount;
        for (size_t i = 0; i < pixel_count; ++i) {
            int idx = static_cast<int>(i) * channels;
            float t = std::max(transmission[i], t0);
            float haze_factor = strength * (1.0f - t);
            pixels[idx]     = std::clamp(pixels[idx]     * (1.0f - haze_factor) + A_r * haze_factor, 0.0f, 1.0f);
            pixels[idx + 1] = std::clamp(pixels[idx + 1] * (1.0f - haze_factor) + A_g * haze_factor, 0.0f, 1.0f);
            pixels[idx + 2] = std::clamp(pixels[idx + 2] * (1.0f - haze_factor) + A_b * haze_factor, 0.0f, 1.0f);
            // Alpha unchanged
        }
    }
}

} // namespace alcedo
