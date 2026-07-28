// AlcedoAndroid - ACES 2.0 Output Display Transform (CPU reference).
// Self-contained port of the Academy CTL Output Transform, sRGB Rec.709 display.
// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "image/image_buffer.hpp"
#include "edit/operators/op_base.hpp"
namespace alcedo {
class AcesOdtCpu {
 public:
  // ACES AP0 -> display-referred Rec.709/sRGB with gamma 2.2.
  void Apply(FloatMat& img, float display_white_l = 1.0f, float display_black_l = 0.0f) const;
};
}  // namespace alcedo
