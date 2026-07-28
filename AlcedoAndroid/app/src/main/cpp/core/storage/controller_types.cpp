// AlcedoAndroid - ConnectionGuard implementation.
// SPDX-License-Identifier: GPL-3.0-only
#include "storage/controller/controller_types.hpp"

#include <utility>

namespace alcedo {

ConnectionGuard::ConnectionGuard(duckdb_connection conn,
                                 std::shared_ptr<std::recursive_mutex> db_lock)
    : conn_(conn), db_lock_(std::move(db_lock)) {}

ConnectionGuard::ConnectionGuard(ConnectionGuard&& other) noexcept
    : conn_(other.conn_), db_lock_(std::move(other.db_lock_)) {
  other.conn_ = nullptr;
}

ConnectionGuard& ConnectionGuard::operator=(ConnectionGuard&& other) noexcept {
  if (this != &other) {
    if (conn_) duckdb_disconnect(&conn_);
    conn_     = other.conn_;
    db_lock_  = std::move(other.db_lock_);
    other.conn_ = nullptr;
  }
  return *this;
}

ConnectionGuard::~ConnectionGuard() {
  if (conn_) duckdb_disconnect(&conn_);
}

auto ConnectionGuard::Lock() const -> std::unique_lock<std::recursive_mutex> {
  if (db_lock_) return std::unique_lock<std::recursive_mutex>(*db_lock_);
  return std::unique_lock<std::recursive_mutex>();
}

}  // namespace alcedo
