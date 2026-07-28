// AlcedoAndroid - Saturation operator (global OKLCh chroma scale).
// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "edit/operators/op_base.hpp"
#include "edit/operators/op_kernel.hpp"
namespace alcedo {
class SaturationOp : public OperatorBase<SaturationOp>, public PointOpTag {
 public:
  static constexpr PriorityLevel     priority_level_    = 11;
  static constexpr PipelineStageName affiliation_stage_ = PipelineStageName::Color_Adjustment;
  static constexpr std::string_view  canonical_name_    = "Saturation";
  static constexpr std::string_view  script_name_       = "saturation";
  static constexpr OperatorType      operator_type_     = OperatorType::SATURATION;
  SaturationOp();
  explicit SaturationOp(float offset);
  explicit SaturationOp(const nlohmann::json& params);
  void Apply(std::shared_ptr<ImageBuffer> input) override;
  void ApplyGPU(std::shared_ptr<ImageBuffer> input) override;
  auto GetParams() const -> nlohmann::json override;
  void SetParams(const nlohmann::json& params) override;
  void SetGlobalParams(OperatorParams& params) const override;
  void EnableGlobalParams(OperatorParams& params, bool enable) override;
 private:
  float saturation_offset_ = 1.0f;  // 1.0 = neutral
};
}  // namespace alcedo
