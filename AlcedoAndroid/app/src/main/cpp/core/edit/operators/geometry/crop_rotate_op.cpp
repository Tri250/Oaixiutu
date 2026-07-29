// AlcedoAndroid - CropRotateOp implementation.
// SPDX-License-Identifier: GPL-3.0-only
#include "edit/operators/geometry/crop_rotate_op.hpp"
#include <algorithm>
#include <cmath>
#include "image/image_buffer.hpp"
#include "utils/app_logging.hpp"
namespace alcedo {
namespace {
inline float ClampF(float v, float lo, float hi) {
  return v < lo ? lo : (v > hi ? hi : v);
}
// Bilinear sample with edge clamping.
inline float BilinearSampleAt(const FloatMat& src, float x, float y, int c) {
  int w = src.Width(), h = src.Height();
  if (w <= 0 || h <= 0) return 0.0f;
  x = ClampF(x, 0.0f, w - 1.001f);
  y = ClampF(y, 0.0f, h - 1.001f);
  int x0 = (int)x, y0 = (int)y;
  int x1 = x0 + 1, y1 = y0 + 1;
  float fx = x - x0, fy = y - y0;
  float v00 = src.Ptr(y0, x0)[c], v10 = src.Ptr(y0, x1)[c];
  float v01 = src.Ptr(y1, x0)[c], v11 = src.Ptr(y1, x1)[c];
  return (1 - fx) * (1 - fy) * v00 + fx * (1 - fy) * v10 +
         (1 - fx) * fy * v01 + fx * fy * v11;
}
}  // namespace
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
    // Arbitrary angle: rotate via inverse mapping with bilinear sampling.
    constexpr float kDeg2Rad = 0.01745329251994329576f;  // pi / 180
    float rad = angle_deg_ * kDeg2Rad;
    float cos_a = std::cos(rad);
    float sin_a = std::sin(rad);
    float hw = cw * 0.5f, hh = ch * 0.5f;
    float corners_x[4] = { -hw,  hw,  hw, -hw };
    float corners_y[4] = { -hh, -hh,  hh,  hh };
    float min_x = 1e30f, max_x = -1e30f, min_y = 1e30f, max_y = -1e30f;
    for (int k = 0; k < 4; ++k) {
      float rx = corners_x[k] * cos_a - corners_y[k] * sin_a;
      float ry = corners_x[k] * sin_a + corners_y[k] * cos_a;
      min_x = std::min(min_x, rx); max_x = std::max(max_x, rx);
      min_y = std::min(min_y, ry); max_y = std::max(max_y, ry);
    }
    int rw = std::max(1, (int)std::ceil(max_x - min_x));
    int rh = std::max(1, (int)std::ceil(max_y - min_y));
    FloatMat r(rw, rh, cropped.Channels());
    float ocx = rw * 0.5f, ocy = rh * 0.5f;
    for (int oy = 0; oy < rh; ++oy) {
      for (int ox = 0; ox < rw; ++ox) {
        float dx = ox - ocx;
        float dy = oy - ocy;
        float sx = dx * cos_a + dy * sin_a + hw;
        float sy = -dx * sin_a + dy * cos_a + hh;
        for (int c = 0; c < r.Channels(); ++c) {
          r.Ptr(oy, ox)[c] = BilinearSampleAt(cropped, sx, sy, c);
        }
      }
    }
    ALOGW("CropRotateOp: arbitrary rotation %.2f deg -> %dx%d", angle_deg_, rw, rh);
    src = std::move(r);
  }
}
void CropRotateOp::ApplyGPU(std::shared_ptr<ImageBuffer> input) {
  input->SyncToCPU();
  Apply(input);
  input->SyncToGPU();
}
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
