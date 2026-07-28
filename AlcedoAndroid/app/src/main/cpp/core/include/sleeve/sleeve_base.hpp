// AlcedoAndroid - SleeveBase.
// In-memory tree of sleeve elements with path-based access, copy/move and a
// dentry cache. Self-contained Android port (std::string paths).
// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <cstddef>
#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <unordered_map>

#include "dentry_cache_manager.hpp"
#include "sleeve/sleeve_element/sleeve_element.hpp"
#include "sleeve/sleeve_element/sleeve_file.hpp"
#include "sleeve/sleeve_element/sleeve_folder.hpp"
#include "sleeve/sleeve_filter/filter_combo.hpp"
#include "storage_service.hpp"
#include "type/type.hpp"

namespace alcedo {

class ElementAccessGuard {
 public:
  std::shared_ptr<SleeveElement> access_element_;
  explicit ElementAccessGuard(std::shared_ptr<SleeveElement> element);
  ~ElementAccessGuard();
};

class SleeveBase {
 public:
  sleeve_id_t                   sleeve_id_ = 0;
  std::shared_ptr<SleeveFolder> root_;

  explicit SleeveBase(sleeve_id_t id);

  void InitializeRoot();

  auto GetStorage() -> std::unordered_map<sl_element_id_t, std::shared_ptr<SleeveElement>>&;
  auto GetFilterStorage() -> std::unordered_map<filter_id_t, std::shared_ptr<FilterCombo>>&;
  auto GetNextElementId() -> sl_element_id_t { return next_element_id_++; }
  auto GetNextFilterId() -> filter_id_t { return next_filter_id_++; }

  auto AccessElementById(sl_element_id_t id) const
      -> std::optional<std::shared_ptr<SleeveElement>>;
  auto AccessElementByPath(const sl_path_t& path) -> std::optional<std::shared_ptr<SleeveElement>>;

  auto CreateElementToPath(const sl_path_t& path, const file_name_t& file_name, ElementType type)
      -> std::optional<std::shared_ptr<SleeveElement>>;
  auto RemoveElementInPath(const sl_path_t& target)
      -> std::optional<std::shared_ptr<SleeveElement>>;
  auto RemoveElementInPath(const sl_path_t& path, const file_name_t& file_name)
      -> std::optional<std::shared_ptr<SleeveElement>>;
  auto CopyElement(const sl_path_t& src, const sl_path_t& dest)
      -> std::optional<std::shared_ptr<SleeveElement>>;
  auto MoveElement(const sl_path_t& src, const sl_path_t& dest)
      -> std::optional<std::shared_ptr<SleeveElement>>;

  auto GetReadGuard(const sl_path_t& target) -> std::optional<ElementAccessGuard>;
  auto GetWriteGuard(const sl_path_t& target) -> std::optional<ElementAccessGuard>;
  auto GetWriteGuard(const sl_path_t& parent_folder_path, const file_name_t& file_name)
      -> std::optional<ElementAccessGuard>;

  void GarbageCollect();
  auto Tree(const sl_path_t& path) -> std::string;
  auto TreeBFS(const sl_path_t& path) -> std::string;
  auto IsSubFolder(const std::shared_ptr<SleeveFolder> folder_a, const sl_path_t& path_b) const
      -> bool;

 private:
  size_t                                                              size_ = 0;
  sl_element_id_t                                                     next_element_id_ = 1;
  filter_id_t                                                         next_filter_id_  = 1;
  std::unordered_map<sl_element_id_t, std::shared_ptr<SleeveElement>> storage_;
  std::unordered_map<filter_id_t, std::shared_ptr<FilterCombo>>       filter_storage_;
  DCacheManager                                                       dentry_cache_;
};

}  // namespace alcedo
