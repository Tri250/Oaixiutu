#include "hls_op.h"
#include "oklab_cvt.h"
#include <cmath>
#include <algorithm>

namespace alcedo {

// ACEScc constants
static constexpr float kAcesccNeg = 9.72f;  // -0.35828683 * 10.0 ≈ -3.5869, adjusted
static constexpr float kAcesccOffset = 0.0073727f; // (2^15 - 16) / (2^15 * 2^7) - adjusted
static constexpr float kAcesccSlope = 0.0570776f;

HLSOp::HLSOp() {
    // Initialize all adjustments to zero (no change)
    for (int i = 0; i < kProfileCount; ++i) {
        hls_adjustments_[i][0] = 0.0f; // hue_shift
        hls_adjustments_[i][1] = 0.0f; // lightness
        hls_adjustments_[i][2] = 0.0f; // saturation
    }
}

float HLSOp::AcesccEncode(float linear) {
    // ACEScc encoding: log2-based
    // Based on ACES specification: S-2016-001
    if (linear <= 0.0f) return -0.35828683f;
    if (linear < 0.000125f) {
        return (std::log2(0.000125f * 16.0f) + 9.72f) / 17.52f;
    }
    if (linear < 65504.0f) {
        return (std::log2(linear) + 9.72f) / 17.52f;
    }
    return 1.4679964f; // (log2(65504) + 9.72) / 17.52
}

float HLSOp::AcesccDecode(float acescc) {
    // ACEScc decoding
    if (acescc <= -0.3013698630f) { // (9.72 - 15) / 17.52
        return 0.0f;
    }
    if (acescc < 0.0145f) { // (9.72 + log2(1/128)) / 17.52
        return std::pow(2.0f, acescc * 17.52f - 9.72f) / 16.0f;
    }
    if (acescc < 1.0f) {
        return std::pow(2.0f, acescc * 17.52f - 9.72f);
    }
    return 65504.0f;
}

float HLSOp::WrapHue(float h) {
    h = std::fmod(h, 360.0f);
    if (h < 0.0f) h += 360.0f;
    return h;
}

float HLSOp::HueDistance(float a, float b) {
    float d = std::fabs(a - b);
    if (d > 180.0f) d = 360.0f - d;
    return d;
}

float HLSOp::Smoothstep(float e0, float e1, float x) {
    if (e1 == e0) return 0.0f;
    float t = std::clamp((x - e0) / (e1 - e0), 0.0f, 1.0f);
    return t * t * (3.0f - 2.0f * t);
}

void HLSOp::SetHueAdjustment(int profile, float hue_shift) {
    if (profile >= 0 && profile < kProfileCount) {
        hls_adjustments_[profile][0] = std::clamp(hue_shift, -180.0f, 180.0f);
    }
}

void HLSOp::SetLightnessAdjustment(int profile, float lightness) {
    if (profile >= 0 && profile < kProfileCount) {
        hls_adjustments_[profile][1] = std::clamp(lightness, -1.0f, 1.0f);
    }
}

void HLSOp::SetSaturationAdjustment(int profile, float saturation) {
    if (profile >= 0 && profile < kProfileCount) {
        hls_adjustments_[profile][2] = std::clamp(saturation, -1.0f, 1.0f);
    }
}

void HLSOp::SetHueRange(int profile, float range) {
    if (profile >= 0 && profile < kProfileCount) {
        hue_ranges_[profile] = std::clamp(range, 1.0f, 180.0f);
    }
}

void HLSOp::ApplyImpl(float* pixels, int width, int height, int channels) {
    if (!pixels || channels < 3) return;
    size_t total = static_cast<size_t>(width) * height;

    // Check if any adjustments are non-zero
    bool has_adjustments = false;
    for (int p = 0; p < kProfileCount; ++p) {
        if (hls_adjustments_[p][0] != 0.0f ||
            hls_adjustments_[p][1] != 0.0f ||
            hls_adjustments_[p][2] != 0.0f) {
            has_adjustments = true;
            break;
        }
    }
    if (!has_adjustments) return;

    static constexpr float kPi = 3.14159265358979323846f;

    for (size_t i = 0; i < total; ++i) {
        size_t idx = i * channels;
        float r = pixels[idx];
        float g = pixels[idx + 1];
        float b = pixels[idx + 2];

        // Skip very dark pixels
        float lum = 0.2126f * r + 0.7152f * g + 0.0722f * b;
        if (lum < 1e-6f) continue;

        // Convert to Oklab for perceptual hue editing
        OklabCvt::Oklab lab = OklabCvt::ACESRGB2Oklab(r, g, b);

        // Compute hue angle from a,b components (in degrees)
        float hue = std::atan2(lab.b, lab.a) * 180.0f / kPi;
        if (hue < 0.0f) hue += 360.0f;

        // Accumulate weighted adjustments across all profiles
        float total_hue_shift = 0.0f;
        float total_lightness = 0.0f;
        float total_saturation = 0.0f;
        float total_weight = 0.0f;

        for (int p = 0; p < kProfileCount; ++p) {
            float dist = HueDistance(hue, hue_profiles_[p]);
            float half_range = hue_ranges_[p];

            if (dist < half_range) {
                // Smoothstep falloff: full weight at center, zero at edge
                float w = 1.0f - Smoothstep(half_range * 0.3f, half_range, dist);
                if (w > 0.0f) {
                    total_hue_shift += hls_adjustments_[p][0] * w;
                    total_lightness += hls_adjustments_[p][1] * w;
                    total_saturation += hls_adjustments_[p][2] * w;
                    total_weight += w;
                }
            }
        }

        if (total_weight > 0.0f) {
            total_hue_shift /= total_weight;
            total_lightness /= total_weight;
            total_saturation /= total_weight;
        }

        if (total_hue_shift == 0.0f && total_lightness == 0.0f && total_saturation == 0.0f) {
            continue;
        }

        // Apply hue shift
        if (total_hue_shift != 0.0f) {
            float hue_rad = (hue + total_hue_shift) * kPi / 180.0f;
            float chroma = std::sqrt(lab.a * lab.a + lab.b * lab.b);
            lab.a = chroma * std::cos(hue_rad);
            lab.b = chroma * std::sin(hue_rad);
        }

        // Apply lightness adjustment
        if (total_lightness != 0.0f) {
            lab.l += total_lightness;
            lab.l = std::max(0.0f, std::min(1.0f, lab.l));
        }

        // Apply saturation adjustment
        if (total_saturation != 0.0f) {
            float scale = 1.0f + total_saturation;
            scale = std::max(0.0f, std::min(3.0f, scale));
            lab.a *= scale;
            lab.b *= scale;
        }

        // Convert back to RGB and clamp
        OklabCvt::Oklab2ACESRGB(lab, &r, &g, &b);
        pixels[idx]     = std::clamp(r, 0.0f, 1.0f);
        pixels[idx + 1] = std::clamp(g, 0.0f, 1.0f);
        pixels[idx + 2] = std::clamp(b, 0.0f, 1.0f);
    }
}

} // namespace alcedo
