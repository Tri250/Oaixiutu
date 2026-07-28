// AlcedoAndroid - OKLab conversion implementation.
// Reference: Björn Ottosson, "A perceptual color space for image processing"
// SPDX-License-Identifier: GPL-3.0-only
#include "edit/operators/color/conversion/oklab_cvt.hpp"
#include <cmath>
namespace alcedo::oklab {
Lab LinearRgbToOkLab(float r, float g, float b) {
  float l = 0.4122214708f * r + 0.5363325363f * g + 0.0514459929f * b;
  float m = 0.2119034982f * r + 0.6806995451f * g + 0.1073969566f * b;
  float s = 0.0883024619f * r + 0.2817188376f * g + 0.6299787005f * b;
  l = std::cbrt(l); m = std::cbrt(m); s = std::cbrt(s);
  Lab out;
  out.L = 0.2104542553f * l + 0.7936177850f * m - 0.0040720468f * s;
  out.a = 1.9779984951f * l - 2.4285922050f * m + 0.4505937099f * s;
  out.b = 0.0259040371f * l + 0.7827717662f * m - 0.8086757660f * s;
  return out;
}
void OkLabToLinearRgb(const Lab& lab, float& r, float& g, float& b) {
  float l = lab.L + 0.3963377774f * lab.a + 0.2158037573f * lab.b;
  float m = lab.L - 0.1055613458f * lab.a - 0.0638541728f * lab.b;
  float s = lab.L - 0.0894841775f * lab.a - 1.2914855480f * lab.b;
  l = l * l * l; m = m * m * m; s = s * s * s;
  r =  4.0767416621f * l - 3.3077115913f * m + 0.2309699292f * s;
  g = -1.2684380046f * l + 2.6097574011f * m - 0.3413193965f * s;
  b = -0.0041960863f * l - 0.7034186147f * m + 1.7076147010f * s;
}
LCh LabToLCh(const Lab& lab) {
  LCh o; o.L = lab.L;
  o.C = std::sqrt(lab.a * lab.a + lab.b * lab.b);
  o.h = std::atan2(lab.b, lab.a);
  return o;
}
Lab LChToLab(const LCh& lch) {
  Lab o; o.L = lch.L;
  o.a = lch.C * std::cos(lch.h);
  o.b = lch.C * std::sin(lch.h);
  return o;
}
void ScaleChroma(FloatMat& img, float chroma_scale) {
  img.ForEachPixel([chroma_scale](Pixel& p, int, int) {
    Lab lab = LinearRgbToOkLab(p.r, p.g, p.b);
    LCh lch = LabToLCh(lab);
    lch.C *= chroma_scale;
    lab = LChToLab(lch);
    OkLabToLinearRgb(lab, p.r, p.g, p.b);
  });
}
}  // namespace alcedo::oklab
