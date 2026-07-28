// AlcedoAndroid - SleeveMapper (sleeve elements <-> DuckDB rows).
// Maps SleeveElement (file/folder) records and folder-content relationships.
// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <memory>
#include <optional>
#include <string>
#include <vector>

#include "duckdb/duckdb_capi.hpp"
#include "storage/controller/controller_types.hpp"
#include "sleeve/sleeve_element/sleeve_element.hpp"
#include "sleeve/sleeve_element/sleeve_file.hpp"
#include "sleeve/sleeve_element/sleeve_folder.hpp"
#include "type/type.hpp"

namespace alcedo {

struct SleeveElementRecord {
  sl_element_id_t id;
  int             type;
  std::string     element_name;
  int64_t         added_time;
  int64_t         modified_time;
  int64_t         ref_count;
};

struct FolderContentRecord {
  sl_element_id_t folder_id;
  sl_element_id_t element_id;
};

class SleeveMapper {
 public:
  explicit SleeveMapper(ConnectionGuard& guard) : guard_(guard) {}

  void UpsertElement(const SleeveElement& elem);
  void RemoveElement(sl_element_id_t id);
  auto SelectElement(sl_element_id_t id) -> std::optional<SleeveElementRecord>;
  auto SelectAllElements() -> std::vector<SleeveElementRecord>;

  void InsertFolderContent(sl_element_id_t folder_id, sl_element_id_t element_id);
  void RemoveFolderContent(sl_element_id_t folder_id, sl_element_id_t element_id);
  auto SelectFolderContent(sl_element_id_t folder_id) -> std::vector<sl_element_id_t>;

  void UpsertEditHistory(sl_element_id_t file_id, const std::string& history_json);
  auto SelectEditHistory(sl_element_id_t file_id) -> std::optional<std::string>;

  void UpsertFileImage(sl_element_id_t file_id, image_id_t image_id);
  auto SelectFileImage(sl_element_id_t file_id) -> std::optional<image_id_t>;

 private:
  ConnectionGuard& guard_;
  auto Exec(const std::string& sql) -> bool;
};

}  // namespace alcedo
