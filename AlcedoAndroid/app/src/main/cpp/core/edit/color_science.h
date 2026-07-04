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

} // namespace color_science
} // namespace alcedo