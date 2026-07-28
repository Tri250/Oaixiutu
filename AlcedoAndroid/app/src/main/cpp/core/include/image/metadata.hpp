// AlcedoAndroid - metadata structures (EXIF display + RAW color context).
// Self-contained replacement for the desktop exiv2/OCIO-backed metadata types.
// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <cstdint>
#include <string>

namespace alcedo {

// Subset of EXIF fields surfaced to the UI.
struct ExifDisplayMetaData {
  std::string camera_make;
  std::string camera_model;
  std::string lens_make;
  std::string lens_model;
  float       focal_length_mm   = 0.0f;
  float       focal_length_35mm = 0.0f;
  float       aperture_f        = 0.0f;
  float       exposure_time_s   = 0.0f;
  float       iso               = 0.0f;
  std::string capture_time;        // ISO-8601
  std::string orientation;         // "1".."8" per EXIF
  int         width_px        = 0;
  int         height_px       = 0;
  int         bit_depth       = 0;
  std::string color_space;         // e.g. "sRGB", "AdobeRGB", "Untagged"
  float       gps_lat         = 0.0f;
  float       gps_lon         = 0.0f;
  bool        has_gps         = false;
};

// Runtime RAW color context populated by the RAW decoder and consumed by the
// ColorTemp and LensCalib operators to resolve camera->working-space matrices.
struct RawRuntimeColorContext {
  bool        valid_                           = false;
  bool        output_in_camera_space_          = false;
  float       cam_mul_[3]                      = {1.0f, 1.0f, 1.0f};
  float       pre_mul_[3]                      = {1.0f, 1.0f, 1.0f};
  float       cam_xyz_[9]                      = {};
  float       rgb_cam_[9]                      = {};
  std::string camera_make_;
  std::string camera_model_;
  bool        color_matrices_valid_            = false;
  double      color_matrix_1_[9]               = {};
  double      color_matrix_2_[9]               = {};
  bool        forward_matrices_valid_          = false;
  double      forward_matrix_1_[9]             = {};
  double      forward_matrix_2_[9]             = {};
  bool        as_shot_neutral_valid_           = false;
  double      as_shot_neutral_[3]              = {};
  bool        calibration_illuminants_valid_   = false;
  double      color_matrix_1_cct_              = 2856.0;
  double      color_matrix_2_cct_              = 6504.0;
  bool        lens_metadata_valid_             = false;
  std::string lens_make_;
  std::string lens_model_;
  float       focal_length_mm_                 = 0.0f;
  float       aperture_f_number_               = 0.0f;
  float       focus_distance_m_                = 0.0f;
  float       focal_35mm_mm_                   = 0.0f;
  float       crop_factor_hint_                = 0.0f;
  bool        dng_warp_rectilinear_present_    = false;
  bool        dng_warp_rectilinear_applied_    = false;
};

}  // namespace alcedo
