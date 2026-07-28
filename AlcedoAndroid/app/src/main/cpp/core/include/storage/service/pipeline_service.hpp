// AlcedoAndroid - PipelineService (pipeline parameter persistence).
// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <optional>
#include <string>

#include "storage/controller/controller_types.hpp"
#include "storage/mapper/pipeline_mapper.hpp"
#include "type/type.hpp"

namespace alcedo {

class PipelineService {
 public:
  explicit PipelineService(ConnectionGuard& guard);
  void SaveParams(sl_element_id_t file_id, const std::string& param_json);
  auto LoadParams(sl_element_id_t file_id) -> std::optional<std::string>;
  void RemoveParams(sl_element_id_t file_id);

 private:
  PipelineMapper mapper_;
};

}  // namespace alcedo
