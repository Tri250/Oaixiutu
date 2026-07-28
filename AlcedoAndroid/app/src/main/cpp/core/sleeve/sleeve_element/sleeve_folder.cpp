// AlcedoAndroid - SleeveFolder implementation.
// SPDX-License-Identifier: GPL-3.0-only
#include "sleeve/sleeve_element/sleeve_folder.hpp"

#include <algorithm>
#include <memory>
#include <utility>

namespace alcedo {

SleeveFolder::SleeveFolder(sl_element_id_t id, file_name_t element_name)
    : SleeveElement(id, std::move(element_name), ElementType::FOLDER) {}

SleeveFolder::~SleeveFolder() = default;

auto SleeveFolder::Copy(sl_element_id_t new_id) const -> std::shared_ptr<SleeveElement> {
  // A folder copy is a shallow copy of the folder node itself; children are not
  // duplicated (the caller wires up children via AddElementToMap as needed).
  auto copy = std::make_shared<SleeveFolder>(new_id, element_name_);
  copy->added_time_         = added_time_;
  copy->last_modified_time_ = last_modified_time_;
  copy->pinned_             = pinned_;
  copy->sync_flag_          = sync_flag_;
  copy->file_count_         = file_count_;
  copy->folder_count_       = folder_count_;
  return copy;
}

void SleeveFolder::AddElementToMap(const std::shared_ptr<SleeveElement> element,
                                   bool change_sync, bool increment_ref_count) {
  if (!element) return;
  const auto& name = element->element_name_;
  contents_[name] = element->element_id_;
  // Maintain the element_list_ deduplicated.
  if (std::find(element_list_.begin(), element_list_.end(), element->element_id_) ==
      element_list_.end()) {
    element_list_.push_back(element->element_id_);
  }
  if (increment_ref_count) element->IncrementRefCount();
  if (change_sync) element->SetSyncFlag(SyncFlag::MODIFIED);
  if (element->type_ == ElementType::FILE) {
    ++file_count_;
  } else {
    ++folder_count_;
  }
  SetLastModifiedTime();
}

void SleeveFolder::ReplaceChild(sl_element_id_t from, sl_element_id_t to) {
  for (auto& kv : contents_) {
    if (kv.second == from) kv.second = to;
  }
  std::replace(element_list_.begin(), element_list_.end(), from, to);
  SetLastModifiedTime();
}

void SleeveFolder::UpdateElementMap(const file_name_t& name, sl_element_id_t old_id,
                                    sl_element_id_t new_id) {
  auto it = contents_.find(name);
  if (it != contents_.end() && it->second == old_id) it->second = new_id;
  std::replace(element_list_.begin(), element_list_.end(), old_id, new_id);
  SetLastModifiedTime();
}

auto SleeveFolder::GetElementIdByName(const file_name_t& name) const
    -> std::optional<sl_element_id_t> {
  auto it = contents_.find(name);
  if (it == contents_.end()) return std::nullopt;
  return it->second;
}

auto SleeveFolder::ListElements() const -> const std::vector<sl_element_id_t>& {
  return element_list_;
}

auto SleeveFolder::ContainsElementId(sl_element_id_t element_id) const -> bool {
  return std::find(element_list_.begin(), element_list_.end(), element_id) !=
         element_list_.end();
}

auto SleeveFolder::Contains(const file_name_t& name) const -> bool {
  return contents_.find(name) != contents_.end();
}

void SleeveFolder::RemoveNameFromMap(const file_name_t& name) {
  auto it = contents_.find(name);
  if (it == contents_.end()) return;
  const sl_element_id_t id = it->second;
  contents_.erase(it);
  element_list_.erase(std::remove(element_list_.begin(), element_list_.end(), id),
                      element_list_.end());
  SetLastModifiedTime();
}

auto SleeveFolder::RemoveElementById(sl_element_id_t element_id) -> bool {
  bool removed = false;
  for (auto it = contents_.begin(); it != contents_.end(); ++it) {
    if (it->second == element_id) {
      contents_.erase(it);
      removed = true;
      break;
    }
  }
  if (removed) {
    element_list_.erase(std::remove(element_list_.begin(), element_list_.end(), element_id),
                        element_list_.end());
    SetLastModifiedTime();
  }
  return removed;
}

void SleeveFolder::CreateIndex(const std::vector<std::shared_ptr<SleeveElement>>& filtered_elements,
                               filter_id_t filter_id) {
  std::vector<sl_element_id_t> ids;
  ids.reserve(filtered_elements.size());
  for (const auto& e : filtered_elements) {
    if (e) ids.push_back(e->element_id_);
  }
  indices_cache_[filter_id] = std::move(ids);
}

auto SleeveFolder::HasFilterIndex(filter_id_t filter_id) const -> bool {
  return indices_cache_.find(filter_id) != indices_cache_.end();
}

auto SleeveFolder::ListElementsByFilter(filter_id_t filter_id) const
    -> const std::vector<sl_element_id_t>& {
  static const std::vector<sl_element_id_t> kEmpty;
  auto it = indices_cache_.find(filter_id);
  if (it == indices_cache_.end()) return kEmpty;
  return it->second;
}

void SleeveFolder::IncrementFolderCount() { ++folder_count_; }
void SleeveFolder::IncrementFileCount() { ++file_count_; }
void SleeveFolder::DecrementFolderCount() { if (folder_count_ > 0) --folder_count_; }
void SleeveFolder::DecrementFileCount() { if (file_count_ > 0) --file_count_; }

auto SleeveFolder::Clear() -> bool {
  contents_.clear();
  element_list_.clear();
  indices_cache_.clear();
  file_count_ = folder_count_ = 0;
  return true;
}

auto SleeveFolder::ResetFilters() -> bool {
  indices_cache_.clear();
  return true;
}

auto SleeveFolder::ContentSize() -> size_t { return element_list_.size(); }

}  // namespace alcedo
