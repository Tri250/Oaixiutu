// AlcedoAndroid - OKLab color conversion utilities (RGB<->OKLab/OKLCh).
// Used by color science operators and the Saturation/Vibrance GPU kernels.
// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "image/image_buffer.hpp"
namespace alcedo {
namespace oklab {

struct Lab { float L = 0, a = 0, b = 0; };
struct LCh { float L = 0, C = 0, h = 0; };

// Linear sRGB (or any linear RGB with sRGB primaries) -> OKLab.
Lab LinearRgbToOkLab(float r, float g, float b);
// OKLab -> linear RGB.
void OkLabToLinearRgb(const Lab& lab, float& r, float& g, float& b);
// Lab <-> LCh.
LCh LabToLCh(const Lab& lab);
Lab LChToLab(const LCh& lch);

// In-place per-pixel OKLCh chroma scale (used by Saturation GPU fallback).
void ScaleChroma(FloatMat& img, float chroma_scale);

}  // namespace oklab
}  // namespace alcedo
