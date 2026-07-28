// AlcedoAndroid - lens calibration runtime params (GPU/host shared).
// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <cstdint>

namespace alcedo {

// Parameters resolved by the LensCalib operator from the lensfun-style database
// and handed to the geometry Vulkan kernel. Radial + tangential distortion with
// an optional rectilinear DNG warp flag.
struct LensCalibGpuParams {
  bool          valid_             = false;
  bool          rectilinear_only_  = false;
  float         focal_px_          = 0.0f;
  float         center_x_          = 0.5f;
  float         center_y_          = 0.5f;
  float         crop_factor_       = 1.0f;
  float         radial_k_[6]       = {};   // k1..k6
  float         tangential_p_[2]   = {};
  float         scale_x_           = 1.0f;
  float         scale_y_           = 1.0f;
  uint64_t      cache_key_         = 0;
};

}  // namespace alcedo
