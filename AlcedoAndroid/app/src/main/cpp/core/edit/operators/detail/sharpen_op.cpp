// AlcedoAndroid - SharpenOp implementation (unsharp mask with threshold).
// SPDX-License-Identifier: GPL-3.0-only
#include "edit/operators/detail/sharpen_op.hpp"
#include <algorithm>
#include <cmath>
#include <vector>
#include "image/image_buffer.hpp"
namespace alcedo {
SharpenOp::SharpenOp() = default;
SharpenOp::SharpenOp(float offset) : offset_(offset) {}
SharpenOp::SharpenOp(const nlohmann::json& params) { SetParams(params); }
void SharpenOp::Apply(std::shared_ptr<ImageBuffer> input) {
  if (offset_ == 0.0f) return;
  FloatMat& img = input->GetCPUData();
  const int w = img.Width(), h = img.Height();
  if (w == 0 || h == 0) return;
  // 3x3 sharpen kernel (unsharp mask).
  const float k[3][3] = {{-1, -1, -1}, {-1, 9, -1}, {-1, -1, -1}};
  FloatMat out = img.Clone();
  const float o = offset_;
  const float thr = threshold_;
  for (int y = 1; y < h - 1; ++y) {
    for (int x = 1; x < w - 1; ++x) {
      for (int c = 0; c < 3; ++c) {
        float sum = 0;
        for (int dy = -1; dy <= 1; ++dy)
          for (int dx = -1; dx <= 1; ++dx)
            sum += img.Ptr(y + dy, x + dx)[c] * k[dy + 1][dx + 1];
        float orig = img.Ptr(y, x)[c];
        float sharpened = sum;
        float diff = std::abs(sharpened - orig);
        if (diff < thr) sharpened = orig;
        out.Ptr(y, x)[c] = orig + (sharpened - orig) * o;
      }
    }
  }
  img = std::move(out);
}
void SharpenOp::ApplyGPU(std::shared_ptr<ImageBuffer> input) { input->SyncToGPU(); }
auto SharpenOp::GetParams() const -> nlohmann::json {
  nlohmann::json o; o[std::string(script_name_)] = offset_;
  o["radius"] = radius_; o["threshold"] = threshold_; return o;
}
void SharpenOp::SetParams(const nlohmann::json& params) {
  offset_ = params.contains(script_name_) ? params[script_name_].get<float>() : 0.0f;
  radius_ = params.value("radius", 3.0f);
  threshold_ = params.value("threshold", 0.0f);
}
void SharpenOp::SetGlobalParams(OperatorParams& params) const {
  params.sharpen_offset_ = offset_;
  params.sharpen_radius_ = radius_;
  params.sharpen_threshold_ = threshold_;
}
void SharpenOp::EnableGlobalParams(OperatorParams& params, bool enable) { params.sharpen_enabled_ = enable; }
}  // namespace alcedo
