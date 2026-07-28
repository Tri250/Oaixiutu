// AlcedoAndroid - StorageService (sleeve-side facade) implementation.
// SPDX-License-Identifier: GPL-3.0-only
#include "storage_service.hpp"

#include <utility>

#include "utils/app_logging.hpp"

namespace alcedo {

// ---- NodeStorageHandler ----

NodeStorageHandler::NodeStorageHandler(
    std::unordered_map<sl_element_id_t, std::shared_ptr<SleeveElement>>& storage)
    : storage_(storage) {}

void NodeStorageHandler::AddToStorage(std::shared_ptr<SleeveElement> new_element) {
  if (!new_element) return;
  storage_[new_element->element_id_] = std::move(new_element);
}

void NodeStorageHandler::EnsureChildrenLoaded(std::shared_ptr<SleeveFolder> folder) {
  if (!folder || folder->ChildrenLoaded()) return;
  // On Android the full element set is loaded eagerly into the storage map at
  // project-open time (see StorageService::LoadAll). Children are therefore
  // already present; we just mark the folder as loaded.
  folder->MarkChildrenLoaded(true);
}

auto NodeStorageHandler::GetElement(sl_element_id_t id) -> std::shared_ptr<SleeveElement> {
  auto it = storage_.find(id);
  if (it == storage_.end()) return nullptr;
  return it->second;
}

void NodeStorageHandler::GarbageCollect() {
  for (auto it = storage_.begin(); it != storage_.end();) {
    auto& elem = it->second;
    if (!elem) {
      it = storage_.erase(it);
      continue;
    }
    if (elem->sync_flag_ == SyncFlag::DELETED && elem.use_count() == 1) {
      it = storage_.erase(it);
    } else {
      ++it;
    }
  }
}

// ---- StorageService ----

StorageService::StorageService(std::filesystem::path db_path)
    : db_path_(std::move(db_path)), node_handler_(storage_) {
  ALOGI("StorageService initialised with db=%s", db_path_.c_str());
}

StorageService::~StorageService() = default;

void StorageService::RememberLiveEditHistory(sl_element_id_t file_id,
                                             const std::shared_ptr<EditHistory>& history) {
  std::lock_guard<std::mutex> lock(live_state_lock_);
  live_histories_[file_id] = history;
}

auto StorageService::GetLiveEditHistory(sl_element_id_t file_id) -> std::shared_ptr<EditHistory> {
  std::lock_guard<std::mutex> lock(live_state_lock_);
  auto it = live_histories_.find(file_id);
  if (it == live_histories_.end()) return nullptr;
  if (auto sp = it->second.lock()) return sp;
  live_histories_.erase(it);
  return nullptr;
}

void StorageService::ForgetLiveEditHistory(sl_element_id_t file_id) {
  std::lock_guard<std::mutex> lock(live_state_lock_);
  live_histories_.erase(file_id);
}

void StorageService::RememberLivePipeline(sl_element_id_t file_id,
                                          const std::shared_ptr<PipelineExecutor>& pipeline) {
  std::lock_guard<std::mutex> lock(live_state_lock_);
  live_pipelines_[file_id] = pipeline;
}

auto StorageService::GetLivePipeline(sl_element_id_t file_id) -> std::shared_ptr<PipelineExecutor> {
  std::lock_guard<std::mutex> lock(live_state_lock_);
  auto it = live_pipelines_.find(file_id);
  if (it == live_pipelines_.end()) return nullptr;
  if (auto sp = it->second.lock()) return sp;
  live_pipelines_.erase(it);
  return nullptr;
}

void StorageService::ForgetLivePipeline(sl_element_id_t file_id) {
  std::lock_guard<std::mutex> lock(live_state_lock_);
  live_pipelines_.erase(file_id);
}

}  // namespace alcedo
