// AlcedoAndroid - ImagePoolManager implementation.
// Bounded LRU image pool with pin/unpin eviction safety.
// SPDX-License-Identifier: GPL-3.0-only
#include "storage/image_pool/image_pool_manager.hpp"

#include <algorithm>
#include <utility>

#include "utils/app_logging.hpp"

namespace alcedo {

// ---- PinnedImageHandle ----

ImagePoolManager::PinnedImageHandle::PinnedImageHandle(ImagePoolManager* manager,
                                                       image_id_t image_id,
                                                       std::shared_ptr<Image> image)
    : manager_(manager), image_id_(image_id), image_(std::move(image)) {}

ImagePoolManager::PinnedImageHandle::PinnedImageHandle(PinnedImageHandle&& other) noexcept
    : manager_(other.manager_), image_id_(other.image_id_), image_(std::move(other.image_)) {
  other.manager_  = nullptr;
  other.image_id_ = 0;
}

ImagePoolManager::PinnedImageHandle& ImagePoolManager::PinnedImageHandle::operator=(
    PinnedImageHandle&& other) noexcept {
  if (this != &other) {
    Release();
    manager_  = other.manager_;
    image_id_ = other.image_id_;
    image_    = std::move(other.image_);
    other.manager_  = nullptr;
    other.image_id_ = 0;
  }
  return *this;
}

ImagePoolManager::PinnedImageHandle::~PinnedImageHandle() { Release(); }

void ImagePoolManager::PinnedImageHandle::Release() {
  if (manager_ && image_id_ != 0) {
    manager_->Unpin(image_id_);
  }
  image_.reset();
  manager_  = nullptr;
  image_id_ = 0;
}

// ---- ImagePoolManager ----

ImagePoolManager::ImagePoolManager() = default;

ImagePoolManager::ImagePoolManager(uint32_t start_id) : id_generator_(start_id) {}

ImagePoolManager::ImagePoolManager(uint32_t capacity, uint32_t start_id)
    : id_generator_(start_id), capacity_(capacity) {}

auto ImagePoolManager::GetPool() -> std::unordered_map<image_id_t, std::shared_ptr<Image>>& {
  return image_pool_;
}

void ImagePoolManager::EnsureCapacityForInsert() {
  while (image_pool_.size() >= capacity_) {
    // Evict the least-recently-used unpinned image. Find an unpinned entry to evict.
    bool evicted = false;
    for (auto& [id, img] : image_pool_) {
      auto pc = pin_counts_.find(id);
      if ((pc == pin_counts_.end() || pc->second == 0) && !img->thumb_pinned_.load() &&
          !img->full_pinned_.load()) {
        EvictByKey(id);
        evicted = true;
        break;
      }
    }
    if (!evicted) break;  // all pinned; cannot evict
  }
}

void ImagePoolManager::EvictByKey(image_id_t id) {
  image_pool_.erase(id);
  pin_counts_.erase(id);
  lru_pool_.Erase(id);
}

void ImagePoolManager::Pin(image_id_t id) {
  pin_counts_[id] = pin_counts_[id] + 1;
}

void ImagePoolManager::Unpin(image_id_t id) {
  auto it = pin_counts_.find(id);
  if (it != pin_counts_.end() && it->second > 0) {
    --it->second;
  }
}

void ImagePoolManager::Insert(const std::shared_ptr<Image> img) {
  if (!img) return;
  if (img->image_id_ == 0) {
    img->image_id_ = id_generator_.Next();
  }
  EnsureCapacityForInsert();
  image_pool_[img->image_id_] = img;
  lru_pool_.Put(img->image_id_, img->image_id_);
}

auto ImagePoolManager::CreateAndReturnPinnedEmpty() -> PinnedImageHandle {
  auto img = std::make_shared<Image>();
  img->image_id_ = id_generator_.Next();
  EnsureCapacityForInsert();
  image_pool_[img->image_id_] = img;
  lru_pool_.Put(img->image_id_, img->image_id_);
  Pin(img->image_id_);
  return PinnedImageHandle(this, img->image_id_, img);
}

auto ImagePoolManager::PoolContains(const image_id_t& id) -> bool {
  return image_pool_.find(id) != image_pool_.end();
}

auto ImagePoolManager::GetImage(const image_id_t& id) -> std::shared_ptr<Image> {
  auto it = image_pool_.find(id);
  if (it == image_pool_.end()) return nullptr;
  lru_pool_.Put(id, id);  // touch LRU
  return it->second;
}

auto ImagePoolManager::GetImagePinned(const image_id_t& id) -> std::optional<PinnedImageHandle> {
  auto img = GetImage(id);
  if (!img) return std::nullopt;
  Pin(id);
  return PinnedImageHandle(this, id, img);
}

auto ImagePoolManager::Capacity() -> uint32_t { return capacity_; }

void ImagePoolManager::ResizeCache(uint32_t new_capacity) {
  lru_pool_.Resize(new_capacity);
}

void ImagePoolManager::ResizePool(uint32_t new_capacity) {
  capacity_ = new_capacity;
  EnsureCapacityForInsert();
}

void ImagePoolManager::Flush() {
  image_pool_.clear();
  pin_counts_.clear();
  lru_pool_.Clear();
}

void ImagePoolManager::Clear() { Flush(); }

}  // namespace alcedo
