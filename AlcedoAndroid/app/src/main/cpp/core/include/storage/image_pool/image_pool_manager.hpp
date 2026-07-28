// AlcedoAndroid - ImagePoolManager (LRU image cache with pin/unpin).
// Self-contained Android port. Manages a bounded pool of decoded Image objects
// with pin-based eviction safety.
// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <cstdint>
#include <limits>
#include <memory>
#include <optional>
#include <unordered_map>

#include "image/image.hpp"
#include "type/type.hpp"
#include "utils/cache/lru_cache.hpp"
#include "utils/id/id_generator.hpp"

namespace alcedo {

class ImagePoolManager {
 public:
  static constexpr uint32_t kDefaultPoolCapacity = 1024;

  class PinnedImageHandle {
   public:
    PinnedImageHandle() = default;
    PinnedImageHandle(ImagePoolManager* manager, image_id_t image_id,
                      std::shared_ptr<Image> image);
    PinnedImageHandle(const PinnedImageHandle&)            = delete;
    PinnedImageHandle& operator=(const PinnedImageHandle&) = delete;
    PinnedImageHandle(PinnedImageHandle&& other) noexcept;
    PinnedImageHandle& operator=(PinnedImageHandle&& other) noexcept;
    ~PinnedImageHandle();

    auto Get() const -> const std::shared_ptr<Image>& { return image_; }
    auto operator->() const -> Image* { return image_.get(); }
    auto operator*() const -> Image& { return *image_; }
    explicit operator bool() const { return image_ != nullptr; }

   private:
    void Release();
    ImagePoolManager*      manager_  = nullptr;
    image_id_t             image_id_ = 0;
    std::shared_ptr<Image> image_;
  };

  ImagePoolManager();
  explicit ImagePoolManager(uint32_t start_id);
  ImagePoolManager(uint32_t capacity, uint32_t start_id);

  auto GetPool() -> std::unordered_map<image_id_t, std::shared_ptr<Image>>&;
  void Insert(const std::shared_ptr<Image> img);
  auto CreateAndReturnPinnedEmpty() -> PinnedImageHandle;
  auto PoolContains(const image_id_t& id) -> bool;

  auto GetImage(const image_id_t& id) -> std::shared_ptr<Image>;
  auto GetImagePinned(const image_id_t& id) -> std::optional<PinnedImageHandle>;

  auto Capacity() -> uint32_t;
  void ResizeCache(uint32_t new_capacity);
  void ResizePool(uint32_t new_capacity);

  auto GetCurrentID() -> image_id_t { return id_generator_.GetCurrentID(); }

  void Flush();
  void Clear();

 private:
  IncrID::IDGenerator<image_id_t>                          id_generator_{0};
  std::unordered_map<image_id_t, std::shared_ptr<Image>>   image_pool_;
  std::unordered_map<image_id_t, uint32_t>                 pin_counts_;
  LRUCache<image_id_t, image_id_t>                         lru_pool_{std::numeric_limits<size_t>::max()};
  uint32_t                                                 capacity_ = kDefaultPoolCapacity;

  void EnsureCapacityForInsert();
  void EvictByKey(image_id_t id);
  void Pin(image_id_t id);
  void Unpin(image_id_t id);
};

}  // namespace alcedo
