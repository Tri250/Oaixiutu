// AlcedoAndroid - Vibrance operator (selective saturation).
// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "edit/operators/op_base.hpp"
#include "edit/operators/op_kernel.hpp"
namespace alcedo {
class VibranceOp : public OperatorBase<VibranceOp>, public PointOpTag {
 public:
  static constexpr PriorityLevel     priority_level_    = 12;
  static constexpr PipelineStageName affiliation_stage_ = PipelineStageName::Color_Adjustment;
  static constexpr std::string_view  canonical_name_    = "Vibrance";
  static constexpr std::string_view  script_name_       = "vibrance";
  static constexpr OperatorType      operator_type_     = OperatorType::VIBRANCE;
  VibranceOp();
  explicit VibranceOp(float offset);
  explicit VibranceOp(const nlohmann::json& params);
  void Apply(std::shared_ptr<ImageBuffer> input) override;
  void ApplyGPU(std::shared_ptr<ImageBuffer> input) override;
  auto GetParams() const -> nlohmann::json override;
  void SetParams(const nlohmann::json& params) override;
  void SetGlobalParams(OperatorParams& params) const override;
  void EnableGlobalParams(OperatorParams& params, bool enable) override;
 private:
  float vibrance_offset_ = 0.0f;
};
}  // namespace alcedo
