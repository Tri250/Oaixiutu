#include "crop_rotate_op.h"
#include <cfloat>
#include <cmath>
#include <cstring>
#include <algorithm>

namespace alcedo {

void CropRotateOperator::apply_crop(float* dst, const float* src, int src_width, int src_height,
                                    int channels, int left, int top, int right, int bottom) {
    if (!dst || !src || src_width <= 0 || src_height <= 0 || channels <= 0) return;

    if (left < 0) left = 0;
    if (top < 0) top = 0;
    if (right > src_width) right = src_width;
    if (bottom > src_height) bottom = src_height;
    if (left >= right || top >= bottom) return;

    int dst_width = right - left;
    int dst_height = bottom - top;

    for (int y = 0; y < dst_height; ++y) {
        int src_y = top + y;
        for (int x = 0; x < dst_width; ++x) {
            int src_x = left + x;
            const float* src_pixel = src + (src_y * src_width + src_x) * channels;
            float* dst_pixel = dst + (y * dst_width + x) * channels;
            for (int c = 0; c < channels; ++c) {
                dst_pixel[c] = src_pixel[c];
            }
        }
    }
}

void CropRotateOperator::apply_rotate(float* dst, const float* src, int src_width, int src_height,
                                      int channels, int angle) {
    if (!dst || !src || src_width <= 0 || src_height <= 0 || channels <= 0) return;

    angle = ((angle % 360) + 360) % 360;

    if (angle == 0) {
        size_t total = static_cast<size_t>(src_width) * src_height * channels;
        std::memcpy(dst, src, total * sizeof(float));
        return;
    }

    if (angle == 90) {
        int dst_width = src_height;
        int dst_height = src_width;
        for (int y = 0; y < dst_height; ++y) {
            for (int x = 0; x < dst_width; ++x) {
                int src_x = y;
                int src_y = src_height - 1 - x;
                const float* src_pixel = src + (src_y * src_width + src_x) * channels;
                float* dst_pixel = dst + (y * dst_width + x) * channels;
                for (int c = 0; c < channels; ++c) {
                    dst_pixel[c] = src_pixel[c];
                }
            }
        }
    } else if (angle == 180) {
        for (int y = 0; y < src_height; ++y) {
            for (int x = 0; x < src_width; ++x) {
                int src_x = src_width - 1 - x;
                int src_y = src_height - 1 - y;
                const float* src_pixel = src + (src_y * src_width + src_x) * channels;
                float* dst_pixel = dst + (y * src_width + x) * channels;
                for (int c = 0; c < channels; ++c) {
                    dst_pixel[c] = src_pixel[c];
                }
            }
        }
    } else if (angle == 270) {
        int dst_width = src_height;
        int dst_height = src_width;
        for (int y = 0; y < dst_height; ++y) {
            for (int x = 0; x < dst_width; ++x) {
                int src_x = src_width - 1 - y;
                int src_y = x;
                const float* src_pixel = src + (src_y * src_width + src_x) * channels;
                float* dst_pixel = dst + (y * dst_width + x) * channels;
                for (int c = 0; c < channels; ++c) {
                    dst_pixel[c] = src_pixel[c];
                }
            }
        }
    } else {
        // Arbitrary rotation using bilinear interpolation
        float rad = static_cast<float>(angle) * M_PI / 180.0f;
        float cos_a = std::cos(rad);
        float sin_a = std::sin(rad);

        // Compute bounding box of rotated image
        float cx = src_width * 0.5f;
        float cy = src_height * 0.5f;

        float corners[4][2] = {
            {-cx, -cy}, {cx, -cy}, {cx, cy}, {-cx, cy}
        };

        float min_x = FLT_MAX, max_x = -FLT_MAX;
        float min_y = FLT_MAX, max_y = -FLT_MAX;

        for (int i = 0; i < 4; ++i) {
            float rx = corners[i][0] * cos_a - corners[i][1] * sin_a;
            float ry = corners[i][0] * sin_a + corners[i][1] * cos_a;
            min_x = std::min(min_x, rx);
            max_x = std::max(max_x, rx);
            min_y = std::min(min_y, ry);
            max_y = std::max(max_y, ry);
        }

        int dst_width = static_cast<int>(std::ceil(max_x - min_x));
        int dst_height = static_cast<int>(std::ceil(max_y - min_y));

        float dst_cx = dst_width * 0.5f;
        float dst_cy = dst_height * 0.5f;

        // Fill with transparent black
        std::memset(dst, 0, static_cast<size_t>(dst_width) * dst_height * channels * sizeof(float));

        for (int y = 0; y < dst_height; ++y) {
            for (int x = 0; x < dst_width; ++x) {
                // Map destination pixel back to source
                float dx = x - dst_cx;
                float dy = y - dst_cy;
                float sx = dx * cos_a + dy * sin_a + cx;
                float sy = -dx * sin_a + dy * cos_a + cy;

                // Bilinear interpolation
                int x0 = static_cast<int>(std::floor(sx));
                int y0 = static_cast<int>(std::floor(sy));
                int x1 = x0 + 1;
                int y1 = y0 + 1;

                if (x0 < -1 || x1 > src_width || y0 < -1 || y1 > src_height) continue;

                float wx1 = sx - static_cast<float>(x0);
                float wx0 = 1.0f - wx1;
                float wy1 = sy - static_cast<float>(y0);
                float wy0 = 1.0f - wy1;

                auto sample = [&](int ix, int iy, float w) {
                    if (ix < 0 || ix >= src_width || iy < 0 || iy >= src_height) return;
                    const float* p = src + (iy * src_width + ix) * channels;
                    float* d = dst + (y * dst_width + x) * channels;
                    for (int c = 0; c < channels; ++c) {
                        d[c] += p[c] * w;
                    }
                };

                sample(x0, y0, wx0 * wy0);
                sample(x1, y0, wx1 * wy0);
                sample(x0, y1, wx0 * wy1);
                sample(x1, y1, wx1 * wy1);
            }
        }
    }
}

} // namespace alcedo
