// AlcedoAndroid - HlsOp implementation.
// SPDX-License-Identifier: GPL-3.0-only
#include "edit/operators/color/hls_op.hpp"
#include <algorithm>
#include <cmath>
#include "image/image_buffer.hpp"
namespace alcedo {
namespace {
void RgbToHsl(float r, float g, float b, float& h, float& s, float& l) {
  float mx = std::max({r, g, b});
  float mn = std::min({r, g, b});
  l = (mx + mn) * 0.5f;
  float d = mx - mn;
  s = (d < 1e-6f) ? 0.0f : d / (1.0f - std::abs(2.0f * l - 1.0f) + 1e-6f);
  if (d < 1e-6f) { h = 0.0f; return; }
  if (mx == r)      h = std::fmod((g - b) / d, 6.0f);
  else if (mx == g) h = (b - r) / d + 2.0f;
  else              h = (r - g) / d + 4.0f;
  h *= 60.0f;
  if (h < 0.0f) h += 360.0f;
}
void HslToRgb(float h, float s, float l, float& r, float& g, float& b) {
  float c = (1.0f - std::abs(2.0f * l - 1.0f)) * s;
  float hp = h / 60.0f;
  float x = c * (1.0f - std::abs(std::fmod(hp, 2.0f) - 1.0f));
  float r1 = 0, g1 = 0, b1 = 0;
  if (hp >= 0 && hp < 1) { r1 = c; g1 = x; }
  else if (hp < 2) { r1 = x; g1 = c; }
  else if (hp < 3) { g1 = c; b1 = x; }
  else if (hp < 4) { g1 = x; b1 = c; }
  else if (hp < 5) { r1 = x; b1 = c; }
  else { r1 = c; b1 = x; }
  float m = l - c * 0.5f;
  r = r1 + m; g = g1 + m; b = b1 + m;
}
inline float ProfileWeight(float hue, float center, float range) {
  float d = std::abs(hue - center);
  if (d > 180.0f) d = 360.0f - d;
  if (d > range) return 0.0f;
  return 1.0f - d / range;
}
}  // namespace

float HlsOp::RgbToHue(float r, float g, float b) {
  float h, s, l; RgbToHsl(r, g, b, h, s, l); return h;
}

HlsOp::HlsOp() = default;
HlsOp::HlsOp(const nlohmann::json& params) { SetParams(params); }

void HlsOp::Apply(std::shared_ptr<ImageBuffer> input) {
  FloatMat& img = input->GetCPUData();
  static const float profile_hues[kProfileCount] = {0, 45, 90, 135, 180, 225, 270, 315};
  const float range = hue_range_;
  img.ForEachPixel([&](Pixel& p, int, int) {
    float h, s, l;
    RgbToHsl(p.r, p.g, p.b, h, s, l);
    float dh = 0, dl = 0, ds = 0;
    for (int i = 0; i < kProfileCount; ++i) {
      float w = ProfileWeight(h, profile_hues[i], range);
      if (w > 0.0f) {
        dh += profile_adjustments_[i][0] * w * 0.01f;
        dl += profile_adjustments_[i][1] * w * 0.01f;
        ds += profile_adjustments_[i][2] * w * 0.01f;
      }
    }
    h = std::fmod(h + dh + 360.0f, 360.0f);
    l = std::clamp(l + dl, 0.0f, 1.0f);
    s = std::clamp(s + ds, 0.0f, 1.0f);
    HslToRgb(h, s, l, p.r, p.g, p.b);
  });
}
void HlsOp::ApplyGPU(std::shared_ptr<ImageBuffer> input) { input->SyncToGPU(); }
auto HlsOp::GetParams() const -> nlohmann::json {
  nlohmann::json o;
  nlohmann::json arr = nlohmann::json::array();
  for (int i = 0; i < kProfileCount; ++i) {
    arr.push_back({profile_adjustments_[i][0], profile_adjustments_[i][1], profile_adjustments_[i][2]});
  }
  o["profiles"] = arr;
  o["hue_range"] = hue_range_;
  return o;
}
void HlsOp::SetParams(const nlohmann::json& params) {
  hue_range_ = params.value("hue_range", 45.0f);
  if (params.contains("profiles")) {
    const auto& arr = params["profiles"];
    for (int i = 0; i < kProfileCount && i < (int)arr.size(); ++i) {
      for (int j = 0; j < 3; ++j) profile_adjustments_[i][j] = arr[i][j].get<float>();
    }
  }
}
void HlsOp::SetGlobalParams(OperatorParams& params) const {
  params.hue_range_ = hue_range_;
  for (int i = 0; i < kProfileCount; ++i) {
    for (int j = 0; j < 3; ++j) params.hls_profile_adjustments_[i][j] = profile_adjustments_[i][j];
  }
}
void HlsOp::EnableGlobalParams(OperatorParams& params, bool enable) { params.hls_enabled_ = enable; }
}  // namespace alcedo
