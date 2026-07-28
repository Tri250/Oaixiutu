// AlcedoAndroid - Shadows operator (tonal region compression).
// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "edit/operators/op_base.hpp"
#include "edit/operators/op_kernel.hpp"
namespace alcedo {
class ShadowOp : public OperatorBase<ShadowOp>, public PointOpTag {
 public:
  static constexpr PriorityLevel     priority_level_    = 4;
  static constexpr PipelineStageName affiliation_stage_ = PipelineStageName::Basic_Adjustment;
  static constexpr std::string_view  canonical_name_    = "Shadows";
  static constexpr std::string_view  script_name_       = "shadows";
  static constexpr OperatorType      operator_type_     = OperatorType::SHADOWS;
  ShadowOp();
  explicit ShadowOp(float offset);
  explicit ShadowOp(const nlohmann::json& params);
  void Apply(std::shared_ptr<ImageBuffer> input) override;
  void ApplyGPU(std::shared_ptr<ImageBuffer> input) override;
  auto GetParams() const -> nlohmann::json override;
  void SetParams(const nlohmann::json& params) override;
  void SetGlobalParams(OperatorParams& params) const override;
  void EnableGlobalParams(OperatorParams& params, bool enable) override;
 private:
  float shadow_offset_ = 0.0f;
  // Hermite S-curve region parameters.
  float x0_ = 0.0f, x1_ = 0.25f;
};
}  // namespace alcedo
