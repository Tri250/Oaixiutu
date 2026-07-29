// AlcedoAndroid - ContrastOp implementation.
// SPDX-License-Identifier: GPL-3.0-only
#include "edit/operators/basic/contrast_op.hpp"
#include "image/image_buffer.hpp"
namespace alcedo {
ContrastOp::ContrastOp() = default;
ContrastOp::ContrastOp(float scale) : contrast_scale_(scale) {}
ContrastOp::ContrastOp(const nlohmann::json& params) { SetParams(params); }
void ContrastOp::Apply(std::shared_ptr<ImageBuffer> input) {
  FloatMat& img = input->GetCPUData();
  const float pivot = 0.18f;
  const float k = 1.0f + contrast_scale_;
  img.ForEachPixel([pivot, k](Pixel& p, int, int) {
    p.r = pivot + (p.r - pivot) * k;
    p.g = pivot + (p.g - pivot) * k;
    p.b = pivot + (p.b - pivot) * k;
  });
}
void ContrastOp::ApplyGPU(std::shared_ptr<ImageBuffer> input) {
  input->SyncToCPU();
  Apply(input);
  input->SyncToGPU();
}
auto ContrastOp::GetParams() const -> nlohmann::json {
  nlohmann::json o; o[std::string(script_name_)] = contrast_scale_; return o;
}
void ContrastOp::SetParams(const nlohmann::json& params) {
  contrast_scale_ = params.contains(script_name_) ? params[script_name_].get<float>() : 0.0f;
}
void ContrastOp::SetGlobalParams(OperatorParams& params) const { params.contrast_scale_ = contrast_scale_; }
void ContrastOp::EnableGlobalParams(OperatorParams& params, bool enable) { params.contrast_enabled_ = enable; }
}  // namespace alcedo
