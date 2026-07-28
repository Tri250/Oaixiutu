// AlcedoAndroid - Storage controller types (ConnectionGuard).
// Self-contained Android port using the DuckDB C API forward declarations.
// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <memory>
#include <mutex>

#include "duckdb/duckdb_capi.hpp"

namespace alcedo {

// RAII guard around a DuckDB connection + recursive DB lock.
class ConnectionGuard {
 public:
  duckdb_connection                     conn_   = nullptr;
  std::shared_ptr<std::recursive_mutex> db_lock_;

  ConnectionGuard() = default;
  ConnectionGuard(duckdb_connection conn, std::shared_ptr<std::recursive_mutex> db_lock = {});
  ConnectionGuard(ConnectionGuard&& other) noexcept;
  ConnectionGuard& operator=(ConnectionGuard&& other) noexcept;
  ConnectionGuard(const ConnectionGuard&)            = delete;
  ConnectionGuard& operator=(const ConnectionGuard&) = delete;
  ~ConnectionGuard();

  [[nodiscard]] auto Lock() const -> std::unique_lock<std::recursive_mutex>;
  [[nodiscard]] auto IsValid() const -> bool { return conn_ != nullptr; }
};

}  // namespace alcedo
