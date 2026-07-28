// AlcedoAndroid - LensCalibOp implementation (inverse radial distortion).
// SPDX-License-Identifier: GPL-3.0-only
#include "edit/operators/geometry/lens_calib_op.hpp"
#include <algorithm>
#include <cmath>
#include "image/image_buffer.hpp"
namespace alcedo {
namespace {
inline float Bilinear(const FloatMat& src, float x, float y, int c) {
  int w = src.Width(), h = src.Height();
  x = std::clamp(x, 0.0f, w - 1.001f);
  y = std::clamp(y, 0.0f, h - 1.001f);
  int x0 = (int)x, y0 = (int)y;
  float fx = x - x0, fy = y - y0;
  return (1 - fx) * (1 - fy) * src.Ptr(y0, x0)[c] + fx * (1 - fy) * src.Ptr(y0, x0 + 1)[c] +
         (1 - fx) * fy * src.Ptr(y0 + 1, x0)[c] + fx * fy * src.Ptr(y0 + 1, x0 + 1)[c];
}
}  // namespace
LensCalibOp::LensCalibOp() = default;
LensCalibOp::LensCalibOp(const nlohmann::json& params) { SetParams(params); }
void LensCalibOp::Apply(std::shared_ptr<ImageBuffer> input) {
  if (!runtime_.valid_) return;
  FloatMat& src = input->GetCPUData();
  if (src.Empty()) return;
  const int w = src.Width(), h = src.Height();
  FloatMat out(w, h, src.Channels());
  const float cx = runtime_.center_x_ * w;
  const float cy = runtime_.center_y_ * h;
  const float f = runtime_.focal_px_ > 0 ? runtime_.focal_px_ : (float)std::max(w, h);
  const float* k = runtime_.radial_k_;
  const float* p = runtime_.tangential_p_;
  for (int y = 0; y < h; ++y) {
    for (int x = 0; x < w; ++x) {
      float dx = (x - cx) / f;
      float dy = (y - cy) / f;
      float r2 = dx * dx + dy * dy;
      float r4 = r2 * r2;
      float r6 = r4 * r2;
      float dist = 1.0f + k[0] * r2 + k[1] * r4 + k[2] * r6 + k[3] * r4 + k[4] * r6 + k[5] * r6;
      float tx = 2.0f * p[0] * dx * dy + p[1] * (r2 + 2.0f * dx * dx);
      float ty = p[0] * (r2 + 2.0f * dy * dy) + 2.0f * p[1] * dx * dy;
      float sx = dx * dist + tx;
      float sy = dy * dist + ty;
      float src_x = sx * f + cx;
      float src_y = sy * f + cy;
      for (int c = 0; c < src.Channels(); ++c)
        out.Ptr(y, x)[c] = Bilinear(src, src_x, src_y, c);
    }
  }
  src = std::move(out);
}
void LensCalibOp::ApplyGPU(std::shared_ptr<ImageBuffer> input) { input->SyncToGPU(); }
auto LensCalibOp::GetParams() const -> nlohmann::json {
  nlohmann::json o; o["enabled"] = runtime_.valid_; return o;
}
void LensCalibOp::SetParams(const nlohmann::json& params) {
  (void)params;  // runtime params resolved from lensfun DB by the pipeline
}
void LensCalibOp::SetGlobalParams(OperatorParams& params) const {
  params.lens_calib_runtime_params_ = runtime_;
  params.lens_calib_runtime_valid_  = runtime_.valid_;
}
void LensCalibOp::EnableGlobalParams(OperatorParams& params, bool enable) { params.lens_calib_enabled_ = enable; }
}  // namespace alcedo
