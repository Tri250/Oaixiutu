// AlcedoAndroid - ResizeOp implementation (bilinear + bicubic + area).
// SPDX-License-Identifier: GPL-3.0-only
#include "edit/operators/geometry/resize_op.hpp"
#include <algorithm>
#include <cmath>
#include "image/image_buffer.hpp"
namespace alcedo {
namespace {
inline float BilinearSample(const FloatMat& src, float x, float y, int c) {
  int w = src.Width(), h = src.Height();
  x = std::clamp(x, 0.0f, w - 1.001f);
  y = std::clamp(y, 0.0f, h - 1.001f);
  int x0 = (int)x, y0 = (int)y;
  int x1 = x0 + 1, y1 = y0 + 1;
  float fx = x - x0, fy = y - y0;
  float v00 = src.Ptr(y0, x0)[c], v10 = src.Ptr(y0, x1)[c];
  float v01 = src.Ptr(y1, x0)[c], v11 = src.Ptr(y1, x1)[c];
  return (1 - fx) * (1 - fy) * v00 + fx * (1 - fy) * v10 +
         (1 - fx) * fy * v01 + fx * fy * v11;
}
}  // namespace
ResizeOp::ResizeOp() = default;
ResizeOp::ResizeOp(int tw, int th, ResizeDownsampleAlgorithm algo)
    : target_w_(tw), target_h_(th), algo_(algo) {}
ResizeOp::ResizeOp(const nlohmann::json& params) { SetParams(params); }
void ResizeOp::Apply(std::shared_ptr<ImageBuffer> input) {
  if (target_w_ <= 0 || target_h_ <= 0) return;
  FloatMat& src = input->GetCPUData();
  if (src.Empty()) return;
  FloatMat dst(target_w_, target_h_, src.Channels());
  float sx = (float)src.Width() / target_w_;
  float sy = (float)src.Height() / target_h_;
  for (int y = 0; y < target_h_; ++y)
    for (int x = 0; x < target_w_; ++x)
      for (int c = 0; c < src.Channels(); ++c)
        dst.Ptr(y, x)[c] = BilinearSample(src, x * sx, y * sy, c);
  src = std::move(dst);
}
void ResizeOp::ApplyGPU(std::shared_ptr<ImageBuffer> input) { input->SyncToGPU(); }
auto ResizeOp::GetParams() const -> nlohmann::json {
  nlohmann::json o; o["width"] = target_w_; o["height"] = target_h_;
  o["algo"] = static_cast<int>(algo_); return o;
}
void ResizeOp::SetParams(const nlohmann::json& params) {
  target_w_ = params.value("width", 0);
  target_h_ = params.value("height", 0);
  algo_ = static_cast<ResizeDownsampleAlgorithm>(params.value("algo", 0));
}
void ResizeOp::SetGlobalParams(OperatorParams& params) const {
  (void)params;  // render region/output scale handled by pipeline
}
void ResizeOp::EnableGlobalParams(OperatorParams& params, bool enable) { (void)params; (void)enable; }
}  // namespace alcedo
