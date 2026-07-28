// AlcedoAndroid - ACES 2.0 Output Display Transform (CPU reference).
// SPDX-License-Identifier: GPL-3.0-only
#include "edit/operators/cst/aces_odt_cpu.hpp"

#include <algorithm>
#include <cmath>

#include "image/image_buffer.hpp"

namespace alcedo {
namespace {
inline float SrgbEncode(float lin) {
  lin = std::max(0.0f, lin);
  return lin <= 0.0031308f ? lin * 12.92f
                           : 1.055f * std::pow(lin, 1.0f / 2.4f) - 0.055f;
}
}  // namespace

void AcesOdtCpu::Apply(FloatMat& img, float display_white_l, float display_black_l) const {
  (void)display_black_l;
  // AP1 -> Rec.709 (D60 sim) approximated matrix.
  static const float ap1_to_rec709[9] = {
    1.0498110175f, 0.0000000000f, -0.0000974845f,
    -0.4959030231f, 1.3733130458f, 0.0982400361f,
    0.0000000000f, 0.0000000000f, 0.9912520182f};
  img.ForEachPixel([&](Pixel& p, int, int) {
    float r = ap1_to_rec709[0]*p.r + ap1_to_rec709[1]*p.g + ap1_to_rec709[2]*p.b;
    float g = ap1_to_rec709[3]*p.r + ap1_to_rec709[4]*p.g + ap1_to_rec709[5]*p.b;
    float b = ap1_to_rec709[6]*p.r + ap1_to_rec709[7]*p.g + ap1_to_rec709[8]*p.b;
    // ACES SSTS approximation (single-segment soft clip).
    auto tone = [](float x) {
      x = std::max(0.0f, x);
      return x / (1.0f + x);
    };
    r = tone(r) * display_white_l;
    g = tone(g) * display_white_l;
    b = tone(b) * display_white_l;
    p.r = SrgbEncode(r);
    p.g = SrgbEncode(g);
    p.b = SrgbEncode(b);
  });
}

}  // namespace alcedo
