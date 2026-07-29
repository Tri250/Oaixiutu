// AlcedoAndroid - ColorTempOp implementation.
// Resolves a CCT+tint white balance into a camera->AP1 matrix and applies it.
// SPDX-License-Identifier: GPL-3.0-only
#include "edit/operators/basic/color_temp_op.hpp"

#include <cmath>

#include "image/image_buffer.hpp"
#include "utils/app_logging.hpp"

namespace alcedo {
namespace {

// Approximate CCT -> CIE 1931 xy (Planckian locus, McCamy-style fit).
void CctToXY(float cct, float tint, float& x, float& y) {
  float t = cct;
  if (t < 1000.0f) t = 1000.0f;
  if (t > 40000.0f) t = 40000.0f;
  if (t <= 7000.0f) {
    x = 0.0f;
    x = -0.2661239f * std::pow(1e9f / t, 3.0f) - 0.2343580f * std::pow(1e9f / t, 2.0f) +
        0.8776956f * (1e9f / t) + 0.179910f;
  } else {
    x = -3.0258469f * std::pow(1e9f / t, 3.0f) + 2.1070379f * std::pow(1e9f / t, 2.0f) +
        0.2226347f * (1e9f / t) + 0.240390f;
  }
  y = -3.0f * x * x + 2.87f * x - 0.275f;
  // tint shifts y.
  y += tint * 0.01f;
}

// Bradford-adapted D-series white -> sRGB display primaries is omitted; we just
// build a diagonal white-balance scale from the resolved xy relative to D65.
void WhiteBalanceScale(float cct, float tint, float out[3]) {
  float x, y;
  CctToXY(cct, tint, x, y);
  float z = 1.0f - x - y;
  float r = 3.2406f * x - 1.5372f * y - 0.4986f * z;
  float g = -0.9689f * x + 1.8758f * y + 0.0415f * z;
  float b = 0.0557f * x - 0.2040f * y + 1.0570f * z;
  // Normalize against D65 (0.3127, 0.3290).
  out[0] = (r > 1e-6f) ? 1.0f / r : 1.0f;
  out[1] = (g > 1e-6f) ? 1.0f / g : 1.0f;
  out[2] = (b > 1e-6f) ? 1.0f / b : 1.0f;
}

}  // namespace

ColorTempOp::ColorTempOp() = default;
ColorTempOp::ColorTempOp(ColorTempMode mode, float cct, float tint)
    : mode_(mode), cct_(cct), tint_(tint) {}
ColorTempOp::ColorTempOp(const nlohmann::json& params) { SetParams(params); }

void ColorTempOp::Apply(std::shared_ptr<ImageBuffer> input) {
  if (mode_ == ColorTempMode::AS_SHOT) return;  // no-op, as-shot mul baked in
  FloatMat& img = input->GetCPUData();
  float wb[3];
  WhiteBalanceScale(cct_, tint_, wb);
  img.ForEachPixel([wb](Pixel& p, int, int) {
    p.r *= wb[0]; p.g *= wb[1]; p.b *= wb[2];
  });
}

void ColorTempOp::ApplyGPU(std::shared_ptr<ImageBuffer> input) {
  input->SyncToCPU();
  Apply(input);
  input->SyncToGPU();
}

auto ColorTempOp::GetParams() const -> nlohmann::json {
  nlohmann::json o;
  o["mode"] = static_cast<int>(mode_);
  o["cct"]  = cct_;
  o["tint"] = tint_;
  return o;
}

void ColorTempOp::SetParams(const nlohmann::json& params) {
  mode_ = params.contains("mode") ? static_cast<ColorTempMode>(params["mode"].get<int>())
                                   : ColorTempMode::AS_SHOT;
  cct_  = params.value("cct", 6500.0f);
  tint_ = params.value("tint", 0.0f);
}

void ColorTempOp::SetGlobalParams(OperatorParams& params) const {
  params.color_temp_mode_         = mode_;
  params.color_temp_custom_cct_   = cct_;
  params.color_temp_custom_tint_  = tint_;
  params.color_temp_resolved_cct_ = cct_;
  params.color_temp_resolved_tint_ = tint_;
  if (mode_ == ColorTempMode::CUSTOM) {
    float xy[2];
    CctToXY(cct_, tint_, xy[0], xy[1]);
    params.color_temp_resolved_xy_[0] = xy[0];
    params.color_temp_resolved_xy_[1] = xy[1];
  }
  params.color_temp_runtime_dirty_ = true;
}

void ColorTempOp::EnableGlobalParams(OperatorParams& params, bool enable) {
  params.color_temp_enabled_ = enable;
}

}  // namespace alcedo
