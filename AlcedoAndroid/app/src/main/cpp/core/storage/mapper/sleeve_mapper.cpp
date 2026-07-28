// AlcedoAndroid - SleeveMapper implementation.
// Maps sleeve elements, folder-content links, edit history and file-image links.
// SPDX-License-Identifier: GPL-3.0-only
#include "storage/mapper/sleeve_mapper.hpp"

#include <cstdio>
#include <cstring>
#include <ctime>
#include <utility>

#include "utils/app_logging.hpp"

namespace alcedo {

static auto escape_sql_string(const std::string& s) -> std::string {
  std::string out;
  out.reserve(s.size() + 2);
  out += '\'';
  for (char c : s) {
    if (c == '\'') out += "''";
    else out += c;
  }
  out += '\'';
  return out;
}

auto SleeveMapper::Exec(const std::string& sql) -> bool {
  if (!guard_.IsValid()) return false;
  auto lock = guard_.Lock();
  duckdb_result result = nullptr;
  auto state = duckdb_query(guard_.conn_, sql.c_str(), &result);
  if (state != DuckDBSuccess) {
    const char* err = result ? duckdb_result_error(result) : "unknown";
    ALOGE("SleeveMapper: query failed: %s | sql: %.200s", err ? err : "unknown", sql.c_str());
    if (result) duckdb_destroy_result(&result);
    return false;
  }
  if (result) duckdb_destroy_result(&result);
  return true;
}

void SleeveMapper::UpsertElement(const SleeveElement& elem) {
  char sql[512];
  std::snprintf(sql, sizeof(sql),
                "INSERT OR REPLACE INTO Element (id, type, element_name, added_time, "
                "modified_time, ref_count) VALUES (%u, %d, %s, %ld, %ld, %u)",
                elem.element_id_, static_cast<int>(elem.type_),
                escape_sql_string(elem.element_name_).c_str(),
                static_cast<long>(elem.added_time_), static_cast<long>(elem.last_modified_time_),
                elem.ref_count_);
  Exec(sql);
}

void SleeveMapper::RemoveElement(sl_element_id_t id) {
  char sql[128];
  std::snprintf(sql, sizeof(sql), "DELETE FROM Element WHERE id = %u", id);
  Exec(sql);
  // Also remove folder-content links.
  std::snprintf(sql, sizeof(sql), "DELETE FROM FolderContent WHERE element_id = %u", id);
  Exec(sql);
  std::snprintf(sql, sizeof(sql), "DELETE FROM FolderContent WHERE folder_id = %u", id);
  Exec(sql);
}

auto SleeveMapper::SelectElement(sl_element_id_t id) -> std::optional<SleeveElementRecord> {
  if (!guard_.IsValid()) return std::nullopt;
  auto lock = guard_.Lock();
  char sql[128];
  std::snprintf(sql, sizeof(sql),
                "SELECT id, type, element_name, added_time, modified_time, ref_count "
                "FROM Element WHERE id = %u",
                id);
  duckdb_result result = nullptr;
  if (duckdb_query(guard_.conn_, sql, &result) != DuckDBSuccess || !result) {
    if (result) duckdb_destroy_result(&result);
    return std::nullopt;
  }
  if (duckdb_row_count(result) == 0) {
    duckdb_destroy_result(&result);
    return std::nullopt;
  }
  SleeveElementRecord rec;
  rec.id            = static_cast<sl_element_id_t>(duckdb_value_int64(result, 0, 0));
  rec.type          = duckdb_value_int32(result, 1, 0);
  const char* name  = duckdb_value_varchar(result, 2, 0);
  if (name) rec.element_name = name;
  rec.added_time    = duckdb_value_int64(result, 3, 0);
  rec.modified_time = duckdb_value_int64(result, 4, 0);
  rec.ref_count     = static_cast<uint32_t>(duckdb_value_int64(result, 5, 0));
  duckdb_destroy_result(&result);
  return rec;
}

auto SleeveMapper::SelectAllElements() -> std::vector<SleeveElementRecord> {
  std::vector<SleeveElementRecord> out;
  if (!guard_.IsValid()) return out;
  auto lock = guard_.Lock();
  const char* sql =
      "SELECT id, type, element_name, added_time, modified_time, ref_count FROM Element";
  duckdb_result result = nullptr;
  if (duckdb_query(guard_.conn_, sql, &result) != DuckDBSuccess || !result) {
    if (result) duckdb_destroy_result(&result);
    return out;
  }
  int64_t rows = duckdb_row_count(result);
  for (int64_t r = 0; r < rows; ++r) {
    SleeveElementRecord rec;
    rec.id            = static_cast<sl_element_id_t>(duckdb_value_int64(result, 0, r));
    rec.type          = duckdb_value_int32(result, 1, r);
    const char* name  = duckdb_value_varchar(result, 2, r);
    if (name) rec.element_name = name;
    rec.added_time    = duckdb_value_int64(result, 3, r);
    rec.modified_time = duckdb_value_int64(result, 4, r);
    rec.ref_count     = static_cast<uint32_t>(duckdb_value_int64(result, 5, r));
    out.push_back(std::move(rec));
  }
  duckdb_destroy_result(&result);
  return out;
}

void SleeveMapper::InsertFolderContent(sl_element_id_t folder_id, sl_element_id_t element_id) {
  char sql[128];
  std::snprintf(sql, sizeof(sql),
                "INSERT OR IGNORE INTO FolderContent (folder_id, element_id) VALUES (%u, %u)",
                folder_id, element_id);
  Exec(sql);
}

void SleeveMapper::RemoveFolderContent(sl_element_id_t folder_id, sl_element_id_t element_id) {
  char sql[128];
  std::snprintf(sql, sizeof(sql),
                "DELETE FROM FolderContent WHERE folder_id = %u AND element_id = %u",
                folder_id, element_id);
  Exec(sql);
}

auto SleeveMapper::SelectFolderContent(sl_element_id_t folder_id) -> std::vector<sl_element_id_t> {
  std::vector<sl_element_id_t> out;
  if (!guard_.IsValid()) return out;
  auto lock = guard_.Lock();
  char sql[128];
  std::snprintf(sql, sizeof(sql), "SELECT element_id FROM FolderContent WHERE folder_id = %u",
                folder_id);
  duckdb_result result = nullptr;
  if (duckdb_query(guard_.conn_, sql, &result) != DuckDBSuccess || !result) {
    if (result) duckdb_destroy_result(&result);
    return out;
  }
  int64_t rows = duckdb_row_count(result);
  for (int64_t r = 0; r < rows; ++r) {
    out.push_back(static_cast<sl_element_id_t>(duckdb_value_int64(result, 0, r)));
  }
  duckdb_destroy_result(&result);
  return out;
}

void SleeveMapper::UpsertEditHistory(sl_element_id_t file_id, const std::string& history_json) {
  std::string sql = "INSERT OR REPLACE INTO EditHistory (file_id, history) VALUES (" +
                    std::to_string(file_id) + ", " + escape_sql_string(history_json) + ")";
  Exec(sql);
}

auto SleeveMapper::SelectEditHistory(sl_element_id_t file_id) -> std::optional<std::string> {
  if (!guard_.IsValid()) return std::nullopt;
  auto lock = guard_.Lock();
  char sql[128];
  std::snprintf(sql, sizeof(sql), "SELECT history FROM EditHistory WHERE file_id = %u", file_id);
  duckdb_result result = nullptr;
  if (duckdb_query(guard_.conn_, sql, &result) != DuckDBSuccess || !result) {
    if (result) duckdb_destroy_result(&result);
    return std::nullopt;
  }
  if (duckdb_row_count(result) == 0) {
    duckdb_destroy_result(&result);
    return std::nullopt;
  }
  const char* json = duckdb_value_varchar(result, 0, 0);
  std::optional<std::string> out;
  if (json) out = std::string(json);
  duckdb_destroy_result(&result);
  return out;
}

void SleeveMapper::UpsertFileImage(sl_element_id_t file_id, image_id_t image_id) {
  char sql[128];
  std::snprintf(sql, sizeof(sql),
                "INSERT OR REPLACE INTO FileImage (file_id, image_id) VALUES (%u, %u)",
                file_id, image_id);
  Exec(sql);
}

auto SleeveMapper::SelectFileImage(sl_element_id_t file_id) -> std::optional<image_id_t> {
  if (!guard_.IsValid()) return std::nullopt;
  auto lock = guard_.Lock();
  char sql[128];
  std::snprintf(sql, sizeof(sql), "SELECT image_id FROM FileImage WHERE file_id = %u", file_id);
  duckdb_result result = nullptr;
  if (duckdb_query(guard_.conn_, sql, &result) != DuckDBSuccess || !result) {
    if (result) duckdb_destroy_result(&result);
    return std::nullopt;
  }
  if (duckdb_row_count(result) == 0) {
    duckdb_destroy_result(&result);
    return std::nullopt;
  }
  auto val = static_cast<image_id_t>(duckdb_value_int64(result, 0, 0));
  duckdb_destroy_result(&result);
  return val;
}

}  // namespace alcedo
