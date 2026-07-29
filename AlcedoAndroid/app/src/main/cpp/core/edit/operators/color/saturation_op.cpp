// AlcedoAndroid - SaturationOp implementation.
// SPDX-License-Identifier: GPL-3.0-only
#include "edit/operators/color/saturation_op.hpp"
#include "image/image_buffer.hpp"
namespace alcedo {
SaturationOp::SaturationOp() = default;
SaturationOp::SaturationOp(float offset) : saturation_offset_(offset) {}
SaturationOp::SaturationOp(const nlohmann::json& params) { SetParams(params); }
void SaturationOp::Apply(std::shared_ptr<ImageBuffer> input) {
  FloatMat& img = input->GetCPUData();
  const float s = saturation_offset_;
  img.ForEachPixel([s](Pixel& p, int, int) {
    float lum = 0.2126f * p.r + 0.7152f * p.g + 0.0722f * p.b;
    p.r = lum + (p.r - lum) * s;
    p.g = lum + (p.g - lum) * s;
    p.b = lum + (p.b - lum) * s;
  });
}
void SaturationOp::ApplyGPU(std::shared_ptr<ImageBuffer> input) {
  input->SyncToCPU();
  Apply(input);
  input->SyncToGPU();
}
auto SaturationOp::GetParams() const -> nlohmann::json {
  nlohmann::json o; o[std::string(script_name_)] = saturation_offset_; return o;
}
void SaturationOp::SetParams(const nlohmann::json& params) {
  saturation_offset_ = params.contains(script_name_) ? params[script_name_].get<float>() : 1.0f;
}
void SaturationOp::SetGlobalParams(OperatorParams& params) const { params.saturation_offset_ = saturation_offset_; }
void SaturationOp::EnableGlobalParams(OperatorParams& params, bool enable) { params.saturation_enabled_ = enable; }
}  // namespace alcedo
