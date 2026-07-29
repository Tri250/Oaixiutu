// AlcedoAndroid - StorageService (sleeve-side facade).
// Self-contained Android port. The desktop StorageService owns the full
// controller stack (DB/Element/Image/Semantic/AI); on Android the controllers
// live in the storage layer and are injected via the storage layer's own
// service. This sleeve-side facade owns the in-memory element storage map and
// the live edit-history/pipeline caches, plus a NodeStorageHandler that
// operates on that map. DB persistence is delegated to the storage layer.
// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <filesystem>
#include <memory>
#include <mutex>
#include <unordered_map>

#include "edit/history/edit_history.hpp"
#include "edit/pipeline/pipeline.hpp"
#include "sleeve/sleeve_element/sleeve_element.hpp"
#include "sleeve/sleeve_element/sleeve_folder.hpp"
#include "type/type.hpp"

namespace alcedo {

class PipelineExecutor;

// Operates on the in-memory element storage held by StorageService.
class NodeStorageHandler {
 public:
  NodeStorageHandler(std::unordered_map<sl_element_id_t, std::shared_ptr<SleeveElement>>& storage,
                     std::mutex& storage_lock);
  void AddToStorage(std::shared_ptr<SleeveElement> new_element);
  void EnsureChildrenLoaded(std::shared_ptr<SleeveFolder> folder);
  auto GetElement(sl_element_id_t id) -> std::shared_ptr<SleeveElement>;
  void GarbageCollect();

 private:
  std::unordered_map<sl_element_id_t, std::shared_ptr<SleeveElement>>& storage_;
  std::mutex&                                                          storage_lock_;
};

class StorageService {
 public:
  explicit StorageService(std::filesystem::path db_path);
  ~StorageService();

  auto GetStorage() -> std::unordered_map<sl_element_id_t, std::shared_ptr<SleeveElement>>& {
    return storage_;
  }
  auto GetNodeStorageHandler() -> NodeStorageHandler& { return node_handler_; }
  auto GetDBPath() const -> const std::filesystem::path& { return db_path_; }
  // Lock protecting the in-memory element storage map. Callers that iterate or
  // mutate storage_ directly (outside NodeStorageHandler) must hold this lock.
  auto GetLiveStateLock() -> std::mutex& { return live_state_lock_; }

  // Live edit-history cache (keyed by sleeve file element id).
  void RememberLiveEditHistory(sl_element_id_t file_id,
                               const std::shared_ptr<EditHistory>& history);
  auto GetLiveEditHistory(sl_element_id_t file_id) -> std::shared_ptr<EditHistory>;
  void ForgetLiveEditHistory(sl_element_id_t file_id);

  // Live pipeline cache.
  void RememberLivePipeline(sl_element_id_t file_id,
                            const std::shared_ptr<PipelineExecutor>& pipeline);
  auto GetLivePipeline(sl_element_id_t file_id) -> std::shared_ptr<PipelineExecutor>;
  void ForgetLivePipeline(sl_element_id_t file_id);

 private:
  std::filesystem::path                                                db_path_;
  std::unordered_map<sl_element_id_t, std::shared_ptr<SleeveElement>> storage_;
  NodeStorageHandler                                                  node_handler_;
  std::mutex                                                           live_state_lock_;
  std::unordered_map<sl_element_id_t, std::weak_ptr<EditHistory>>     live_histories_;
  std::unordered_map<sl_element_id_t, std::weak_ptr<PipelineExecutor>> live_pipelines_;
};

}  // namespace alcedo
