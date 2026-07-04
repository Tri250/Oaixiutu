#pragma once

namespace alcedo {
namespace OklabCvt {

struct Oklab { float l, a, b; };

Oklab LinearRGB2Oklab(float r, float g, float b);
void Oklab2LinearRGB(const Oklab& lab, float* r, float* g, float* b);
Oklab ACESRGB2Oklab(float r, float g, float b);
void Oklab2ACESRGB(const Oklab& lab, float* r, float* g, float* b);

// ACEScc ↔ Linear LUT (4096 levels) for fast conversion
void InitAcesccLUT();
float AcesccToLinear(float acescc);
float LinearToAcescc(float linear);

} // namespace OklabCvt
} // namespace alcedo
