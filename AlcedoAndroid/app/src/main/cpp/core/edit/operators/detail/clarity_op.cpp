// AlcedoAndroid - ClarityOp implementation (unsharp-mask of mid-frequency).
// SPDX-License-Identifier: GPL-3.0-only
#include "edit/operators/detail/clarity_op.hpp"
#include <algorithm>
#include <cmath>
#include <vector>
#include "image/image_buffer.hpp"
namespace alcedo {
namespace {
inline float Gauss(float x, float sigma) { return std::exp(-0.5f * (x * x) / (sigma * sigma)); }
}  // namespace
ClarityOp::ClarityOp() = default;
ClarityOp::ClarityOp(float offset) : offset_(offset) {}
ClarityOp::ClarityOp(const nlohmann::json& params) { SetParams(params); }
void ClarityOp::Apply(std::shared_ptr<ImageBuffer> input) {
  if (offset_ == 0.0f) return;
  FloatMat& img = input->GetCPUData();
  const int w = img.Width(), h = img.Height();
  if (w == 0 || h == 0) return;
  int radius = std::max(1, static_cast<int>(radius_));
  // Luminance.
  std::vector<float> lum(w * h);
  for (int y = 0; y < h; ++y)
    for (int x = 0; x < w; ++x) {
      const Pixel& p = img.PixelAt(y, x);
      lum[y * w + x] = 0.2126f * p.r + 0.7152f * p.g + 0.0722f * p.b;
    }
  // Box blur as a cheap low-pass for the local average.
  std::vector<float> blur(w * h, 0.0f);
  for (int y = 0; y < h; ++y)
    for (int x = 0; x < w; ++x) {
      float s = 0; int n = 0;
      for (int dy = -radius; dy <= radius; ++dy) {
        int yy = y + dy; if (yy < 0 || yy >= h) continue;
        for (int dx = -radius; dx <= radius; ++dx) {
          int xx = x + dx; if (xx < 0 || xx >= w) continue;
          s += lum[yy * w + xx]; ++n;
        }
      }
      blur[y * w + x] = n ? s / n : 0.0f;
    }
  const float o = offset_;
  img.ForEachPixel([&](Pixel& p, int x, int y) {
    float L = 0.2126f * p.r + 0.7152f * p.g + 0.0722f * p.b;
    float local = blur[y * w + x];
    float detail = L - local;
    float scale = (L > 1e-5f) ? (1.0f + o * detail / L) : 1.0f;
    p.r *= scale; p.g *= scale; p.b *= scale;
  });
}
void ClarityOp::ApplyGPU(std::shared_ptr<ImageBuffer> input) { input->SyncToGPU(); }
auto ClarityOp::GetParams() const -> nlohmann::json {
  nlohmann::json o; o[std::string(script_name_)] = offset_; o["radius"] = radius_; return o;
}
void ClarityOp::SetParams(const nlohmann::json& params) {
  offset_ = params.contains(script_name_) ? params[script_name_].get<float>() : 0.0f;
  radius_ = params.value("radius", 5.0f);
}
void ClarityOp::SetGlobalParams(OperatorParams& params) const {
  params.clarity_offset_ = offset_;
  params.clarity_radius_ = radius_;
}
void ClarityOp::EnableGlobalParams(OperatorParams& params, bool enable) { params.clarity_enabled_ = enable; }
}  // namespace alcedo
