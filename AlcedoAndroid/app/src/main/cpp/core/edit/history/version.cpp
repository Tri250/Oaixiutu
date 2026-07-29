// AlcedoAndroid - Version / WorkingVersion implementation.
// SPDX-License-Identifier: GPL-3.0-only
#include "edit/history/version.hpp"

#include <algorithm>
#include <chrono>
#include <cstdint>
#include <stdexcept>
#include <utility>
#include <vector>

#include "edit/pipeline/pipeline.hpp"
#include "type/hash_type.hpp"
#include "utils/app_logging.hpp"
#include "utils/time_provider.hpp"

namespace alcedo {
namespace {

auto JsonHash(const nlohmann::json& j) -> Hash128 {
  const std::string text = j.dump();
  return Hash128::Compute(text.data(), text.size());
}

auto NowTime() -> std::time_t { return TimeProvider::Now(); }

auto NewCreationNonce() -> uint64_t {
  return static_cast<uint64_t>(
      std::chrono::high_resolution_clock::now().time_since_epoch().count());
}

auto MaxTransactionId(const std::vector<EditTransaction>& transactions) -> tx_id_t {
  tx_id_t max_id = 0;
  for (const auto& tx : transactions) {
    max_id = std::max(max_id, tx.GetID());
  }
  return max_id;
}

// Merkle-style reduction over a list of 128-bit hashes.
auto MerkleRoot(std::vector<Hash128> hashes) -> Hash128 {
  if (hashes.empty()) return Hash128{};
  while (hashes.size() > 1) {
    std::vector<Hash128> next_level;
    next_level.reserve((hashes.size() + 1) / 2);
    for (size_t i = 0; i < hashes.size(); i += 2) {
      if (i + 1 < hashes.size()) {
        next_level.push_back(Hash128::Blend(hashes[i], hashes[i + 1]));
      } else {
        next_level.push_back(hashes[i]);
      }
    }
    hashes = std::move(next_level);
  }
  return hashes[0];
}

}  // namespace

// ---------------------------------------------------------------------------
// Version
// ---------------------------------------------------------------------------

Version::Version() {
  added_time_         = NowTime();
  last_modified_time_ = added_time_;
  creation_nonce_     = NewCreationNonce();
  CalculateVersionID();
  ComputeVersionHash();
}

Version::Version(sl_element_id_t bound_image) : bound_image_(bound_image) {
  added_time_         = NowTime();
  last_modified_time_ = added_time_;
  creation_nonce_     = NewCreationNonce();
  CalculateVersionID();
  ComputeVersionHash();
}

Version::Version(nlohmann::json& j) { FromJSON(j); }

auto Version::Default(sl_element_id_t bound_image, nlohmann::json params) -> Version {
  Version version(bound_image);
  version.materialized_params_ = std::move(params);
  version.display_name_        = "Default";
  version.CalculateVersionID();
  return version;
}

auto Version::Empty(sl_element_id_t bound_image, std::string display_name,
                    std::optional<nlohmann::json> materialized_params) -> Version {
  Version version(bound_image);
  version.materialized_params_ = std::move(materialized_params);
  version.display_name_        = std::move(display_name);
  version.CalculateVersionID();
  return version;
}

void Version::SetAddTime() {
  added_time_         = NowTime();
  last_modified_time_ = added_time_;
}

void Version::SetLastModifiedTime() { last_modified_time_ = NowTime(); }

void Version::CalculateVersionID() {
  Hash128 h = Hash128::Compute(&bound_image_, sizeof(bound_image_));
  if (materialized_params_.has_value()) {
    h = Hash128::Blend(h, JsonHash(*materialized_params_));
  }
  h = Hash128::Blend(h, Hash128::Compute(&cursor_, sizeof(cursor_)));
  if (last_transaction_.has_value()) {
    // Fold the last transaction's JSON representation into the id.
    h = Hash128::Blend(h, JsonHash(last_transaction_->ToJSON()));
  }
  h = Hash128::Blend(h, Hash128::Compute(&added_time_, sizeof(added_time_)));
  h = Hash128::Blend(h, Hash128::Compute(&creation_nonce_, sizeof(creation_nonce_)));
  version_id_ = h;
}

void Version::ComputeVersionHash() {
  if (transactions_.empty()) {
    version_hash_ = Hash128{};
    return;
  }
  std::vector<Hash128> leaves;
  leaves.reserve(transactions_.size() + 1);
  for (const auto& tx : transactions_) {
    leaves.push_back(JsonHash(tx.ToJSON()));
  }
  leaves.push_back(Hash128::Compute(&cursor_, sizeof(cursor_)));
  version_hash_ = MerkleRoot(std::move(leaves));
}

auto Version::CloneForImage(sl_element_id_t bound_image) const -> Version {
  auto cloned   = *this;
  cloned.bound_image_ = bound_image;
  cloned.CalculateVersionID();
  return cloned;
}

void Version::AppendEditTransaction(EditTransaction&& edit_transaction) {
  transactions_.push_back(std::move(edit_transaction));
  cursor_ = transactions_.size();
  if (!transactions_.empty()) {
    last_transaction_ = transactions_.back();
  } else {
    last_transaction_ = std::nullopt;
  }
  SetLastModifiedTime();
  ComputeVersionHash();
}

auto Version::RemoveLastEditTransaction() -> EditTransaction {
  if (cursor_ == 0 || transactions_.empty()) {
    throw std::runtime_error("Version: No edit transaction to remove");
  }
  EditTransaction last = std::move(transactions_[cursor_ - 1]);
  transactions_.erase(transactions_.begin() + static_cast<std::ptrdiff_t>(cursor_ - 1));
  cursor_ = std::min(cursor_ - 1, transactions_.size());
  last_transaction_ =
      cursor_ > 0 ? std::optional<EditTransaction>(transactions_[cursor_ - 1]) : std::nullopt;
  SetLastModifiedTime();
  ComputeVersionHash();
  return last;
}

auto Version::GetTransactionByID(tx_id_t transaction_id) -> EditTransaction& {
  auto it = std::find_if(transactions_.begin(), transactions_.end(),
                         [transaction_id](const EditTransaction& tx) {
                           return tx.GetID() == transaction_id;
                         });
  if (it == transactions_.end()) {
    throw std::runtime_error("Version: transaction not found");
  }
  return *it;
}

auto Version::GetLastEditTransaction() -> EditTransaction& {
  if (cursor_ == 0 || transactions_.empty()) {
    throw std::runtime_error("Version: No edit transaction");
  }
  return transactions_[cursor_ - 1];
}

void Version::UpdateFromWorkingVersion(const WorkingVersion& working_version,
                                       const nlohmann::json& head_pipeline_params) {
  materialized_params_ = head_pipeline_params;
  transactions_        = working_version.GetAllEditTransactions();
  cursor_              = working_version.GetCursor();
  last_transaction_ =
      cursor_ > 0 ? std::optional<EditTransaction>(transactions_[cursor_ - 1]) : std::nullopt;
  SetLastModifiedTime();
  ComputeVersionHash();
}

auto Version::ToJSON() const -> nlohmann::json {
  nlohmann::json j;
  j["version_id"]         = version_id_.ToString();
  j["version_id_low"]     = version_id_.low64();
  j["version_id_high"]    = version_id_.high64();
  j["added_time"]         = static_cast<int64_t>(added_time_);
  j["last_modified_time"] = static_cast<int64_t>(last_modified_time_);
  j["creation_nonce"]     = creation_nonce_;
  j["bound_image"]        = bound_image_;
  j["display_name"]       = display_name_;
  j["cursor"]             = cursor_;
  j["version_hash"]       = version_hash_.ToString();
  j["transactions"]       = nlohmann::json::array();
  for (const auto& tx : transactions_) {
    j["transactions"].push_back(tx.ToJSON());
  }
  if (materialized_params_.has_value()) {
    j["materialized_params"] = *materialized_params_;
  }
  if (last_transaction_.has_value()) {
    j["last_transaction"] = last_transaction_->ToJSON();
  }
  return j;
}

void Version::FromJSON(const nlohmann::json& j) {
  try {  // Wrap all j.at() calls; on failure reset to a default/empty state.
  if (!j.is_object() || (!j.contains("version_id_low") && !j.contains("version_id")) ||
      !j.contains("added_time") || !j.contains("last_modified_time") ||
      !j.contains("bound_image")) {
    throw std::runtime_error("Version: Invalid JSON format");
  }
  if (j.contains("version_id_low") && j.contains("version_id_high")) {
    version_id_ =
        Hash128(j.at("version_id_low").get<uint64_t>(), j.at("version_id_high").get<uint64_t>());
  } else {
    version_id_ = Hash128::FromString(j.at("version_id").get<std::string>());
  }
  added_time_         = static_cast<std::time_t>(j.at("added_time").get<int64_t>());
  last_modified_time_ = static_cast<std::time_t>(j.at("last_modified_time").get<int64_t>());
  creation_nonce_     = j.value("creation_nonce", uint64_t{0});
  bound_image_        = j.at("bound_image").get<sl_element_id_t>();
  display_name_       = j.value("display_name", std::string{});

  if (j.contains("materialized_params")) {
    materialized_params_ = j.at("materialized_params");
  } else if (j.contains("final_pipeline_params")) {
    materialized_params_ = j.at("final_pipeline_params");
  } else {
    materialized_params_ = std::nullopt;
  }
  if (j.contains("last_transaction")) {
    EditTransaction lt;
    lt.FromJSON(j.at("last_transaction"));
    last_transaction_ = std::move(lt);
  } else {
    last_transaction_ = std::nullopt;
  }
  transactions_.clear();
  if (j.contains("transactions") && j.at("transactions").is_array()) {
    for (const auto& tx_j : j.at("transactions")) {
      EditTransaction tx;
      tx.FromJSON(tx_j);
      transactions_.push_back(std::move(tx));
    }
  }
  cursor_ = std::min(j.value("cursor", transactions_.size()), transactions_.size());
  if (j.contains("version_hash") && j.at("version_hash").is_string()) {
    version_hash_ = Hash128::FromString(j.at("version_hash").get<std::string>());
  } else {
    ComputeVersionHash();
  }
  } catch (const std::exception& e) {
    ALOGW("Version::FromJSON: JSON parse failure: %s", e.what());
    // Reset to a default/empty state so callers don't observe a half-parsed
    // version with inconsistent cursor / transactions.
    version_id_          = version_id_t{};
    added_time_          = 0;
    last_modified_time_ = 0;
    creation_nonce_     = 0;
    bound_image_        = 0;
    materialized_params_ = std::nullopt;
    last_transaction_   = std::nullopt;
    display_name_.clear();
    transactions_.clear();
    cursor_             = 0;
    version_hash_       = Hash128{};
  }
}

// ---------------------------------------------------------------------------
// WorkingVersion
// ---------------------------------------------------------------------------

WorkingVersion::WorkingVersion(sl_element_id_t bound_image, version_id_t version_id,
                               std::optional<nlohmann::json> head_pipeline_params)
    : version_id_(version_id),
      bound_image_(bound_image),
      head_pipeline_params_(std::move(head_pipeline_params)) {}

WorkingVersion::WorkingVersion(sl_element_id_t bound_image, version_id_t version_id,
                               std::optional<nlohmann::json> head_pipeline_params,
                               std::vector<EditTransaction> transactions, size_t cursor)
    : version_id_(version_id),
      bound_image_(bound_image),
      transactions_(std::move(transactions)),
      cursor_(std::min(cursor, transactions_.size())),
      head_pipeline_params_(std::move(head_pipeline_params)) {
  // Resume the id generator above the highest existing transaction id so newly
  // appended transactions do not collide.
  tx_id_generator_.Reset(MaxTransactionId(transactions_) + 1);
}

void WorkingVersion::AppendEditTransaction(EditTransaction&& edit_transaction) {
  // Drop any redo tail: a new edit branches off the current cursor.
  if (cursor_ < transactions_.size()) {
    transactions_.erase(transactions_.begin() + static_cast<std::ptrdiff_t>(cursor_),
                        transactions_.end());
  }
  edit_transaction.SetTimestamp(TimeProvider::Now());
  transactions_.push_back(std::move(edit_transaction));
  cursor_ = transactions_.size();
}

auto WorkingVersion::RemoveLastEditTransaction() -> EditTransaction {
  if (cursor_ == 0 || transactions_.empty()) {
    throw std::runtime_error("WorkingVersion: No edit transaction to remove");
  }
  EditTransaction last = std::move(transactions_[cursor_ - 1]);
  transactions_.erase(transactions_.begin() + static_cast<std::ptrdiff_t>(cursor_ - 1));
  cursor_ = std::min(cursor_ - 1, transactions_.size());
  return last;
}

auto WorkingVersion::UndoLastTransaction(PipelineExecutor& pipeline) -> bool {
  if (cursor_ == 0) return false;
  // Our transactions store full operator params (not before/after diffs), so
  // the only way to undo is to rebuild the pipeline from the baseline snapshot
  // and replay the remaining transactions.
  if (head_pipeline_params_.has_value()) {
    pipeline.ImportPipelineParams(*head_pipeline_params_);
  }
  const size_t new_cursor = cursor_ - 1;
  for (size_t i = 0; i < new_cursor; ++i) {
    transactions_[i].ApplyToPipeline(pipeline);
  }
  cursor_ = new_cursor;
  return true;
}

auto WorkingVersion::RedoNextTransaction(PipelineExecutor& pipeline) -> bool {
  if (cursor_ >= transactions_.size()) return false;
  transactions_[cursor_].ApplyToPipeline(pipeline);
  ++cursor_;
  return true;
}

auto WorkingVersion::MoveCursorTo(size_t target_cursor, PipelineExecutor& pipeline) -> bool {
  target_cursor = std::min(target_cursor, transactions_.size());
  while (cursor_ > target_cursor) {
    if (!UndoLastTransaction(pipeline)) return false;
  }
  while (cursor_ < target_cursor) {
    if (!RedoNextTransaction(pipeline)) return false;
  }
  return true;
}

auto WorkingVersion::AppliedTransactions() const -> std::vector<EditTransaction> {
  return std::vector<EditTransaction>(transactions_.begin(),
                                      transactions_.begin() + static_cast<std::ptrdiff_t>(cursor_));
}

auto WorkingVersion::ToJSON() const -> nlohmann::json {
  nlohmann::json j;
  j["version_id"]  = version_id_.ToString();
  j["bound_image"] = bound_image_;
  j["cursor"]      = cursor_;
  j["tx_id_next"]  = tx_id_generator_.Peek();
  if (head_pipeline_params_.has_value()) {
    j["head_pipeline_params"] = *head_pipeline_params_;
  }
  j["transactions"] = nlohmann::json::array();
  for (const auto& tx : transactions_) {
    j["transactions"].push_back(tx.ToJSON());
  }
  return j;
}

void WorkingVersion::FromJSON(const nlohmann::json& j) {
  try {  // Wrap all j.at() calls; on failure reset to a default/empty state.
  if (!j.is_object() || !j.contains("version_id") || !j.contains("bound_image") ||
      !j.contains("cursor") || !j.contains("transactions")) {
    throw std::runtime_error("WorkingVersion: Invalid JSON format");
  }
  version_id_  = Hash128::FromString(j.at("version_id").get<std::string>());
  bound_image_ = j.at("bound_image").get<sl_element_id_t>();
  cursor_      = j.at("cursor").get<size_t>();
  tx_id_generator_.Reset(j.value("tx_id_next", tx_id_t{0}));
  head_pipeline_params_ = j.contains("head_pipeline_params")
                              ? std::optional<nlohmann::json>(j.at("head_pipeline_params"))
                              : std::nullopt;
  transactions_.clear();
  for (const auto& tx_j : j.at("transactions")) {
    EditTransaction tx;
    tx.FromJSON(tx_j);
    transactions_.push_back(std::move(tx));
  }
  cursor_ = std::min(cursor_, transactions_.size());
  } catch (const std::exception& e) {
    ALOGW("WorkingVersion::FromJSON: JSON parse failure: %s", e.what());
    // Reset to a default/empty state so callers don't observe a half-parsed
    // working version with a dangling cursor.
    version_id_           = version_id_t{};
    bound_image_          = 0;
    transactions_.clear();
    cursor_               = 0;
    head_pipeline_params_ = std::nullopt;
    tx_id_generator_.Reset(tx_id_t{0});
  }
}

}  // namespace alcedo
