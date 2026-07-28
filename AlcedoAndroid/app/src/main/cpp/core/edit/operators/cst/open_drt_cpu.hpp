// AlcedoAndroid - OpenDRT output transform (CPU reference).
// Self-contained port of the OpenDRT display rendering transform (sRGB).
// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "image/image_buffer.hpp"
#include "edit/operators/op_base.hpp"
namespace alcedo {
class OpenDrtCpu {
 public:
  void Apply(FloatMat& img, float display_white_l = 1.0f, float display_black_l = 0.0f,
             float grading_midpoint = 0.18f, float grading_contrast = 1.0f,
             float rendering_gamma = 1.0f) const;
};
}  // namespace alcedo
