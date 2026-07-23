#include "color_temp_op.h"
#include "../color_science.h"
#include <cmath>
#include <algorithm>
#include <cstring>

namespace alcedo {

ColorTempOp::ColorTempOp() = default;

ColorTempOp::ColorTempOp(float cct, float tint, Mode mode)
    : mode_(mode), custom_cct_(std::clamp(cct, 2000.0f, 15000.0f)),
      custom_tint_(std::clamp(tint, -150.0f, 150.0f)) {}

void ColorTempOp::ComputeTemperatureMatrix(float cct, float tint, float* out_3x3) {
    // Step 1: Get Planckian locus RGB multipliers for the target CCT
    float rm, gm, bm;
    color_science::planckian_locus_rgb(cct, &rm, &gm, &bm);

    // Step 2: Apply tint as a green-magenta shift
    // Positive tint → shift toward green (increase green multiplier)
    // Negative tint → shift toward magenta (decrease green multiplier)
    float tint_shift = tint * 0.01f;
    gm += tint_shift;

    // Step 3: Build white balance diagonal matrix
    // D65-normalized multipliers, invert for camera-to-working transform
    float wb[9] = {
        1.0f / std::max(rm, 1e-6f), 0.0f, 0.0f,
        0.0f, 1.0f / std::max(gm, 1e-6f), 0.0f,
        0.0f, 0.0f, 1.0f / std::max(bm, 1e-6f)
    };

    // Step 4: Compose: xyz_d50_to_ap1_ * wb * cam_to_xyz_
    // This creates the full temperature-correction matrix:
    // camera → XYZ (D50) → white-balanced → AP1 working space
    float temp[9];
    // Manual 3x3 multiply: temp = wb * cam_to_xyz_
    for (int i = 0; i < 3; ++i) {
        for (int j = 0; j < 3; ++j) {
            temp[i * 3 + j] = 0.0f;
            for (int k = 0; k < 3; ++k) {
                temp[i * 3 + j] += wb[i * 3 + k] * cam_to_xyz_[k * 3 + j];
            }
        }
    }

    // out_3x3 = xyz_d50_to_ap1_ * temp
    for (int i = 0; i < 3; ++i) {
        for (int j = 0; j < 3; ++j) {
            out_3x3[i * 3 + j] = 0.0f;
            for (int k = 0; k < 3; ++k) {
                out_3x3[i * 3 + j] += xyz_d50_to_ap1_[i * 3 + k] * temp[k * 3 + j];
            }
        }
    }
}

void ColorTempOp::ApplyImpl(float* pixels, int width, int height, int channels) {
    if (!pixels || channels < 3) return;

    float cct, tint;
    if (mode_ == Mode::AS_SHOT) {
        cct = as_shot_cct_;
        tint = as_shot_tint_;
    } else {
        cct = custom_cct_;
        tint = custom_tint_;
    }

    // Compute the temperature-adjustment matrix
    float mat[9];
    ComputeTemperatureMatrix(cct, tint, mat);

    // Apply the 3x3 matrix to each pixel
    size_t total = static_cast<size_t>(width) * height;
    for (size_t i = 0; i < total; ++i) {
        size_t idx = i * channels;
        float r = pixels[idx];
        float g = pixels[idx + 1];
        float b = pixels[idx + 2];

        pixels[idx]     = std::clamp(r * mat[0] + g * mat[1] + b * mat[2], 0.0f, 1.0f);
        pixels[idx + 1] = std::clamp(r * mat[3] + g * mat[4] + b * mat[5], 0.0f, 1.0f);
        pixels[idx + 2] = std::clamp(r * mat[6] + g * mat[7] + b * mat[8], 0.0f, 1.0f);
    }
}

} // namespace alcedo
