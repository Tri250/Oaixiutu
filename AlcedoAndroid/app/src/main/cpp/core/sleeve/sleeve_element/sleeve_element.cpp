// AlcedoAndroid - SleeveElement implementation.
// SPDX-License-Identifier: GPL-3.0-only
#include "sleeve/sleeve_element/sleeve_element.hpp"

#include <memory>
#include <utility>

#include "utils/time_provider.hpp"

namespace alcedo {

SleeveElement::SleeveElement(sl_element_id_t id, file_name_t element_name, ElementType type)
    : element_id_(id), type_(type), element_name_(std::move(element_name)) {
  SetAddTime();
  last_modified_time_ = added_time_;
}

SleeveElement::~SleeveElement() = default;

auto SleeveElement::Copy(sl_element_id_t new_id) const -> std::shared_ptr<SleeveElement> {
  auto copy = std::make_shared<SleeveElement>(new_id, element_name_, type_);
  copy->added_time_         = added_time_;
  copy->last_modified_time_ = last_modified_time_;
  copy->pinned_             = pinned_;
  copy->sync_flag_          = sync_flag_;
  return copy;
}

auto SleeveElement::Clear() -> bool { return true; }

void SleeveElement::SetAddTime() { added_time_ = TimeProvider::Now(); }

void SleeveElement::SetLastModifiedTime() { last_modified_time_ = TimeProvider::Now(); }

void SleeveElement::IncrementRefCount() { ++ref_count_; }
void SleeveElement::DecrementRefCount() { if (ref_count_ > 0) --ref_count_; }
auto SleeveElement::IsShared() -> bool { return ref_count_ > 1; }
void SleeveElement::SetSyncFlag(SyncFlag flag) { sync_flag_ = flag; }

}  // namespace alcedo
