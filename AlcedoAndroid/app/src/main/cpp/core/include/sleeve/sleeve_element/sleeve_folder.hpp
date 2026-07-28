// AlcedoAndroid - SleeveFolder: a container of files/folders with filter indices.
// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <unordered_map>
#include <vector>

#include "sleeve_element.hpp"
#include "type/type.hpp"

namespace alcedo {

class SleeveFolder : public SleeveElement {
 public:
  SleeveFolder(sl_element_id_t id, file_name_t element_name);
  ~SleeveFolder() override;

  auto Copy(sl_element_id_t new_id) const -> std::shared_ptr<SleeveElement> override;

  void AddElementToMap(const std::shared_ptr<SleeveElement> element,
                       bool change_sync = true, bool increment_ref_count = true);
  void ReplaceChild(sl_element_id_t from, sl_element_id_t to);
  void UpdateElementMap(const file_name_t& name, sl_element_id_t old_id, sl_element_id_t new_id);
  auto GetElementIdByName(const file_name_t& name) const -> std::optional<sl_element_id_t>;
  auto ListElements() const -> const std::vector<sl_element_id_t>&;
  auto ContainsElementId(sl_element_id_t element_id) const -> bool;
  auto Contains(const file_name_t& name) const -> bool;
  void RemoveNameFromMap(const file_name_t& name);
  auto RemoveElementById(sl_element_id_t element_id) -> bool;

  void CreateIndex(const std::vector<std::shared_ptr<SleeveElement>>& filtered_elements,
                   filter_id_t filter_id);
  auto HasFilterIndex(filter_id_t filter_id) const -> bool;
  auto ListElementsByFilter(filter_id_t filter_id) const -> const std::vector<sl_element_id_t>&;

  void IncrementFolderCount();
  void IncrementFileCount();
  void DecrementFolderCount();
  void DecrementFileCount();
  auto Clear() -> bool override;
  auto ResetFilters() -> bool;
  auto ContentSize() -> size_t;
  auto ChildrenLoaded() const -> bool { return children_loaded_; }
  void MarkChildrenLoaded(bool loaded = true) { children_loaded_ = loaded; }

 private:
  std::unordered_map<file_name_t, sl_element_id_t>              contents_;
  std::unordered_map<filter_id_t, std::vector<sl_element_id_t>> indices_cache_;
  std::vector<sl_element_id_t>                                  element_list_;
  filter_id_t                                                   default_filter_ = 0;
  uint32_t                                                      file_count_   = 0;
  uint32_t                                                      folder_count_ = 0;
  bool                                                          children_loaded_ = false;
};

}  // namespace alcedo
