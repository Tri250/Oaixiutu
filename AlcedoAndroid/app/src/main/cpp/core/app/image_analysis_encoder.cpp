// AlcedoAndroid - ImageAnalysisEncoder implementation.
// Preprocesses an image into a normalised float tensor for AI inference.
// SPDX-License-Identifier: GPL-3.0-only
#include "app/app_services.hpp"

#include <algorithm>

namespace alcedo {

auto ImageAnalysisEncoder::Encode(const std::shared_ptr<Image>& image, int target_size)
    -> std::vector<float> {
  if (!image || !image->has_full_img_.load()) return {};
  auto& mat = image->GetImageData().GetCPUData();
  if (mat.Width() == 0 || mat.Height() == 0) return {};

  int tw = target_size;
  int th = target_size;
  int channels = mat.Channels();
  std::vector<float> out(static_cast<size_t>(tw) * th * channels, 0.0f);

  float scale_x = static_cast<float>(mat.Width()) / tw;
  float scale_y = static_cast<float>(mat.Height()) / th;

  for (int y = 0; y < th; ++y) {
    float sy = (y + 0.5f) * scale_y - 0.5f;
    int y0 = std::max(0, std::min(mat.Height() - 1, static_cast<int>(sy)));
    int y1 = std::max(0, std::min(mat.Height() - 1, y0 + 1));
    float wy = sy - y0;
    if (wy < 0) wy = 0;
    for (int x = 0; x < tw; ++x) {
      float sx = (x + 0.5f) * scale_x - 0.5f;
      int x0 = std::max(0, std::min(mat.Width() - 1, static_cast<int>(sx)));
      int x1 = std::max(0, std::min(mat.Width() - 1, x0 + 1));
      float wx = sx - x0;
      if (wx < 0) wx = 0;
      for (int c = 0; c < channels; ++c) {
        float v = mat.Ptr(y0, x0)[c] * (1.0f - wx) * (1.0f - wy) +
                  mat.Ptr(y0, x1)[c] * wx * (1.0f - wy) +
                  mat.Ptr(y1, x0)[c] * (1.0f - wx) * wy +
                  mat.Ptr(y1, x1)[c] * wx * wy;
        out[(static_cast<size_t>(y) * tw + x) * channels + c] = v;
      }
    }
  }
  return out;
}

}  // namespace alcedo
