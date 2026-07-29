// AlcedoAndroid - DuckDB C API implementation backed by the SQLite amalgamation.
//
// The storage layer speaks the DuckDB C API surface declared in
// duckdb/duckdb_capi.hpp. Rather than cross-compile and ship the full DuckDB
// engine for Android arm64, this file implements that surface on top of the
// vendored SQLite amalgamation (third_party/sqlite). The SQL the mappers emit
// is already SQLite-compatible (INSERT OR REPLACE/IGNORE, TRUE/FALSE literals,
// standard DDL/CRUD), so the shim only has to bridge handles, results and the
// value-accessors. Multi-statement scripts (the schema bootstrap) are executed
// statement-by-statement via sqlite3_prepare_v2's tail pointer, and the result
// reflects the last row-producing statement, matching DuckDB's behaviour.
//
// SQLite is public-domain; see the header inside third_party/sqlite/sqlite3.c.
// SPDX-License-Identifier: GPL-3.0-only
#include "duckdb/duckdb_capi.hpp"

#include <cctype>
#include <cstdlib>
#include <cstring>
#include <string>
#include <vector>

#include "sqlite3.h"

// ---------------------------------------------------------------------------
// Opaque handle definitions (the header only forward-declares the structs).
// ---------------------------------------------------------------------------

struct duckdb_database_s {
  sqlite3*    db   = nullptr;
  std::string path;
};

struct duckdb_connection_s {
  // Connections share the underlying sqlite3* owned by the database handle.
  // Access is serialised by the C++ ConnectionGuard mutex, and the database is
  // opened with SQLITE_OPEN_FULLMUTEX, so sharing is safe.
  sqlite3* db = nullptr;
};

namespace {

struct Cell {
  bool        is_null = false;
  std::string text;
};

}  // namespace

struct duckdb_result_s {
  bool                     had_error = false;
  std::string              error;
  int                      ncol = 0;
  int64_t                  nrow = 0;
  std::vector<std::string> col_names;
  std::vector<std::vector<Cell>> rows;
};

struct duckdb_prepared_statement_s {
  sqlite3*      db   = nullptr;
  sqlite3_stmt* stmt = nullptr;
};

// ---------------------------------------------------------------------------
// Lifecycle: database / connection.
// ---------------------------------------------------------------------------

extern "C" {

duckdb_state duckdb_open(const char* path, duckdb_database* out_db) {
  if (!out_db) return DuckDBError;
  *out_db = nullptr;
  auto* dbh = new duckdb_database_s{};
  dbh->path = path ? path : "";
  const char* target = dbh->path.empty() ? ":memory:" : dbh->path.c_str();
  int flags = SQLITE_OPEN_READWRITE | SQLITE_OPEN_CREATE | SQLITE_OPEN_FULLMUTEX;
  int rc = sqlite3_open_v2(target, &dbh->db, flags, nullptr);
  if (rc != SQLITE_OK) {
    delete dbh;
    return DuckDBError;
  }
  // Normalise: enable foreign-key enforcement and a slightly faster sync mode.
  sqlite3_exec(dbh->db, "PRAGMA foreign_keys=ON;PRAGMA synchronous=NORMAL;",
               nullptr, nullptr, nullptr);
  *out_db = dbh;
  return DuckDBSuccess;
}

duckdb_state duckdb_open_ext(const char* path, duckdb_database* out_db,
                             void* config, char** out_error) {
  (void)config;  // DuckDB config options have no SQLite equivalent; ignored.
  if (out_error) *out_error = nullptr;
  return duckdb_open(path, out_db);
}

void duckdb_close(duckdb_database* db) {
  if (!db || !*db) return;
  if ((*db)->db) sqlite3_close((*db)->db);
  delete *db;
  *db = nullptr;
}

duckdb_state duckdb_connect(duckdb_database db, duckdb_connection* out_con) {
  if (!out_con) return DuckDBError;
  *out_con = nullptr;
  if (!db || !db->db) return DuckDBError;
  auto* con = new duckdb_connection_s{};
  con->db = db->db;
  *out_con = con;
  return DuckDBSuccess;
}

void duckdb_disconnect(duckdb_connection* con) {
  if (!con || !*con) return;
  // The sqlite3* is owned by the database handle; do not close it here.
  delete *con;
  *con = nullptr;
}

// ---------------------------------------------------------------------------
// Config (declared for API completeness; the storage layer only uses
// duckdb_open, so these accept-and-ignore).
// ---------------------------------------------------------------------------

duckdb_state duckdb_create_config(void** out_config) {
  if (!out_config) return DuckDBError;
  *out_config = nullptr;
  return DuckDBSuccess;
}

void duckdb_destroy_config(void** config) {
  if (!config) return;
  *config = nullptr;
}

duckdb_state duckdb_set_config(void* config, const char* name, const char* option) {
  (void)config;
  (void)name;
  (void)option;
  return DuckDBSuccess;
}

// ---------------------------------------------------------------------------
// Result helpers.
// ---------------------------------------------------------------------------

static void CaptureRow(sqlite3_stmt* stmt, int ncol, duckdb_result_s* res) {
  std::vector<Cell> row(ncol);
  for (int c = 0; c < ncol; ++c) {
    if (sqlite3_column_type(stmt, c) == SQLITE_NULL) {
      row[c].is_null = true;
      continue;
    }
    const unsigned char* txt = sqlite3_column_text(stmt, c);
    int len = sqlite3_column_bytes(stmt, c);
    row[c].text.assign(reinterpret_cast<const char*>(txt), len > 0 ? len : 0);
  }
  res->rows.push_back(std::move(row));
}

static void BeginRowset(sqlite3_stmt* stmt, duckdb_result_s* res) {
  int ncol = sqlite3_column_count(stmt);
  res->rows.clear();
  res->col_names.clear();
  res->ncol = ncol;
  res->nrow = 0;
  for (int c = 0; c < ncol; ++c) {
    const char* nm = sqlite3_column_name(stmt, c);
    res->col_names.emplace_back(nm ? nm : "");
  }
}

// ---------------------------------------------------------------------------
// Query execution.
// ---------------------------------------------------------------------------

duckdb_state duckdb_query(duckdb_connection con, const char* sql, duckdb_result* out_result) {
  if (out_result) *out_result = nullptr;
  if (!con || !con->db || !sql) return DuckDBError;

  auto* res = new duckdb_result_s{};
  const char* tail = sql;
  while (tail && *tail) {
    // Skip whitespace and stray semicolons between statements.
    while (*tail && (std::isspace(static_cast<unsigned char>(*tail)) || *tail == ';')) ++tail;
    if (!*tail) break;

    sqlite3_stmt* stmt = nullptr;
    int rc = sqlite3_prepare_v2(con->db, tail, -1, &stmt, &tail);
    if (rc != SQLITE_OK) {
      res->had_error = true;
      res->error = sqlite3_errmsg(con->db);
      sqlite3_finalize(stmt);
      if (out_result) *out_result = res; else delete res;
      return DuckDBError;
    }
    if (!stmt) break;  // trailing whitespace / comment only

    int ncol = sqlite3_column_count(stmt);
    if (ncol > 0) BeginRowset(stmt, res);

    for (;;) {
      rc = sqlite3_step(stmt);
      if (rc == SQLITE_ROW) {
        if (ncol > 0) CaptureRow(stmt, ncol, res);
      } else if (rc == SQLITE_DONE) {
        break;
      } else {
        res->had_error = true;
        res->error = sqlite3_errmsg(con->db);
        sqlite3_finalize(stmt);
        if (out_result) *out_result = res; else delete res;
        return DuckDBError;
      }
    }
    sqlite3_finalize(stmt);
  }

  res->nrow = static_cast<int64_t>(res->rows.size());
  if (out_result) *out_result = res; else delete res;
  return DuckDBSuccess;
}

void duckdb_destroy_result(duckdb_result* result) {
  if (!result || !*result) return;
  delete *result;
  *result = nullptr;
}

const char* duckdb_result_error(duckdb_result result) {
  if (!result || !result->had_error) return nullptr;
  return result->error.c_str();
}

// ---------------------------------------------------------------------------
// Result metadata + value accessors.
//
// value_varchar returns a pointer into the result's materialised string
// storage; it stays valid until duckdb_destroy_result is called, matching the
// DuckDB contract that callers rely on.
// ---------------------------------------------------------------------------

int64_t duckdb_row_count(duckdb_result result) {
  return result ? result->nrow : 0;
}

int64_t duckdb_column_count(duckdb_result result) {
  return result ? result->ncol : 0;
}

const char* duckdb_column_name(duckdb_result result, uint64_t col) {
  if (!result || col >= result->col_names.size()) return "";
  return result->col_names[col].c_str();
}

duckdb_type duckdb_column_type(duckdb_result result, uint64_t col) {
  if (!result || col >= static_cast<uint64_t>(result->ncol)) return DUCKDB_TYPE_INVALID;
  if (result->rows.empty()) return DUCKDB_TYPE_INVALID;
  const std::string& name = result->col_names[col];
  if (name.find("id") != std::string::npos) return DUCKDB_TYPE_BIGINT;
  if (name.find("count") != std::string::npos || name.find("rating") != std::string::npos ||
      name.find("dim") != std::string::npos || name.find("size") != std::string::npos)
    return DUCKDB_TYPE_INTEGER;
  if (name.find("score") != std::string::npos || name.find("confidence") != std::string::npos)
    return DUCKDB_TYPE_DOUBLE;
  if (name == "active" || name == "confident") return DUCKDB_TYPE_BOOLEAN;
  return DUCKDB_TYPE_VARCHAR;
}

static const Cell* CellAt(duckdb_result result, uint64_t col, uint64_t row) {
  if (!result || row >= result->rows.size() || col >= result->rows[row].size()) return nullptr;
  return &result->rows[row][col];
}

void* duckdb_value_void(duckdb_result result, uint64_t col, uint64_t row) {
  const Cell* c = CellAt(result, col, row);
  if (!c || c->is_null) return nullptr;
  return const_cast<char*>(c->text.c_str());
}

bool duckdb_value_boolean(duckdb_result result, uint64_t col, uint64_t row) {
  const Cell* c = CellAt(result, col, row);
  if (!c || c->is_null) return false;
  // SQLite stores booleans as 0/1 integers; tolerate textual forms too.
  if (c->text == "1" || c->text == "true" || c->text == "TRUE") return true;
  return false;
}

int32_t duckdb_value_int32(duckdb_result result, uint64_t col, uint64_t row) {
  const Cell* c = CellAt(result, col, row);
  if (!c || c->is_null) return 0;
  return static_cast<int32_t>(std::strtol(c->text.c_str(), nullptr, 10));
}

int64_t duckdb_value_int64(duckdb_result result, uint64_t col, uint64_t row) {
  const Cell* c = CellAt(result, col, row);
  if (!c || c->is_null) return 0;
  return std::strtoll(c->text.c_str(), nullptr, 10);
}

double duckdb_value_double(duckdb_result result, uint64_t col, uint64_t row) {
  const Cell* c = CellAt(result, col, row);
  if (!c || c->is_null) return 0.0;
  return std::strtod(c->text.c_str(), nullptr);
}

const char* duckdb_value_varchar(duckdb_result result, uint64_t col, uint64_t row) {
  const Cell* c = CellAt(result, col, row);
  if (!c || c->is_null) return nullptr;
  return c->text.c_str();
}

// ---------------------------------------------------------------------------
// Prepared statements (declared in the header; kept functional for parity).
// DuckDB bind indices are 1-based, matching sqlite3_bind_*.
// ---------------------------------------------------------------------------

duckdb_state duckdb_prepare(duckdb_connection con, const char* sql,
                            duckdb_prepared_statement* out_stmt) {
  if (!out_stmt) return DuckDBError;
  *out_stmt = nullptr;
  if (!con || !con->db || !sql) return DuckDBError;
  auto* ps = new duckdb_prepared_statement_s{};
  ps->db = con->db;
  if (sqlite3_prepare_v2(con->db, sql, -1, &ps->stmt, nullptr) != SQLITE_OK) {
    delete ps;
    return DuckDBError;
  }
  *out_stmt = ps;
  return DuckDBSuccess;
}

duckdb_state duckdb_execute_prepared(duckdb_prepared_statement stmt, duckdb_result* out_result) {
  if (out_result) *out_result = nullptr;
  if (!stmt || !stmt->stmt) return DuckDBError;
  auto* res = new duckdb_result_s{};
  int ncol = sqlite3_column_count(stmt->stmt);
  if (ncol > 0) {
    BeginRowset(stmt->stmt, res);
  }
  for (;;) {
    int rc = sqlite3_step(stmt->stmt);
    if (rc == SQLITE_ROW) {
      if (ncol > 0) CaptureRow(stmt->stmt, ncol, res);
    } else if (rc == SQLITE_DONE) {
      break;
    } else {
      res->had_error = true;
      res->error = sqlite3_errmsg(stmt->db);
      if (out_result) *out_result = res; else delete res;
      return DuckDBError;
    }
  }
  res->nrow = static_cast<int64_t>(res->rows.size());
  if (out_result) *out_result = res; else delete res;
  return DuckDBSuccess;
}

void duckdb_destroy_prepare(duckdb_prepared_statement* stmt) {
  if (!stmt || !*stmt) return;
  if ((*stmt)->stmt) sqlite3_finalize((*stmt)->stmt);
  delete *stmt;
  *stmt = nullptr;
}

duckdb_state duckdb_bind_boolean(duckdb_prepared_statement stmt, uint64_t idx, bool val) {
  if (!stmt || !stmt->stmt) return DuckDBError;
  return sqlite3_bind_int(stmt->stmt, idx, val ? 1 : 0) == SQLITE_OK ? DuckDBSuccess : DuckDBError;
}

duckdb_state duckdb_bind_int32(duckdb_prepared_statement stmt, uint64_t idx, int32_t val) {
  if (!stmt || !stmt->stmt) return DuckDBError;
  return sqlite3_bind_int(stmt->stmt, idx, val) == SQLITE_OK ? DuckDBSuccess : DuckDBError;
}

duckdb_state duckdb_bind_int64(duckdb_prepared_statement stmt, uint64_t idx, int64_t val) {
  if (!stmt || !stmt->stmt) return DuckDBError;
  return sqlite3_bind_int64(stmt->stmt, idx, val) == SQLITE_OK ? DuckDBSuccess : DuckDBError;
}

duckdb_state duckdb_bind_double(duckdb_prepared_statement stmt, uint64_t idx, double val) {
  if (!stmt || !stmt->stmt) return DuckDBError;
  return sqlite3_bind_double(stmt->stmt, idx, val) == SQLITE_OK ? DuckDBSuccess : DuckDBError;
}

duckdb_state duckdb_bind_varchar(duckdb_prepared_statement stmt, uint64_t idx, const char* val) {
  if (!stmt || !stmt->stmt) return DuckDBError;
  return sqlite3_bind_text(stmt->stmt, idx, val ? val : "", -1, SQLITE_TRANSIENT) == SQLITE_OK
             ? DuckDBSuccess
             : DuckDBError;
}

duckdb_state duckdb_bind_blob(duckdb_prepared_statement stmt, uint64_t idx,
                              const void* data, uint64_t len) {
  if (!stmt || !stmt->stmt) return DuckDBError;
  return sqlite3_bind_blob(stmt->stmt, idx, data, static_cast<int>(len), SQLITE_TRANSIENT) == SQLITE_OK
             ? DuckDBSuccess
             : DuckDBError;
}

}  // extern "C"
