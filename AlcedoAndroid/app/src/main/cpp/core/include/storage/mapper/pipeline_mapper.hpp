// AlcedoAndroid - PipelineMapper (pipeline params <-> DuckDB rows).
// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <memory>
#include <string>
#include <optional>

#include "duckdb/duckdb_capi.hpp"
#include "storage/controller/controller_types.hpp"
#include "type/type.hpp"

namespace alcedo {

class PipelineMapper {
 public:
  explicit PipelineMapper(ConnectionGuard& guard) : guard_(guard) {}

  void Upsert(sl_element_id_t file_id, const std::string& param_json);
  auto Select(sl_element_id_t file_id) -> std::optional<std::string>;
  void Remove(sl_element_id_t file_id);

 private:
  ConnectionGuard& guard_;
  auto Exec(const std::string& sql) -> bool;
};

}  // namespace alcedo
