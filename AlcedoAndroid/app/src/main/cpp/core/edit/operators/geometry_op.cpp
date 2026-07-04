#include "geometry_op.h"
#include <cmath>
#include <algorithm>
#include <vector>
#include <cstring>
#include <android/log.h>

#define LOG_TAG "AlcedoGeometry"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace alcedo {

void GeometryOperator::bilinear_sample(const float* src, int width, int height, int channels,
                                        float x, float y, float* out) {
    int x0 = static_cast<int>(x);
    int y0 = static_cast<int>(y);
    int x1 = std::min(x0 + 1, width - 1);
    int y1 = std::min(y0 + 1, height - 1);

    x0 = std::max(0, std::min(width - 1, x0));
    y0 = std::max(0, std::min(height - 1, y0));

    float fx = x - x0;
    float fy = y - y0;

    for (int c = 0; c < channels; ++c) {
        float v00 = src[(y0 * width + x0) * channels + c];
        float v01 = src[(y1 * width + x0) * channels + c];
        float v10 = src[(y0 * width + x1) * channels + c];
        float v11 = src[(y1 * width + x1) * channels + c];

        out[c] = v00 * (1.0f - fx) * (1.0f - fy) +
                 v10 * fx * (1.0f - fy) +
                 v01 * (1.0f - fx) * fy +
                 v11 * fx * fy;
    }
}

void GeometryOperator::rotate_scale(float* dst, int dst_width, int dst_height,
                                     const float* src, int src_width, int src_height,
                                     int channels, float angle, float scale,
                                     float center_x, float center_y) {
    float rad = angle * M_PI / 180.0f;
    float cos_a = std::cos(rad);
    float sin_a = std::sin(rad);

    float cx_src = center_x * src_width;
    float cy_src = center_y * src_height;
    float cx_dst = dst_width * 0.5f;
    float cy_dst = dst_height * 0.5f;

    for (int y = 0; y < dst_height; ++y) {
        for (int x = 0; x < dst_width; ++x) {
            // Map destination pixel to source
            float dx = (x - cx_dst) / scale;
            float dy = (y - cy_dst) / scale;

            float sx = dx * cos_a + dy * sin_a + cx_src;
            float sy = -dx * sin_a + dy * cos_a + cy_src;

            if (sx >= 0.0f && sx < src_width && sy >= 0.0f && sy < src_height) {
                float out[4] = {0, 0, 0, 1};
                bilinear_sample(src, src_width, src_height, channels, sx, sy, out);
                int idx = (y * dst_width + x) * channels;
                for (int c = 0; c < channels; ++c) {
                    dst[idx + c] = out[c];
                }
            } else {
                int idx = (y * dst_width + x) * channels;
                for (int c = 0; c < channels; ++c) {
                    dst[idx + c] = 0.0f;
                }
            }
        }
    }
}

bool GeometryOperator::solve_perspective(const float src[8], const float dst[8], float matrix[9]) {
    // Build linear system for perspective transform
    // We need to solve for 8 unknowns (matrix[0..7], matrix[8]=1)
    // Using 4 point correspondences gives 8 equations

    float A[64] = {0}; // 8x8 matrix
    float B[8] = {0};

    for (int i = 0; i < 4; ++i) {
        float sx = src[i * 2];
        float sy = src[i * 2 + 1];
        float dx = dst[i * 2];
        float dy = dst[i * 2 + 1];

        A[i * 8 + 0] = sx;
        A[i * 8 + 1] = sy;
        A[i * 8 + 2] = 1.0f;
        A[i * 8 + 3] = 0.0f;
        A[i * 8 + 4] = 0.0f;
        A[i * 8 + 5] = 0.0f;
        A[i * 8 + 6] = -sx * dx;
        A[i * 8 + 7] = -sy * dx;
        B[i] = dx;

        A[(i + 4) * 8 + 0] = 0.0f;
        A[(i + 4) * 8 + 1] = 0.0f;
        A[(i + 4) * 8 + 2] = 0.0f;
        A[(i + 4) * 8 + 3] = sx;
        A[(i + 4) * 8 + 4] = sy;
        A[(i + 4) * 8 + 5] = 1.0f;
        A[(i + 4) * 8 + 6] = -sx * dy;
        A[(i + 4) * 8 + 7] = -sy * dy;
        B[i + 4] = dy;
    }

    // Gaussian elimination with partial pivoting
    for (int col = 0; col < 8; ++col) {
        // Find pivot
        int max_row = col;
        float max_val = std::abs(A[col * 8 + col]);
        for (int row = col + 1; row < 8; ++row) {
            float val = std::abs(A[row * 8 + col]);
            if (val > max_val) {
                max_val = val;
                max_row = row;
            }
        }
        if (max_val < 1e-10f) return false;

        // Swap rows
        if (max_row != col) {
            for (int j = 0; j < 8; ++j) {
                std::swap(A[col * 8 + j], A[max_row * 8 + j]);
            }
            std::swap(B[col], B[max_row]);
        }

        // Eliminate
        float pivot = A[col * 8 + col];
        for (int row = 0; row < 8; ++row) {
            if (row == col) continue;
            float factor = A[row * 8 + col] / pivot;
            for (int j = col; j < 8; ++j) {
                A[row * 8 + j] -= factor * A[col * 8 + j];
            }
            B[row] -= factor * B[col];
        }
    }

    // Back substitution
    for (int i = 0; i < 8; ++i) {
        matrix[i] = B[i] / A[i * 8 + i];
    }
    matrix[8] = 1.0f;

    return true;
}

void GeometryOperator::transform_point(const float matrix[9], float x, float y, float& ox, float& oy) {
    float w = matrix[6] * x + matrix[7] * y + matrix[8];
    ox = (matrix[0] * x + matrix[1] * y + matrix[2]) / w;
    oy = (matrix[3] * x + matrix[4] * y + matrix[5]) / w;
}

void GeometryOperator::perspective_warp(float* dst, int dst_width, int dst_height,
                                         const float* src, int src_width, int src_height,
                                         int channels,
                                         const float src_points[8],
                                         const float dst_points[8]) {
    float matrix[9];
    if (!solve_perspective(dst_points, src_points, matrix)) {
        // Fallback: copy
        LOGI("Perspective solve failed, using identity");
        matrix[0] = 1; matrix[1] = 0; matrix[2] = 0;
        matrix[3] = 0; matrix[4] = 1; matrix[5] = 0;
        matrix[6] = 0; matrix[7] = 0; matrix[8] = 1;
    }

    for (int y = 0; y < dst_height; ++y) {
        for (int x = 0; x < dst_width; ++x) {
            float sx, sy;
            transform_point(matrix, static_cast<float>(x), static_cast<float>(y), sx, sy);

            float out[4] = {0, 0, 0, 1};
            if (sx >= 0.0f && sx < src_width && sy >= 0.0f && sy < src_height) {
                bilinear_sample(src, src_width, src_height, channels, sx, sy, out);
            }
            int idx = (y * dst_width + x) * channels;
            for (int c = 0; c < channels; ++c) {
                dst[idx + c] = out[c];
            }
        }
    }
}

void GeometryOperator::dng_warp_rectilinear(float* dst, int dst_width, int dst_height,
                                             const float* src, int src_width, int src_height,
                                             int channels,
                                             const float coefficients[4],
                                             float cx, float cy) {
    float cx_px = cx * src_width;
    float cy_px = cy * src_height;
    float max_r = std::sqrt(cx_px * cx_px + cy_px * cy_px);

    for (int y = 0; y < dst_height; ++y) {
        for (int x = 0; x < dst_width; ++x) {
            // Map destination to source using inverse polynomial
            float dx = (static_cast<float>(x) / dst_width) * src_width;
            float dy = (static_cast<float>(y) / dst_height) * src_height;

            float xn = (dx - cx_px) / max_r;
            float yn = (dy - cy_px) / max_r;
            float r = std::sqrt(xn * xn + yn * yn);

            // Apply polynomial: r' = r * (1 + k0*r^2 + k1*r^4 + k2*r^6 + k3*r^8)
            float r2 = r * r;
            float r4 = r2 * r2;
            float r6 = r4 * r2;
            float r8 = r6 * r2;
            float scale = 1.0f + coefficients[0] * r2 + coefficients[1] * r4 +
                          coefficients[2] * r6 + coefficients[3] * r8;

            float sx = xn * scale * max_r + cx_px;
            float sy = yn * scale * max_r + cy_px;

            float out[4] = {0, 0, 0, 1};
            if (sx >= 0.0f && sx < src_width && sy >= 0.0f && sy < src_height) {
                bilinear_sample(src, src_width, src_height, channels, sx, sy, out);
            }
            int idx = (y * dst_width + x) * channels;
            for (int c = 0; c < channels; ++c) {
                dst[idx + c] = out[c];
            }
        }
    }
}

void GeometryOperator::crop(float* dst, int dst_width, int dst_height,
                            const float* src, int src_width, int src_height,
                            int channels,
                            int src_x, int src_y, int src_w, int src_h) {
    for (int y = 0; y < dst_height; ++y) {
        for (int x = 0; x < dst_width; ++x) {
            int sx = src_x + x * src_w / dst_width;
            int sy = src_y + y * src_h / dst_height;
            sx = std::max(0, std::min(src_width - 1, sx));
            sy = std::max(0, std::min(src_height - 1, sy));

            int src_idx = (sy * src_width + sx) * channels;
            int dst_idx = (y * dst_width + x) * channels;
            for (int c = 0; c < channels; ++c) {
                dst[dst_idx + c] = src[src_idx + c];
            }
        }
    }
}

void GeometryOperator::flip_horizontal(float* data, int width, int height, int channels) {
    int row_size = width * channels;
    std::vector<float> row(row_size);
    for (int y = 0; y < height; ++y) {
        float* row_ptr = data + y * row_size;
        std::copy(row_ptr, row_ptr + row_size, row.begin());
        for (int x = 0; x < width; ++x) {
            for (int c = 0; c < channels; ++c) {
                row_ptr[x * channels + c] = row[(width - 1 - x) * channels + c];
            }
        }
    }
}

void GeometryOperator::flip_vertical(float* data, int width, int height, int channels) {
    int row_size = width * channels;
    std::vector<float> row(row_size);
    for (int y = 0; y < height / 2; ++y) {
        float* top = data + y * row_size;
        float* bottom = data + (height - 1 - y) * row_size;
        std::copy(top, top + row_size, row.begin());
        std::copy(bottom, bottom + row_size, top);
        std::copy(row.begin(), row.end(), bottom);
    }
}

} // namespace alcedo