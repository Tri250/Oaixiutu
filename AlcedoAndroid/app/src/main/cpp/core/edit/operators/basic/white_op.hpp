// AlcedoAndroid - White point operator.
// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "edit/operators/op_base.hpp"
#include "edit/operators/op_kernel.hpp"
namespace alcedo {
class WhiteOp : public OperatorBase<WhiteOp>, public PointOpTag {
 public:
  static constexpr PriorityLevel     priority_level_    = 3;
  static constexpr PipelineStageName affiliation_stage_ = PipelineStageName::Basic_Adjustment;
  static constexpr std::string_view  canonical_name_    = "White";
  static constexpr std::string_view  script_name_       = "white";
  static constexpr OperatorType      operator_type_     = OperatorType::WHITE;
  WhiteOp();
  explicit WhiteOp(float white_point);
  explicit WhiteOp(const nlohmann::json& params);
  void Apply(std::shared_ptr<ImageBuffer> input) override;
  void ApplyGPU(std::shared_ptr<ImageBuffer> input) override;
  auto GetParams() const -> nlohmann::json override;
  void SetParams(const nlohmann::json& params) override;
  void SetGlobalParams(OperatorParams& params) const override;
  void EnableGlobalParams(OperatorParams& params, bool enable) override;
 private:
  float white_point_ = 1.0f;
};
}  // namespace alcedo
