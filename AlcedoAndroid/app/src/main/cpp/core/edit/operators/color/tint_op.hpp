// AlcedoAndroid - Tint operator (green/magenta balance).
// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "edit/operators/op_base.hpp"
#include "edit/operators/op_kernel.hpp"
namespace alcedo {
class TintOp : public OperatorBase<TintOp>, public PointOpTag {
 public:
  static constexpr PriorityLevel     priority_level_    = 10;
  static constexpr PipelineStageName affiliation_stage_ = PipelineStageName::Color_Adjustment;
  static constexpr std::string_view  canonical_name_    = "Tint";
  static constexpr std::string_view  script_name_       = "tint";
  static constexpr OperatorType      operator_type_     = OperatorType::TINT;
  TintOp();
  explicit TintOp(float offset);
  explicit TintOp(const nlohmann::json& params);
  void Apply(std::shared_ptr<ImageBuffer> input) override;
  void ApplyGPU(std::shared_ptr<ImageBuffer> input) override;
  auto GetParams() const -> nlohmann::json override;
  void SetParams(const nlohmann::json& params) override;
  void SetGlobalParams(OperatorParams& params) const override;
  void EnableGlobalParams(OperatorParams& params, bool enable) override;
 private:
  float tint_offset_ = 0.0f;
};
}  // namespace alcedo
