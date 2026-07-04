#include "lens_correction_op.h"
#include <cmath>
#include <algorithm>
#include <vector>
#include <cstring>

namespace alcedo {

void LensCorrectionOperator::bilinear_sample(const float* src, int width, int height, int channels,
                                              float x, float y, float* out) {
    // Clamp to valid range
    x = std::max(0.0f, std::min(static_cast<float>(width - 1), x));
    y = std::max(0.0f, std::min(static_cast<float>(height - 1), y));

    int x0 = static_cast<int>(x);
    int y0 = static_cast<int>(y);
    int x1 = std::min(x0 + 1, width - 1);
    int y1 = std::min(y0 + 1, height - 1);

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

void LensCorrectionOperator::apply_rgb(float* pixels, int width, int height,
                                        float k1, float k2, float k3,
                                        float p1, float p2,
                                        float cx, float cy,
                                        float focal_length_ratio) {
    if (k1 == 0.0f && k2 == 0.0f && k3 == 0.0f && p1 == 0.0f && p2 == 0.0f) return;
    int channels = 3;
    int total = width * height * channels;

    std::vector<float> src(total);
    std::copy(pixels, pixels + total, src.begin());

    float cx_px = cx * width;
    float cy_px = cy * height;
    float max_radius = std::sqrt(cx_px * cx_px + cy_px * cy_px);

    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            // Normalized coordinates relative to principal point
            float xn = (x - cx_px) / max_radius;
            float yn = (y - cy_px) / max_radius * focal_length_ratio;

            float r2 = xn * xn + yn * yn;
            float r4 = r2 * r2;
            float r6 = r4 * r2;

            // Radial distortion
            float radial = 1.0f + k1 * r2 + k2 * r4 + k3 * r6;

            // Tangential distortion
            float x_tangential = 2.0f * p1 * xn * yn + p2 * (r2 + 2.0f * xn * xn);
            float y_tangential = p1 * (r2 + 2.0f * yn * yn) + 2.0f * p2 * xn * yn;

            // Distorted coordinates (inverse mapping: find source pixel for this output pixel)
            // We use the forward model and iterate to invert
            float xd = xn * radial + x_tangential;
            float yd = yn * radial + y_tangential;

            // Convert back to pixel coordinates
            float src_x = xd * max_radius + cx_px;
            float src_y = yd * max_radius / focal_length_ratio + cy_px;

            float out[3];
            bilinear_sample(src.data(), width, height, channels, src_x, src_y, out);

            int idx = (y * width + x) * channels;
            pixels[idx]     = out[0];
            pixels[idx + 1] = out[1];
            pixels[idx + 2] = out[2];
        }
    }
}

void LensCorrectionOperator::apply_rgba(float* pixels, int width, int height,
                                         float k1, float k2, float k3,
                                         float p1, float p2,
                                         float cx, float cy,
                                         float focal_length_ratio) {
    if (k1 == 0.0f && k2 == 0.0f && k3 == 0.0f && p1 == 0.0f && p2 == 0.0f) return;
    int channels = 4;
    int total = width * height * channels;

    std::vector<float> src(total);
    std::copy(pixels, pixels + total, src.begin());

    float cx_px = cx * width;
    float cy_px = cy * height;
    float max_radius = std::sqrt(cx_px * cx_px + cy_px * cy_px);

    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            float xn = (x - cx_px) / max_radius;
            float yn = (y - cy_px) / max_radius * focal_length_ratio;

            float r2 = xn * xn + yn * yn;
            float r4 = r2 * r2;
            float r6 = r4 * r2;

            float radial = 1.0f + k1 * r2 + k2 * r4 + k3 * r6;
            float x_tangential = 2.0f * p1 * xn * yn + p2 * (r2 + 2.0f * xn * xn);
            float y_tangential = p1 * (r2 + 2.0f * yn * yn) + 2.0f * p2 * xn * yn;

            float xd = xn * radial + x_tangential;
            float yd = yn * radial + y_tangential;

            float src_x = xd * max_radius + cx_px;
            float src_y = yd * max_radius / focal_length_ratio + cy_px;

            float out[4];
            bilinear_sample(src.data(), width, height, channels, src_x, src_y, out);

            int idx = (y * width + x) * channels;
            pixels[idx]     = out[0];
            pixels[idx + 1] = out[1];
            pixels[idx + 2] = out[2];
            pixels[idx + 3] = out[3];
        }
    }
}

void LensCorrectionOperator::correct_vignette_rgb(float* pixels, int width, int height,
                                                   float strength, float midpoint,
                                                   float roundness) {
    if (strength <= 0.0f) return;
    int channels = 3;
    float cx = width * 0.5f;
    float cy = height * 0.5f;

    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            float dx = (x - cx) / cx;
            float dy = (y - cy) / cy;

            // Distance with roundness control
            float circular = std::sqrt(dx * dx + dy * dy);
            float square = std::max(std::abs(dx), std::abs(dy));
            float dist = circular * (1.0f - roundness) + square * roundness;

            // Vignette mask: darken corners
            float mask = 1.0f - std::max(0.0f, (dist - midpoint) / (1.0f - midpoint));
            mask = std::max(0.0f, mask);

            // Apply correction: brighten corners
            float correction = 1.0f + strength * (1.0f - mask);

            int idx = (y * width + x) * channels;
            for (int c = 0; c < channels; ++c) {
                pixels[idx + c] *= correction;
            }
        }
    }
}

void LensCorrectionOperator::correct_vignette_rgba(float* pixels, int width, int height,
                                                    float strength, float midpoint,
                                                    float roundness) {
    if (strength <= 0.0f) return;
    int channels = 4;
    float cx = width * 0.5f;
    float cy = height * 0.5f;

    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            float dx = (x - cx) / cx;
            float dy = (y - cy) / cy;

            float circular = std::sqrt(dx * dx + dy * dy);
            float square = std::max(std::abs(dx), std::abs(dy));
            float dist = circular * (1.0f - roundness) + square * roundness;

            float mask = 1.0f - std::max(0.0f, (dist - midpoint) / (1.0f - midpoint));
            mask = std::max(0.0f, mask);

            float correction = 1.0f + strength * (1.0f - mask);

            int idx = (y * width + x) * channels;
            for (int c = 0; c < 3; ++c) {
                pixels[idx + c] *= correction;
            }
        }
    }
}

} // namespace alcedo