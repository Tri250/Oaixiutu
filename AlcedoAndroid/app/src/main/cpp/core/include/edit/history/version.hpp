// AlcedoAndroid - Version node (a user-visible look in the Git-like history tree).
// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <ctime>
#include <optional>
#include <string>
#include <vector>

#include "edit/history/edit_transaction.hpp"
#include "edit/pipeline/pipeline.hpp"
#include "json.hpp"
#include "type/hash_type.hpp"
#include "type/type.hpp"
#include "utils/id/id_generator.hpp"

namespace alcedo {

using version_id_t = Hash128;

class WorkingVersion;

// A Version is one branch in the version tree. It owns an ordered list of
// transactions and a cursor pointing at the applied head. Replaying the
// transactions from the import baseline reconstructs the pipeline params.
class Version {
 public:
  Version();
  explicit Version(sl_element_id_t bound_image);
  explicit Version(nlohmann::json& j);

  static auto Default(sl_element_id_t bound_image, nlohmann::json params) -> Version;
  static auto Empty(sl_element_id_t bound_image, std::string display_name,
                    std::optional<nlohmann::json> materialized_params = std::nullopt) -> Version;

  void CalculateVersionID();
  auto GetVersionID() const -> version_id_t { return version_id_; }
  void SetAddTime();
  auto GetAddTime() const -> std::time_t { return added_time_; }
  void SetLastModifiedTime();
  auto GetLastModifiedTime() const -> std::time_t { return last_modified_time_; }
  void SetBoundImage(sl_element_id_t bound_image) { bound_image_ = bound_image; }
  auto GetBoundImage() const -> sl_element_id_t { return bound_image_; }
  auto CloneForImage(sl_element_id_t bound_image) const -> Version;

  void SetFinalPipelineParams(const nlohmann::json& params) { materialized_params_ = params; }
  auto GetFinalPipelineParams() const -> std::optional<nlohmann::json> { return materialized_params_; }
  auto GetMaterializedParams() const -> std::optional<nlohmann::json> { return materialized_params_; }
  auto GetTransactionCount() const -> size_t { return transactions_.size(); }
  auto GetLastTransaction() const -> const std::optional<EditTransaction>& { return last_transaction_; }
  auto GetDisplayName() const -> const std::string& { return display_name_; }
  void SetDisplayName(std::string display_name) {
    display_name_ = std::move(display_name);
    SetLastModifiedTime();
  }
  auto GetCursor() const -> size_t { return cursor_; }
  auto GetVersionHash() const -> Hash128 { return version_hash_; }

  void AppendEditTransaction(EditTransaction&& edit_transaction);
  auto RemoveLastEditTransaction() -> EditTransaction;
  auto GetTransactionByID(tx_id_t transaction_id) -> EditTransaction&;
  auto GetLastEditTransaction() -> EditTransaction&;
  auto GetAllEditTransactions() const -> const std::vector<EditTransaction>& { return transactions_; }
  void UpdateFromWorkingVersion(const WorkingVersion& working_version,
                                const nlohmann::json& head_pipeline_params);

  auto ToJSON() const -> nlohmann::json;
  void FromJSON(const nlohmann::json& j);

 private:
  version_id_t                   version_id_  = {};
  std::time_t                    added_time_  = 0;
  std::time_t                    last_modified_time_ = 0;
  uint64_t                       creation_nonce_ = 0;
  sl_element_id_t                bound_image_ = 0;
  std::optional<nlohmann::json>  materialized_params_ = std::nullopt;
  std::optional<EditTransaction> last_transaction_ = std::nullopt;
  std::string                    display_name_;
  std::vector<EditTransaction>   transactions_;
  size_t                         cursor_ = 0;
  Hash128                        version_hash_;
  void ComputeVersionHash();
};

// A mutable working copy of a version used while editing; supports undo/redo.
class WorkingVersion {
 public:
  WorkingVersion() = default;
  WorkingVersion(sl_element_id_t bound_image, version_id_t version_id,
                 std::optional<nlohmann::json> head_pipeline_params = std::nullopt);
  WorkingVersion(sl_element_id_t bound_image, version_id_t version_id,
                 std::optional<nlohmann::json> head_pipeline_params,
                 std::vector<EditTransaction> transactions, size_t cursor);

  auto GetVersionID() const -> version_id_t { return version_id_; }
  auto HasVersion() const -> bool { return version_id_.low64() != 0 || version_id_.high64() != 0; }
  auto GetBoundImage() const -> sl_element_id_t { return bound_image_; }
  auto GetAppliedTransactionCount() const -> size_t { return cursor_; }
  auto GetCursor() const -> size_t { return cursor_; }
  auto GetAllEditTransactions() const -> const std::vector<EditTransaction>& { return transactions_; }
  auto GetHeadPipelineParams() const -> std::optional<nlohmann::json> { return head_pipeline_params_; }
  void SetHeadPipelineParams(const nlohmann::json& params) { head_pipeline_params_ = params; }

  void AppendEditTransaction(EditTransaction&& edit_transaction);
  auto RemoveLastEditTransaction() -> EditTransaction;
  auto UndoLastTransaction(PipelineExecutor& pipeline) -> bool;
  auto RedoNextTransaction(PipelineExecutor& pipeline) -> bool;
  auto MoveCursorTo(size_t target_cursor, PipelineExecutor& pipeline) -> bool;
  auto AppliedTransactions() const -> std::vector<EditTransaction>;

  auto ToJSON() const -> nlohmann::json;
  void FromJSON(const nlohmann::json& j);

 private:
  IncrID::IDGenerator<tx_id_t>  tx_id_generator_{0};
  version_id_t                  version_id_      = {};
  sl_element_id_t               bound_image_     = 0;
  std::vector<EditTransaction>  transactions_;
  size_t                        cursor_               = 0;
  std::optional<nlohmann::json> head_pipeline_params_ = std::nullopt;
};

}  // namespace alcedo
