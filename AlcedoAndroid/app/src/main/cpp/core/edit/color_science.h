#pragma once

#include <cstddef>
#include <cmath>
#include <algorithm>

namespace alcedo {
namespace color_science {

// ============================================================
// Color space primaries and matrices
// ============================================================

// ACES AP0 primaries (ACES 2065-1)
struct ACES_AP0 {
    static constexpr float R_x = 0.7347f, R_y = 0.2653f;
    static constexpr float G_x = 0.0000f, G_y = 1.0000f;
    static constexpr float B_x = 0.0001f, B_y = -0.0770f;
    static constexpr float W_x = 0.32168f, W_y = 0.33767f;
};

// ACES AP1 primaries (ACEScg)
struct ACES_AP1 {
    static constexpr float R_x = 0.713f, R_y = 0.293f;
    static constexpr float G_x = 0.165f, G_y = 0.830f;
    static constexpr float B_x = 0.128f, B_y = 0.044f;
    static constexpr float W_x = 0.32168f, W_y = 0.33767f;
};

// Display P3 primaries
struct DISPLAY_P3 {
    static constexpr float R_x = 0.680f, R_y = 0.320f;
    static constexpr float G_x = 0.265f, G_y = 0.690f;
    static constexpr float B_x = 0.150f, B_y = 0.060f;
    static constexpr float W_x = 0.3127f, W_y = 0.3290f;
};

// Rec.2020 primaries
struct REC2020 {
    static constexpr float R_x = 0.708f, R_y = 0.292f;
    static constexpr float G_x = 0.170f, G_y = 0.797f;
    static constexpr float B_x = 0.131f, B_y = 0.046f;
    static constexpr float W_x = 0.3127f, W_y = 0.3290f;
};

// sRGB / Rec.709 primaries
struct SRGB {
    static constexpr float R_x = 0.640f, R_y = 0.330f;
    static constexpr float G_x = 0.300f, G_y = 0.600f;
    static constexpr float B_x = 0.150f, B_y = 0.060f;
    static constexpr float W_x = 0.3127f, W_y = 0.3290f;
};

// ============================================================
// Color space conversion matrices
// ============================================================

// Get precomputed 3x3 matrices for color space conversions
void get_xyz_to_srgb_matrix(float m[9]);
void get_srgb_to_xyz_matrix(float m[9]);
void get_xyz_to_display_p3_matrix(float m[9]);
void get_display_p3_to_xyz_matrix(float m[9]);
void get_xyz_to_rec2020_matrix(float m[9]);
void get_rec2020_to_xyz_matrix(float m[9]);
void get_xyz_to_aces_ap0_matrix(float m[9]);
void get_aces_ap0_to_xyz_matrix(float m[9]);
void get_aces_ap0_to_ap1_matrix(float m[9]);
void get_aces_ap1_to_ap0_matrix(float m[9]);
void get_srgb_to_ap1_matrix(float m[9]);
void get_ap1_to_srgb_matrix(float m[9]);

// ============================================================
// EOTF / OETF Functions
// ============================================================

// sRGB EOTF (electro-optical transfer function): linear -> sRGB
float srgb_eotf(float linear);
void srgb_eotf_rgb(float* r, float* g, float* b);

// sRGB inverse EOTF: sRGB -> linear
float srgb_inverse_eotf(float srgb);
void srgb_inverse_eotf_rgb(float* r, float* g, float* b);

// PQ (ST.2084) EOTF: linear -> PQ
float pq_eotf(float linear, float peak_luminance = 10000.0f);
void pq_eotf_rgb(float* r, float* g, float* b, float peak_luminance = 10000.0f);

// PQ inverse EOTF: PQ -> linear
float pq_inverse_eotf(float pq, float peak_luminance = 10000.0f);
void pq_inverse_eotf_rgb(float* r, float* g, float* b, float peak_luminance = 10000.0f);

// HLG OETF: linear -> HLG
float hlg_oetf(float linear);
void hlg_oetf_rgb(float* r, float* g, float* b);

// HLG inverse OETF: HLG -> linear
float hlg_inverse_oetf(float hlg);
void hlg_inverse_oetf_rgb(float* r, float* g, float* b);

// Pure gamma EOTF
float gamma_eotf(float linear, float gamma = 2.2f);
void gamma_eotf_rgb(float* r, float* g, float* b, float gamma = 2.2f);

// Pure gamma inverse EOTF
float gamma_inverse_eotf(float encoded, float gamma = 2.2f);
void gamma_inverse_eotf_rgb(float* r, float* g, float* b, float gamma = 2.2f);

// ============================================================
// ACES 2.0 Output Rendering Transform
// ============================================================

// ACES 2.0 Reference Rendering Transform (RRT)
// Input: scene-linear ACES AP0 values
// Output: display-linear values in the output color space
// Uses the ACES 2.0 algorithm with the following components:
// 1. Glow module (simulates filmic highlight rolloff)
// 2. Tone mapping (compresses dynamic range)
// 3. Red modifier (protects saturated reds)
// 4. Gamut compression (maps out-of-gamut colors)
void aces_rrt(float* r, float* g, float* b);

// Bulk processing
void aces_rrt_bulk(float* pixels, int count, int channels, int pixel_stride = 3);

// Full ACES Output Transform (RRT + ODT for sRGB)
void aces_output_transform_srgb(float* r, float* g, float* b, float peak_luminance = 100.0f);
void aces_output_transform_srgb_bulk(float* pixels, int count, int channels, float peak_luminance = 100.0f);

// ============================================================
// OpenDRT (Open Display Rendering Transform)
// ============================================================

// OpenDRT is an open-source alternative to ACES RRT developed by the community.
// Based on the reference implementation using a simplified tone mapping approach.
// Input: scene-linear in a working space (e.g., ACES AP1)
// Output: display-linear

void opendrt_tone_map(float* r, float* g, float* b);
void opendrt_tone_map_bulk(float* pixels, int count, int channels, int pixel_stride = 3);

// Full OpenDRT pipeline
void opendrt_output_transform(float* r, float* g, float* b, float peak_luminance = 100.0f);
void opendrt_output_transform_bulk(float* pixels, int count, int channels, float peak_luminance = 100.0f);

// ============================================================
// Sigmoid Contrast Curve
// ============================================================

// Sigmoid contrast: a smooth S-curve that avoids hard clipping.
// contrast: -1.0 to 1.0 (0 = no change)
// pivot: gray point, typically 0.18 (18% gray)
// shoulder: how much to compress highlights (0.0 to 1.0)
void sigmoid_contrast(float* r, float* g, float* b, float contrast, float pivot = 0.18f, float shoulder = 0.5f);
void sigmoid_contrast_bulk(float* pixels, int count, int channels, float contrast, float pivot = 0.18f, float shoulder = 0.5f);

// ============================================================
// OKLab Color Space
// ============================================================

// OKLab is a perceptually uniform color space.
// Conversions between linear sRGB and OKLab.

void linear_srgb_to_oklab(float r, float g, float b, float* L, float* a, float* bb);
void oklab_to_linear_srgb(float L, float a, float bb, float* r, float* g, float* b);

void okhsl_to_srgb(float h, float s, float l, float* r, float* g, float* b);
void srgb_to_okhsl(float r, float g, float b, float* h, float* s, float* l);

// ============================================================
// Peak Luminance Handling
// ============================================================

// Scale linear values to account for display peak luminance.
// This maps the scene-referred linear values to a display-referred
// range where 1.0 corresponds to the peak luminance of the display.
void apply_peak_luminance_scale(float* r, float* g, float* b, float peak_luminance, float reference_white = 100.0f);
void apply_peak_luminance_scale_bulk(float* pixels, int count, int channels, float peak_luminance, float reference_white = 100.0f);

// ============================================================
// Utility
// ============================================================

// Apply 3x3 matrix to RGB triplet
void apply_matrix_3x3(const float m[9], float* r, float* g, float* b);
void apply_matrix_3x3_bulk(const float m[9], float* pixels, int count, int channels, int pixel_stride = 3);

// Clamp to valid range
float clampf(float v, float lo, float hi);

// Convert between color spaces in one step
// src_space: 0=sRGB, 1=Display P3, 2=Rec2020, 3=ACES AP0, 4=ACES AP1
// dst_space: same encoding
void convert_color_space(float* r, float* g, float* b, int src_space, int dst_space);
void convert_color_space_bulk(float* pixels, int count, int channels, int src_space, int dst_space);

// ============================================================
// Planckian Locus (Black-Body Radiation Color Temperature)
// ============================================================

// Planckian locus lookup table: (color_temp_K, r, g, b)
// Derived from CIE 1931 2-degree observer chromaticity coordinates of
// Planckian radiator, converted to sRGB linear (D65 adapted).
// Covers 2000K to 15000K at key intervals.

struct PlanckianLocusEntry {
    int temp_K;
    float r, g, b;
};

// Planckian locus table: 2000K to 15000K
inline constexpr PlanckianLocusEntry kPlanckianLocusTable[] = {
    { 2000, 1.273590f, 0.680762f, 0.259394f },
    { 2200, 1.224730f, 0.711782f, 0.302100f },
    { 2400, 1.184030f, 0.738447f, 0.342314f },
    { 2600, 1.149490f, 0.761716f, 0.380009f },
    { 2800, 1.119710f, 0.782277f, 0.415273f },
    { 3000, 1.093720f, 0.800646f, 0.448226f },
    { 3200, 1.070810f, 0.817210f, 0.479005f },
    { 3400, 1.050460f, 0.832164f, 0.507753f },
    { 3600, 1.032260f, 0.845741f, 0.534615f },
    { 3800, 1.015920f, 0.858130f, 0.559725f },
    { 4000, 1.001190f, 0.869478f, 0.583207f },
    { 4200, 0.987867f, 0.879902f, 0.605173f },
    { 4400, 0.975778f, 0.889501f, 0.625721f },
    { 4600, 0.964781f, 0.898360f, 0.644940f },
    { 4800, 0.954754f, 0.906556f, 0.662909f },
    { 5000, 0.945591f, 0.914154f, 0.679700f },
    { 5200, 0.937201f, 0.921209f, 0.695378f },
    { 5400, 0.929507f, 0.927769f, 0.710000f },
    { 5600, 0.922442f, 0.933877f, 0.723618f },
    { 5800, 0.915945f, 0.939571f, 0.736377f },
    { 6000, 0.909959f, 0.944885f, 0.748320f },
    { 6200, 0.904434f, 0.949850f, 0.759490f },
    { 6400, 0.899325f, 0.954493f, 0.769928f },
    { 6500, 0.896934f, 0.956700f, 0.774858f },
    { 6600, 0.894598f, 0.958811f, 0.779617f },
    { 6800, 0.890240f, 0.962787f, 0.788707f },
    { 7000, 0.886200f, 0.966468f, 0.797126f },
    { 7500, 0.877229f, 0.974477f, 0.815829f },
    { 8000, 0.869568f, 0.981312f, 0.832191f },
    { 8500, 0.862927f, 0.987204f, 0.846723f },
    { 9000, 0.857100f, 0.992322f, 0.859769f },
    { 9500, 0.851934f, 0.996804f, 0.871577f },
    {10000, 0.847310f, 1.000750f, 0.882321f },
    {10500, 0.843145f, 1.004250f, 0.892139f },
    {11000, 0.839365f, 1.007360f, 0.901145f },
    {11500, 0.835916f, 1.010130f, 0.909442f },
    {12000, 0.832754f, 1.012600f, 0.917118f },
    {13000, 0.827166f, 1.016770f, 0.930570f },
    {14000, 0.822500f, 1.020030f, 0.941890f },
    {15000, 0.818557f, 1.022640f, 0.951560f },
};

inline constexpr int kPlanckianLocusTableSize =
    sizeof(kPlanckianLocusTable) / sizeof(kPlanckianLocusTable[0]);

// Look up the Planckian locus RGB multipliers for a given color temperature.
// Interpolates between table entries for arbitrary temperatures.
// Returns (r_mult, g_mult, b_mult) multipliers that can be applied
// to convert from the given illuminant to D65 white point.
inline void planckian_locus_rgb(float temp_K, float* r_mult, float* g_mult, float* b_mult) {
    // Clamp to table range
    float t = std::max(2000.0f, std::min(15000.0f, temp_K));

    // Binary search for the correct interval
    int lo = 0;
    int hi = kPlanckianLocusTableSize - 1;

    while (lo < hi - 1) {
        int mid = (lo + hi) / 2;
        if (kPlanckianLocusTable[mid].temp_K < t) {
            lo = mid;
        } else {
            hi = mid;
        }
    }

    const auto& entry_lo = kPlanckianLocusTable[lo];
    const auto& entry_hi = kPlanckianLocusTable[hi];

    float t_lo = static_cast<float>(entry_lo.temp_K);
    float t_hi = static_cast<float>(entry_hi.temp_K);
    float frac = (t_hi > t_lo) ? (t - t_lo) / (t_hi - t_lo) : 0.0f;
    frac = std::max(0.0f, std::min(1.0f, frac));

    // Linear interpolation of RGB multipliers
    *r_mult = entry_lo.r + frac * (entry_hi.r - entry_lo.r);
    *g_mult = entry_lo.g + frac * (entry_hi.g - entry_lo.g);
    *b_mult = entry_lo.b + frac * (entry_hi.b - entry_lo.b);

    // Normalize so that 6500K (D65) yields (1, 1, 1)
    // The table entry for 6500K is approximately (0.896934, 0.956700, 0.774858)
    // D65 reference multipliers:
    constexpr float d65_r = 0.896934f;
    constexpr float d65_g = 0.956700f;
    constexpr float d65_b = 0.774858f;

    *r_mult /= d65_r;
    *g_mult /= d65_g;
    *b_mult /= d65_b;
}

// Apply Planckian locus white balance to a single pixel
inline void planckian_white_balance(float* r, float* g, float* b, float temp_K, float tint) {
    float rm, gm, bm;
    planckian_locus_rgb(temp_K, &rm, &gm, &bm);

    *r *= rm;
    *g *= gm + tint * 0.01f;  // tint shifts green-magenta axis
    *b *= bm;
}

// Apply Planckian locus white balance in bulk
inline void planckian_white_balance_bulk(float* pixels, int count, int channels,
                                          float temp_K, float tint) {
    float rm, gm, bm;
    planckian_locus_rgb(temp_K, &rm, &gm, &bm);
    float g_offset = tint * 0.01f;

    for (int i = 0; i < count; ++i) {
        int idx = i * channels;
        pixels[idx]     *= rm;
        pixels[idx + 1] += g_offset;
        pixels[idx + 1] *= gm;
        pixels[idx + 2] *= bm;
    }
}

} // namespace color_science
} // namespace alcedo