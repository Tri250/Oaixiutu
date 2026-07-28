// AlcedoAndroid - BlackOp implementation.
// SPDX-License-Identifier: GPL-3.0-only
#include "edit/operators/basic/black_op.hpp"
#include "image/image_buffer.hpp"
namespace alcedo {
BlackOp::BlackOp() = default;
BlackOp::BlackOp(float black_point) : black_point_(black_point) {}
BlackOp::BlackOp(const nlohmann::json& params) { SetParams(params); }
void BlackOp::Apply(std::shared_ptr<ImageBuffer> input) {
  FloatMat& img = input->GetCPUData();
  const float bp = black_point_;
  img.ForEachPixel([bp](Pixel& p, int, int) {
    p.r = p.r - bp * (1.0f - p.r);
    p.g = p.g - bp * (1.0f - p.g);
    p.b = p.b - bp * (1.0f - p.b);
  });
}
void BlackOp::ApplyGPU(std::shared_ptr<ImageBuffer> input) { input->SyncToGPU(); }
auto BlackOp::GetParams() const -> nlohmann::json {
  nlohmann::json o; o[std::string(script_name_)] = black_point_; return o;
}
void BlackOp::SetParams(const nlohmann::json& params) {
  black_point_ = params.contains(script_name_) ? params[script_name_].get<float>() : 0.0f;
}
void BlackOp::SetGlobalParams(OperatorParams& params) const { params.black_point_ = black_point_; }
void BlackOp::EnableGlobalParams(OperatorParams& params, bool enable) { params.black_enabled_ = enable; }
}  // namespace alcedo
