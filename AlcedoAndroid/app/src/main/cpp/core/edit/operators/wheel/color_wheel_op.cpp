// AlcedoAndroid - ColorWheelOp implementation (Lift/Gamma/Gain CDL-style).
// SPDX-License-Identifier: GPL-3.0-only
#include "edit/operators/wheel/color_wheel_op.hpp"
#include <algorithm>
#include <cmath>
#include "image/image_buffer.hpp"
namespace alcedo {
ColorWheelOp::ColorWheelOp() = default;
ColorWheelOp::ColorWheelOp(const nlohmann::json& params) { SetParams(params); }
void ColorWheelOp::Apply(std::shared_ptr<ImageBuffer> input) {
  FloatMat& img = input->GetCPUData();
  const float* lift = lift_;
  const float* gamma = gamma_;
  const float* gain = gain_;
  img.ForEachPixel([lift, gamma, gain](Pixel& p, int, int) {
    for (int c = 0; c < 3; ++c) {
      float v = p[c];
      // Lift + Gain (linear), then power for gamma.
      v = v * gain[c] + lift[c] * (1.0f - v);
      if (v > 0.0f && gamma[c] > 0.0f) v = std::pow(v, 1.0f / gamma[c]);
      p[c] = v;
    }
  });
}
void ColorWheelOp::ApplyGPU(std::shared_ptr<ImageBuffer> input) { input->SyncToGPU(); }
auto ColorWheelOp::GetParams() const -> nlohmann::json {
  nlohmann::json o;
  o["lift"]  = {lift_[0], lift_[1], lift_[2]};
  o["gamma"] = {gamma_[0], gamma_[1], gamma_[2]};
  o["gain"]  = {gain_[0], gain_[1], gain_[2]};
  return o;
}
void ColorWheelOp::SetParams(const nlohmann::json& params) {
  auto load3 = [&](const char* key, float out[3], float def) {
    if (params.contains(key)) {
      auto a = params[key];
      for (int i = 0; i < 3 && i < (int)a.size(); ++i) out[i] = a[i].get<float>();
    } else { out[0] = out[1] = out[2] = def; }
  };
  load3("lift", lift_, 0.0f);
  load3("gamma", gamma_, 1.0f);
  load3("gain", gain_, 1.0f);
}
void ColorWheelOp::SetGlobalParams(OperatorParams& params) const {
  for (int i = 0; i < 3; ++i) {
    params.lift_color_offset_[i]  = lift_[i];
    params.gamma_color_offset_[i] = gamma_[i];
    params.gain_color_offset_[i]  = gain_[i];
  }
}
void ColorWheelOp::EnableGlobalParams(OperatorParams& params, bool enable) { params.color_wheel_enabled_ = enable; }
}  // namespace alcedo
