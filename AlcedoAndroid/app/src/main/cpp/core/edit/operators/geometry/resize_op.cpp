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

// Catmull-Rom bicubic (a = -0.5) with clamped edge sampling.
inline float BicubicSample(const FloatMat& src, float x, float y, int c) {
  int w = src.Width(), h = src.Height();
  x = std::clamp(x, 0.0f, w - 1.001f);
  y = std::clamp(y, 0.0f, h - 1.001f);
  int x0 = (int)std::floor(x);
  int y0 = (int)std::floor(y);
  float fx = x - x0;
  float fy = y - y0;
  auto kernel = [](float t) {
    t = std::fabs(t);
    const float a = -0.5f;
    if (t <= 1.0f) return (a + 2.0f) * t * t * t - (a + 3.0f) * t * t + 1.0f;
    if (t < 2.0f)  return a * t * t * t - 5.0f * a * t * t + 8.0f * a * t - 4.0f * a;
    return 0.0f;
  };
  double sum = 0.0;
  for (int j = -1; j <= 2; ++j) {
    int yy = std::clamp(y0 + j, 0, h - 1);
    for (int i = -1; i <= 2; ++i) {
      int xx = std::clamp(x0 + i, 0, w - 1);
      sum += static_cast<double>(kernel(i - fx) * kernel(j - fy)) * src.Ptr(yy, xx)[c];
    }
  }
  return (float)sum;
}

// Area-averaging resample: average all source pixels under the output pixel's
// footprint (box_w x box_h in source space). Good for high-quality downscaling;
// falls back to a single nearest sample when upscaling.
inline float AreaSample(const FloatMat& src, float cx, float cy, int c,
                        float box_w, float box_h) {
  float x0 = cx - box_w * 0.5f;
  float y0 = cy - box_h * 0.5f;
  float x1 = cx + box_w * 0.5f;
  float y1 = cy + box_h * 0.5f;
  int ix0 = std::max(0, (int)std::floor(x0));
  int iy0 = std::max(0, (int)std::floor(y0));
  int ix1 = std::min(src.Width() - 1, (int)std::ceil(x1));
  int iy1 = std::min(src.Height() - 1, (int)std::ceil(y1));
  if (ix1 < ix0) ix1 = ix0;
  if (iy1 < iy0) iy1 = iy0;
  double sum = 0.0;
  int count = 0;
  for (int yy = iy0; yy <= iy1; ++yy) {
    for (int xx = ix0; xx <= ix1; ++xx) {
      sum += src.Ptr(yy, xx)[c];
      ++count;
    }
  }
  return count ? (float)(sum / count) : 0.0f;
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
  for (int y = 0; y < target_h_; ++y) {
    for (int x = 0; x < target_w_; ++x) {
      for (int c = 0; c < src.Channels(); ++c) {
        switch (algo_) {
          case ResizeDownsampleAlgorithm::Bicubic:
            dst.Ptr(y, x)[c] = BicubicSample(src, x * sx, y * sy, c);
            break;
          case ResizeDownsampleAlgorithm::Area:
            dst.Ptr(y, x)[c] = AreaSample(src, (x + 0.5f) * sx, (y + 0.5f) * sy, c, sx, sy);
            break;
          case ResizeDownsampleAlgorithm::Bilinear:
          case ResizeDownsampleAlgorithm::Lanczos:
          default:
            dst.Ptr(y, x)[c] = BilinearSample(src, x * sx, y * sy, c);
            break;
        }
      }
    }
  }
  src = std::move(dst);
}
void ResizeOp::ApplyGPU(std::shared_ptr<ImageBuffer> input) {
  input->SyncToCPU();
  Apply(input);
  input->SyncToGPU();
}
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
