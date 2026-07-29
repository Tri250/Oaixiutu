// AlcedoAndroid - DBController implementation.
// Owns the DuckDB instance and initialises the project schema.
// SPDX-License-Identifier: GPL-3.0-only
#include "storage/controller/db_controller.hpp"

#include <cstring>
#include <utility>

#include "utils/app_logging.hpp"

namespace alcedo {

namespace {

constexpr const char* kSchemaInit =
    "CREATE TABLE IF NOT EXISTS Sleeve (id BIGINT PRIMARY KEY);"
    "CREATE TABLE IF NOT EXISTS Image (id BIGINT PRIMARY KEY, image_path TEXT, "
    "file_name TEXT, type INTEGER, metadata JSON);"
    "CREATE TABLE IF NOT EXISTS SleeveRoot (id BIGINT PRIMARY KEY);"
    "CREATE TABLE IF NOT EXISTS Element (id BIGINT PRIMARY KEY, type INTEGER, "
    "element_name TEXT, added_time TIMESTAMP, modified_time TIMESTAMP, ref_count BIGINT);"
    "CREATE TABLE IF NOT EXISTS FolderContent (folder_id BIGINT NOT NULL, "
    "element_id BIGINT NOT NULL, PRIMARY KEY(folder_id, element_id));"
    "CREATE INDEX IF NOT EXISTS idx_folder_content_folder ON FolderContent(folder_id);"
    "CREATE INDEX IF NOT EXISTS idx_folder_content_element ON FolderContent(element_id);"
    "CREATE TABLE IF NOT EXISTS FileImage (file_id BIGINT, image_id BIGINT);"
    "CREATE TABLE IF NOT EXISTS ComboFolder (combo_id BIGINT, folder_id BIGINT);"
    "CREATE TABLE IF NOT EXISTS Filter (combo_id BIGINT, type INTEGER, data JSON);"
    "CREATE TABLE IF NOT EXISTS EditHistory (file_id BIGINT PRIMARY KEY, history JSON);"
    "CREATE TABLE IF NOT EXISTS Version (hash BIGINT PRIMARY KEY, history_id BIGINT, "
    "parent_hash BIGINT, content JSON);"
    "CREATE TABLE IF NOT EXISTS PipelineParam (file_id BIGINT PRIMARY KEY, param_json JSON);"
    "CREATE TABLE IF NOT EXISTS SemanticModel ("
    "model_key VARCHAR PRIMARY KEY, model_id VARCHAR NOT NULL, revision VARCHAR NOT NULL, "
    "embedding_dim INTEGER NOT NULL, image_size INTEGER NOT NULL, active BOOLEAN NOT NULL DEFAULT FALSE, "
    "created_at TIMESTAMP DEFAULT current_timestamp);"
    "CREATE TABLE IF NOT EXISTS SemanticImageEmbedding ("
    "file_id BIGINT NOT NULL, image_id BIGINT NOT NULL, model_key VARCHAR NOT NULL, "
    "embedding TEXT NOT NULL, embedding_dim INTEGER NOT NULL, thumbnail_resolution INTEGER NOT NULL, "
    "generated_at TIMESTAMP DEFAULT current_timestamp, status VARCHAR NOT NULL, error VARCHAR, "
    "PRIMARY KEY(file_id, model_key));"
    "CREATE TABLE IF NOT EXISTS SemanticImageLabel ("
    "file_id BIGINT NOT NULL, model_key VARCHAR NOT NULL, label VARCHAR NOT NULL, "
    "score DOUBLE NOT NULL, second_label VARCHAR, second_score DOUBLE, margin DOUBLE, "
    "confident BOOLEAN NOT NULL, top_scores JSON, updated_at TIMESTAMP DEFAULT current_timestamp, "
    "PRIMARY KEY(file_id, model_key));"
    "CREATE TABLE IF NOT EXISTS AiImageUnderstanding ("
    "file_id BIGINT NOT NULL, task_id VARCHAR NOT NULL DEFAULT '', "
    "provider_id VARCHAR NOT NULL DEFAULT '', model_id VARCHAR NOT NULL DEFAULT '', "
    "prompt_profile_id VARCHAR NOT NULL DEFAULT '', rendition_kind VARCHAR NOT NULL DEFAULT '', "
    "caption VARCHAR NOT NULL DEFAULT '', tags_json VARCHAR NOT NULL DEFAULT '', "
    "scene VARCHAR NOT NULL DEFAULT '', confidence DOUBLE NOT NULL DEFAULT 0.0, "
    "active BOOLEAN NOT NULL DEFAULT TRUE, updated_at TIMESTAMP DEFAULT current_timestamp, "
    "PRIMARY KEY (file_id, task_id));"
    "CREATE TABLE IF NOT EXISTS AiImageFtsDocument ("
    "file_id BIGINT PRIMARY KEY, body VARCHAR NOT NULL DEFAULT '', "
    "updated_at TIMESTAMP DEFAULT current_timestamp);"
    "CREATE TABLE IF NOT EXISTS AiImageRating ("
    "file_id BIGINT NOT NULL, task_id VARCHAR NOT NULL DEFAULT '', "
    "provider_id VARCHAR NOT NULL DEFAULT '', model_id VARCHAR NOT NULL DEFAULT '', "
    "prompt_profile_id VARCHAR NOT NULL DEFAULT '', rendition_kind VARCHAR NOT NULL DEFAULT '', "
    "rating INTEGER NOT NULL DEFAULT 0, rubric_id VARCHAR NOT NULL DEFAULT '', "
    "rubric_version VARCHAR NOT NULL DEFAULT '', reasons VARCHAR NOT NULL DEFAULT '', "
    "active BOOLEAN NOT NULL DEFAULT TRUE, updated_at TIMESTAMP DEFAULT current_timestamp, "
    "PRIMARY KEY (file_id, task_id));";

}  // namespace

DBController::DBController(file_path_t db_path)
    : db_lock_(std::make_shared<std::recursive_mutex>()), db_path_(std::move(db_path)) {}

DBController::~DBController() {
  if (db_) duckdb_close(&db_);
}

auto DBController::RunSchemaInit(duckdb_connection conn) -> bool {
  duckdb_result result = nullptr;
  auto state = duckdb_query(conn, kSchemaInit, &result);
  if (state != DuckDBSuccess) {
    const char* err = result ? duckdb_result_error(result) : "unknown";
    ALOGE("DBController: schema init failed: %s", err ? err : "unknown");
    if (result) duckdb_destroy_result(&result);
    return false;
  }
  if (result) duckdb_destroy_result(&result);
  return true;
}

void DBController::InitializeDB() {
  if (initialized_ || in_error_state_) return;
  std::string path_str = db_path_.string();
  auto state = duckdb_open(path_str.c_str(), &db_);
  if (state != DuckDBSuccess) {
    ALOGE("DBController: duckdb_open failed for %s", path_str.c_str());
    in_error_state_ = true;
    return;
  }
  // Create a temporary connection for schema init.
  duckdb_connection conn = nullptr;
  state = duckdb_connect(db_, &conn);
  if (state != DuckDBSuccess || !conn) {
    ALOGE("DBController: duckdb_connect failed");
    duckdb_close(&db_);
    db_ = nullptr;
    in_error_state_ = true;
    return;
  }
  bool schema_ok = false;
  {
    std::unique_lock<std::recursive_mutex> lk(*db_lock_);
    schema_ok = RunSchemaInit(conn);
  }
  duckdb_disconnect(&conn);
  if (!schema_ok) {
    // Schema init failed: tear down the database handle and mark the controller
    // as in error state so GetConnectionGuard() refuses to hand out
    // connections to a half-initialised database instead of silently
    // proceeding with missing tables.
    ALOGE("DBController: aborting initialisation due to schema init failure");
    duckdb_close(&db_);
    db_ = nullptr;
    in_error_state_ = true;
    return;
  }
  initialized_ = true;
  ALOGI("DBController: initialised db at %s", path_str.c_str());
}

auto DBController::GetConnectionGuard() -> ConnectionGuard {
  if (in_error_state_) {
    // Refuse to hand out connections once the database is in an unrecoverable
    // error state (e.g. schema init failed).
    return ConnectionGuard();
  }
  if (!initialized_) InitializeDB();
  if (in_error_state_) return ConnectionGuard();
  duckdb_connection conn = nullptr;
  if (db_) duckdb_connect(db_, &conn);
  return ConnectionGuard(conn, db_lock_);
}

}  // namespace alcedo
