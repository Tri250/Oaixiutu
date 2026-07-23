#include "tint_op.h"
#include <cmath>
#include <algorithm>

namespace alcedo {

static constexpr float kLumR = 0.2126f;
static constexpr float kLumG = 0.7152f;
static constexpr float kLumB = 0.0722f;

// Convert HSL hue to RGB color (saturation=1, lightness=0.5)
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

void TintOperator::apply_rgb(float* pixels, int width, int height,
                              float highlight_hue, float highlight_strength,
                              float shadow_hue, float shadow_strength,
                              float balance) {
    if (!pixels) return;
    if (highlight_strength == 0.0f && shadow_strength == 0.0f) return;
    int total = width * height;

    // Compute highlight tint color
    float hh_norm = std::fmod(highlight_hue, 360.0f) / 360.0f;
    if (hh_norm < 0.0f) hh_norm += 1.0f;
    float hr, hg, hb;
    hsl_to_rgb(hh_norm, 1.0f, 0.5f, hr, hg, hb);

    // Compute shadow tint color
    float sh_norm = std::fmod(shadow_hue, 360.0f) / 360.0f;
    if (sh_norm < 0.0f) sh_norm += 1.0f;
    float sr, sg, sb;
    hsl_to_rgb(sh_norm, 1.0f, 0.5f, sr, sg, sb);

    // Balance controls the midpoint: 0 = even split, 1 = all highlights, -1 = all shadows
    float split_point = 0.5f + balance * 0.15f;
    split_point = std::max(0.05f, std::min(0.95f, split_point));

    for (int i = 0; i < total; ++i) {
        int idx = i * 3;
        float r = pixels[idx];
        float g = pixels[idx + 1];
        float b = pixels[idx + 2];

        float lum = r * kLumR + g * kLumG + b * kLumB;

        // Smooth split between shadow and highlight weighting
        float highlight_weight = 1.0f / (1.0f + std::exp(-12.0f * (lum - split_point)));
        float shadow_weight = 1.0f - highlight_weight;

        // Blend tint colors into the pixel
        float tint_r = highlight_weight * hr * highlight_strength + shadow_weight * sr * shadow_strength;
        float tint_g = highlight_weight * hg * highlight_strength + shadow_weight * sg * shadow_strength;
        float tint_b = highlight_weight * hb * highlight_strength + shadow_weight * sb * shadow_strength;

        // Apply tint as a blend (preserve luminance)
        float total_tint = (highlight_weight * highlight_strength + shadow_weight * shadow_strength);
        float blend = total_tint * 0.5f;
        pixels[idx]     = r * (1.0f - blend) + tint_r * lum * blend * 2.0f;
        pixels[idx + 1] = g * (1.0f - blend) + tint_g * lum * blend * 2.0f;
        pixels[idx + 2] = b * (1.0f - blend) + tint_b * lum * blend * 2.0f;
    }
}

void TintOperator::apply_rgba(float* pixels, int width, int height,
                               float highlight_hue, float highlight_strength,
                               float shadow_hue, float shadow_strength,
                               float balance) {
    if (!pixels) return;
    if (highlight_strength == 0.0f && shadow_strength == 0.0f) return;
    int total = width * height;

    float hh_norm = std::fmod(highlight_hue, 360.0f) / 360.0f;
    if (hh_norm < 0.0f) hh_norm += 1.0f;
    float hr, hg, hb;
    hsl_to_rgb(hh_norm, 1.0f, 0.5f, hr, hg, hb);

    float sh_norm = std::fmod(shadow_hue, 360.0f) / 360.0f;
    if (sh_norm < 0.0f) sh_norm += 1.0f;
    float sr, sg, sb;
    hsl_to_rgb(sh_norm, 1.0f, 0.5f, sr, sg, sb);

    float split_point = 0.5f + balance * 0.15f;
    split_point = std::max(0.05f, std::min(0.95f, split_point));

    for (int i = 0; i < total; ++i) {
        int idx = i * 4;
        float r = pixels[idx];
        float g = pixels[idx + 1];
        float b = pixels[idx + 2];

        float lum = r * kLumR + g * kLumG + b * kLumB;

        float highlight_weight = 1.0f / (1.0f + std::exp(-12.0f * (lum - split_point)));
        float shadow_weight = 1.0f - highlight_weight;

        float tint_r = highlight_weight * hr * highlight_strength + shadow_weight * sr * shadow_strength;
        float tint_g = highlight_weight * hg * highlight_strength + shadow_weight * sg * shadow_strength;
        float tint_b = highlight_weight * hb * highlight_strength + shadow_weight * sb * shadow_strength;

        float total_tint = (highlight_weight * highlight_strength + shadow_weight * shadow_strength);
        float blend = total_tint * 0.5f;
        pixels[idx]     = r * (1.0f - blend) + tint_r * lum * blend * 2.0f;
        pixels[idx + 1] = g * (1.0f - blend) + tint_g * lum * blend * 2.0f;
        pixels[idx + 2] = b * (1.0f - blend) + tint_b * lum * blend * 2.0f;
    }
}

} // namespace alcedo