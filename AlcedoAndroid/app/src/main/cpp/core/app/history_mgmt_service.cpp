// AlcedoAndroid - HistoryMgmtService implementation.
// Manages edit history retrieval and version operations per sleeve file.
// SPDX-License-Identifier: GPL-3.0-only
#include "app/app_services.hpp"

#include "edit/history/version.hpp"

namespace alcedo {

HistoryMgmtService::HistoryMgmtService(SleeveManager& sleeve) : sleeve_(sleeve) {}

auto HistoryMgmtService::Undo(sl_element_id_t file_id) -> bool {
  // Undo requires a WorkingVersion + PipelineExecutor context (managed by the
  // pipeline/renderer layer). This service exposes history state; the actual
  // undo/redo cursor movement is driven by PipelineAppService.
  auto history = GetHistory(file_id);
  if (!history) return false;
  auto& version = history->GetActiveVersion();
  return version.GetCursor() > 0;
}

auto HistoryMgmtService::Redo(sl_element_id_t file_id) -> bool {
  auto history = GetHistory(file_id);
  if (!history) return false;
  auto& version = history->GetActiveVersion();
  return version.GetCursor() < version.GetTransactionCount();
}

auto HistoryMgmtService::GetHistory(sl_element_id_t file_id) -> std::shared_ptr<EditHistory> {
  auto file = sleeve_.GetFile(file_id);
  if (!file) return nullptr;
  auto history = file->GetEditHistory();
  if (!history) {
    history = std::make_shared<EditHistory>(file_id);
    file->SetEditHistory(history);
  }
  return history;
}

auto HistoryMgmtService::GetVersionCount(sl_element_id_t file_id) -> size_t {
  auto history = GetHistory(file_id);
  if (!history) return 0;
  return history->GetVersions().size();
}

}  // namespace alcedo
