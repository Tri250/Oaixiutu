// AlcedoAndroid - SleeveElement base.
// Self-contained Android port (std::string instead of desktop std::wstring).
// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <cstdint>
#include <ctime>
#include <memory>
#include <string>

#include "type/type.hpp"

namespace alcedo {

enum class ElementType { FILE, FOLDER };
enum class SyncFlag { UNSYNC, MODIFIED, SYNCED, DELETED };

// Abstract object residing in a sleeve: a file or a folder.
class SleeveElement {
 public:
  sl_element_id_t element_id_ = 0;
  ElementType     type_       = ElementType::FILE;
  file_name_t     element_name_;
  std::time_t     added_time_         = 0;
  std::time_t     last_modified_time_ = 0;
  uint32_t        ref_count_          = 0;
  bool            pinned_             = false;
  SyncFlag        sync_flag_          = SyncFlag::UNSYNC;

  SleeveElement(sl_element_id_t id, file_name_t element_name, ElementType type);
  virtual ~SleeveElement();

  virtual auto Copy(sl_element_id_t new_id) const -> std::shared_ptr<SleeveElement>;
  virtual auto Clear() -> bool;
  void         SetAddTime();
  void         SetLastModifiedTime();
  void         IncrementRefCount();
  void         DecrementRefCount();
  auto         IsShared() -> bool;
  void         SetSyncFlag(SyncFlag flag);
};

}  // namespace alcedo
