// AlcedoAndroid - PipelineService implementation.
// SPDX-License-Identifier: GPL-3.0-only
#include "storage/service/pipeline_service.hpp"

namespace alcedo {

PipelineService::PipelineService(ConnectionGuard& guard) : mapper_(guard) {}

void PipelineService::SaveParams(sl_element_id_t file_id, const std::string& param_json) {
  mapper_.Upsert(file_id, param_json);
}

auto PipelineService::LoadParams(sl_element_id_t file_id) -> std::optional<std::string> {
  return mapper_.Select(file_id);
}

void PipelineService::RemoveParams(sl_element_id_t file_id) { mapper_.Remove(file_id); }

}  // namespace alcedo
