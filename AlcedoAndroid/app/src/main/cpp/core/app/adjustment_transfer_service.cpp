// AlcedoAndroid - AdjustmentTransferService implementation.
// Copies edit settings (pipeline params / history) between images.
// SPDX-License-Identifier: GPL-3.0-only
#include "app/app_services.hpp"

#include "edit/pipeline/pipeline.hpp"
#include "utils/app_logging.hpp"

namespace alcedo {

AdjustmentTransferService::AdjustmentTransferService(SleeveManager& sleeve) : sleeve_(sleeve) {}

auto AdjustmentTransferService::CopyAdjustments(sl_element_id_t src_file_id,
                                                sl_element_id_t dest_file_id) -> bool {
  auto src = sleeve_.GetFile(src_file_id);
  auto dst = sleeve_.GetFile(dest_file_id);
  if (!src || !dst) return false;

  // Copy the edit history if present.
  auto src_history = src->GetEditHistory();
  if (src_history) {
    dst->SetEditHistory(src_history->CloneForFile(dest_file_id));
    return true;
  }
  // Fallback: copy pipeline params via the executor's export/import.
  auto executor = CreatePipelineExecutor();
  if (!executor) return false;
  executor->SetBoundFile(src_file_id);
  auto params = executor->ExportPipelineParams();
  auto dst_executor = CreatePipelineExecutor();
  dst_executor->SetBoundFile(dest_file_id);
  dst_executor->ImportPipelineParams(params);
  return true;
}

auto AdjustmentTransferService::CopyAdjustmentsBatch(
    sl_element_id_t src_file_id, const std::vector<sl_element_id_t>& dest_file_ids) -> size_t {
  size_t count = 0;
  for (auto dest_id : dest_file_ids) {
    if (CopyAdjustments(src_file_id, dest_id)) ++count;
  }
  return count;
}

}  // namespace alcedo
