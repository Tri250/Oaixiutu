// AlcedoAndroid - ImageMapper implementation.
// Maps Image objects to/from DuckDB rows via the C API.
// SPDX-License-Identifier: GPL-3.0-only
#include "storage/mapper/image_mapper.hpp"

#include <cstdio>
#include <cstring>
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

auto ImageMapper::Exec(const std::string& sql) -> bool {
  if (!guard_.IsValid()) return false;
  auto lock = guard_.Lock();
  duckdb_result result = nullptr;
  auto state = duckdb_query(guard_.conn_, sql.c_str(), &result);
  if (state != DuckDBSuccess) {
    const char* err = result ? duckdb_result_error(result) : "unknown";
    ALOGE("ImageMapper: query failed: %s | sql: %.200s", err ? err : "unknown", sql.c_str());
    if (result) duckdb_destroy_result(&result);
    return false;
  }
  if (result) duckdb_destroy_result(&result);
  return true;
}

void ImageMapper::Insert(const std::shared_ptr<Image>& image) {
  if (!image) return;
  char sql[1024];
  std::snprintf(sql, sizeof(sql),
                "INSERT INTO Image (id, image_path, file_name, type, metadata) "
                "VALUES (%u, %s, %s, %d, '%s')",
                image->image_id_,
                escape_sql_string(image->image_path_.string()).c_str(),
                escape_sql_string(image->image_name_).c_str(),
                static_cast<int>(image->image_type_),
                image->ExifToJson().c_str());
  Exec(sql);
}

void ImageMapper::InsertBatch(const std::vector<std::shared_ptr<Image>>& images) {
  for (const auto& img : images) Insert(img);
}

void ImageMapper::Update(const std::shared_ptr<Image>& image) {
  if (!image) return;
  char sql[1024];
  std::snprintf(sql, sizeof(sql),
                "UPDATE Image SET image_path = %s, file_name = %s, type = %d, "
                "metadata = '%s' WHERE id = %u",
                escape_sql_string(image->image_path_.string()).c_str(),
                escape_sql_string(image->image_name_).c_str(),
                static_cast<int>(image->image_type_),
                image->ExifToJson().c_str(),
                image->image_id_);
  Exec(sql);
}

void ImageMapper::RemoveById(image_id_t id) {
  char sql[128];
  std::snprintf(sql, sizeof(sql), "DELETE FROM Image WHERE id = %u", id);
  Exec(sql);
}

void ImageMapper::RemoveByType(ImageType type) {
  char sql[128];
  std::snprintf(sql, sizeof(sql), "DELETE FROM Image WHERE type = %d", static_cast<int>(type));
  Exec(sql);
}

void ImageMapper::RemoveByPath(const std::string& path) {
  std::string sql = "DELETE FROM Image WHERE image_path = " + escape_sql_string(path);
  Exec(sql);
}

auto ImageMapper::SelectById(image_id_t id) -> std::shared_ptr<Image> {
  if (!guard_.IsValid()) return nullptr;
  auto lock = guard_.Lock();
  char sql[128];
  std::snprintf(sql, sizeof(sql), "SELECT id, image_path, file_name, type FROM Image WHERE id = %u", id);
  duckdb_result result = nullptr;
  auto state = duckdb_query(guard_.conn_, sql, &result);
  if (state != DuckDBSuccess || !result) {
    if (result) duckdb_destroy_result(&result);
    return nullptr;
  }
  if (duckdb_row_count(result) == 0) {
    duckdb_destroy_result(&result);
    return nullptr;
  }
  auto img = std::make_shared<Image>();
  img->image_id_ = static_cast<image_id_t>(duckdb_value_int64(result, 0, 0));
  const char* path = duckdb_value_varchar(result, 1, 0);
  if (path) img->image_path_ = std::filesystem::path(path);
  const char* name = duckdb_value_varchar(result, 2, 0);
  if (name) img->image_name_ = name;
  img->image_type_ = static_cast<ImageType>(duckdb_value_int32(result, 3, 0));
  duckdb_destroy_result(&result);
  return img;
}

auto ImageMapper::SelectByType(ImageType type) -> std::vector<std::shared_ptr<Image>> {
  std::vector<std::shared_ptr<Image>> out;
  if (!guard_.IsValid()) return out;
  auto lock = guard_.Lock();
  char sql[128];
  std::snprintf(sql, sizeof(sql), "SELECT id, image_path, file_name, type FROM Image WHERE type = %d",
                static_cast<int>(type));
  duckdb_result result = nullptr;
  if (duckdb_query(guard_.conn_, sql, &result) != DuckDBSuccess || !result) {
    if (result) duckdb_destroy_result(&result);
    return out;
  }
  int64_t rows = duckdb_row_count(result);
  for (int64_t r = 0; r < rows; ++r) {
    auto img = std::make_shared<Image>();
    img->image_id_ = static_cast<image_id_t>(duckdb_value_int64(result, 0, r));
    const char* path = duckdb_value_varchar(result, 1, r);
    if (path) img->image_path_ = std::filesystem::path(path);
    const char* name = duckdb_value_varchar(result, 2, r);
    if (name) img->image_name_ = name;
    img->image_type_ = static_cast<ImageType>(duckdb_value_int32(result, 3, r));
    out.push_back(img);
  }
  duckdb_destroy_result(&result);
  return out;
}

auto ImageMapper::SelectByName(const std::string& name) -> std::vector<std::shared_ptr<Image>> {
  std::vector<std::shared_ptr<Image>> out;
  if (!guard_.IsValid()) return out;
  auto lock = guard_.Lock();
  std::string sql = "SELECT id, image_path, file_name, type FROM Image WHERE file_name = " +
                    escape_sql_string(name);
  duckdb_result result = nullptr;
  if (duckdb_query(guard_.conn_, sql.c_str(), &result) != DuckDBSuccess || !result) {
    if (result) duckdb_destroy_result(&result);
    return out;
  }
  int64_t rows = duckdb_row_count(result);
  for (int64_t r = 0; r < rows; ++r) {
    auto img = std::make_shared<Image>();
    img->image_id_ = static_cast<image_id_t>(duckdb_value_int64(result, 0, r));
    const char* path = duckdb_value_varchar(result, 1, r);
    if (path) img->image_path_ = std::filesystem::path(path);
    const char* nm = duckdb_value_varchar(result, 2, r);
    if (nm) img->image_name_ = nm;
    img->image_type_ = static_cast<ImageType>(duckdb_value_int32(result, 3, r));
    out.push_back(img);
  }
  duckdb_destroy_result(&result);
  return out;
}

auto ImageMapper::SelectByPath(const std::string& path) -> std::vector<std::shared_ptr<Image>> {
  std::vector<std::shared_ptr<Image>> out;
  if (!guard_.IsValid()) return out;
  auto lock = guard_.Lock();
  std::string sql = "SELECT id, image_path, file_name, type FROM Image WHERE image_path = " +
                    escape_sql_string(path);
  duckdb_result result = nullptr;
  if (duckdb_query(guard_.conn_, sql.c_str(), &result) != DuckDBSuccess || !result) {
    if (result) duckdb_destroy_result(&result);
    return out;
  }
  int64_t rows = duckdb_row_count(result);
  for (int64_t r = 0; r < rows; ++r) {
    auto img = std::make_shared<Image>();
    img->image_id_ = static_cast<image_id_t>(duckdb_value_int64(result, 0, r));
    const char* p = duckdb_value_varchar(result, 1, r);
    if (p) img->image_path_ = std::filesystem::path(p);
    const char* nm = duckdb_value_varchar(result, 2, r);
    if (nm) img->image_name_ = nm;
    img->image_type_ = static_cast<ImageType>(duckdb_value_int32(result, 3, r));
    out.push_back(img);
  }
  duckdb_destroy_result(&result);
  return out;
}

}  // namespace alcedo
