// AlcedoAndroid - DCacheManager (LRU dentry cache: path -> element id).
// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <cstdint>
#include <list>
#include <optional>
#include <string>
#include <unordered_map>

#include "type/type.hpp"

namespace alcedo {

class DCacheManager {
 public:
  static const uint32_t default_capacity_ = 256;

  DCacheManager();
  explicit DCacheManager(uint32_t capacity);

  auto AccessElement(sl_path_t path) -> std::optional<sl_element_id_t>;
  void RecordAccess(sl_path_t path, sl_element_id_t element_id);
  void RemoveRecord(sl_path_t path);
  auto Evict() -> std::optional<sl_element_id_t>;
  auto Contains(const sl_path_t& path) -> bool;
  void Flush();
  void Resize(uint32_t new_capacity);

 private:
  using ListIterator = std::list<std::pair<sl_path_t, sl_element_id_t>>::iterator;
  std::unordered_map<sl_path_t, ListIterator> cache_map_;
  std::list<std::pair<sl_path_t, sl_element_id_t>> cache_list_;
  uint32_t capacity_;
  uint32_t evict_count_  = 0;
  uint32_t access_count_ = 0;
};

}  // namespace alcedo
