#include "tone_region_op.h"
#include <cmath>
#include <algorithm>

namespace alcedo {

static constexpr float kLumR = 0.2126f;
static constexpr float kLumG = 0.7152f;
static constexpr float kLumB = 0.0722f;

// Smooth step function for soft region masks
static inline float smoothstep(float edge0, float edge1, float x) {
    float range = edge1 - edge0;
    if (std::abs(range) < 1e-10f) return (x >= edge1) ? 1.0f : 0.0f;
    float t = std::max(0.0f, std::min(1.0f, (x - edge0) / range));
    return t * t * (3.0f - 2.0f * t);
}

void ToneRegionOperator::apply_rgb(float* pixels, int width, int height,
                                    float shadows, float midtones, float highlights,
                                    float shadow_boundary, float highlight_boundary,
                                    float smoothness) {
    if (shadows == 0.0f && midtones == 0.0f && highlights == 0.0f) return;
    size_t total = static_cast<size_t>(width) * height;

    // Adjust boundaries based on smoothness
    float sb_low = shadow_boundary * 0.5f;
    float sb_high = shadow_boundary * (1.0f + smoothness * 0.5f);
    float hb_low = highlight_boundary * (1.0f - smoothness * 0.3f);
    float hb_high = highlight_boundary + (1.0f - highlight_boundary) * 0.5f;

    for (size_t i = 0; i < total; ++i) {
        size_t idx = i * 3;
        float r = pixels[idx];
        float g = pixels[idx + 1];
        float b = pixels[idx + 2];

        float lum = r * kLumR + g * kLumG + b * kLumB;

        // Compute soft region masks
        float shadow_mask = 1.0f - smoothstep(sb_low, sb_high, lum);
        float highlight_mask = smoothstep(hb_low, hb_high, lum);
        float midtone_mask = 1.0f - shadow_mask - highlight_mask;

        // Apply adjustments per region
        // Shadow adjustment: multiplicative (darker tones)
        float shadow_scale = 1.0f + shadows * 0.5f;
        shadow_scale = std::max(0.1f, shadow_scale);

        // Highlight adjustment: multiplicative (brighter tones)
        float highlight_scale = 1.0f + highlights * 0.5f;
        highlight_scale = std::max(0.1f, highlight_scale);

        // Midtone adjustment: additive offset
        float midtone_offset = midtones * 0.2f;

        // Blend the adjustments
        float sr = r * shadow_scale;
        float sg = g * shadow_scale;
        float sb = b * shadow_scale;

        float hr = r * highlight_scale;
        float hg = g * highlight_scale;
        float hb = b * highlight_scale;

        float mr = r + midtone_offset;
        float mg = g + midtone_offset;
        float mb = b + midtone_offset;

        pixels[idx]     = std::clamp(sr * shadow_mask + mr * midtone_mask + hr * highlight_mask, 0.0f, 1.0f);
        pixels[idx + 1] = std::clamp(sg * shadow_mask + mg * midtone_mask + hg * highlight_mask, 0.0f, 1.0f);
        pixels[idx + 2] = std::clamp(sb * shadow_mask + mb * midtone_mask + hb * highlight_mask, 0.0f, 1.0f);
    }
}

void ToneRegionOperator::apply_rgba(float* pixels, int width, int height,
                                     float shadows, float midtones, float highlights,
                                     float shadow_boundary, float highlight_boundary,
                                     float smoothness) {
    if (shadows == 0.0f && midtones == 0.0f && highlights == 0.0f) return;
    size_t total = static_cast<size_t>(width) * height;

    float sb_low = shadow_boundary * 0.5f;
    float sb_high = shadow_boundary * (1.0f + smoothness * 0.5f);
    float hb_low = highlight_boundary * (1.0f - smoothness * 0.3f);
    float hb_high = highlight_boundary + (1.0f - highlight_boundary) * 0.5f;

    for (size_t i = 0; i < total; ++i) {
        size_t idx = i * 4;
        float r = pixels[idx];
        float g = pixels[idx + 1];
        float b = pixels[idx + 2];

        float lum = r * kLumR + g * kLumG + b * kLumB;

        float shadow_mask = 1.0f - smoothstep(sb_low, sb_high, lum);
        float highlight_mask = smoothstep(hb_low, hb_high, lum);
        float midtone_mask = 1.0f - shadow_mask - highlight_mask;

        float shadow_scale = 1.0f + shadows * 0.5f;
        shadow_scale = std::max(0.1f, shadow_scale);

        float highlight_scale = 1.0f + highlights * 0.5f;
        highlight_scale = std::max(0.1f, highlight_scale);

        float midtone_offset = midtones * 0.2f;

        pixels[idx]     = std::clamp(r * shadow_scale * shadow_mask + (r + midtone_offset) * midtone_mask + r * highlight_scale * highlight_mask, 0.0f, 1.0f);
        pixels[idx + 1] = std::clamp(g * shadow_scale * shadow_mask + (g + midtone_offset) * midtone_mask + g * highlight_scale * highlight_mask, 0.0f, 1.0f);
        pixels[idx + 2] = std::clamp(b * shadow_scale * shadow_mask + (b + midtone_offset) * midtone_mask + b * highlight_scale * highlight_mask, 0.0f, 1.0f);
    }
}

} // namespace alcedo