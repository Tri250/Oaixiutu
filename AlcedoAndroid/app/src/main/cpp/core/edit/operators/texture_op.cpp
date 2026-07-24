#include "texture_op.h"
#include <cmath>
#include <algorithm>
#include <cstring>
#include <vector>

namespace alcedo {

void TextureOperator::box_blur_h(float* src, float* dst, int width, int height, int channels, int radius) {
    float scale = 1.0f / (2.0f * radius + 1.0f);
    for (int y = 0; y < height; ++y) {
        int row_base = y * width * channels;
        for (int c = 0; c < channels; ++c) {
            float sum = 0.0f;
            for (int kx = -radius; kx <= radius; ++kx) {
                int sx = std::max(0, std::min(width - 1, kx));
                sum += src[row_base + sx * channels + c];
            }
            dst[row_base + c] = sum * scale;

            for (int x = 1; x < width; ++x) {
                int left = std::max(0, std::min(width - 1, x - radius - 1));
                int right = std::max(0, std::min(width - 1, x + radius));
                sum += src[row_base + right * channels + c];
                sum -= src[row_base + left * channels + c];
                dst[row_base + x * channels + c] = sum * scale;
            }
        }
    }
}

void TextureOperator::box_blur_v(float* src, float* dst, int width, int height, int channels, int radius) {
    float scale = 1.0f / (2.0f * radius + 1.0f);
    int stride = width * channels;
    for (int x = 0; x < width; ++x) {
        for (int c = 0; c < channels; ++c) {
            int col_offset = x * channels + c;
            float sum = 0.0f;
            for (int ky = -radius; ky <= radius; ++ky) {
                int sy = std::max(0, std::min(height - 1, ky));
                sum += src[sy * stride + col_offset];
            }
            dst[col_offset] = sum * scale;

            for (int y = 1; y < height; ++y) {
                int top = std::max(0, std::min(height - 1, y - radius - 1));
                int bottom = std::max(0, std::min(height - 1, y + radius));
                sum += src[bottom * stride + col_offset];
                sum -= src[top * stride + col_offset];
                dst[y * stride + col_offset] = sum * scale;
            }
        }
    }
}

void TextureOperator::box_blur(float* src, float* dst, int width, int height, int channels, int radius) {
    size_t size = static_cast<size_t>(width) * height * channels;
    std::vector<float> tmp(size);
    box_blur_h(src, tmp.data(), width, height, channels, radius);
    box_blur_v(tmp.data(), dst, width, height, channels, radius);
}

void TextureOperator::apply_rgb(float* pixels, int width, int height, float amount, int radius) {
    if (amount == 0.0f || radius <= 0 || width <= 0 || height <= 0) return;
    // Early exit if no texture adjustment needed
    if (std::abs(amount) < 1e-6f) return;
    int channels = 3;
    size_t size = static_cast<size_t>(width) * height * channels;

    // Step 1: Create blurred version (low-frequency component)
    std::vector<float> blurred(size);
    box_blur(pixels, blurred.data(), width, height, channels, radius);

    // Step 2: Extract high-frequency detail: detail = original - blurred
    // Step 3: Scale detail: enhanced_detail = detail * (1 + amount)
    // Step 4: Recombine: result = blurred + enhanced_detail
    //         = blurred + (original - blurred) * (1 + amount)
    //         = blurred + detail + detail * amount
    //         = original + detail * amount
    // Where detail = original - blurred
    float detail_scale = 1.0f + amount;
    for (size_t i = 0; i < size; ++i) {
        float detail = pixels[i] - blurred[i];
        pixels[i] = std::clamp(blurred[i] + detail * detail_scale, 0.0f, 1.0f);
    }
}

void TextureOperator::apply_rgba(float* pixels, int width, int height, float amount, int radius) {
    if (amount == 0.0f || radius <= 0 || width <= 0 || height <= 0) return;
    // Early exit if no texture adjustment needed
    if (std::abs(amount) < 1e-6f) return;
    int channels = 4;
    size_t size = static_cast<size_t>(width) * height * channels;

    // Step 1: Create blurred version
    std::vector<float> blurred(size);
    box_blur(pixels, blurred.data(), width, height, channels, radius);

    // Step 2-4: Extract detail, scale, and recombine (RGB only, preserve alpha)
    float detail_scale = 1.0f + amount;
    for (size_t i = 0; i < size; i += 4) {
        for (int c = 0; c < 3; ++c) {
            float detail = pixels[i + c] - blurred[i + c];
            pixels[i + c] = std::clamp(blurred[i + c] + detail * detail_scale, 0.0f, 1.0f);
        }
        // Alpha channel unchanged
    }
}

} // namespace alcedo
