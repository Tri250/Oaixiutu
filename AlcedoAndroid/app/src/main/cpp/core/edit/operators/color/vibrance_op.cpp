// AlcedoAndroid - VibranceOp implementation.
// SPDX-License-Identifier: GPL-3.0-only
#include "edit/operators/color/vibrance_op.hpp"
#include <algorithm>
#include <cmath>
#include "image/image_buffer.hpp"
namespace alcedo {
namespace {
inline float Max3(float a, float b, float c) { return std::max({a, b, c}); }
inline float Min3(float a, float b, float c) { return std::min({a, b, c}); }
}  // namespace
VibranceOp::VibranceOp() = default;
VibranceOp::VibranceOp(float offset) : vibrance_offset_(offset) {}
VibranceOp::VibranceOp(const nlohmann::json& params) { SetParams(params); }
void VibranceOp::Apply(std::shared_ptr<ImageBuffer> input) {
  FloatMat& img = input->GetCPUData();
  const float v = vibrance_offset_ * 0.01f;
  img.ForEachPixel([v](Pixel& p, int, int) {
    float mx = Max3(p.r, p.g, p.b);
    float mn = Min3(p.r, p.g, p.b);
    float sat = (mx > 1e-6f) ? (mx - mn) / mx : 0.0f;
    // Boost less-saturated pixels more.
    float amount = v * (1.0f - sat);
    float lum = 0.2126f * p.r + 0.7152f * p.g + 0.0722f * p.b;
    p.r = lum + (p.r - lum) * (1.0f + amount);
    p.g = lum + (p.g - lum) * (1.0f + amount);
    p.b = lum + (p.b - lum) * (1.0f + amount);
  });
}
void VibranceOp::ApplyGPU(std::shared_ptr<ImageBuffer> input) { input->SyncToGPU(); }
auto VibranceOp::GetParams() const -> nlohmann::json {
  nlohmann::json o; o[std::string(script_name_)] = vibrance_offset_; return o;
}
void VibranceOp::SetParams(const nlohmann::json& params) {
  vibrance_offset_ = params.contains(script_name_) ? params[script_name_].get<float>() : 0.0f;
}
void VibranceOp::SetGlobalParams(OperatorParams& params) const { params.vibrance_offset_ = vibrance_offset_; }
void VibranceOp::EnableGlobalParams(OperatorParams& params, bool enable) { params.vibrance_enabled_ = enable; }
}  // namespace alcedo
