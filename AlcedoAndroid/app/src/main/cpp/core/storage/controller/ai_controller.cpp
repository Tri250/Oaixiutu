// AlcedoAndroid - AiStorageController implementation.
// AI image understanding + rating annotation persistence.
// SPDX-License-Identifier: GPL-3.0-only
#include "storage/controller/ai_controller.hpp"

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
  char sql[1024];
  std::snprintf(sql, sizeof(sql),
                "INSERT OR REPLACE INTO AiImageUnderstanding (file_id, task_id, provider_id, "
                "model_id, prompt_profile_id, rendition_kind, caption, tags_json, scene, "
                "confidence, active) VALUES (%u, %s, %s, %s, %s, %s, %s, %s, %s, %f, %s)",
                rec.file_id,
                escape_sql_string(rec.task_id).c_str(),
                escape_sql_string(rec.provider_id).c_str(),
                escape_sql_string(rec.model_id).c_str(),
                escape_sql_string(rec.prompt_profile_id).c_str(),
                escape_sql_string(rec.rendition_kind).c_str(),
                escape_sql_string(rec.caption).c_str(),
                escape_sql_string(rec.tags_json).c_str(),
                escape_sql_string(rec.scene).c_str(),
                rec.confidence,
                rec.active ? "TRUE" : "FALSE");
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
  const char* v = duckdb_value_varchar(result, 1, 0); if (v) rec.task_id = v;
  v = duckdb_value_varchar(result, 2, 0); if (v) rec.provider_id = v;
  v = duckdb_value_varchar(result, 3, 0); if (v) rec.model_id = v;
  v = duckdb_value_varchar(result, 4, 0); if (v) rec.prompt_profile_id = v;
  v = duckdb_value_varchar(result, 5, 0); if (v) rec.rendition_kind = v;
  v = duckdb_value_varchar(result, 6, 0); if (v) rec.caption = v;
  v = duckdb_value_varchar(result, 7, 0); if (v) rec.tags_json = v;
  v = duckdb_value_varchar(result, 8, 0); if (v) rec.scene = v;
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
  const char* v = duckdb_value_varchar(result, 1, 0); if (v) rec.task_id = v;
  v = duckdb_value_varchar(result, 2, 0); if (v) rec.provider_id = v;
  v = duckdb_value_varchar(result, 3, 0); if (v) rec.model_id = v;
  v = duckdb_value_varchar(result, 4, 0); if (v) rec.prompt_profile_id = v;
  v = duckdb_value_varchar(result, 5, 0); if (v) rec.rendition_kind = v;
  v = duckdb_value_varchar(result, 6, 0); if (v) rec.caption = v;
  v = duckdb_value_varchar(result, 7, 0); if (v) rec.tags_json = v;
  v = duckdb_value_varchar(result, 8, 0); if (v) rec.scene = v;
  rec.confidence = duckdb_value_double(result, 9, 0);
  rec.active     = duckdb_value_boolean(result, 10, 0);
  duckdb_destroy_result(&result);
  return rec;
}

void AiStorageController::UpsertRating(const AiImageRatingRecord& rec) {
  char sql[1024];
  std::snprintf(sql, sizeof(sql),
                "INSERT OR REPLACE INTO AiImageRating (file_id, task_id, provider_id, model_id, "
                "prompt_profile_id, rendition_kind, rating, rubric_id, rubric_version, reasons, "
                "active) VALUES (%u, %s, %s, %s, %s, %s, %d, %s, %s, %s, %s)",
                rec.file_id,
                escape_sql_string(rec.task_id).c_str(),
                escape_sql_string(rec.provider_id).c_str(),
                escape_sql_string(rec.model_id).c_str(),
                escape_sql_string(rec.prompt_profile_id).c_str(),
                escape_sql_string(rec.rendition_kind).c_str(),
                rec.rating,
                escape_sql_string(rec.rubric_id).c_str(),
                escape_sql_string(rec.rubric_version).c_str(),
                escape_sql_string(rec.reasons).c_str(),
                rec.active ? "TRUE" : "FALSE");
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
  const char* v = duckdb_value_varchar(result, 1, 0); if (v) rec.task_id = v;
  v = duckdb_value_varchar(result, 2, 0); if (v) rec.provider_id = v;
  v = duckdb_value_varchar(result, 3, 0); if (v) rec.model_id = v;
  v = duckdb_value_varchar(result, 4, 0); if (v) rec.prompt_profile_id = v;
  v = duckdb_value_varchar(result, 5, 0); if (v) rec.rendition_kind = v;
  rec.rating        = duckdb_value_int32(result, 6, 0);
  v = duckdb_value_varchar(result, 7, 0); if (v) rec.rubric_id = v;
  v = duckdb_value_varchar(result, 8, 0); if (v) rec.rubric_version = v;
  v = duckdb_value_varchar(result, 9, 0); if (v) rec.reasons = v;
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
  const char* v = duckdb_value_varchar(result, 1, 0); if (v) rec.task_id = v;
  v = duckdb_value_varchar(result, 2, 0); if (v) rec.provider_id = v;
  v = duckdb_value_varchar(result, 3, 0); if (v) rec.model_id = v;
  v = duckdb_value_varchar(result, 4, 0); if (v) rec.prompt_profile_id = v;
  v = duckdb_value_varchar(result, 5, 0); if (v) rec.rendition_kind = v;
  rec.rating        = duckdb_value_int32(result, 6, 0);
  v = duckdb_value_varchar(result, 7, 0); if (v) rec.rubric_id = v;
  v = duckdb_value_varchar(result, 8, 0); if (v) rec.rubric_version = v;
  v = duckdb_value_varchar(result, 9, 0); if (v) rec.reasons = v;
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
  const char* body = duckdb_value_varchar(result, 0, 0);
  std::optional<std::string> out;
  if (body) out = std::string(body);
  duckdb_destroy_result(&result);
  return out;
}

}  // namespace alcedo
