// AlcedoAndroid - Contrast operator.
// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "edit/operators/op_base.hpp"
#include "edit/operators/op_kernel.hpp"
namespace alcedo {
class ContrastOp : public OperatorBase<ContrastOp>, public PointOpTag {
 public:
  static constexpr PriorityLevel     priority_level_    = 1;
  static constexpr PipelineStageName affiliation_stage_ = PipelineStageName::Basic_Adjustment;
  static constexpr std::string_view  canonical_name_    = "Contrast";
  static constexpr std::string_view  script_name_       = "contrast";
  static constexpr OperatorType      operator_type_     = OperatorType::CONTRAST;
  ContrastOp();
  explicit ContrastOp(float scale);
  explicit ContrastOp(const nlohmann::json& params);
  void Apply(std::shared_ptr<ImageBuffer> input) override;
  void ApplyGPU(std::shared_ptr<ImageBuffer> input) override;
  auto GetParams() const -> nlohmann::json override;
  void SetParams(const nlohmann::json& params) override;
  void SetGlobalParams(OperatorParams& params) const override;
  void EnableGlobalParams(OperatorParams& params, bool enable) override;
 private:
  float contrast_scale_ = 0.0f;
};
}  // namespace alcedo
