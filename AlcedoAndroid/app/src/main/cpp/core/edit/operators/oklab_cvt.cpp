#include "oklab_cvt.h"
#include "../color_science.h"
#include <cmath>
#include <algorithm>
#include <vector>

namespace alcedo {
namespace OklabCvt {

// ============================================================
// Oklab conversion matrices (Björn Ottosson's specification)
// ============================================================

// Linear sRGB → OKLab via LMS (cone response)
static constexpr float kRGB2LMS[9] = {
    0.4122214708f, 0.5363325363f, 0.0514459929f,
    0.2119034982f, 0.6806995451f, 0.1073969566f,
    0.0883024619f, 0.2817188376f, 0.6299787005f
};

// LMS' (cube root) → OKLab
static constexpr float kLMSp2Lab[9] = {
    0.2104542553f,  0.7936177850f, -0.0040720468f,
    1.9779984951f, -2.4285922050f,  0.4505937099f,
    0.0259040371f,  0.7827717662f, -0.8086757660f
};

// OKLab → LMS' (cube root)
static constexpr float kLab2LMSp[9] = {
    1.0f,  0.3963377774f,  0.2158037573f,
    1.0f, -0.1055613458f, -0.0638541728f,
    1.0f, -0.0894841775f, -1.2914855480f
};

// LMS → Linear sRGB
static constexpr float kLMS2RGB[9] = {
     4.0767416621f, -3.3077115913f,  0.2309699292f,
    -1.2684380046f,  2.6097574011f, -0.3413193965f,
    -0.0041960863f, -0.7034186147f,  1.7076147010f
};

// ACES AP1 → Linear sRGB matrix (for ACESRGB↔Oklab bridge)
static float s_ap1_to_srgb[9] = {};
static float s_srgb_to_ap1[9] = {};
static bool s_matrices_initialized = false;

static void EnsureMatrices() {
    if (!s_matrices_initialized) {
        color_science::get_ap1_to_srgb_matrix(s_ap1_to_srgb);
        color_science::get_srgb_to_ap1_matrix(s_srgb_to_ap1);
        s_matrices_initialized = true;
    }
}

// ============================================================
// ACEScc LUT (4096 levels)
// ============================================================

static constexpr int kAcesccLUTSize = 4096;
static std::vector<float> s_acescc_to_linear_lut;
static std::vector<float> s_linear_to_acescc_lut;
static bool s_lut_initialized = false;

static float AcesccEncodeRaw(float linear) {
    if (linear <= 0.0f) return -0.35828683f;
    if (linear < 0.000125f) {
        return (std::log2(0.000125f * 16.0f) + 9.72f) / 17.52f;
    }
    if (linear < 65504.0f) {
        return (std::log2(linear) + 9.72f) / 17.52f;
    }
    return 1.4679964f;
}

static float AcesccDecodeRaw(float acescc) {
    if (acescc <= -0.3013698630f) return 0.0f;
    if (acescc < 0.0145f) {
        return std::pow(2.0f, acescc * 17.52f - 9.72f) / 16.0f;
    }
    if (acescc < 1.0f) {
        return std::pow(2.0f, acescc * 17.52f - 9.72f);
    }
    return 65504.0f;
}

void InitAcesccLUT() {
    if (s_lut_initialized) return;

    s_acescc_to_linear_lut.resize(kAcesccLUTSize);
    s_linear_to_acescc_lut.resize(kAcesccLUTSize);

    // ACEScc range: approximately -0.358 to 1.468
    float acescc_min = -0.36f;
    float acescc_max = 1.47f;

    for (int i = 0; i < kAcesccLUTSize; ++i) {
        float acescc = acescc_min + (acescc_max - acescc_min) * i / (kAcesccLUTSize - 1);
        s_acescc_to_linear_lut[i] = AcesccDecodeRaw(acescc);
    }

    // Linear range: 0 to ~65504, but most useful range is 0 to ~16
    // Use logarithmic spacing for better precision in shadows
    for (int i = 0; i < kAcesccLUTSize; ++i) {
        float t = static_cast<float>(i) / (kAcesccLUTSize - 1);
        // Map t∈[0,1] to linear∈[0, 16] with sqrt spacing for shadow precision
        float linear = t * t * 16.0f;
        s_linear_to_acescc_lut[i] = AcesccEncodeRaw(linear);
    }

    s_lut_initialized = true;
}

float AcesccToLinear(float acescc) {
    if (!s_lut_initialized) InitAcesccLUT();

    float acescc_min = -0.36f;
    float acescc_max = 1.47f;
    float t = (acescc - acescc_min) / (acescc_max - acescc_min);
    t = std::clamp(t, 0.0f, 1.0f);

    float idx_f = t * (kAcesccLUTSize - 1);
    int idx0 = static_cast<int>(idx_f);
    int idx1 = std::min(idx0 + 1, kAcesccLUTSize - 1);
    float frac = idx_f - idx0;

    return s_acescc_to_linear_lut[idx0] * (1.0f - frac) +
           s_acescc_to_linear_lut[idx1] * frac;
}

float LinearToAcescc(float linear) {
    if (!s_lut_initialized) InitAcesccLUT();

    float t = std::sqrt(std::clamp(linear / 16.0f, 0.0f, 1.0f));
    float idx_f = t * (kAcesccLUTSize - 1);
    int idx0 = static_cast<int>(idx_f);
    int idx1 = std::min(idx0 + 1, kAcesccLUTSize - 1);
    float frac = idx_f - idx0;

    return s_linear_to_acescc_lut[idx0] * (1.0f - frac) +
           s_linear_to_acescc_lut[idx1] * frac;
}

// ============================================================
// Oklab conversions
// ============================================================

Oklab LinearRGB2Oklab(float r, float g, float b) {
    // Step 1: Linear RGB → LMS
    float l = kRGB2LMS[0] * r + kRGB2LMS[1] * g + kRGB2LMS[2] * b;
    float m = kRGB2LMS[3] * r + kRGB2LMS[4] * g + kRGB2LMS[5] * b;
    float s = kRGB2LMS[6] * r + kRGB2LMS[7] * g + kRGB2LMS[8] * b;

    // Step 2: LMS → LMS' (cube root for perceptual uniformity)
    float lp = std::cbrt(std::max(l, 0.0f));
    float mp = std::cbrt(std::max(m, 0.0f));
    float sp = std::cbrt(std::max(s, 0.0f));

    // Step 3: LMS' → OKLab
    Oklab lab;
    lab.l = kLMSp2Lab[0] * lp + kLMSp2Lab[1] * mp + kLMSp2Lab[2] * sp;
    lab.a = kLMSp2Lab[3] * lp + kLMSp2Lab[4] * mp + kLMSp2Lab[5] * sp;
    lab.b = kLMSp2Lab[6] * lp + kLMSp2Lab[7] * mp + kLMSp2Lab[8] * sp;
    return lab;
}

void Oklab2LinearRGB(const Oklab& lab, float* r, float* g, float* b) {
    // Step 1: OKLab → LMS'
    float lp = kLab2LMSp[0] * lab.l + kLab2LMSp[1] * lab.a + kLab2LMSp[2] * lab.b;
    float mp = kLab2LMSp[3] * lab.l + kLab2LMSp[4] * lab.a + kLab2LMSp[5] * lab.b;
    float sp = kLab2LMSp[6] * lab.l + kLab2LMSp[7] * lab.a + kLab2LMSp[8] * lab.b;

    // Step 2: LMS' → LMS (cube)
    float l = lp * lp * lp;
    float m = mp * mp * mp;
    float s = sp * sp * sp;

    // Step 3: LMS → Linear RGB
    *r = kLMS2RGB[0] * l + kLMS2RGB[1] * m + kLMS2RGB[2] * s;
    *g = kLMS2RGB[3] * l + kLMS2RGB[4] * m + kLMS2RGB[5] * s;
    *b = kLMS2RGB[6] * l + kLMS2RGB[7] * m + kLMS2RGB[8] * s;
}

Oklab ACESRGB2Oklab(float r, float g, float b) {
    EnsureMatrices();
    // Convert ACES AP1 → Linear sRGB first
    float sr = r * s_ap1_to_srgb[0] + g * s_ap1_to_srgb[1] + b * s_ap1_to_srgb[2];
    float sg = r * s_ap1_to_srgb[3] + g * s_ap1_to_srgb[4] + b * s_ap1_to_srgb[5];
    float sb = r * s_ap1_to_srgb[6] + g * s_ap1_to_srgb[7] + b * s_ap1_to_srgb[8];
    return LinearRGB2Oklab(sr, sg, sb);
}

void Oklab2ACESRGB(const Oklab& lab, float* r, float* g, float* b) {
    EnsureMatrices();
    // Convert via Linear sRGB → ACES AP1
    float sr, sg, sb;
    Oklab2LinearRGB(lab, &sr, &sg, &sb);
    *r = sr * s_srgb_to_ap1[0] + sg * s_srgb_to_ap1[1] + sb * s_srgb_to_ap1[2];
    *g = sr * s_srgb_to_ap1[3] + sg * s_srgb_to_ap1[4] + sb * s_srgb_to_ap1[5];
    *b = sr * s_srgb_to_ap1[6] + sg * s_srgb_to_ap1[7] + sb * s_srgb_to_ap1[8];
}

} // namespace OklabCvt
} // namespace alcedo
