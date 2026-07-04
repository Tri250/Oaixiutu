#include "resize_op.h"
#include <algorithm>
#include <cmath>

namespace alcedo {

namespace {

void resize_nearest(const float* src, int src_w, int src_h,
                    float* dst, int dst_w, int dst_h, int channels) {
    float scale_x = static_cast<float>(src_w) / dst_w;
    float scale_y = static_cast<float>(src_h) / dst_h;

    for (int y = 0; y < dst_h; ++y) {
        float src_y = (y + 0.5f) * scale_y - 0.5f;
        int src_yi = static_cast<int>(std::round(src_y));
        src_yi = std::max(0, std::min(src_h - 1, src_yi));

        for (int x = 0; x < dst_w; ++x) {
            float src_x = (x + 0.5f) * scale_x - 0.5f;
            int src_xi = static_cast<int>(std::round(src_x));
            src_xi = std::max(0, std::min(src_w - 1, src_xi));

            const float* src_pixel = src + (src_yi * src_w + src_xi) * channels;
            float* dst_pixel = dst + (y * dst_w + x) * channels;
            for (int c = 0; c < channels; ++c) {
                dst_pixel[c] = src_pixel[c];
            }
        }
    }
}

void resize_bilinear(const float* src, int src_w, int src_h,
                     float* dst, int dst_w, int dst_h, int channels) {
    float scale_x = static_cast<float>(src_w) / dst_w;
    float scale_y = static_cast<float>(src_h) / dst_h;

    for (int y = 0; y < dst_h; ++y) {
        float src_y = (y + 0.5f) * scale_y - 0.5f;
        int y0 = static_cast<int>(std::floor(src_y));
        int y1 = y0 + 1;
        float wy1 = src_y - static_cast<float>(y0);
        float wy0 = 1.0f - wy1;
        y0 = std::max(0, std::min(src_h - 1, y0));
        y1 = std::max(0, std::min(src_h - 1, y1));

        for (int x = 0; x < dst_w; ++x) {
            float src_x = (x + 0.5f) * scale_x - 0.5f;
            int x0 = static_cast<int>(std::floor(src_x));
            int x1 = x0 + 1;
            float wx1 = src_x - static_cast<float>(x0);
            float wx0 = 1.0f - wx1;
            x0 = std::max(0, std::min(src_w - 1, x0));
            x1 = std::max(0, std::min(src_w - 1, x1));

            const float* p00 = src + (y0 * src_w + x0) * channels;
            const float* p01 = src + (y0 * src_w + x1) * channels;
            const float* p10 = src + (y1 * src_w + x0) * channels;
            const float* p11 = src + (y1 * src_w + x1) * channels;
            float* dst_pixel = dst + (y * dst_w + x) * channels;

            for (int c = 0; c < channels; ++c) {
                float v0 = p00[c] * wx0 + p01[c] * wx1;
                float v1 = p10[c] * wx0 + p11[c] * wx1;
                dst_pixel[c] = v0 * wy0 + v1 * wy1;
            }
        }
    }
}

} // anonymous namespace

void ResizeOperator::resize(float* src, int src_w, int src_h,
                            float* dst, int dst_w, int dst_h,
                            int channels, int method) {
    if (!src || !dst || src_w <= 0 || src_h <= 0 || dst_w <= 0 || dst_h <= 0 || channels <= 0) {
        return;
    }

    if (method == static_cast<int>(Method::NEAREST)) {
        resize_nearest(src, src_w, src_h, dst, dst_w, dst_h, channels);
    } else {
        resize_bilinear(src, src_w, src_h, dst, dst_w, dst_h, channels);
    }
}

} // namespace alcedo
