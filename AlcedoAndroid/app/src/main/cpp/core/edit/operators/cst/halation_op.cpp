// AlcedoAndroid - HalationOp implementation (separable Gaussian glow on highlights).
// SPDX-License-Identifier: GPL-3.0-only
#include "edit/operators/cst/halation_op.hpp"
#include <algorithm>
#include <cmath>
#include <vector>
#include "image/image_buffer.hpp"
namespace alcedo {
namespace {
inline float Gauss(float x, float sigma) {
  return std::exp(-0.5f * (x * x) / (sigma * sigma));
}
}  // namespace
HalationOp::HalationOp() = default;
HalationOp::HalationOp(const nlohmann::json& params) { SetParams(params); }
void HalationOp::Apply(std::shared_ptr<ImageBuffer> input) {
  if (!enabled_ || strength_ == 0.0f) return;
  FloatMat& img = input->GetCPUData();
  const int w = img.Width(), h = img.Height();
  if (w == 0 || h == 0) return;
  // Build a highlight mask: luminance above low_threshold ramping to high_threshold.
  std::vector<float> mask(w * h, 0.0f);
  for (int y = 0; y < h; ++y) {
    for (int x = 0; x < w; ++x) {
      const Pixel& p = img.PixelAt(y, x);
      float lum = 0.2126f * p.r + 0.7152f * p.g + 0.0722f * p.b;
      if (lum <= low_threshold_) { mask[y * w + x] = 0.0f; continue; }
      float t = std::clamp((lum - low_threshold_) /
                           std::max(1e-6f, high_threshold_ - low_threshold_), 0.0f, 1.0f);
      mask[y * w + x] = t;
    }
  }
  // Separable Gaussian blur of the mask.
  int radius = std::max(1, static_cast<int>(sigma_ * 3));
  std::vector<float> tmp(w * h, 0.0f), blurred(w * h, 0.0f);
  // Horizontal
  for (int y = 0; y < h; ++y) {
    for (int x = 0; x < w; ++x) {
      float sum = 0, wsum = 0;
      for (int r = -radius; r <= radius; ++r) {
        int xx = std::clamp(x + r, 0, w - 1);
        float g = Gauss((float)r, sigma_);
        sum += mask[y * w + xx] * g; wsum += g;
      }
      tmp[y * w + x] = wsum > 0 ? sum / wsum : 0;
    }
  }
  // Vertical
  for (int y = 0; y < h; ++y) {
    for (int x = 0; x < w; ++x) {
      float sum = 0, wsum = 0;
      for (int r = -radius; r <= radius; ++r) {
        int yy = std::clamp(y + r, 0, h - 1);
        float g = Gauss((float)r, sigma_);
        sum += tmp[yy * w + x] * g; wsum += g;
      }
      blurred[y * w + x] = wsum > 0 ? sum / wsum : 0;
    }
  }
  const float s = strength_ * additive_scale_;
  img.ForEachPixel([&](Pixel& p, int x, int y) {
    float g = blurred[y * w + x] * s;
    p.r += g * redshift_[0];
    p.g += g * redshift_[1];
    p.b += g * redshift_[2];
  });
}
void HalationOp::ApplyGPU(std::shared_ptr<ImageBuffer> input) {
  input->SyncToCPU();
  Apply(input);
  input->SyncToGPU();
}
auto HalationOp::GetParams() const -> nlohmann::json {
  nlohmann::json o;
  o["enabled"]        = enabled_;
  o["strength"]       = strength_;
  o["low_threshold"]  = low_threshold_;
  o["high_threshold"] = high_threshold_;
  o["sigma"]          = sigma_;
  nlohmann::json rs = {redshift_[0], redshift_[1], redshift_[2]};
  o["redshift"]      = rs;
  o["additive_scale"] = additive_scale_;
  return o;
}
void HalationOp::SetParams(const nlohmann::json& params) {
  enabled_        = params.value("enabled", true);
  strength_       = params.value("strength", 0.0f);
  low_threshold_  = params.value("low_threshold", 0.6f);
  high_threshold_ = params.value("high_threshold", 0.7f);
  sigma_          = params.value("sigma", 20.0f);
  if (params.contains("redshift")) {
    auto rs = params["redshift"];
    for (int i = 0; i < 3 && i < (int)rs.size(); ++i) redshift_[i] = rs[i].get<float>();
  }
  additive_scale_ = params.value("additive_scale", 1.0f);
}
void HalationOp::SetGlobalParams(OperatorParams& params) const {
  params.halation_.enabled_        = enabled_;
  params.halation_.strength_       = strength_;
  params.halation_.low_threshold_  = low_threshold_;
  params.halation_.high_threshold_ = high_threshold_;
  params.halation_.sigma_          = sigma_;
  for (int i = 0; i < 3; ++i) params.halation_.redshift_[i] = redshift_[i];
  params.halation_.additive_scale_ = additive_scale_;
}
void HalationOp::EnableGlobalParams(OperatorParams& params, bool enable) { params.halation_.enabled_ = enable; }
}  // namespace alcedo
