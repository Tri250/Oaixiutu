#include "lens_correction_op.h"
#include <cmath>
#include <algorithm>
#include <vector>
#include <cstring>

namespace alcedo {

void LensCorrectionOperator::bilinear_sample(const float* src, int width, int height, int channels,
                                              float x, float y, float* out) {
    // If out of bounds, return transparent/black (for lens correction, out-of-bounds
    // pixels beyond the corrected image boundary should not be clamped to edge values)
    if (x < 0.0f || x >= static_cast<float>(width) ||
        y < 0.0f || y >= static_cast<float>(height)) {
        for (int c = 0; c < channels; ++c) {
            out[c] = 0.0f;
        }
        return;
    }

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

// Helper: compute the forward distortion model (undistorted → distorted)
static void forward_distort(float xn, float yn,
                             float k1, float k2, float k3,
                             float p1, float p2,
                             float* xd, float* yd) {
    float r2 = xn * xn + yn * yn;
    float r4 = r2 * r2;
    float r6 = r4 * r2;
    float radial = 1.0f + k1 * r2 + k2 * r4 + k3 * r6;
    float x_tangential = 2.0f * p1 * xn * yn + p2 * (r2 + 2.0f * xn * xn);
    float y_tangential = p1 * (r2 + 2.0f * yn * yn) + 2.0f * p2 * xn * yn;
    *xd = xn * radial + x_tangential;
    *yd = yn * radial + y_tangential;
}

// Iterative inverse distortion: given a distorted point (xd, yd), find the
// undistorted point (xn, yn) such that forward_distort(xn, yn) ≈ (xd, yd).
// Uses Newton-Raphson-style fixed-point iteration (typically converges in 3-5 iterations).
static void inverse_distort(float xd, float yd,
                             float k1, float k2, float k3,
                             float p1, float p2,
                             float* xn, float* yn) {
    // Initial guess: assume distortion is small, so undistorted ≈ distorted
    float x = xd;
    float y = yd;

    // Fixed-point iteration: x_new = xd / radial(x,y) - tangential_correction
    // This is equivalent to iterating: x_{n+1} = (xd - tangential) / radial
    for (int iter = 0; iter < 5; ++iter) {
        float r2 = x * x + y * y;
        float r4 = r2 * r2;
        float r6 = r4 * r2;
        float radial = 1.0f + k1 * r2 + k2 * r4 + k3 * r6;
        float x_tangential = 2.0f * p1 * x * y + p2 * (r2 + 2.0f * x * x);
        float y_tangential = p1 * (r2 + 2.0f * y * y) + 2.0f * p2 * x * y;

        // Invert: x_undistorted ≈ (xd - x_tangential) / radial
        if (std::fabs(radial) > 1e-10f) {
            x = (xd - x_tangential) / radial;
            y = (yd - y_tangential) / radial;
        }
    }

    *xn = x;
    *yn = y;
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

            // Apply inverse distortion: find the source (undistorted) point
            // that maps to this output (distorted) position.
            float src_xn, src_yn;
            inverse_distort(xn, yn, k1, k2, k3, p1, p2, &src_xn, &src_yn);

            // Convert back to pixel coordinates
            float src_x = src_xn * max_radius + cx_px;
            float src_y = src_yn * max_radius / focal_length_ratio + cy_px;

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

            float src_xn, src_yn;
            inverse_distort(xn, yn, k1, k2, k3, p1, p2, &src_xn, &src_yn);

            float src_x = src_xn * max_radius + cx_px;
            float src_y = src_yn * max_radius / focal_length_ratio + cy_px;

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

    // Guard against midpoint >= 1.0 which would cause division by zero
    float safe_midpoint = std::min(midpoint, 0.99f);

    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            float dx = (x - cx) / cx;
            float dy = (y - cy) / cy;

            // Distance with roundness control
            float circular = std::sqrt(dx * dx + dy * dy);
            float square = std::max(std::abs(dx), std::abs(dy));
            float dist = circular * (1.0f - roundness) + square * roundness;

            // Vignette mask: darken corners
            float mask = 1.0f - std::max(0.0f, (dist - safe_midpoint) / (1.0f - safe_midpoint));
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

    // Guard against midpoint >= 1.0 which would cause division by zero
    float safe_midpoint = std::min(midpoint, 0.99f);

    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            float dx = (x - cx) / cx;
            float dy = (y - cy) / cy;

            float circular = std::sqrt(dx * dx + dy * dy);
            float square = std::max(std::abs(dx), std::abs(dy));
            float dist = circular * (1.0f - roundness) + square * roundness;

            float mask = 1.0f - std::max(0.0f, (dist - safe_midpoint) / (1.0f - safe_midpoint));
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
