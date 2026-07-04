#include "hsl_op.h"
#include <cmath>
#include <algorithm>

namespace alcedo {

static constexpr float kLumR = 0.2126f;
static constexpr float kLumG = 0.7152f;
static constexpr float kLumB = 0.0722f;

// Convert RGB to HSL
static void rgb_to_hsl(float r, float g, float b, float& h, float& s, float& l) {
    float mx = std::max({r, g, b});
    float mn = std::min({r, g, b});
    float delta = mx - mn;

    l = (mx + mn) * 0.5f;

    if (delta < 1e-6f) {
        h = 0.0f;
        s = 0.0f;
        return;
    }

    s = l > 0.5f ? delta / (2.0f - mx - mn) : delta / (mx + mn);

    if (mx == r) {
        h = (g - b) / delta + (g < b ? 6.0f : 0.0f);
    } else if (mx == g) {
        h = (b - r) / delta + 2.0f;
    } else {
        h = (r - g) / delta + 4.0f;
    }
    h /= 6.0f;
}

// Convert HSL to RGB
static void hsl_to_rgb(float h, float s, float l, float& r, float& g, float& b) {
    if (s == 0.0f) {
        r = g = b = l;
        return;
    }
    auto hue2rgb = [](float p, float q, float t) -> float {
        if (t < 0.0f) t += 1.0f;
        if (t > 1.0f) t -= 1.0f;
        if (t < 1.0f / 6.0f) return p + (q - p) * 6.0f * t;
        if (t < 1.0f / 2.0f) return q;
        if (t < 2.0f / 3.0f) return p + (q - p) * (2.0f / 3.0f - t) * 6.0f;
        return p;
    };
    float q = l < 0.5f ? l * (1.0f + s) : l + s - l * s;
    float p = 2.0f * l - q;
    r = hue2rgb(p, q, h + 1.0f / 3.0f);
    g = hue2rgb(p, q, h);
    b = hue2rgb(p, q, h - 1.0f / 3.0f);
}

// Compute soft mask weight for a hue against a range center
static float hue_weight(float hue, float center, float half_width) {
    // Circular hue distance
    float dist = std::abs(hue - center);
    if (dist > 0.5f) dist = 1.0f - dist;

    if (dist >= half_width) return 0.0f;
    if (dist <= half_width * 0.5f) return 1.0f;

    // Smooth falloff
    float t = (dist - half_width * 0.5f) / (half_width * 0.5f);
    return 1.0f - t * t * (3.0f - 2.0f * t);
}

void HSLOperator::apply_rgb(float* pixels, int width, int height,
                             const float hue_ranges[8], float hue_width,
                             const float hue_shift[8],
                             const float saturation_scale[8],
                             const float luminance_scale[8]) {
    int total = width * height;
    float half_width = hue_width / 360.0f;

    for (int i = 0; i < total; ++i) {
        int idx = i * 3;
        float r = pixels[idx];
        float g = pixels[idx + 1];
        float b = pixels[idx + 2];

        float h, s, l;
        rgb_to_hsl(r, g, b, h, s, l);

        float total_hue_shift = 0.0f;
        float total_sat_scale = 1.0f;
        float total_lum_scale = 1.0f;
        float total_weight = 0.0f;

        for (int c = 0; c < 8; ++c) {
            float center = hue_ranges[c] / 360.0f;
            float w = hue_weight(h, center, half_width);
            if (w > 0.0f) {
                total_hue_shift += hue_shift[c] * w;
                total_sat_scale += (saturation_scale[c] - 1.0f) * w;
                total_lum_scale += (luminance_scale[c] - 1.0f) * w;
                total_weight += w;
            }
        }

        if (total_weight > 0.0f) {
            total_hue_shift /= total_weight;
            total_sat_scale = 1.0f + (total_sat_scale - 1.0f) / total_weight;
            total_lum_scale = 1.0f + (total_lum_scale - 1.0f) / total_weight;
        }

        // Apply adjusted values
        float new_h = std::fmod(h + total_hue_shift / 360.0f + 1.0f, 1.0f);
        float new_s = std::max(0.0f, std::min(1.0f, s * total_sat_scale));
        float new_l = std::max(0.0f, std::min(1.0f, l * total_lum_scale));

        hsl_to_rgb(new_h, new_s, new_l, pixels[idx], pixels[idx + 1], pixels[idx + 2]);
    }
}

void HSLOperator::apply_rgba(float* pixels, int width, int height,
                              const float hue_ranges[8], float hue_width,
                              const float hue_shift[8],
                              const float saturation_scale[8],
                              const float luminance_scale[8]) {
    int total = width * height;
    float half_width = hue_width / 360.0f;

    for (int i = 0; i < total; ++i) {
        int idx = i * 4;
        float r = pixels[idx];
        float g = pixels[idx + 1];
        float b = pixels[idx + 2];

        float h, s, l;
        rgb_to_hsl(r, g, b, h, s, l);

        float total_hue_shift = 0.0f;
        float total_sat_scale = 1.0f;
        float total_lum_scale = 1.0f;
        float total_weight = 0.0f;

        for (int c = 0; c < 8; ++c) {
            float center = hue_ranges[c] / 360.0f;
            float w = hue_weight(h, center, half_width);
            if (w > 0.0f) {
                total_hue_shift += hue_shift[c] * w;
                total_sat_scale += (saturation_scale[c] - 1.0f) * w;
                total_lum_scale += (luminance_scale[c] - 1.0f) * w;
                total_weight += w;
            }
        }

        if (total_weight > 0.0f) {
            total_hue_shift /= total_weight;
            total_sat_scale = 1.0f + (total_sat_scale - 1.0f) / total_weight;
            total_lum_scale = 1.0f + (total_lum_scale - 1.0f) / total_weight;
        }

        float new_h = std::fmod(h + total_hue_shift / 360.0f + 1.0f, 1.0f);
        float new_s = std::max(0.0f, std::min(1.0f, s * total_sat_scale));
        float new_l = std::max(0.0f, std::min(1.0f, l * total_lum_scale));

        hsl_to_rgb(new_h, new_s, new_l, pixels[idx], pixels[idx + 1], pixels[idx + 2]);
    }
}

} // namespace alcedo