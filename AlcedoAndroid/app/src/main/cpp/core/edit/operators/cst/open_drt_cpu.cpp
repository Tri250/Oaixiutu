// AlcedoAndroid - OpenDRT output transform (CPU reference).
// SPDX-License-Identifier: GPL-3.0-only
#include "edit/operators/cst/open_drt_cpu.hpp"

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

void OpenDrtCpu::Apply(FloatMat& img, float display_white_l, float display_black_l,
                       float grading_midpoint, float grading_contrast,
                       float rendering_gamma) const {
  (void)display_black_l;
  img.ForEachPixel([&](Pixel& p, int, int) {
    auto render = [&](float x) {
      x = std::max(0.0f, x);
      float logx = std::log2(x / grading_midpoint + 1e-6f) * grading_contrast;
      float y = grading_midpoint * std::exp2(logx);
      y = y / (1.0f + y);  // soft shoulder
      y = std::pow(y, rendering_gamma) * display_white_l;
      return SrgbEncode(y);
    };
    p.r = render(p.r);
    p.g = render(p.g);
    p.b = render(p.b);
  });
}

}  // namespace alcedo
