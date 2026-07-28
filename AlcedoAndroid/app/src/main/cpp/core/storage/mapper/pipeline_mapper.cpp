// AlcedoAndroid - PipelineMapper implementation.
// Persists pipeline parameter JSON per sleeve file id.
// SPDX-License-Identifier: GPL-3.0-only
#include "storage/mapper/pipeline_mapper.hpp"

#include <cstdio>
#include <cstring>

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

auto PipelineMapper::Exec(const std::string& sql) -> bool {
  if (!guard_.IsValid()) return false;
  auto lock = guard_.Lock();
  duckdb_result result = nullptr;
  auto state = duckdb_query(guard_.conn_, sql.c_str(), &result);
  if (state != DuckDBSuccess) {
    const char* err = result ? duckdb_result_error(result) : "unknown";
    ALOGE("PipelineMapper: query failed: %s", err ? err : "unknown");
    if (result) duckdb_destroy_result(&result);
    return false;
  }
  if (result) duckdb_destroy_result(&result);
  return true;
}

void PipelineMapper::Upsert(sl_element_id_t file_id, const std::string& param_json) {
  std::string sql = "INSERT OR REPLACE INTO PipelineParam (file_id, param_json) VALUES (" +
                    std::to_string(file_id) + ", " + escape_sql_string(param_json) + ")";
  Exec(sql);
}

auto PipelineMapper::Select(sl_element_id_t file_id) -> std::optional<std::string> {
  if (!guard_.IsValid()) return std::nullopt;
  auto lock = guard_.Lock();
  char sql[128];
  std::snprintf(sql, sizeof(sql),
                "SELECT param_json FROM PipelineParam WHERE file_id = %u", file_id);
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

void PipelineMapper::Remove(sl_element_id_t file_id) {
  char sql[128];
  std::snprintf(sql, sizeof(sql), "DELETE FROM PipelineParam WHERE file_id = %u", file_id);
  Exec(sql);
}

}  // namespace alcedo
