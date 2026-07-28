// AlcedoAndroid - SemanticStorageController implementation.
// SPDX-License-Identifier: GPL-3.0-only
#include "storage/controller/semantic_controller.hpp"

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

SemanticStorageController::SemanticStorageController(ConnectionGuard&& guard)
    : guard_(std::move(guard)) {}

auto SemanticStorageController::Exec(const std::string& sql) -> bool {
  if (!guard_.IsValid()) return false;
  auto lock = guard_.Lock();
  duckdb_result result = nullptr;
  auto state = duckdb_query(guard_.conn_, sql.c_str(), &result);
  if (state != DuckDBSuccess) {
    const char* err = result ? duckdb_result_error(result) : "unknown";
    ALOGE("SemanticStorageController: query failed: %s", err ? err : "unknown");
    if (result) duckdb_destroy_result(&result);
    return false;
  }
  if (result) duckdb_destroy_result(&result);
  return true;
}

void SemanticStorageController::UpsertModel(const SemanticModelRecord& rec) {
  char sql[512];
  std::snprintf(sql, sizeof(sql),
                "INSERT OR REPLACE INTO SemanticModel (model_key, model_id, revision, "
                "embedding_dim, image_size, active) VALUES (%s, %s, %s, %d, %d, %s)",
                escape_sql_string(rec.model_key).c_str(),
                escape_sql_string(rec.model_id).c_str(),
                escape_sql_string(rec.revision).c_str(),
                rec.embedding_dim, rec.image_size,
                rec.active ? "TRUE" : "FALSE");
  Exec(sql);
}

auto SemanticStorageController::SelectActiveModel() -> std::optional<SemanticModelRecord> {
  if (!guard_.IsValid()) return std::nullopt;
  auto lock = guard_.Lock();
  const char* sql =
      "SELECT model_key, model_id, revision, embedding_dim, image_size, active "
      "FROM SemanticModel WHERE active = TRUE LIMIT 1";
  duckdb_result result = nullptr;
  if (duckdb_query(guard_.conn_, sql, &result) != DuckDBSuccess || !result) {
    if (result) duckdb_destroy_result(&result);
    return std::nullopt;
  }
  if (duckdb_row_count(result) == 0) {
    duckdb_destroy_result(&result);
    return std::nullopt;
  }
  SemanticModelRecord rec;
  const char* mk = duckdb_value_varchar(result, 0, 0);
  if (mk) rec.model_key = mk;
  const char* mid = duckdb_value_varchar(result, 1, 0);
  if (mid) rec.model_id = mid;
  const char* rev = duckdb_value_varchar(result, 2, 0);
  if (rev) rec.revision = rev;
  rec.embedding_dim = duckdb_value_int32(result, 3, 0);
  rec.image_size    = duckdb_value_int32(result, 4, 0);
  rec.active        = duckdb_value_boolean(result, 5, 0);
  duckdb_destroy_result(&result);
  return rec;
}

void SemanticStorageController::UpsertEmbedding(const SemanticEmbeddingRecord& rec) {
  // Store embedding as JSON array for simplicity (DuckDB FLOAT[] binding via C API is complex).
  std::string emb_json = "[";
  for (size_t i = 0; i < rec.embedding.size(); ++i) {
    if (i > 0) emb_json += ",";
    emb_json += std::to_string(rec.embedding[i]);
  }
  emb_json += "]";
  char sql[256];
  std::snprintf(sql, sizeof(sql),
                "INSERT OR REPLACE INTO SemanticImageEmbedding (file_id, image_id, model_key, "
                "embedding, embedding_dim, thumbnail_resolution, status) VALUES (%u, %u, %s, "
                "'%s', %d, %d, %s)",
                rec.file_id, rec.image_id,
                escape_sql_string(rec.model_key).c_str(), emb_json.c_str(),
                rec.embedding_dim, rec.thumbnail_resolution,
                escape_sql_string(rec.status).c_str());
  Exec(sql);
}

auto SemanticStorageController::SelectEmbedding(sl_element_id_t file_id,
                                                const std::string& model_key)
    -> std::optional<SemanticEmbeddingRecord> {
  if (!guard_.IsValid()) return std::nullopt;
  auto lock = guard_.Lock();
  std::string sql =
      "SELECT file_id, image_id, model_key, embedding_dim, thumbnail_resolution, status "
      "FROM SemanticImageEmbedding WHERE file_id = " +
      std::to_string(file_id) + " AND model_key = " + escape_sql_string(model_key);
  duckdb_result result = nullptr;
  if (duckdb_query(guard_.conn_, sql.c_str(), &result) != DuckDBSuccess || !result) {
    if (result) duckdb_destroy_result(&result);
    return std::nullopt;
  }
  if (duckdb_row_count(result) == 0) {
    duckdb_destroy_result(&result);
    return std::nullopt;
  }
  SemanticEmbeddingRecord rec;
  rec.file_id     = static_cast<sl_element_id_t>(duckdb_value_int64(result, 0, 0));
  rec.image_id    = static_cast<image_id_t>(duckdb_value_int64(result, 1, 0));
  const char* mk  = duckdb_value_varchar(result, 2, 0);
  if (mk) rec.model_key = mk;
  rec.embedding_dim       = duckdb_value_int32(result, 3, 0);
  rec.thumbnail_resolution = duckdb_value_int32(result, 4, 0);
  const char* st = duckdb_value_varchar(result, 5, 0);
  if (st) rec.status = st;
  duckdb_destroy_result(&result);
  return rec;
}

void SemanticStorageController::UpsertLabel(const SemanticLabelRecord& rec) {
  char sql[512];
  std::snprintf(sql, sizeof(sql),
                "INSERT OR REPLACE INTO SemanticImageLabel (file_id, model_key, label, score, "
                "confident) VALUES (%u, %s, %s, %f, %s)",
                rec.file_id,
                escape_sql_string(rec.model_key).c_str(),
                escape_sql_string(rec.label).c_str(),
                rec.score,
                rec.confident ? "TRUE" : "FALSE");
  Exec(sql);
}

auto SemanticStorageController::SelectLabels(const std::string& model_key)
    -> std::vector<SemanticLabelRecord> {
  std::vector<SemanticLabelRecord> out;
  if (!guard_.IsValid()) return out;
  auto lock = guard_.Lock();
  std::string sql =
      "SELECT file_id, model_key, label, score, confident FROM SemanticImageLabel WHERE model_key = " +
      escape_sql_string(model_key);
  duckdb_result result = nullptr;
  if (duckdb_query(guard_.conn_, sql.c_str(), &result) != DuckDBSuccess || !result) {
    if (result) duckdb_destroy_result(&result);
    return out;
  }
  int64_t rows = duckdb_row_count(result);
  for (int64_t r = 0; r < rows; ++r) {
    SemanticLabelRecord rec;
    rec.file_id = static_cast<sl_element_id_t>(duckdb_value_int64(result, 0, r));
    const char* mk = duckdb_value_varchar(result, 1, r);
    if (mk) rec.model_key = mk;
    const char* lb = duckdb_value_varchar(result, 2, r);
    if (lb) rec.label = lb;
    rec.score     = duckdb_value_double(result, 3, r);
    rec.confident = duckdb_value_boolean(result, 4, r);
    out.push_back(std::move(rec));
  }
  duckdb_destroy_result(&result);
  return out;
}

auto SemanticStorageController::SelectLabel(sl_element_id_t file_id,
                                            const std::string& model_key)
    -> std::optional<SemanticLabelRecord> {
  if (!guard_.IsValid()) return std::nullopt;
  auto lock = guard_.Lock();
  std::string sql =
      "SELECT file_id, model_key, label, score, confident FROM SemanticImageLabel "
      "WHERE file_id = " +
      std::to_string(file_id) + " AND model_key = " + escape_sql_string(model_key);
  duckdb_result result = nullptr;
  if (duckdb_query(guard_.conn_, sql.c_str(), &result) != DuckDBSuccess || !result) {
    if (result) duckdb_destroy_result(&result);
    return std::nullopt;
  }
  if (duckdb_row_count(result) == 0) {
    duckdb_destroy_result(&result);
    return std::nullopt;
  }
  SemanticLabelRecord rec;
  rec.file_id = static_cast<sl_element_id_t>(duckdb_value_int64(result, 0, 0));
  const char* mk = duckdb_value_varchar(result, 1, 0);
  if (mk) rec.model_key = mk;
  const char* lb = duckdb_value_varchar(result, 2, 0);
  if (lb) rec.label = lb;
  rec.score     = duckdb_value_double(result, 3, 0);
  rec.confident = duckdb_value_boolean(result, 4, 0);
  duckdb_destroy_result(&result);
  return rec;
}

}  // namespace alcedo
