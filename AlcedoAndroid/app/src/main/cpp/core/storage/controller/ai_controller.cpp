// AlcedoAndroid - AiStorageController implementation.
// AI image understanding + rating annotation persistence.
// SPDX-License-Identifier: GPL-3.0-only
#include "storage/controller/ai_controller.hpp"

#include <cstdio>
#include <cstring>
#include <string>
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

// Copy a duckdb_value_varchar() result into `dst` and free the DuckDB-owned
// buffer. Every duckdb_value_varchar() call returns a string the caller MUST
// release with duckdb_free(); previously the returned pointers were assigned
// into std::string fields and then leaked on every column of every row.
static void AssignDuckdbString(std::string& dst, duckdb_result result, int col) {
  char* v = duckdb_value_varchar(result, col, /*row=*/0);
  if (v) {
    dst = v;
    duckdb_free(v);
  } else {
    dst.clear();
  }
}

AiStorageController::AiStorageController(ConnectionGuard&& guard)
    : guard_(std::move(guard)) {}

auto AiStorageController::Exec(const std::string& sql) -> bool {
  if (!guard_.IsValid()) return false;
  auto lock = guard_.Lock();
  duckdb_result result = nullptr;
  auto state = duckdb_query(guard_.conn_, sql.c_str(), &result);
  if (state != DuckDBSuccess) {
    const char* err = result ? duckdb_result_error(result) : "unknown";
    ALOGE("AiStorageController: query failed: %s", err ? err : "unknown");
    if (result) duckdb_destroy_result(&result);
    return false;
  }
  if (result) duckdb_destroy_result(&result);
  return true;
}

void AiStorageController::UpsertUnderstanding(const AiImageUnderstandingRecord& rec) {
  // Build with std::string concatenation so arbitrarily long captions / tag
  // blobs cannot overflow a fixed-size buffer (the previous char sql[1024]
  // could be truncated or overrun by large JSON fields).
  std::string sql =
      "INSERT OR REPLACE INTO AiImageUnderstanding (file_id, task_id, provider_id, "
      "model_id, prompt_profile_id, rendition_kind, caption, tags_json, scene, "
      "confidence, active) VALUES (" +
      std::to_string(rec.file_id) + ", " +
      escape_sql_string(rec.task_id) + ", " +
      escape_sql_string(rec.provider_id) + ", " +
      escape_sql_string(rec.model_id) + ", " +
      escape_sql_string(rec.prompt_profile_id) + ", " +
      escape_sql_string(rec.rendition_kind) + ", " +
      escape_sql_string(rec.caption) + ", " +
      escape_sql_string(rec.tags_json) + ", " +
      escape_sql_string(rec.scene) + ", " +
      std::to_string(rec.confidence) + ", " +
      (rec.active ? "TRUE" : "FALSE") + ")";
  Exec(sql);
}

auto AiStorageController::SelectUnderstanding(sl_element_id_t file_id, const std::string& task_id)
    -> std::optional<AiImageUnderstandingRecord> {
  if (!guard_.IsValid()) return std::nullopt;
  auto lock = guard_.Lock();
  std::string sql =
      "SELECT file_id, task_id, provider_id, model_id, prompt_profile_id, rendition_kind, "
      "caption, tags_json, scene, confidence, active FROM AiImageUnderstanding "
      "WHERE file_id = " +
      std::to_string(file_id) + " AND task_id = " + escape_sql_string(task_id);
  duckdb_result result = nullptr;
  if (duckdb_query(guard_.conn_, sql.c_str(), &result) != DuckDBSuccess || !result) {
    if (result) duckdb_destroy_result(&result);
    return std::nullopt;
  }
  if (duckdb_row_count(result) == 0) {
    duckdb_destroy_result(&result);
    return std::nullopt;
  }
  AiImageUnderstandingRecord rec;
  rec.file_id  = static_cast<sl_element_id_t>(duckdb_value_int64(result, 0, 0));
  AssignDuckdbString(rec.task_id, result, 1);
  AssignDuckdbString(rec.provider_id, result, 2);
  AssignDuckdbString(rec.model_id, result, 3);
  AssignDuckdbString(rec.prompt_profile_id, result, 4);
  AssignDuckdbString(rec.rendition_kind, result, 5);
  AssignDuckdbString(rec.caption, result, 6);
  AssignDuckdbString(rec.tags_json, result, 7);
  AssignDuckdbString(rec.scene, result, 8);
  rec.confidence = duckdb_value_double(result, 9, 0);
  rec.active     = duckdb_value_boolean(result, 10, 0);
  duckdb_destroy_result(&result);
  return rec;
}

auto AiStorageController::SelectActiveUnderstanding(sl_element_id_t file_id)
    -> std::optional<AiImageUnderstandingRecord> {
  if (!guard_.IsValid()) return std::nullopt;
  auto lock = guard_.Lock();
  std::string sql =
      "SELECT file_id, task_id, provider_id, model_id, prompt_profile_id, rendition_kind, "
      "caption, tags_json, scene, confidence, active FROM AiImageUnderstanding "
      "WHERE file_id = " +
      std::to_string(file_id) + " AND active = TRUE ORDER BY updated_at DESC LIMIT 1";
  duckdb_result result = nullptr;
  if (duckdb_query(guard_.conn_, sql.c_str(), &result) != DuckDBSuccess || !result) {
    if (result) duckdb_destroy_result(&result);
    return std::nullopt;
  }
  if (duckdb_row_count(result) == 0) {
    duckdb_destroy_result(&result);
    return std::nullopt;
  }
  AiImageUnderstandingRecord rec;
  rec.file_id  = static_cast<sl_element_id_t>(duckdb_value_int64(result, 0, 0));
  AssignDuckdbString(rec.task_id, result, 1);
  AssignDuckdbString(rec.provider_id, result, 2);
  AssignDuckdbString(rec.model_id, result, 3);
  AssignDuckdbString(rec.prompt_profile_id, result, 4);
  AssignDuckdbString(rec.rendition_kind, result, 5);
  AssignDuckdbString(rec.caption, result, 6);
  AssignDuckdbString(rec.tags_json, result, 7);
  AssignDuckdbString(rec.scene, result, 8);
  rec.confidence = duckdb_value_double(result, 9, 0);
  rec.active     = duckdb_value_boolean(result, 10, 0);
  duckdb_destroy_result(&result);
  return rec;
}

void AiStorageController::UpsertRating(const AiImageRatingRecord& rec) {
  // Build with std::string concatenation; the previous char sql[1024] could be
  // overflowed by long reasons / rubric metadata.
  std::string sql =
      "INSERT OR REPLACE INTO AiImageRating (file_id, task_id, provider_id, model_id, "
      "prompt_profile_id, rendition_kind, rating, rubric_id, rubric_version, reasons, "
      "active) VALUES (" +
      std::to_string(rec.file_id) + ", " +
      escape_sql_string(rec.task_id) + ", " +
      escape_sql_string(rec.provider_id) + ", " +
      escape_sql_string(rec.model_id) + ", " +
      escape_sql_string(rec.prompt_profile_id) + ", " +
      escape_sql_string(rec.rendition_kind) + ", " +
      std::to_string(rec.rating) + ", " +
      escape_sql_string(rec.rubric_id) + ", " +
      escape_sql_string(rec.rubric_version) + ", " +
      escape_sql_string(rec.reasons) + ", " +
      (rec.active ? "TRUE" : "FALSE") + ")";
  Exec(sql);
}

auto AiStorageController::SelectRating(sl_element_id_t file_id, const std::string& task_id)
    -> std::optional<AiImageRatingRecord> {
  if (!guard_.IsValid()) return std::nullopt;
  auto lock = guard_.Lock();
  std::string sql =
      "SELECT file_id, task_id, provider_id, model_id, prompt_profile_id, rendition_kind, "
      "rating, rubric_id, rubric_version, reasons, active FROM AiImageRating "
      "WHERE file_id = " +
      std::to_string(file_id) + " AND task_id = " + escape_sql_string(task_id);
  duckdb_result result = nullptr;
  if (duckdb_query(guard_.conn_, sql.c_str(), &result) != DuckDBSuccess || !result) {
    if (result) duckdb_destroy_result(&result);
    return std::nullopt;
  }
  if (duckdb_row_count(result) == 0) {
    duckdb_destroy_result(&result);
    return std::nullopt;
  }
  AiImageRatingRecord rec;
  rec.file_id   = static_cast<sl_element_id_t>(duckdb_value_int64(result, 0, 0));
  AssignDuckdbString(rec.task_id, result, 1);
  AssignDuckdbString(rec.provider_id, result, 2);
  AssignDuckdbString(rec.model_id, result, 3);
  AssignDuckdbString(rec.prompt_profile_id, result, 4);
  AssignDuckdbString(rec.rendition_kind, result, 5);
  rec.rating        = duckdb_value_int32(result, 6, 0);
  AssignDuckdbString(rec.rubric_id, result, 7);
  AssignDuckdbString(rec.rubric_version, result, 8);
  AssignDuckdbString(rec.reasons, result, 9);
  rec.active = duckdb_value_boolean(result, 10, 0);
  duckdb_destroy_result(&result);
  return rec;
}

auto AiStorageController::SelectActiveRating(sl_element_id_t file_id)
    -> std::optional<AiImageRatingRecord> {
  if (!guard_.IsValid()) return std::nullopt;
  auto lock = guard_.Lock();
  std::string sql =
      "SELECT file_id, task_id, provider_id, model_id, prompt_profile_id, rendition_kind, "
      "rating, rubric_id, rubric_version, reasons, active FROM AiImageRating "
      "WHERE file_id = " +
      std::to_string(file_id) + " AND active = TRUE ORDER BY updated_at DESC LIMIT 1";
  duckdb_result result = nullptr;
  if (duckdb_query(guard_.conn_, sql.c_str(), &result) != DuckDBSuccess || !result) {
    if (result) duckdb_destroy_result(&result);
    return std::nullopt;
  }
  if (duckdb_row_count(result) == 0) {
    duckdb_destroy_result(&result);
    return std::nullopt;
  }
  AiImageRatingRecord rec;
  rec.file_id   = static_cast<sl_element_id_t>(duckdb_value_int64(result, 0, 0));
  AssignDuckdbString(rec.task_id, result, 1);
  AssignDuckdbString(rec.provider_id, result, 2);
  AssignDuckdbString(rec.model_id, result, 3);
  AssignDuckdbString(rec.prompt_profile_id, result, 4);
  AssignDuckdbString(rec.rendition_kind, result, 5);
  rec.rating        = duckdb_value_int32(result, 6, 0);
  AssignDuckdbString(rec.rubric_id, result, 7);
  AssignDuckdbString(rec.rubric_version, result, 8);
  AssignDuckdbString(rec.reasons, result, 9);
  rec.active = duckdb_value_boolean(result, 10, 0);
  duckdb_destroy_result(&result);
  return rec;
}

void AiStorageController::UpsertFtsDocument(sl_element_id_t file_id, const std::string& body) {
  std::string sql = "INSERT OR REPLACE INTO AiImageFtsDocument (file_id, body) VALUES (" +
                    std::to_string(file_id) + ", " + escape_sql_string(body) + ")";
  Exec(sql);
}

auto AiStorageController::SelectFtsDocument(sl_element_id_t file_id) -> std::optional<std::string> {
  if (!guard_.IsValid()) return std::nullopt;
  auto lock = guard_.Lock();
  std::string sql = "SELECT body FROM AiImageFtsDocument WHERE file_id = " + std::to_string(file_id);
  duckdb_result result = nullptr;
  if (duckdb_query(guard_.conn_, sql.c_str(), &result) != DuckDBSuccess || !result) {
    if (result) duckdb_destroy_result(&result);
    return std::nullopt;
  }
  if (duckdb_row_count(result) == 0) {
    duckdb_destroy_result(&result);
    return std::nullopt;
  }
  std::optional<std::string> out;
  char* body = duckdb_value_varchar(result, 0, 0);
  if (body) {
    out = std::string(body);
    duckdb_free(body);  // duckdb_value_varchar result must be freed.
  }
  duckdb_destroy_result(&result);
  return out;
}

}  // namespace alcedo
