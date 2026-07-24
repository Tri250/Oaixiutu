#include "mask_op.h"
#include <cstring>
#include <algorithm>
#include <memory>

namespace alcedo {

// ============================================================
// Public API
// ============================================================

void MaskOperator::generate_mask(float* mask_out, int width, int height, const MaskParams& params) {
    if (!mask_out || width <= 0 || height <= 0) return;

    const size_t n = static_cast<size_t>(width) * height;

    // Initialize mask to zero
    std::fill(mask_out, mask_out + n, 0.0f);

    switch (params.type) {
        case MaskType::Brush:
            generate_brush_mask(mask_out, width, height, params);
            break;
        case MaskType::LinearGradient:
            generate_linear_gradient_mask(mask_out, width, height, params);
            break;
        case MaskType::RadialGradient:
            generate_radial_gradient_mask(mask_out, width, height, params);
            break;
        case MaskType::Luminosity:
            generate_luminosity_mask(mask_out, width, height, params);
            break;
        case MaskType::ColorRange:
            generate_color_range_mask(mask_out, width, height, params);
            break;
        case MaskType::WholeImage:
            std::fill(mask_out, mask_out + n, 1.0f);
            break;
    }

    // Apply feather (convert normalized feather to pixel radius)
    if (params.feather > 0.001f && params.type != MaskType::WholeImage) {
        float radius_px = params.feather * static_cast<float>(std::min(width, height));
        if (radius_px >= 1.0f) {
            feather_mask(mask_out, width, height, radius_px);
        }
    }

    // Apply opacity
    if (params.opacity < 1.0f) {
        float op = std::clamp(params.opacity, 0.0f, 1.0f);
        for (size_t i = 0; i < n; ++i) {
            mask_out[i] *= op;
        }
    }

    // Apply inversion
    if (params.inverted) {
        invert_mask(mask_out, width, height);
    }
}

void MaskOperator::apply_mask(const float* original, const float* edited, float* output,
                               int width, int height, int channels, const float* mask) {
    if (!original || !edited || !output || !mask) return;
    if (width <= 0 || height <= 0 || channels <= 0) return;

    const size_t pixel_count = static_cast<size_t>(width) * height;
    for (size_t i = 0; i < pixel_count; ++i) {
        float w = std::clamp(mask[i], 0.0f, 1.0f);
        float inv_w = 1.0f - w;
        size_t idx = i * channels;
        for (int c = 0; c < channels; ++c) {
            output[idx + c] = original[idx + c] * inv_w + edited[idx + c] * w;
        }
    }
}

void MaskOperator::feather_mask(float* mask, int width, int height, float radius_px) {
    if (!mask || width <= 0 || height <= 0 || radius_px < 1.0f) return;

    int radius = static_cast<int>(radius_px + 0.5f);
    if (radius < 1) radius = 1;

    // Three passes of box blur approximate Gaussian blur.
    // This is the standard "stack blur" approach.
    const int passes = 3;
    for (int p = 0; p < passes; ++p) {
        box_blur_pass(mask, width, height, radius);
    }
}

void MaskOperator::invert_mask(float* mask, int width, int height) {
    if (!mask) return;
    const size_t n = static_cast<size_t>(width) * height;
    for (size_t i = 0; i < n; ++i) {
        mask[i] = 1.0f - mask[i];
    }
}

void MaskOperator::combine_masks(float* mask_out, const float* mask_a, const float* mask_b,
                                  int width, int height, CombineMode mode) {
    if (!mask_out || !mask_a || !mask_b || width <= 0 || height <= 0) return;
    const size_t n = static_cast<size_t>(width) * height;
    switch (mode) {
        case CombineMode::Add:
            for (size_t i = 0; i < n; ++i) {
                mask_out[i] = std::min(1.0f, mask_a[i] + mask_b[i]);
            }
            break;
        case CombineMode::Subtract:
            for (size_t i = 0; i < n; ++i) {
                mask_out[i] = std::max(0.0f, mask_a[i] - mask_b[i]);
            }
            break;
        case CombineMode::Intersect:
            for (size_t i = 0; i < n; ++i) {
                mask_out[i] = std::min(mask_a[i], mask_b[i]);
            }
            break;
    }
}

// ============================================================
// Private: per-type mask generators
// ============================================================

void MaskOperator::generate_brush_mask(float* mask, int width, int height, const MaskParams& params) {
    if (params.brush_points.empty()) return;

    float effective_radius = std::max(1.0f, params.brush_size * std::min(width, height));
    const float radius_px = effective_radius;
    const float hardness = std::clamp(params.brush_hardness, 0.0f, 1.0f);
    const float opacity = std::clamp(params.brush_opacity, 0.0f, 1.0f);
    const float transition = radius_px * (1.0f - hardness);
    const float inner_r = radius_px - transition;

    // Draw each brush stamp along the stroke path
    for (size_t i = 0; i < params.brush_points.size(); ++i) {
        const auto& pt = params.brush_points[i];
        float px = pt.x * static_cast<float>(width);
        float py = pt.y * static_cast<float>(height);
        float pressure = std::clamp(pt.pressure, 0.0f, 1.0f);

        // If there's a next point, interpolate stamps along the segment
        float next_px = px, next_py = py;
        bool has_next = (i + 1 < params.brush_points.size());
        if (has_next) {
            next_px = params.brush_points[i + 1].x * static_cast<float>(width);
            next_py = params.brush_points[i + 1].y * static_cast<float>(height);
        }

        float seg_dx = next_px - px;
        float seg_dy = next_py - py;
        float seg_len = std::sqrt(seg_dx * seg_dx + seg_dy * seg_dy);
        float step = radius_px * 0.25f;
        if (step < 1.0f) step = 1.0f;
        int steps = has_next ? static_cast<int>(seg_len / step) : 0;

        for (int s = 0; s <= steps; ++s) {
            float t = (steps > 0) ? static_cast<float>(s) / steps : 0.0f;
            float cx = px + seg_dx * t;
            float cy = py + seg_dy * t;

            int minX = static_cast<int>(std::floor(cx - radius_px));
            int maxX = static_cast<int>(std::ceil(cx + radius_px));
            int minY = static_cast<int>(std::floor(cy - radius_px));
            int maxY = static_cast<int>(std::ceil(cy + radius_px));
            minX = std::max(0, minX);
            maxX = std::min(width - 1, maxX);
            minY = std::max(0, minY);
            maxY = std::min(height - 1, maxY);

            for (int y = minY; y <= maxY; ++y) {
                for (int x = minX; x <= maxX; ++x) {
                    float dx = static_cast<float>(x) - cx;
                    float dy = static_cast<float>(y) - cy;
                    float dist = std::sqrt(dx * dx + dy * dy);
                    if (dist > radius_px) continue;

                    float value;
                    if (transition <= 1e-6f || dist <= inner_r) {
                        value = opacity * pressure;
                    } else {
                        float t2 = (dist - inner_r) / transition;
                        value = opacity * pressure * (1.0f - t2);
                    }
                    int idx = y * width + x;
                    mask[idx] = std::max(mask[idx], value);
                }
            }
        }

        // Always stamp the final point
        if (!has_next) {
            int minX = static_cast<int>(std::floor(px - radius_px));
            int maxX = static_cast<int>(std::ceil(px + radius_px));
            int minY = static_cast<int>(std::floor(py - radius_px));
            int maxY = static_cast<int>(std::ceil(py + radius_px));
            minX = std::max(0, minX);
            maxX = std::min(width - 1, maxX);
            minY = std::max(0, minY);
            maxY = std::min(height - 1, maxY);

            for (int y = minY; y <= maxY; ++y) {
                for (int x = minX; x <= maxX; ++x) {
                    float dx = static_cast<float>(x) - px;
                    float dy = static_cast<float>(y) - py;
                    float dist = std::sqrt(dx * dx + dy * dy);
                    if (dist > radius_px) continue;

                    float value;
                    if (transition <= 1e-6f || dist <= inner_r) {
                        value = opacity * pressure;
                    } else {
                        float t2 = (dist - inner_r) / transition;
                        value = opacity * pressure * (1.0f - t2);
                    }
                    int idx = y * width + x;
                    mask[idx] = std::max(mask[idx], value);
                }
            }
        }
    }
}

void MaskOperator::generate_linear_gradient_mask(float* mask, int width, int height, const MaskParams& params) {
    float sx = params.linear_start_x * static_cast<float>(width);
    float sy = params.linear_start_y * static_cast<float>(height);
    float ex = params.linear_end_x * static_cast<float>(width);
    float ey = params.linear_end_y * static_cast<float>(height);

    float dx = ex - sx;
    float dy = ey - sy;
    float len2 = dx * dx + dy * dy;
    if (len2 < 1e-6f) {
        // Degenerate: start == end → full mask
        const size_t n = static_cast<size_t>(width) * height;
        std::fill(mask, mask + n, 1.0f);
        return;
    }

    float len = std::sqrt(len2);
    float feather_px = params.feather * len;
    if (feather_px < 1.0f) feather_px = 1.0f;

    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            float px = static_cast<float>(x) - sx;
            float py = static_cast<float>(y) - sy;

            // Project onto gradient direction: t goes from 0 (start) to 1 (end)
            float t = (px * dx + py * dy) / len2;
            t = std::clamp(t, 0.0f, 1.0f);

            // Perpendicular distance from gradient line
            float proj_x = sx + t * dx;
            float proj_y = sy + t * dy;
            float perp_dist = std::sqrt((static_cast<float>(x) - proj_x) * (static_cast<float>(x) - proj_x) +
                                         (static_cast<float>(y) - proj_y) * (static_cast<float>(y) - proj_y));

            // Along gradient: 1 at start, 0 at end
            float along = 1.0f - t;
            // Perpendicular feather: fade out away from the gradient line
            float across = std::clamp(1.0f - perp_dist / feather_px, 0.0f, 1.0f);

            mask[y * width + x] = along * across;
        }
    }
}

void MaskOperator::generate_radial_gradient_mask(float* mask, int width, int height, const MaskParams& params) {
    float cx = params.radial_center_x * static_cast<float>(width);
    float cy = params.radial_center_y * static_cast<float>(height);
    float rx = params.radial_radius_x * static_cast<float>(width);
    float ry = params.radial_radius_y * static_cast<float>(height);

    if (rx < 1.0f) rx = 1.0f;
    if (ry < 1.0f) ry = 1.0f;

    float feather_px = params.feather * std::min(rx, ry);
    if (feather_px < 1.0f) feather_px = 1.0f;

    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            // Normalized elliptical distance
            float ndx = (static_cast<float>(x) - cx) / rx;
            float ndy = (static_cast<float>(y) - cy) / ry;
            float d = std::sqrt(ndx * ndx + ndy * ndy); // 0 at center, 1 at ellipse edge

            // Inside → full, feather band → smooth falloff, outside → 0
            float inner = 1.0f - feather_px / std::min(rx, ry);
            if (inner < 0.0f) inner = 0.0f;

            float value;
            if (d <= inner) {
                value = 1.0f;
            } else if (d <= 1.0f) {
                value = std::clamp((1.0f - d) / (1.0f - inner + 1e-6f), 0.0f, 1.0f);
            } else {
                // Beyond ellipse with feather extension
                float feather_extent = feather_px / std::min(rx, ry);
                value = std::clamp((1.0f + feather_extent - d) / (feather_extent + 1e-6f), 0.0f, 1.0f);
            }
            mask[y * width + x] = value;
        }
    }
}

void MaskOperator::generate_luminosity_mask(float* mask, int width, int height, const MaskParams& params) {
    // For the C++ operator level, we require the caller to provide a luminance
    // buffer via the mask_out input (pre-computed from image pixels).
    // If mask_out is all zeros (no pre-computed luminance), generate a simple
    // luminance range mask based on linear gradient as placeholder.
    // The real luminance computation is done in the Kotlin layer (MaskInferenceService).
    //
    // Here we generate a mask that selects pixels in the luminance range
    // [lum_min, lum_max] with feathering at the boundaries.

    float lo = std::clamp(params.lum_min, 0.0f, 1.0f);
    float hi = std::clamp(params.lum_max, 0.0f, 1.0f);
    if (lo > hi) std::swap(lo, hi);

    float range = hi - lo;
    float feather = std::clamp(params.feather, 0.001f, 1.0f) * range * 0.5f;
    if (feather < 0.01f) feather = 0.01f;

    // Generate a smooth gradient from 0→1→0 across the luminance range.
    // Since we don't have actual pixel data here, we generate a vertical
    // gradient that represents the luminosity selection pattern.
    for (int y = 0; y < height; ++y) {
        float t = static_cast<float>(y) / static_cast<float>(height - 1); // 0-1 top to bottom
        float value;
        if (t >= lo && t <= hi) {
            value = 1.0f;
        } else if (t < lo) {
            value = std::clamp(1.0f - (lo - t) / feather, 0.0f, 1.0f);
        } else {
            value = std::clamp(1.0f - (t - hi) / feather, 0.0f, 1.0f);
        }
        for (int x = 0; x < width; ++x) {
            mask[y * width + x] = value;
        }
    }
}

void MaskOperator::generate_color_range_mask(float* mask, int width, int height, const MaskParams& params) {
    // Similar to luminosity — without actual pixel data, we generate a
    // radial-falloff placeholder. Real computation is in Kotlin.
    float range = std::clamp(params.color_range, 0.01f, 1.0f);
    float cx = params.color_target_r * static_cast<float>(width);
    float cy = params.color_target_g * static_cast<float>(height);
    float r = range * static_cast<float>(std::min(width, height));

    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            float dx = static_cast<float>(x) - cx;
            float dy = static_cast<float>(y) - cy;
            float dist = std::sqrt(dx * dx + dy * dy);
            mask[y * width + x] = std::clamp(1.0f - dist / (r + 1e-6f), 0.0f, 1.0f);
        }
    }
}

// ============================================================
// Private: box blur helper
// ============================================================

void MaskOperator::box_blur_pass(float* data, int width, int height, int radius) {
    if (radius < 1 || width <= 0 || height <= 0) return;

    const size_t n = static_cast<size_t>(width) * height;
    auto tmp = std::make_unique<float[]>(n);

    // Horizontal pass
    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            float sum = 0.0f;
            int count = 0;
            int x0 = std::max(0, x - radius);
            int x1 = std::min(width - 1, x + radius);
            for (int xx = x0; xx <= x1; ++xx) {
                sum += data[y * width + xx];
                count++;
            }
            tmp[y * width + x] = sum / static_cast<float>(count);
        }
    }

    // Vertical pass
    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            float sum = 0.0f;
            int count = 0;
            int y0 = std::max(0, y - radius);
            int y1 = std::min(height - 1, y + radius);
            for (int yy = y0; yy <= y1; ++yy) {
                sum += tmp[yy * width + x];
                count++;
            }
            data[y * width + x] = sum / static_cast<float>(count);
        }
    }
}

} // namespace alcedo
