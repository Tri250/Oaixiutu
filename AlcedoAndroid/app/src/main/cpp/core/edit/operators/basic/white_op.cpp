// AlcedoAndroid - WhiteOp implementation.
// SPDX-License-Identifier: GPL-3.0-only
#include "edit/operators/basic/white_op.hpp"
#include "image/image_buffer.hpp"
namespace alcedo {
WhiteOp::WhiteOp() = default;
WhiteOp::WhiteOp(float white_point) : white_point_(white_point) {}
WhiteOp::WhiteOp(const nlohmann::json& params) { SetParams(params); }
void WhiteOp::Apply(std::shared_ptr<ImageBuffer> input) {
  FloatMat& img = input->GetCPUData();
  const float scale = white_point_ > 1e-6f ? 1.0f / white_point_ : 1.0f;
  img.ForEachPixel([scale](Pixel& p, int, int) {
    p.r *= scale; p.g *= scale; p.b *= scale;
  });
}
void WhiteOp::ApplyGPU(std::shared_ptr<ImageBuffer> input) { input->SyncToGPU(); }
auto WhiteOp::GetParams() const -> nlohmann::json {
  nlohmann::json o; o[std::string(script_name_)] = white_point_; return o;
}
void WhiteOp::SetParams(const nlohmann::json& params) {
  white_point_ = params.contains(script_name_) ? params[script_name_].get<float>() : 1.0f;
}
void WhiteOp::SetGlobalParams(OperatorParams& params) const { params.white_point_ = white_point_; }
void WhiteOp::EnableGlobalParams(OperatorParams& params, bool enable) { params.white_enabled_ = enable; }
}  // namespace alcedo
