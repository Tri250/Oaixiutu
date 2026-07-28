// AlcedoAndroid - DuckDB C API forward declarations.
// The actual DuckDB library is linked by the build system; here we only
// declare the opaque handles and the subset of the C API used by the storage
// layer so the native core compiles without bundling DuckDB headers.
// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <cstdint>
#include <cstddef>

extern "C" {

typedef struct duckdb_database* duckdb_database;
typedef struct duckdb_connection* duckdb_connection;
typedef struct duckdb_result* duckdb_result;
typedef struct duckdb_prepared_statement* duckdb_prepared_statement;

enum duckdb_state { DuckDBSuccess = 0, DuckDBError = 1 };

typedef enum duckdb_type {
  DUCKDB_TYPE_INVALID = 0,
  DUCKDB_TYPE_BOOLEAN = 1,
  DUCKDB_TYPE_TINYINT = 2,
  DUCKDB_TYPE_SMALLINT = 3,
  DUCKDB_TYPE_INTEGER = 4,
  DUCKDB_TYPE_BIGINT = 5,
  DUCKDB_TYPE_FLOAT = 10,
  DUCKDB_TYPE_DOUBLE = 11,
  DUCKDB_TYPE_VARCHAR = 16,
  DUCKDB_TYPE_BLOB = 18,
  DUCKDB_TYPE_TIMESTAMP = 19,
  DUCKDB_TYPE_JSON = 36
} duckdb_type;

duckdb_state duckdb_open(const char* path, duckdb_database* out_db);
duckdb_state duckdb_open_ext(const char* path, duckdb_database* out_db, void* config, char** out_error);
void         duckdb_close(duckdb_database* db);
duckdb_state duckdb_connect(duckdb_database db, duckdb_connection* out_con);
void         duckdb_disconnect(duckdb_connection* con);
duckdb_state duckdb_query(duckdb_connection con, const char* query, duckdb_result* out_result);
void         duckdb_destroy_result(duckdb_result* result);
const char*  duckdb_result_error(duckdb_result result);
duckdb_state duckdb_prepare(duckdb_connection con, const char* query, duckdb_prepared_statement* out_stmt);
duckdb_state duckdb_execute_prepared(duckdb_prepared_statement stmt, duckdb_result* out_result);
void         duckdb_destroy_prepare(duckdb_prepared_statement* stmt);
duckdb_state duckdb_bind_boolean(duckdb_prepared_statement stmt, uint64_t idx, bool val);
duckdb_state duckdb_bind_int32(duckdb_prepared_statement stmt, uint64_t idx, int32_t val);
duckdb_state duckdb_bind_int64(duckdb_prepared_statement stmt, uint64_t idx, int64_t val);
duckdb_state duckdb_bind_double(duckdb_prepared_statement stmt, uint64_t idx, double val);
duckdb_state duckdb_bind_varchar(duckdb_prepared_statement stmt, uint64_t idx, const char* val);
duckdb_state duckdb_bind_blob(duckdb_prepared_statement stmt, uint64_t idx, const void* data, uint64_t len);
int64_t      duckdb_row_count(duckdb_result result);
int64_t      duckdb_column_count(duckdb_result result);
const char*  duckdb_column_name(duckdb_result result, uint64_t col);
duckdb_type  duckdb_column_type(duckdb_result result, uint64_t col);
void*        duckdb_value_void(duckdb_result result, uint64_t col, uint64_t row);
bool         duckdb_value_boolean(duckdb_result result, uint64_t col, uint64_t row);
int32_t      duckdb_value_int32(duckdb_result result, uint64_t col, uint64_t row);
int64_t      duckdb_value_int64(duckdb_result result, uint64_t col, uint64_t row);
double       duckdb_value_double(duckdb_result result, uint64_t col, uint64_t row);
const char*  duckdb_value_varchar(duckdb_result result, uint64_t col, uint64_t row);

duckdb_state duckdb_create_config(void** out_config);
void         duckdb_destroy_config(void** config);
duckdb_state duckdb_set_config(void* config, const char* name, const char* option);

}  // extern "C"
