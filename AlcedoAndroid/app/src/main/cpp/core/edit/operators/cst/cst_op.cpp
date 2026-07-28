// AlcedoAndroid - CstOp implementation (applies a 3x3 matrix in place).
// SPDX-License-Identifier: GPL-3.0-only
#include "edit/operators/cst/cst_op.hpp"
#include "image/image_buffer.hpp"
namespace alcedo {
CstOp::CstOp() = default;
CstOp::CstOp(const nlohmann::json& params) { SetParams(params); }
void CstOp::Apply(std::shared_ptr<ImageBuffer> input) {
  FloatMat& img = input->GetCPUData();
  const float* m = matrix_;
  img.ForEachPixel([m](Pixel& p, int, int) {
    float r = m[0]*p.r + m[1]*p.g + m[2]*p.b;
    float g = m[3]*p.r + m[4]*p.g + m[5]*p.b;
    float b = m[6]*p.r + m[7]*p.g + m[8]*p.b;
    p.r = r; p.g = g; p.b = b;
  });
}
void CstOp::ApplyGPU(std::shared_ptr<ImageBuffer> input) { input->SyncToGPU(); }
auto CstOp::GetParams() const -> nlohmann::json {
  nlohmann::json o; nlohmann::json arr = nlohmann::json::array();
  for (int i = 0; i < 9; ++i) arr.push_back(matrix_[i]);
  o["matrix"] = arr; return o;
}
void CstOp::SetParams(const nlohmann::json& params) {
  if (params.contains("matrix")) {
    const auto& arr = params["matrix"];
    for (int i = 0; i < 9 && i < (int)arr.size(); ++i) matrix_[i] = arr[i].get<float>();
  }
}
void CstOp::SetGlobalParams(OperatorParams& params) const {
  // The resolved camera->AP1 matrix lives in color_temp_cam_to_ap1_.
  for (int i = 0; i < 9; ++i) params.color_temp_cam_to_ap1_[i] = matrix_[i];
  params.to_ws_dirty_ = true;
}
void CstOp::EnableGlobalParams(OperatorParams& params, bool enable) { params.to_ws_enabled_ = enable; }
}  // namespace alcedo
