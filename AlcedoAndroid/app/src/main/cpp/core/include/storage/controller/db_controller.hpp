// AlcedoAndroid - DBController (DuckDB connection manager).
// Owns the DuckDB database instance, hands out ConnectionGuard objects and
// initialises the schema on first open. Self-contained Android port.
// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <filesystem>
#include <memory>
#include <mutex>
#include <string>

#include "controller_types.hpp"
#include "duckdb/duckdb_capi.hpp"
#include "type/type.hpp"

namespace alcedo {

class DBController {
 public:
  explicit DBController(file_path_t db_path);
  ~DBController();

  DBController(const DBController&)            = delete;
  DBController& operator=(const DBController&) = delete;

  void InitializeDB();
  auto GetConnectionGuard() -> ConnectionGuard;
  auto IsInitialized() const -> bool { return initialized_; }
  auto GetDBPath() const -> const file_path_t& { return db_path_; }

 private:
  duckdb_database                       db_            = nullptr;
  std::shared_ptr<std::recursive_mutex> db_lock_;
  file_path_t                           db_path_;
  bool                                  initialized_   = false;

  void RunSchemaInit(duckdb_connection conn);
};

}  // namespace alcedo
