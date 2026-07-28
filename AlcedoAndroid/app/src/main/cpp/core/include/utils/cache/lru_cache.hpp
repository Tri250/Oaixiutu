// AlcedoAndroid - thread-safe LRU cache.
// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <cstddef>
#include <list>
#include <mutex>
#include <optional>
#include <unordered_map>

namespace alcedo {

template <typename Key, typename Value>
class LRUCache {
 public:
  explicit LRUCache(size_t capacity) : capacity_(capacity) {}

  void Put(const Key& key, const Value& value) {
    std::lock_guard<std::mutex> lock(mtx_);
    auto it = map_.find(key);
    if (it != map_.end()) {
      it->second->second = value;
      list_.splice(list_.begin(), list_, it->second);
      return;
    }
    if (capacity_ != 0 && list_.size() >= capacity_) {
      map_.erase(list_.back().first);
      list_.pop_back();
    }
    list_.emplace_front(key, value);
    map_[key] = list_.begin();
  }

  std::optional<Value> Get(const Key& key) {
    std::lock_guard<std::mutex> lock(mtx_);
    auto it = map_.find(key);
    if (it == map_.end()) return std::nullopt;
    list_.splice(list_.begin(), list_, it->second);
    return it->second->second;
  }

  bool Contains(const Key& key) {
    std::lock_guard<std::mutex> lock(mtx_);
    return map_.find(key) != map_.end();
  }

  void Erase(const Key& key) {
    std::lock_guard<std::mutex> lock(mtx_);
    auto it = map_.find(key);
    if (it == map_.end()) return;
    list_.erase(it->second);
    map_.erase(it);
  }

  void Clear() {
    std::lock_guard<std::mutex> lock(mtx_);
    list_.clear();
    map_.clear();
  }

  size_t Size() {
    std::lock_guard<std::mutex> lock(mtx_);
    return list_.size();
  }

  void Resize(size_t new_capacity) {
    std::lock_guard<std::mutex> lock(mtx_);
    capacity_ = new_capacity;
    while (capacity_ != 0 && list_.size() > capacity_) {
      map_.erase(list_.back().first);
      list_.pop_back();
    }
  }

 private:
  using ListIt = typename std::list<std::pair<Key, Value>>::iterator;
  std::list<std::pair<Key, Value>>          list_;
  std::unordered_map<Key, ListIt>           map_;
  std::mutex                                mtx_;
  size_t                                    capacity_;
};

}  // namespace alcedo
