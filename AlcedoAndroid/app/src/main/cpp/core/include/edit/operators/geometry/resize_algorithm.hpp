// AlcedoAndroid - resize algorithm enumeration (referenced by the pipeline).
// SPDX-License-Identifier: GPL-3.0-only
#pragma once

namespace alcedo {

enum class ResizeDownsampleAlgorithm {
  Bilinear = 0,
  Bicubic  = 1,
  Lanczos  = 2,
  Area     = 3,
};

}  // namespace alcedo
