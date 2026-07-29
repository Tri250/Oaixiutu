// AlcedoAndroid - DCacheManager implementation (LRU dentry cache).
// SPDX-License-Identifier: GPL-3.0-only
#include "sleeve/dentry_cache_manager.hpp"

#include <utility>

namespace alcedo {

DCacheManager::DCacheManager() : DCacheManager(default_capacity_) {}

DCacheManager::DCacheManager(uint32_t capacity) : capacity_(capacity) {}

auto DCacheManager::AccessElement(sl_path_t path) -> std::optional<sl_element_id_t> {
  auto it = cache_map_.find(path);
  if (it == cache_map_.end()) return std::nullopt;
  // Move the accessed entry to the front (most-recently-used).
  cache_list_.splice(cache_list_.begin(), cache_list_, it->second);
  ++access_count_;
  return it->second->second;
}

void DCacheManager::RecordAccess(sl_path_t path, sl_element_id_t element_id) {
  auto it = cache_map_.find(path);
  if (it != cache_map_.end()) {
    it->second->second = element_id;
    cache_list_.splice(cache_list_.begin(), cache_list_, it->second);
    return;
  }
  if (capacity_ != 0 && cache_list_.size() >= capacity_) {
    Evict();
  }
  cache_list_.emplace_front(std::move(path), element_id);
  cache_map_[cache_list_.front().first] = cache_list_.begin();
}

void DCacheManager::RemoveRecord(sl_path_t path) {
  auto it = cache_map_.find(path);
  if (it == cache_map_.end()) return;
  cache_list_.erase(it->second);
  cache_map_.erase(it);
}

auto DCacheManager::Evict() -> std::optional<sl_element_id_t> {
  if (cache_list_.empty()) return std::nullopt;
  auto& back = cache_list_.back();
  sl_element_id_t evicted_id = back.second;
  cache_map_.erase(back.first);
  cache_list_.pop_back();
  ++evict_count_;
  return evicted_id;
}

auto DCacheManager::Contains(const sl_path_t& path) -> bool {
  return cache_map_.find(path) != cache_map_.end();
}

void DCacheManager::Flush() {
  cache_list_.clear();
  cache_map_.clear();
  evict_count_  = 0;
  access_count_ = 0;
}

void DCacheManager::Resize(uint32_t new_capacity) {
  capacity_ = new_capacity;
  while (capacity_ != 0 && cache_list_.size() > capacity_) {
    Evict();
  }
}

}  // namespace alcedo
