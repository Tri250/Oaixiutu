#include "color_science.h"
#include <cmath>
#include <algorithm>
#include <android/log.h>

#define LOG_TAG "AlcedoColorSci"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace alcedo {
namespace color_science {

// ============================================================
// Safety utilities for NaN/Inf protection
// ============================================================

static inline float safe_div(float a, float b) {
    if (std::fabs(b) < 1e-10f) return 0.0f;
    float result = a / b;
    if (!std::isfinite(result)) return 0.0f;
    return result;
}

static inline float safe_pow(float base, float exp) {
    if (base < 0.0f && exp != std::floor(exp)) return 0.0f;
    float result = std::pow(base, exp);
    if (!std::isfinite(result)) return 0.0f;
    return result;
}

static inline float clamp01(float v) {
    if (!std::isfinite(v)) return 0.0f;
    return std::max(0.0f, std::min(1.0f, v));
}

// ============================================================
// Utility functions
// ============================================================

float clampf(float v, float lo, float hi) {
    return std::max(lo, std::min(hi, v));
}

void apply_matrix_3x3(const float m[9], float* r, float* g, float* b) {
    float nr = *r * m[0] + *g * m[1] + *b * m[2];
    float ng = *r * m[3] + *g * m[4] + *b * m[5];
    float nb = *r * m[6] + *g * m[7] + *b * m[8];
    *r = nr;
    *g = ng;
    *b = nb;
}

void apply_matrix_3x3_bulk(const float m[9], float* pixels, int count, int channels, int pixel_stride) {
    for (int i = 0; i < count; ++i) {
        int idx = i * pixel_stride;
        float r = pixels[idx];
        float g = pixels[idx + 1];
        float b = pixels[idx + 2];
        pixels[idx]     = r * m[0] + g * m[1] + b * m[2];
        pixels[idx + 1] = r * m[3] + g * m[4] + b * m[5];
        pixels[idx + 2] = r * m[6] + g * m[7] + b * m[8];
    }
}

// ============================================================
// XYZ Color Matching Functions (CIE 1931 2-degree)
// ============================================================

// Helper to compute RGB-to-XYZ matrix from primaries
static void compute_rgb_to_xyz_matrix(
    float rx, float ry, float gx, float gy, float bx, float by, float wx, float wy,
    float m[9]) {
    // Compute XYZ of primaries
    float Xr = rx / ry;
    float Yr = 1.0f;
    float Zr = (1.0f - rx - ry) / ry;

    float Xg = gx / gy;
    float Yg = 1.0f;
    float Zg = (1.0f - gx - gy) / gy;

    float Xb = bx / by;
    float Yb = 1.0f;
    float Zb = (1.0f - bx - by) / by;

    float Xw = wx / wy;
    float Yw = 1.0f;
    float Zw = (1.0f - wx - wy) / wy;

    // Solve for scale factors
    float det = Xr * (Yg * Zb - Zg * Yb) - Yr * (Xg * Zb - Zg * Xb) + Zr * (Xg * Yb - Yg * Xb);
    if (std::abs(det) < 1e-10f) {
        // Identity fallback
        m[0] = 1; m[1] = 0; m[2] = 0;
        m[3] = 0; m[4] = 1; m[5] = 0;
        m[6] = 0; m[7] = 0; m[8] = 1;
        return;
    }

    float Sr = (Xw * (Yg * Zb - Zg * Yb) - Yw * (Xg * Zb - Zg * Xb) + Zw * (Xg * Yb - Yg * Xb)) / det;
    float Sg = (Xr * (Yw * Zb - Zw * Yb) - Yr * (Xw * Zb - Zw * Xb) + Zr * (Xw * Yb - Yw * Xb)) / det;
    float Sb = (Xr * (Yg * Zw - Zg * Yw) - Yr * (Xg * Zw - Zg * Xw) + Zr * (Xg * Yw - Yg * Xw)) / det;

    m[0] = Sr * Xr; m[1] = Sg * Xg; m[2] = Sb * Xb;
    m[3] = Sr * Yr; m[4] = Sg * Yg; m[5] = Sb * Yb;
    m[6] = Sr * Zr; m[7] = Sg * Zg; m[8] = Sb * Zb;
}

static void invert_3x3(const float m[9], float inv[9]) {
    float det = m[0] * (m[4] * m[8] - m[5] * m[7]) -
                m[1] * (m[3] * m[8] - m[5] * m[6]) +
                m[2] * (m[3] * m[7] - m[4] * m[6]);
    if (std::abs(det) < 1e-10f) {
        inv[0] = 1; inv[1] = 0; inv[2] = 0;
        inv[3] = 0; inv[4] = 1; inv[5] = 0;
        inv[6] = 0; inv[7] = 0; inv[8] = 1;
        return;
    }
    float idet = 1.0f / det;
    inv[0] = (m[4] * m[8] - m[5] * m[7]) * idet;
    inv[1] = (m[2] * m[7] - m[1] * m[8]) * idet;
    inv[2] = (m[1] * m[5] - m[2] * m[4]) * idet;
    inv[3] = (m[5] * m[6] - m[3] * m[8]) * idet;
    inv[4] = (m[0] * m[8] - m[2] * m[6]) * idet;
    inv[5] = (m[2] * m[3] - m[0] * m[5]) * idet;
    inv[6] = (m[3] * m[7] - m[4] * m[6]) * idet;
    inv[7] = (m[1] * m[6] - m[0] * m[7]) * idet;
    inv[8] = (m[0] * m[4] - m[1] * m[3]) * idet;
}

static void multiply_3x3(const float a[9], const float b[9], float result[9]) {
    for (int i = 0; i < 3; ++i) {
        for (int j = 0; j < 3; ++j) {
            result[i * 3 + j] = a[i * 3 + 0] * b[0 * 3 + j] +
                                a[i * 3 + 1] * b[1 * 3 + j] +
                                a[i * 3 + 2] * b[2 * 3 + j];
        }
    }
}

// ============================================================
// Color space conversion matrices
// ============================================================

void get_xyz_to_srgb_matrix(float m[9]) {
    float srgb_to_xyz[9];
    compute_rgb_to_xyz_matrix(SRGB::R_x, SRGB::R_y, SRGB::G_x, SRGB::G_y,
                              SRGB::B_x, SRGB::B_y, SRGB::W_x, SRGB::W_y, srgb_to_xyz);
    invert_3x3(srgb_to_xyz, m);
}

void get_srgb_to_xyz_matrix(float m[9]) {
    compute_rgb_to_xyz_matrix(SRGB::R_x, SRGB::R_y, SRGB::G_x, SRGB::G_y,
                              SRGB::B_x, SRGB::B_y, SRGB::W_x, SRGB::W_y, m);
}

void get_xyz_to_display_p3_matrix(float m[9]) {
    float p3_to_xyz[9];
    compute_rgb_to_xyz_matrix(DISPLAY_P3::R_x, DISPLAY_P3::R_y, DISPLAY_P3::G_x, DISPLAY_P3::G_y,
                              DISPLAY_P3::B_x, DISPLAY_P3::B_y, DISPLAY_P3::W_x, DISPLAY_P3::W_y, p3_to_xyz);
    invert_3x3(p3_to_xyz, m);
}

void get_display_p3_to_xyz_matrix(float m[9]) {
    compute_rgb_to_xyz_matrix(DISPLAY_P3::R_x, DISPLAY_P3::R_y, DISPLAY_P3::G_x, DISPLAY_P3::G_y,
                              DISPLAY_P3::B_x, DISPLAY_P3::B_y, DISPLAY_P3::W_x, DISPLAY_P3::W_y, m);
}

void get_xyz_to_rec2020_matrix(float m[9]) {
    float rec2020_to_xyz[9];
    compute_rgb_to_xyz_matrix(REC2020::R_x, REC2020::R_y, REC2020::G_x, REC2020::G_y,
                              REC2020::B_x, REC2020::B_y, REC2020::W_x, REC2020::W_y, rec2020_to_xyz);
    invert_3x3(rec2020_to_xyz, m);
}

void get_rec2020_to_xyz_matrix(float m[9]) {
    compute_rgb_to_xyz_matrix(REC2020::R_x, REC2020::R_y, REC2020::G_x, REC2020::G_y,
                              REC2020::B_x, REC2020::B_y, REC2020::W_x, REC2020::W_y, m);
}

void get_xyz_to_aces_ap0_matrix(float m[9]) {
    // ACES AP0 primaries are defined with non-standard W (D60)
    // Using the standard ACES conversion matrices
    float ap0_to_xyz[9] = {
         0.9525523959f,  0.0000000000f,  0.0000936786f,
         0.3439664498f,  0.7281660966f, -0.0721325464f,
         0.0000000000f,  0.0000000000f,  1.0088251844f
    };
    invert_3x3(ap0_to_xyz, m);
}

void get_aces_ap0_to_xyz_matrix(float m[9]) {
    // ACES AP0 → XYZ (D60)
    float ap0_to_xyz[9] = {
         0.9525523959f,  0.0000000000f,  0.0000936786f,
         0.3439664498f,  0.7281660966f, -0.0721325464f,
         0.0000000000f,  0.0000000000f,  1.0088251844f
    };
    for (int i = 0; i < 9; ++i) m[i] = ap0_to_xyz[i];
}

void get_aces_ap0_to_ap1_matrix(float m[9]) {
    // ACES AP0 → AP1
    float mat[9] = {
         1.4514393161f, -0.2365107469f, -0.2149285693f,
        -0.0765537734f,  1.1762296998f, -0.0996759264f,
         0.0083161484f, -0.0060324498f,  0.9977163014f
    };
    for (int i = 0; i < 9; ++i) m[i] = mat[i];
}

void get_aces_ap1_to_ap0_matrix(float m[9]) {
    float ap0_to_ap1[9];
    get_aces_ap0_to_ap1_matrix(ap0_to_ap1);
    invert_3x3(ap0_to_ap1, m);
}

void get_srgb_to_ap1_matrix(float m[9]) {
    float srgb_to_xyz[9];
    get_srgb_to_xyz_matrix(srgb_to_xyz);
    float xyz_to_ap0[9];
    get_xyz_to_aces_ap0_matrix(xyz_to_ap0);
    float ap0_to_ap1[9];
    get_aces_ap0_to_ap1_matrix(ap0_to_ap1);

    float temp[9];
    multiply_3x3(xyz_to_ap0, srgb_to_xyz, temp);
    multiply_3x3(ap0_to_ap1, temp, m);
}

void get_ap1_to_srgb_matrix(float m[9]) {
    float srgb_to_ap1[9];
    get_srgb_to_ap1_matrix(srgb_to_ap1);
    invert_3x3(srgb_to_ap1, m);
}

// ============================================================
// EOTF / OETF Functions
// ============================================================

float srgb_eotf(float linear) {
    if (linear <= 0.0031308f) {
        return 12.92f * linear;
    }
    return 1.055f * std::pow(linear, 1.0f / 2.4f) - 0.055f;
}

void srgb_eotf_rgb(float* r, float* g, float* b) {
    *r = srgb_eotf(*r);
    *g = srgb_eotf(*g);
    *b = srgb_eotf(*b);
}

float srgb_inverse_eotf(float srgb) {
    if (srgb <= 0.04045f) {
        return srgb / 12.92f;
    }
    return std::pow((srgb + 0.055f) / 1.055f, 2.4f);
}

void srgb_inverse_eotf_rgb(float* r, float* g, float* b) {
    *r = srgb_inverse_eotf(*r);
    *g = srgb_inverse_eotf(*g);
    *b = srgb_inverse_eotf(*b);
}

float pq_eotf(float linear, float peak_luminance) {
    // ST.2084 PQ EOTF
    // Normalize to 0..1 range (1.0 = peak_luminance)
    float y = linear * (10000.0f / peak_luminance);
    y = std::max(0.0f, y);

    const float m1 = 0.1593017578125f;  // 2610/16384
    const float m2 = 78.84375f;          // 2523/32
    const float c1 = 0.8359375f;         // 3424/4096
    const float c2 = 18.8515625f;        // 2413/128
    const float c3 = 18.6875f;           // 2392/128

    float y_pow = std::pow(y, m1);
    float result = std::pow((c1 + c2 * y_pow) / (1.0f + c3 * y_pow), m2);
    return result;
}

void pq_eotf_rgb(float* r, float* g, float* b, float peak_luminance) {
    *r = pq_eotf(*r, peak_luminance);
    *g = pq_eotf(*g, peak_luminance);
    *b = pq_eotf(*b, peak_luminance);
}

float pq_inverse_eotf(float pq, float peak_luminance) {
    const float m1 = 0.1593017578125f;
    const float m2 = 78.84375f;
    const float c1 = 0.8359375f;
    const float c2 = 18.8515625f;
    const float c3 = 18.6875f;

    pq = std::max(0.0f, pq);
    float pq_pow = std::pow(pq, 1.0f / m2);
    float y = std::pow(std::max(0.0f, pq_pow - c1) / (c2 - c3 * pq_pow), 1.0f / m1);
    return y * (peak_luminance / 10000.0f);
}

void pq_inverse_eotf_rgb(float* r, float* g, float* b, float peak_luminance) {
    *r = pq_inverse_eotf(*r, peak_luminance);
    *g = pq_inverse_eotf(*g, peak_luminance);
    *b = pq_inverse_eotf(*b, peak_luminance);
}

float hlg_oetf(float linear) {
    // HLG OETF (BT.2100)
    // Linear input is scene-referred (0..1 = reference white)
    const float a = 0.17883277f;
    const float b = 1.0f - 4.0f * a;
    const float c = 0.5f - a * std::log(4.0f * a);

    if (linear <= 1.0f / 12.0f) {
        return std::sqrt(3.0f * linear);
    }
    return a * std::log(12.0f * linear - b) + c;
}

void hlg_oetf_rgb(float* r, float* g, float* b) {
    *r = hlg_oetf(*r);
    *g = hlg_oetf(*g);
    *b = hlg_oetf(*b);
}

float hlg_inverse_oetf(float hlg) {
    const float a = 0.17883277f;
    const float b = 1.0f - 4.0f * a;
    const float c = 0.5f - a * std::log(4.0f * a);

    if (hlg <= 0.5f) {
        return hlg * hlg / 3.0f;
    }
    return (std::exp((hlg - c) / a) + b) / 12.0f;
}

void hlg_inverse_oetf_rgb(float* r, float* g, float* b) {
    *r = hlg_inverse_oetf(*r);
    *g = hlg_inverse_oetf(*g);
    *b = hlg_inverse_oetf(*b);
}

float gamma_eotf(float linear, float gamma) {
    float v = std::max(0.0f, linear);
    return std::pow(v, 1.0f / gamma);
}

void gamma_eotf_rgb(float* r, float* g, float* b, float gamma) {
    *r = gamma_eotf(*r, gamma);
    *g = gamma_eotf(*g, gamma);
    *b = gamma_eotf(*b, gamma);
}

float gamma_inverse_eotf(float encoded, float gamma) {
    float v = std::max(0.0f, encoded);
    return std::pow(v, gamma);
}

void gamma_inverse_eotf_rgb(float* r, float* g, float* b, float gamma) {
    *r = gamma_inverse_eotf(*r, gamma);
    *g = gamma_inverse_eotf(*g, gamma);
    *b = gamma_inverse_eotf(*b, gamma);
}

// ============================================================
// ACES 2.0 Reference Rendering Transform (RRT)
// ============================================================

// ACES 2.0 RRT: Based on the publicly documented ACES 2.0 algorithm.
// This implements the core tone mapping and gamut compression.
// Reference: ACES 2.0 Technical Documentation
//
// The RRT pipeline:
// 1. Convert to ACES AP1 working space
// 2. Apply glow module (shoulder compression)
// 3. Apply tone mapping via spline-based compression
// 4. Apply red modifier to protect saturated reds
// 5. Apply gamut compression

static float aces_glow(float x, float threshold, float gain) {
    if (x <= threshold) return x;
    float excess = x - threshold;
    return threshold + (1.0f - std::exp(-excess * gain)) / gain;
}

static float aces_tone_map_channel(float x) {
    // ACES 2.0 per-channel tone curve approximation
    // Uses a rational function for smooth highlight rolloff
    if (x <= 0.0f) return 0.0f;

    // ACES 2.0 style compression
    // output = (x * (a * x + b)) / (x * (c * x + d) + e)
    const float a = 2.51f;
    const float b = 0.03f;
    const float c = 2.43f;
    const float d = 0.59f;
    const float e = 0.14f;

    return (x * (a * x + b)) / (x * (c * x + d) + e);
}

void aces_rrt(float* r, float* g, float* b) {
    // ACES 2.0 RRT implementation
    // Input is assumed to be in ACES AP0 already

    // Step 1: Convert to AP1 working space
    float ap0_to_ap1[9];
    get_aces_ap0_to_ap1_matrix(ap0_to_ap1);
    apply_matrix_3x3(ap0_to_ap1, r, g, b);

    // Step 2: Glow module - simulate highlight bloom
    float lum = *r * 0.2126f + *g * 0.7152f + *b * 0.0722f;
    if (lum > 0.0f) {
        float glow_factor = aces_glow(lum, 0.5f, 2.0f);
        float glow_ratio = glow_factor / std::max(lum, 0.0001f);
        *r *= glow_ratio;
        *g *= glow_ratio;
        *b *= glow_ratio;
    }

    // Step 3: Per-channel tone mapping
    *r = aces_tone_map_channel(std::max(0.0f, *r));
    *g = aces_tone_map_channel(std::max(0.0f, *g));
    *b = aces_tone_map_channel(std::max(0.0f, *b));

    // Step 4: Red modifier - protect saturated reds from desaturation
    // This reduces the green and blue channels when red is very saturated
    float red_saturation = *r - std::max(*g, *b);
    if (red_saturation > 0.0f) {
        float red_mod = 1.0f - red_saturation * 0.15f;
        red_mod = std::max(0.7f, red_mod);
        *g *= red_mod;
        *b *= red_mod;
    }

    // Step 5: Gamut compression - softly compress out-of-gamut values
    // Project extreme values toward the gamut boundary
    float max_channel = std::max({*r, *g, *b});
    if (max_channel > 1.0f) {
        float excess = max_channel - 1.0f;
        float compression = 1.0f / (1.0f + excess);
        *r -= excess * (1.0f - compression) * (*r / max_channel);
        *g -= excess * (1.0f - compression) * (*g / max_channel);
        *b -= excess * (1.0f - compression) * (*b / max_channel);
    }

    // Convert back to AP0 (output is in AP0)
    float ap1_to_ap0[9];
    get_aces_ap1_to_ap0_matrix(ap1_to_ap0);
    apply_matrix_3x3(ap1_to_ap0, r, g, b);
}

void aces_rrt_bulk(float* pixels, int count, int channels, int pixel_stride) {
    for (int i = 0; i < count; ++i) {
        int idx = i * pixel_stride;
        float r = pixels[idx];
        float g = pixels[idx + 1];
        float b = pixels[idx + 2];
        aces_rrt(&r, &g, &b);
        pixels[idx] = r;
        pixels[idx + 1] = g;
        pixels[idx + 2] = b;
    }
}

void aces_output_transform_srgb(float* r, float* g, float* b, float peak_luminance) {
    // Full ACES Output Transform: AP0 → RRT → ODT (sRGB)
    aces_rrt(r, g, b);

    // Convert from AP0 to sRGB linear
    float ap0_to_xyz[9];
    get_aces_ap0_to_xyz_matrix(ap0_to_xyz);
    float xyz_to_srgb[9];
    get_xyz_to_srgb_matrix(xyz_to_srgb);

    float temp[9];
    multiply_3x3(xyz_to_srgb, ap0_to_xyz, temp);
    apply_matrix_3x3(temp, r, g, b);

    // Apply peak luminance scaling
    apply_peak_luminance_scale(r, g, b, peak_luminance);

    // Apply sRGB EOTF
    srgb_eotf_rgb(r, g, b);
}

void aces_output_transform_srgb_bulk(float* pixels, int count, int channels, float peak_luminance) {
    for (int i = 0; i < count; ++i) {
        int idx = i * channels;
        float r = pixels[idx];
        float g = pixels[idx + 1];
        float b = pixels[idx + 2];
        aces_output_transform_srgb(&r, &g, &b, peak_luminance);
        pixels[idx] = r;
        pixels[idx + 1] = g;
        pixels[idx + 2] = b;
    }
}

// ============================================================
// OpenDRT
// ============================================================

// OpenDRT is a simplified, open-source alternative to the ACES RRT.
// It uses a smooth sigmoid-based tone mapping approach.

static float opendrt_tone_curve(float x) {
    // OpenDRT tone curve: smooth sigmoid with controlled highlight rolloff
    if (x <= 0.0f) return 0.0f;

    // Parameters tuned for natural-looking filmic response
    const float toe_strength = 0.15f;
    const float toe_length = 0.5f;
    const float shoulder_strength = 0.5f;
    const float shoulder_length = 0.5f;
    const float shoulder_angle = 0.75f;

    // Toe (shadows)
    float toe = x < toe_length ?
        toe_length * (1.0f - std::exp(-x * toe_strength / toe_length)) :
        x - toe_length * toe_strength * std::exp(-1.0f);

    // Shoulder (highlights)
    float shoulder = toe < shoulder_length ? toe :
        shoulder_length + (1.0f - shoulder_length) *
        (1.0f - std::exp(-(toe - shoulder_length) * shoulder_strength / (1.0f - shoulder_length)));

    // Mix based on shoulder angle
    return toe * (1.0f - shoulder_angle) + shoulder * shoulder_angle;
}

void opendrt_tone_map(float* r, float* g, float* b) {
    // Apply OpenDRT tone mapping
    // Input is assumed to be in a working space (e.g., sRGB linear)

    // Compute luminance
    float lum = *r * 0.2126f + *g * 0.7152f + *b * 0.0722f;

    if (lum > 0.0f) {
        float mapped_lum = opendrt_tone_curve(lum);
        float scale = mapped_lum / lum;
        *r *= scale;
        *g *= scale;
        *b *= scale;
    }

    // Soft clip
    *r = std::max(0.0f, *r);
    *g = std::max(0.0f, *g);
    *b = std::max(0.0f, *b);
}

void opendrt_tone_map_bulk(float* pixels, int count, int channels, int pixel_stride) {
    for (int i = 0; i < count; ++i) {
        int idx = i * pixel_stride;
        float r = pixels[idx];
        float g = pixels[idx + 1];
        float b = pixels[idx + 2];
        opendrt_tone_map(&r, &g, &b);
        pixels[idx] = r;
        pixels[idx + 1] = g;
        pixels[idx + 2] = b;
    }
}

void opendrt_output_transform(float* r, float* g, float* b, float peak_luminance) {
    opendrt_tone_map(r, g, b);
    apply_peak_luminance_scale(r, g, b, peak_luminance);
    srgb_eotf_rgb(r, g, b);
}

void opendrt_output_transform_bulk(float* pixels, int count, int channels, float peak_luminance) {
    for (int i = 0; i < count; ++i) {
        int idx = i * channels;
        float r = pixels[idx];
        float g = pixels[idx + 1];
        float b = pixels[idx + 2];
        opendrt_output_transform(&r, &g, &b, peak_luminance);
        pixels[idx] = r;
        pixels[idx + 1] = g;
        pixels[idx + 2] = b;
    }
}

// ============================================================
// Sigmoid Contrast Curve
// ============================================================

void sigmoid_contrast(float* r, float* g, float* b, float contrast, float pivot, float shoulder) {
    if (contrast == 0.0f) return;

    // Sigmoid contrast: S-curve around pivot
    float strength = contrast * 5.0f; // Scale contrast for visible effect

    auto sigmoid = [strength, pivot, shoulder](float x) -> float {
        // Normalize around pivot
        float xn = (x - pivot) / (pivot + 0.001f);
        // Apply sigmoid
        float sig = 1.0f / (1.0f + std::exp(-strength * xn));
        // Remap from [0,1] sigmoid to [0,1] output
        // Keep pivot at the same value
        float sig_at_pivot = 0.5f; // sigmoid(0) = 0.5
        float result = sig;
        // Adjust to maintain pivot
        float pivot_offset = pivot - sig_at_pivot;
        result += pivot_offset;

        // Shoulder: compress highlights
        if (result > 1.0f && shoulder > 0.0f) {
            float excess = result - 1.0f;
            result = 1.0f + excess / (1.0f + excess * shoulder * 5.0f);
        }

        return std::max(0.0f, std::min(1.5f, result));
    };

    *r = sigmoid(*r);
    *g = sigmoid(*g);
    *b = sigmoid(*b);
}

void sigmoid_contrast_bulk(float* pixels, int count, int channels, float contrast, float pivot, float shoulder) {
    for (int i = 0; i < count; ++i) {
        int idx = i * channels;
        float r = pixels[idx];
        float g = pixels[idx + 1];
        float b = pixels[idx + 2];
        sigmoid_contrast(&r, &g, &b, contrast, pivot, shoulder);
        pixels[idx] = r;
        pixels[idx + 1] = g;
        pixels[idx + 2] = b;
    }
}

// ============================================================
// OKLab Color Space
// ============================================================

// OKLab: Perceptually uniform color space by Björn Ottosson
// https://bottosson.github.io/posts/oklab/

void linear_srgb_to_oklab(float r, float g, float b, float* L, float* a, float* bb) {
    // Step 1: Linear sRGB → LMS
    float l_ = 0.4122214708f * r + 0.5363325363f * g + 0.0514459929f * b;
    float m_ = 0.2119034982f * r + 0.6806995451f * g + 0.1073969566f * b;
    float s_ = 0.0883024619f * r + 0.2817188376f * g + 0.6299787005f * b;

    // Step 2: LMS → LMS' (cube root)
    float l_p = std::cbrt(l_);
    float m_p = std::cbrt(m_);
    float s_p = std::cbrt(s_);

    // Step 3: LMS' → OKLab
    *L = 0.2104542553f * l_p + 0.7936177850f * m_p - 0.0040720468f * s_p;
    *a = 1.9779984951f * l_p - 2.4285922050f * m_p + 0.4505937099f * s_p;
    *bb = 0.0259040371f * l_p + 0.7827717662f * m_p - 0.8086757660f * s_p;
}

void oklab_to_linear_srgb(float L, float a, float bb, float* r, float* g, float* b) {
    // Step 1: OKLab → LMS'
    float l_p = L + 0.3963377774f * a + 0.2158037573f * bb;
    float m_p = L - 0.1055613458f * a - 0.0638541728f * bb;
    float s_p = L - 0.0894841775f * a - 1.2914855480f * bb;

    // Step 2: LMS' → LMS (cube)
    float l_ = l_p * l_p * l_p;
    float m_ = m_p * m_p * m_p;
    float s_ = s_p * s_p * s_p;

    // Step 3: LMS → Linear sRGB
    *r =  4.0767416621f * l_ - 3.3077115913f * m_ + 0.2309699292f * s_;
    *g = -1.2684380046f * l_ + 2.6097574011f * m_ - 0.3413193965f * s_;
    *b = -0.0041960863f * l_ - 0.7034186147f * m_ + 1.7076147010f * s_;
}

void okhsl_to_srgb(float h, float s, float l, float* r, float* g, float* b) {
    // Simplified Okhsl → sRGB
    // Okhsl is a polar form of OKLab designed for color picking
    // h: 0-360, s: 0-1, l: 0-1

    float a_ = s * std::cos(h * M_PI / 180.0f);
    float bb_ = s * std::sin(h * M_PI / 180.0f);

    oklab_to_linear_srgb(l, a_, bb_, r, g, b);

    // Clamp
    *r = std::max(0.0f, *r);
    *g = std::max(0.0f, *g);
    *b = std::max(0.0f, *b);
}

void srgb_to_okhsl(float r, float g, float b, float* h, float* s, float* l) {
    float L, a_, bb_;
    linear_srgb_to_oklab(r, g, b, &L, &a_, &bb_);

    *l = L;
    *s = std::sqrt(a_ * a_ + bb_ * bb_);
    *h = std::atan2(bb_, a_) * 180.0f / M_PI;
    if (*h < 0.0f) *h += 360.0f;
}

// ============================================================
// Peak Luminance Handling
// ============================================================

void apply_peak_luminance_scale(float* r, float* g, float* b, float peak_luminance, float reference_white) {
    // Scale display-referred linear values so that 1.0 = reference_white cd/m²
    // and peak_luminance maps to the maximum displayable value
    float scale = reference_white / peak_luminance;
    *r *= scale;
    *g *= scale;
    *b *= scale;
}

void apply_peak_luminance_scale_bulk(float* pixels, int count, int channels, float peak_luminance, float reference_white) {
    float scale = reference_white / peak_luminance;
    for (int i = 0; i < count; ++i) {
        int idx = i * channels;
        pixels[idx]     *= scale;
        pixels[idx + 1] *= scale;
        pixels[idx + 2] *= scale;
    }
}

// ============================================================
// Color space conversion
// ============================================================

void convert_color_space(float* r, float* g, float* b, int src_space, int dst_space) {
    // All conversions go through XYZ as the intermediate space
    // Space codes: 0=sRGB, 1=Display P3, 2=Rec2020, 3=ACES AP0, 4=ACES AP1

    auto get_to_xyz = [](int space, float m[9]) {
        switch (space) {
            case 0: get_srgb_to_xyz_matrix(m); break;
            case 1: get_display_p3_to_xyz_matrix(m); break;
            case 2: get_rec2020_to_xyz_matrix(m); break;
            case 3: get_aces_ap0_to_xyz_matrix(m); break;
            case 4: { float ap1_to_ap0[9], ap0_to_xyz[9];
                     get_aces_ap1_to_ap0_matrix(ap1_to_ap0);
                     get_aces_ap0_to_xyz_matrix(ap0_to_xyz);
                     multiply_3x3(ap0_to_xyz, ap1_to_ap0, m); break; }
            default: m[0]=1;m[1]=0;m[2]=0;m[3]=0;m[4]=1;m[5]=0;m[6]=0;m[7]=0;m[8]=1; break;
        }
    };

    auto get_from_xyz = [](int space, float m[9]) {
        switch (space) {
            case 0: get_xyz_to_srgb_matrix(m); break;
            case 1: get_xyz_to_display_p3_matrix(m); break;
            case 2: get_xyz_to_rec2020_matrix(m); break;
            case 3: get_xyz_to_aces_ap0_matrix(m); break;
            case 4: { float xyz_to_ap0[9], ap0_to_ap1[9];
                     get_xyz_to_aces_ap0_matrix(xyz_to_ap0);
                     get_aces_ap0_to_ap1_matrix(ap0_to_ap1);
                     multiply_3x3(ap0_to_ap1, xyz_to_ap0, m); break; }
            default: m[0]=1;m[1]=0;m[2]=0;m[3]=0;m[4]=1;m[5]=0;m[6]=0;m[7]=0;m[8]=1; break;
        }
    };

    float to_xyz[9], from_xyz[9];
    get_to_xyz(src_space, to_xyz);
    get_from_xyz(dst_space, from_xyz);

    float full[9];
    multiply_3x3(from_xyz, to_xyz, full);
    apply_matrix_3x3(full, r, g, b);
}

void convert_color_space_bulk(float* pixels, int count, int channels, int src_space, int dst_space) {
    for (int i = 0; i < count; ++i) {
        int idx = i * channels;
        float r = pixels[idx];
        float g = pixels[idx + 1];
        float b = pixels[idx + 2];
        convert_color_space(&r, &g, &b, src_space, dst_space);
        pixels[idx] = r;
        pixels[idx + 1] = g;
        pixels[idx + 2] = b;
    }
}

} // namespace color_science
} // namespace alcedo