// AlcedoAndroid - ThumbnailService implementation.
// SPDX-License-Identifier: GPL-3.0-only
#include "app/app_services.hpp"

#include <algorithm>

#include "utils/app_logging.hpp"

namespace alcedo {

ThumbnailService::ThumbnailService(ImageController& img_ctrl) : img_ctrl_(img_ctrl) {}

auto ThumbnailService::GetThumbnail(image_id_t image_id) -> std::shared_ptr<Image> {
  auto img = img_ctrl_.GetImageById(image_id);
  if (!img) return nullptr;
  if (img->has_thumbnail_.load()) return img;
  GenerateThumbnail(img, 256);
  return img;
}

void ThumbnailService::GenerateThumbnail(const std::shared_ptr<Image>& image, uint32_t target_size) {
  if (!image || !image->has_full_img_.load()) return;
  image->thumb_state_.store(ThumbState::PENDING);
  auto& src = image->GetImageData().GetCPUData();
  if (src.Width() == 0 || src.Height() == 0) return;
  float scale = std::min(static_cast<float>(target_size) / src.Width(),
                         static_cast<float>(target_size) / src.Height());
  int tw = std::max(1, static_cast<int>(src.Width() * scale));
  int th = std::max(1, static_cast<int>(src.Height() * scale));
  FloatMat thumb(tw, th, src.Channels());
  for (int y = 0; y < th; ++y) {
    for (int x = 0; x < tw; ++x) {
      int sx = static_cast<int>(x / scale);
      int sy = static_cast<int>(y / scale);
      for (int c = 0; c < src.Channels(); ++c) {
        thumb.Ptr(y, x)[c] = src.Ptr(sy, sx)[c];
      }
    }
  }
  image->GetThumbnailBuffer() = FloatMat(std::move(thumb));
  image->has_thumbnail_.store(true);
  image->thumb_state_.store(ThumbState::READY);
}

}  // namespace alcedo
