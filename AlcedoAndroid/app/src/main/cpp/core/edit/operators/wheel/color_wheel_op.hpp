// AlcedoAndroid - Color wheel operator (Lift / Gamma / Gain).
// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "edit/operators/op_base.hpp"
#include "edit/operators/op_kernel.hpp"
namespace alcedo {
class ColorWheelOp : public OperatorBase<ColorWheelOp>, public PointOpTag {
 public:
  static constexpr PriorityLevel     priority_level_    = 14;
  static constexpr PipelineStageName affiliation_stage_ = PipelineStageName::Color_Adjustment;
  static constexpr std::string_view  canonical_name_    = "ColorWheel";
  static constexpr std::string_view  script_name_       = "color_wheel";
  static constexpr OperatorType      operator_type_     = OperatorType::COLOR_WHEEL;
  ColorWheelOp();
  explicit ColorWheelOp(const nlohmann::json& params);
  void Apply(std::shared_ptr<ImageBuffer> input) override;
  void ApplyGPU(std::shared_ptr<ImageBuffer> input) override;
  auto GetParams() const -> nlohmann::json override;
  void SetParams(const nlohmann::json& params) override;
  void SetGlobalParams(OperatorParams& params) const override;
  void EnableGlobalParams(OperatorParams& params, bool enable) override;
  // Lift (shadows), Gamma (midtones), Gain (highlights) per-channel offsets.
  float lift_[3]   = {0.0f, 0.0f, 0.0f};
  float gamma_[3]  = {1.0f, 1.0f, 1.0f};
  float gain_[3]   = {1.0f, 1.0f, 1.0f};
};
}  // namespace alcedo
