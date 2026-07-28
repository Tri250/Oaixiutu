// AlcedoAndroid - HighlightOp implementation.
// SPDX-License-Identifier: GPL-3.0-only
#include "edit/operators/basic/highlight_op.hpp"
#include <algorithm>
#include <cmath>
#include "image/image_buffer.hpp"
namespace alcedo {
namespace {
inline float HighlightMask(float v) {
  if (v <= 0.5f) return 0.0f;
  if (v >= 1.0f) return 1.0f;
  float t = (v - 0.5f) / 0.5f;
  return t * t;
}
}  // namespace
HighlightOp::HighlightOp() = default;
HighlightOp::HighlightOp(float offset) : highlight_offset_(offset) {}
HighlightOp::HighlightOp(const nlohmann::json& params) { SetParams(params); }
void HighlightOp::Apply(std::shared_ptr<ImageBuffer> input) {
  FloatMat& img = input->GetCPUData();
  const float off = highlight_offset_;
  img.ForEachPixel([off](Pixel& p, int, int) {
    float lum = 0.2126f * p.r + 0.7152f * p.g + 0.0722f * p.b;
    float m = HighlightMask(std::clamp(lum, 0.0f, 1.0f));
    p.r += off * m; p.g += off * m; p.b += off * m;
  });
}
void HighlightOp::ApplyGPU(std::shared_ptr<ImageBuffer> input) { input->SyncToGPU(); }
auto HighlightOp::GetParams() const -> nlohmann::json {
  nlohmann::json o; o[std::string(script_name_)] = highlight_offset_; return o;
}
void HighlightOp::SetParams(const nlohmann::json& params) {
  highlight_offset_ = params.contains(script_name_) ? params[script_name_].get<float>() : 0.0f;
}
void HighlightOp::SetGlobalParams(OperatorParams& params) const {
  params.highlights_offset_ = highlight_offset_;
  params.highlights_slider_value_ = highlight_offset_;
  params.highlights_operator_present_ = true;
}
void HighlightOp::EnableGlobalParams(OperatorParams& params, bool enable) { params.highlights_enabled_ = enable; }
}  // namespace alcedo
