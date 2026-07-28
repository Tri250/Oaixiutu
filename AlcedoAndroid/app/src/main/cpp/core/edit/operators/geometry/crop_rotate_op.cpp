// AlcedoAndroid - CropRotateOp implementation.
// SPDX-License-Identifier: GPL-3.0-only
#include "edit/operators/geometry/crop_rotate_op.hpp"
#include <algorithm>
#include <cmath>
#include "image/image_buffer.hpp"
namespace alcedo {
CropRotateOp::CropRotateOp() = default;
CropRotateOp::CropRotateOp(const nlohmann::json& params) { SetParams(params); }
void CropRotateOp::Apply(std::shared_ptr<ImageBuffer> input) {
  FloatMat& src = input->GetCPUData();
  if (src.Empty()) return;
  int w = src.Width(), h = src.Height();
  // Crop first (clamp to bounds).
  int cx = std::clamp(crop_x_, 0, w);
  int cy = std::clamp(crop_y_, 0, h);
  int cw = crop_w_ > 0 ? std::min(crop_w_, w - cx) : w - cx;
  int ch = crop_h_ > 0 ? std::min(crop_h_, h - cy) : h - cy;
  FloatMat cropped(cw, ch, src.Channels());
  for (int y = 0; y < ch; ++y)
    for (int x = 0; x < cw; ++x)
      for (int c = 0; c < src.Channels(); ++c)
        cropped.Ptr(y, x)[c] = src.Ptr(cy + y, cx + x)[c];
  // Rotate by multiples of 90 (fast path).
  int a = ((int)std::round(angle_deg_) % 360 + 360) % 360;
  if (a == 0) { src = std::move(cropped); return; }
  if (a == 90) {
    FloatMat r(ch, cw, cropped.Channels());
    for (int y = 0; y < ch; ++y)
      for (int x = 0; x < cw; ++x)
        for (int c = 0; c < r.Channels(); ++c)
          r.Ptr(x, ch - 1 - y)[c] = cropped.Ptr(y, x)[c];
    src = std::move(r);
  } else if (a == 180) {
    FloatMat r(cw, ch, cropped.Channels());
    for (int y = 0; y < ch; ++y)
      for (int x = 0; x < cw; ++x)
        for (int c = 0; c < r.Channels(); ++c)
          r.Ptr(ch - 1 - y, cw - 1 - x)[c] = cropped.Ptr(y, x)[c];
    src = std::move(r);
  } else if (a == 270) {
    FloatMat r(ch, cw, cropped.Channels());
    for (int y = 0; y < ch; ++y)
      for (int x = 0; x < cw; ++x)
        for (int c = 0; c < r.Channels(); ++c)
          r.Ptr(cw - 1 - x, y)[c] = cropped.Ptr(y, x)[c];
    src = std::move(r);
  } else {
    src = std::move(cropped);
  }
}
void CropRotateOp::ApplyGPU(std::shared_ptr<ImageBuffer> input) { input->SyncToGPU(); }
auto CropRotateOp::GetParams() const -> nlohmann::json {
  nlohmann::json o;
  o["x"] = crop_x_; o["y"] = crop_y_; o["w"] = crop_w_; o["h"] = crop_h_;
  o["angle"] = angle_deg_;
  return o;
}
void CropRotateOp::SetParams(const nlohmann::json& params) {
  crop_x_ = params.value("x", 0);
  crop_y_ = params.value("y", 0);
  crop_w_ = params.value("w", 0);
  crop_h_ = params.value("h", 0);
  angle_deg_ = params.value("angle", 0.0f);
}
void CropRotateOp::SetGlobalParams(OperatorParams& params) const { (void)params; }
void CropRotateOp::EnableGlobalParams(OperatorParams& params, bool enable) { (void)params; (void)enable; }
}  // namespace alcedo
