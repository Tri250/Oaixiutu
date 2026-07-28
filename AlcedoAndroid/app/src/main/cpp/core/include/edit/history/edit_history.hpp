// AlcedoAndroid - EditHistory: a Git-like tree of versions for one image.
// Adapted from the desktop project; the CPU pipeline executor is replaced by
// the Vulkan-first PipelineExecutor created via CreatePipelineExecutor().
// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <cstdint>
#include <list>
#include <memory>
#include <optional>
#include <string>
#include <unordered_map>

#include "edit/history/version.hpp"
#include "edit/pipeline/pipeline.hpp"
#include "json.hpp"
#include "type/hash_type.hpp"
#include "type/type.hpp"

namespace alcedo {

class VersionNode {
 public:
  explicit VersionNode(Version& ver_ref) : ver_ref_(ver_ref) {}
  Version&               ver_ref_;
  p_hash_t               commit_id_ = 0;
};

using history_id_t = Hash128;

// A history of alternate looks for a specific image. Each Version is a
// user-visible look with its own transaction timeline. Versions replay from
// the image-specific import baseline rather than from one another; cached
// materialized params are an internal acceleration detail only.
class EditHistory {
 public:
  explicit EditHistory(sl_element_id_t bound_image);

  void SetAddTime();
  void SetLastModifiedTime();
  auto GetAddTime() const -> std::time_t { return added_time_; }
  auto GetLastModifiedTime() const -> std::time_t { return last_modified_time_; }

  auto GetHistoryId() const -> history_id_t { return history_id_; }
  auto GetBoundImage() const -> sl_element_id_t { return bound_image_; }

  auto GetVersion(history_id_t ver_id) -> Version&;
  auto GetDefaultVersion() -> Version&;
  auto GetDefaultVersionID() const -> history_id_t { return default_version_id_; }
  auto GetActiveVersionID() const -> history_id_t { return active_version_id_; }
  auto GetActiveVersion() -> Version&;
  auto GetActiveVersionHash() -> Hash128;
  auto CloneForFile(sl_element_id_t bound_image) const -> std::shared_ptr<EditHistory>;
  auto GetImportPipelineParams() const -> const nlohmann::json& { return import_pipeline_params_; }
  void SetImportPipelineParams(nlohmann::json params);
  auto ReconstructPipelineParamsForVersion(history_id_t ver_id) -> std::optional<nlohmann::json>;
  auto CreateVersion(std::string display_name = {}) -> history_id_t;
  auto CommitVersion(Version&& ver) -> history_id_t;
  void RenameVersion(history_id_t ver_id, std::string display_name);
  auto RemoveVersion(history_id_t ver_id) -> bool;
  void SetActiveVersionID(history_id_t ver_id);
  void UpdateVersionFromWorkingVersion(history_id_t ver_id, const WorkingVersion& working_version,
                                       const nlohmann::json& head_pipeline_params);

  auto GetVersions() const -> const std::list<VersionNode>& { return version_order_; }

  auto ToJSON() const -> nlohmann::json;
  void FromJSON(const nlohmann::json& j);

 private:
  void CalculateHistoryID();
  void EnsureDefaultVersion();

  history_id_t                              history_id_{};
  sl_element_id_t                           bound_image_ = 0;
  std::time_t                               added_time_         = 0;
  std::time_t                               last_modified_time_ = 0;
  std::list<VersionNode>                    version_order_;
  std::unordered_map<history_id_t, Version> version_storage_;
  history_id_t                              default_version_id_{};
  history_id_t                              active_version_id_{};
  nlohmann::json                            import_pipeline_params_ = nlohmann::json::object();
  std::optional<nlohmann::json>             active_pipeline_params_ = std::nullopt;
};

}  // namespace alcedo
