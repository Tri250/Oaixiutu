// AlcedoAndroid - Edit transaction (a single atomic edit applied to a version).
// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <ctime>
#include <string>

#include "edit/operators/op_base.hpp"
#include "json.hpp"
#include "type/hash_type.hpp"
#include "type/type.hpp"

namespace alcedo {

using tx_id_t = uint64_t;

// A transaction records one operator-parameter change against the head of a
// version. It is the atomic unit of the history timeline.
class EditTransaction {
 public:
  EditTransaction() = default;
  EditTransaction(tx_id_t id, OperatorType op_type, nlohmann::json params);

  tx_id_t               GetID() const { return id_; }
  OperatorType          GetOperatorType() const { return op_type_; }
  const nlohmann::json& GetParams() const { return params_; }
  void                  SetParams(nlohmann::json params) { params_ = std::move(params); }
  std::time_t           GetTimestamp() const { return timestamp_; }
  void                  SetTimestamp(std::time_t t) { timestamp_ = t; }

  auto ToJSON() const -> nlohmann::json;
  void FromJSON(const nlohmann::json& j);

  // Apply this transaction's operator parameter update to the pipeline.
  void ApplyToPipeline(class PipelineExecutor& pipeline) const;

 private:
  tx_id_t         id_         = 0;
  OperatorType    op_type_    = OperatorType::UNKNOWN;
  nlohmann::json  params_;
  std::time_t     timestamp_  = 0;
};

}  // namespace alcedo
