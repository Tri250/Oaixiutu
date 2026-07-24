#include "clarity_op.h"
#include <cmath>
#include <algorithm>
#include <cstring>
#include <vector>

namespace alcedo {

void ClarityOperator::box_blur_h(float* src, float* dst, int width, int height, int channels, int radius) {
    float scale = 1.0f / (2.0f * radius + 1.0f);
    for (int y = 0; y < height; ++y) {
        int row_base = y * width * channels;
        for (int c = 0; c < channels; ++c) {
            float sum = 0.0f;
            // Initialize sum for the first pixel
            for (int kx = -radius; kx <= radius; ++kx) {
                int sx = std::max(0, std::min(width - 1, kx));
                sum += src[row_base + sx * channels + c];
            }
            dst[row_base + c] = sum * scale;

            // Sliding window
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

void ClarityOperator::box_blur_v(float* src, float* dst, int width, int height, int channels, int radius) {
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

void ClarityOperator::box_blur(float* src, float* dst, int width, int height, int channels, int radius) {
    size_t size = static_cast<size_t>(width) * height * channels;
    std::vector<float> tmp(size);
    box_blur_h(src, tmp.data(), width, height, channels, radius);
    box_blur_v(tmp.data(), dst, width, height, channels, radius);
}

void ClarityOperator::apply_rgb(float* pixels, int width, int height, float amount, float radius) {
    if (amount == 0.0f || radius <= 0.0f) return;
    int channels = 3;
    size_t size = static_cast<size_t>(width) * height * channels;
    int iradius = std::max(1, static_cast<int>(radius));

    std::vector<float> blurred(size);
    box_blur(pixels, blurred.data(), width, height, channels, iradius);

    float scale = amount * 0.5f;
    for (size_t i = 0; i < size; ++i) {
        float detail = pixels[i] - blurred[i];
        pixels[i] = std::clamp(pixels[i] + detail * scale, 0.0f, 1.0f);
    }
}

void ClarityOperator::apply_rgba(float* pixels, int width, int height, float amount, float radius) {
    if (amount == 0.0f || radius <= 0.0f) return;
    int channels = 4;
    size_t size = static_cast<size_t>(width) * height * channels;
    int iradius = std::max(1, static_cast<int>(radius));

    std::vector<float> blurred(size);
    box_blur(pixels, blurred.data(), width, height, channels, iradius);

    float scale = amount * 0.5f;
    for (size_t i = 0; i < size; i += 4) {
        for (int c = 0; c < 3; ++c) {
            float detail = pixels[i + c] - blurred[i + c];
            pixels[i + c] = std::clamp(pixels[i + c] + detail * scale, 0.0f, 1.0f);
        }
    }
}

} // namespace alcedo