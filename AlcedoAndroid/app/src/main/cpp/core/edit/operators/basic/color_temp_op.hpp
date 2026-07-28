// AlcedoAndroid - Color temperature / white balance operator.
// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "edit/operators/op_base.hpp"
#include "edit/operators/op_kernel.hpp"
namespace alcedo {
class ColorTempOp : public OperatorBase<ColorTempOp>, public PointOpTag {
 public:
  static constexpr PriorityLevel     priority_level_    = 0;
  static constexpr PipelineStageName affiliation_stage_ = PipelineStageName::Basic_Adjustment;
  static constexpr std::string_view  canonical_name_    = "ColorTemperature";
  static constexpr std::string_view  script_name_       = "color_temp";
  static constexpr OperatorType      operator_type_     = OperatorType::COLOR_TEMP;
  ColorTempOp();
  ColorTempOp(ColorTempMode mode, float cct, float tint);
  explicit ColorTempOp(const nlohmann::json& params);
  void Apply(std::shared_ptr<ImageBuffer> input) override;
  void ApplyGPU(std::shared_ptr<ImageBuffer> input) override;
  auto GetParams() const -> nlohmann::json override;
  void SetParams(const nlohmann::json& params) override;
  void SetGlobalParams(OperatorParams& params) const override;
  void EnableGlobalParams(OperatorParams& params, bool enable) override;
 private:
  ColorTempMode mode_  = ColorTempMode::AS_SHOT;
  float cct_           = 6500.0f;
  float tint_          = 0.0f;
  // Resolved camera->AP1 matrix (row-major 3x3).
  float cam_to_ap1_[9] = {1,0,0,0,1,0,0,0,1};
};
}  // namespace alcedo
