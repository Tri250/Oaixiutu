// AlcedoAndroid - ImagePoolService implementation.
// SPDX-License-Identifier: GPL-3.0-only
#include "app/app_services.hpp"

namespace alcedo {

ImagePoolService::ImagePoolService(std::shared_ptr<ImagePoolManager> pool)
    : pool_(std::move(pool)) {}

auto ImagePoolService::PinImage(image_id_t id) -> ImagePoolManager::PinnedImageHandle {
  if (!pool_) return ImagePoolManager::PinnedImageHandle{};
  auto handle = pool_->GetImagePinned(id);
  // PinnedImageHandle is move-only (copy deleted), so std::optional::value_or
  // cannot be used (it requires copy constructibility). Move out manually.
  if (handle) {
    return std::move(*handle);
  }
  return ImagePoolManager::PinnedImageHandle{};
}

void ImagePoolService::UnpinAll() {
  // PinnedImageHandle releases on destruction; callers own their handles.
  // This clears the pool itself.
  if (pool_) pool_->Flush();
}

auto ImagePoolService::PoolSize() -> size_t {
  if (!pool_) return 0;
  return pool_->GetPool().size();
}

}  // namespace alcedo
