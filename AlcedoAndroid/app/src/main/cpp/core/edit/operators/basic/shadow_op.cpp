// AlcedoAndroid - ShadowOp implementation.
// SPDX-License-Identifier: GPL-3.0-only
#include "edit/operators/basic/shadow_op.hpp"
#include <algorithm>
#include <cmath>
#include "image/image_buffer.hpp"
namespace alcedo {
namespace {
// Smooth shadow-region lift: full effect below x1, fades to 0 by 0.5.
inline float ShadowMask(float v, float x1) {
  if (v <= 0.0f) return 1.0f;
  if (v >= 0.5f) return 0.0f;
  float t = (v - 0.0f) / (0.5f - 0.0f);
  return std::cos(t * 1.5707963267948966f) * std::cos(t * 1.5707963267948966f);
}
}  // namespace
ShadowOp::ShadowOp() = default;
ShadowOp::ShadowOp(float offset) : shadow_offset_(offset) {}
ShadowOp::ShadowOp(const nlohmann::json& params) { SetParams(params); }
void ShadowOp::Apply(std::shared_ptr<ImageBuffer> input) {
  FloatMat& img = input->GetCPUData();
  const float off = shadow_offset_;
  img.ForEachPixel([off](Pixel& p, int, int) {
    float lum = 0.2126f * p.r + 0.7152f * p.g + 0.0722f * p.b;
    float m = ShadowMask(std::clamp(lum, 0.0f, 1.0f), 0.25f);
    p.r += off * m; p.g += off * m; p.b += off * m;
  });
}
void ShadowOp::ApplyGPU(std::shared_ptr<ImageBuffer> input) {
  input->SyncToCPU();
  Apply(input);
  input->SyncToGPU();
}
auto ShadowOp::GetParams() const -> nlohmann::json {
  nlohmann::json o; o[std::string(script_name_)] = shadow_offset_; return o;
}
void ShadowOp::SetParams(const nlohmann::json& params) {
  shadow_offset_ = params.contains(script_name_) ? params[script_name_].get<float>() : 0.0f;
}
void ShadowOp::SetGlobalParams(OperatorParams& params) const {
  params.shadows_offset_ = shadow_offset_;
  params.shadows_slider_value_ = shadow_offset_;
  params.shadows_operator_present_ = true;
}
void ShadowOp::EnableGlobalParams(OperatorParams& params, bool enable) { params.shadows_enabled_ = enable; }
}  // namespace alcedo
