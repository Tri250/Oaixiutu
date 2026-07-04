#include "crop_rotate_op.h"

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
        for (int y = 0; y < src_height; ++y) {
            for (int x = 0; x < src_width; ++x) {
                const float* src_pixel = src + (y * src_width + x) * channels;
                float* dst_pixel = dst + (y * src_width + x) * channels;
                for (int c = 0; c < channels; ++c) {
                    dst_pixel[c] = src_pixel[c];
                }
            }
        }
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
    }
}

} // namespace alcedo
