// AlcedoAndroid - TintOp implementation.
// SPDX-License-Identifier: GPL-3.0-only
#include "edit/operators/color/tint_op.hpp"
#include "image/image_buffer.hpp"
namespace alcedo {
TintOp::TintOp() = default;
TintOp::TintOp(float offset) : tint_offset_(offset) {}
TintOp::TintOp(const nlohmann::json& params) { SetParams(params); }
void TintOp::Apply(std::shared_ptr<ImageBuffer> input) {
  FloatMat& img = input->GetCPUData();
  const float t = tint_offset_ * 0.01f;
  img.ForEachPixel([t](Pixel& p, int, int) {
    // Magenta <-> green: shift G vs R+B.
    p.g += t;
    p.r -= t * 0.5f;
    p.b -= t * 0.5f;
  });
}
void TintOp::ApplyGPU(std::shared_ptr<ImageBuffer> input) {
  input->SyncToCPU();
  Apply(input);
  input->SyncToGPU();
}
auto TintOp::GetParams() const -> nlohmann::json {
  nlohmann::json o; o[std::string(script_name_)] = tint_offset_; return o;
}
void TintOp::SetParams(const nlohmann::json& params) {
  tint_offset_ = params.contains(script_name_) ? params[script_name_].get<float>() : 0.0f;
}
void TintOp::SetGlobalParams(OperatorParams& params) const { params.tint_offset_ = tint_offset_; }
void TintOp::EnableGlobalParams(OperatorParams& params, bool enable) { params.tint_enabled_ = enable; }
}  // namespace alcedo
