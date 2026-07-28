// AlcedoAndroid - EditTransaction implementation.
// SPDX-License-Identifier: GPL-3.0-only
#include "edit/history/edit_transaction.hpp"

#include <chrono>
#include <stdexcept>
#include <utility>

#include "edit/pipeline/pipeline.hpp"
#include "edit/pipeline/pipeline_stage.hpp"
#include "utils/app_logging.hpp"
#include "utils/time_provider.hpp"

namespace alcedo {
namespace {

// Map an operator type to the pipeline stage that owns it. Mirrors the
// affiliation_stage_ declared by each concrete operator.
auto StageForOperator(OperatorType type) -> PipelineStageName {
  switch (type) {
    case OperatorType::RAW_DECODE:
      return PipelineStageName::Image_Loading;
    case OperatorType::RESIZE:
    case OperatorType::CROP_ROTATE:
    case OperatorType::LENS_CALIBRATION:
      return PipelineStageName::Geometry_Adjustment;
    case OperatorType::CST:
    case OperatorType::TO_WS:
      return PipelineStageName::To_WorkingSpace;
    case OperatorType::EXPOSURE:
    case OperatorType::CONTRAST:
    case OperatorType::WHITE:
    case OperatorType::BLACK:
    case OperatorType::SHADOWS:
    case OperatorType::HIGHLIGHTS:
    case OperatorType::COLOR_TEMP:
    case OperatorType::ACES_TONE_MAPPING:
      return PipelineStageName::Basic_Adjustment;
    case OperatorType::HLS:
    case OperatorType::SATURATION:
    case OperatorType::TINT:
    case OperatorType::VIBRANCE:
    case OperatorType::CURVE:
    case OperatorType::COLOR_WHEEL:
      return PipelineStageName::Color_Adjustment;
    case OperatorType::CLARITY:
    case OperatorType::SHARPEN:
      return PipelineStageName::Detail_Adjustment;
    case OperatorType::LMT:
    case OperatorType::ODT:
    case OperatorType::TO_OUTPUT:
    case OperatorType::FILM_GRAIN:
    case OperatorType::HALATION:
      return PipelineStageName::Output_Transform;
    case OperatorType::AUTO_EXPOSURE:
    case OperatorType::UNKNOWN:
    default:
      return PipelineStageName::Basic_Adjustment;
  }
}

auto ParamsArePresent(const nlohmann::json& params) -> bool { return !params.is_null(); }

// Resolve the enabled flag for the operator given its params blob. Most
// operators carry an "enabled" boolean; LMT is considered enabled when a LUT
// path is present.
auto ResolveEnabled(OperatorType type, const nlohmann::json& params) -> bool {
  if (type == OperatorType::LMT) {
    if (params.is_object() && params.contains("ocio_lmt")) {
      try {
        const auto path = params["ocio_lmt"].get<std::string>();
        return !path.empty();
      } catch (...) {
        return false;
      }
    }
    return false;
  }
  if (params.is_object() && params.contains("enabled") && params["enabled"].is_boolean()) {
    return params["enabled"].get<bool>();
  }
  // Some operators wrap their params in a single nested object keyed by the
  // script name; honor an embedded "enabled" there too.
  if (params.is_object() && params.size() == 1) {
    const auto& inner = params.begin().value();
    if (inner.is_object() && inner.contains("enabled") && inner["enabled"].is_boolean()) {
      return inner["enabled"].get<bool>();
    }
  }
  return true;
}

}  // namespace

EditTransaction::EditTransaction(tx_id_t id, OperatorType op_type, nlohmann::json params)
    : id_(id), op_type_(op_type), params_(std::move(params)),
      timestamp_(TimeProvider::Now()) {}

void EditTransaction::ApplyToPipeline(PipelineExecutor& pipeline) const {
  const auto stage_name = StageForOperator(op_type_);
  auto& stage = pipeline.GetStage(stage_name);
  auto& global_params = pipeline.GetGlobalParams();

  const bool enabled = ResolveEnabled(op_type_, params_);
  if (ParamsArePresent(params_)) {
    stage.SetOperator(op_type_, params_, global_params);
  }
  stage.EnableOperator(op_type_, enabled, global_params);
}

auto EditTransaction::ToJSON() const -> nlohmann::json {
  nlohmann::json j;
  j["id"]            = id_;
  j["operator_type"] = static_cast<int>(op_type_);
  j["params"]        = params_;
  j["timestamp"]     = static_cast<int64_t>(timestamp_);
  return j;
}

void EditTransaction::FromJSON(const nlohmann::json& j) {
  if (!j.is_object()) {
    throw std::runtime_error("EditTransaction: invalid JSON (not an object)");
  }
  id_         = j.value("id", tx_id_t{0});
  op_type_    = static_cast<OperatorType>(j.value("operator_type", static_cast<int>(OperatorType::UNKNOWN)));
  params_     = j.contains("params") ? j["params"]
                                     : (j.contains("after_params") ? j["after_params"]
                                                                   : nlohmann::json::object());
  timestamp_  = static_cast<std::time_t>(j.value("timestamp", static_cast<int64_t>(0)));
  if (timestamp_ == 0) {
    timestamp_ = TimeProvider::Now();
  }
}

}  // namespace alcedo
