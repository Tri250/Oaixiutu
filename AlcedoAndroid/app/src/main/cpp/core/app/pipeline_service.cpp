// AlcedoAndroid - PipelineAppService implementation.
// SPDX-License-Identifier: GPL-3.0-only
#include "app/app_services.hpp"

#include "edit/pipeline/pipeline.hpp"
#include "utils/app_logging.hpp"

namespace alcedo {

PipelineAppService::PipelineAppService() = default;

auto PipelineAppService::GetExecutor() -> std::shared_ptr<PipelineExecutor> {
  // Lazily create the long-lived executor so that parameter import/export and
  // render-region mutations target a persistent object rather than a throwaway.
  // Callers (JNI entry points) hold the JniAppContext mutex, so this lazy init
  // is race-free in practice; CreatePipelineExecutor is itself idempotent.
  if (!executor_) {
    executor_ = CreatePipelineExecutor();
    if (!executor_) {
      ALOGE("PipelineAppService: failed to create executor");
    }
  }
  return executor_;
}

auto PipelineAppService::Execute(const std::shared_ptr<Image>& image, const std::string& param_json)
    -> std::shared_ptr<Image> {
  if (!image) return nullptr;
  auto executor = GetExecutor();
  if (!executor) {
    ALOGE("PipelineAppService: no executor available");
    return image;
  }
  nlohmann::json params = nlohmann::json::parse(param_json, nullptr, false);
  if (!params.is_discarded()) {
    executor->ImportPipelineParams(params);
  }
  // Apply the pipeline to the image's working buffer.
  auto input = std::make_shared<ImageBuffer>(image->GetImageData());
  auto output = executor->Apply(input);
  if (output) {
    image->LoadOriginalData(std::move(*output));
  }
  return image;
}

auto PipelineAppService::Undo(EditHistory& history) -> bool {
  auto& version = history.GetActiveVersion();
  if (version.GetCursor() == 0) return false;
  auto executor = GetExecutor();
  if (!executor) {
    ALOGE("PipelineAppService::Undo: no executor available");
    return false;
  }
  // Rebuild a WorkingVersion from the persisted version's transactions + the
  // import baseline, drive one undo through the executor, then write the new
  // cursor + materialized params back to the persisted version.
  WorkingVersion wv(version.GetBoundImage(), version.GetVersionID(),
                     history.GetImportPipelineParams(),
                     version.GetAllEditTransactions(),
                     version.GetCursor());
  if (!wv.UndoLastTransaction(*executor)) return false;
  nlohmann::json params = executor->ExportPipelineParams();
  version.UpdateFromWorkingVersion(wv, params);
  return true;
}

auto PipelineAppService::Redo(EditHistory& history) -> bool {
  auto& version = history.GetActiveVersion();
  if (version.GetCursor() >= version.GetTransactionCount()) return false;
  auto executor = GetExecutor();
  if (!executor) {
    ALOGE("PipelineAppService::Redo: no executor available");
    return false;
  }
  WorkingVersion wv(version.GetBoundImage(), version.GetVersionID(),
                     history.GetImportPipelineParams(),
                     version.GetAllEditTransactions(),
                     version.GetCursor());
  if (!wv.RedoNextTransaction(*executor)) return false;
  nlohmann::json params = executor->ExportPipelineParams();
  version.UpdateFromWorkingVersion(wv, params);
  return true;
}

}  // namespace alcedo
