// AlcedoAndroid - Halation operator (red glow around bright highlights).
// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "edit/operators/op_base.hpp"
#include "edit/operators/op_kernel.hpp"
namespace alcedo {
class HalationOp : public OperatorBase<HalationOp>, public NeighborOpTag {
 public:
  static constexpr PriorityLevel     priority_level_    = 0;
  static constexpr PipelineStageName affiliation_stage_ = PipelineStageName::Output_Transform;
  static constexpr std::string_view  canonical_name_    = "Halation";
  static constexpr std::string_view  script_name_       = "halation";
  static constexpr OperatorType      operator_type_     = OperatorType::HALATION;
  HalationOp();
  explicit HalationOp(const nlohmann::json& params);
  void Apply(std::shared_ptr<ImageBuffer> input) override;
  void ApplyGPU(std::shared_ptr<ImageBuffer> input) override;
  auto GetParams() const -> nlohmann::json override;
  void SetParams(const nlohmann::json& params) override;
  void SetGlobalParams(OperatorParams& params) const override;
  void EnableGlobalParams(OperatorParams& params, bool enable) override;
 private:
  bool  enabled_        = true;
  float strength_       = 0.0f;
  float low_threshold_  = 0.6f;
  float high_threshold_ = 0.7f;
  float sigma_          = 20.0f;
  float redshift_[3]    = {1.0f, 0.05f, 0.02f};
  float additive_scale_ = 1.0f;
};
}  // namespace alcedo
