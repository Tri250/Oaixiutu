// AlcedoAndroid - Sharpen operator (unsharp mask).
// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "edit/operators/op_base.hpp"
#include "edit/operators/op_kernel.hpp"
namespace alcedo {
class SharpenOp : public OperatorBase<SharpenOp>, public NeighborOpTag {
 public:
  static constexpr PriorityLevel     priority_level_    = 1;
  static constexpr PipelineStageName affiliation_stage_ = PipelineStageName::Detail_Adjustment;
  static constexpr std::string_view  canonical_name_    = "Sharpen";
  static constexpr std::string_view  script_name_       = "sharpen";
  static constexpr OperatorType      operator_type_     = OperatorType::SHARPEN;
  SharpenOp();
  explicit SharpenOp(float offset);
  explicit SharpenOp(const nlohmann::json& params);
  void Apply(std::shared_ptr<ImageBuffer> input) override;
  void ApplyGPU(std::shared_ptr<ImageBuffer> input) override;
  auto GetParams() const -> nlohmann::json override;
  void SetParams(const nlohmann::json& params) override;
  void SetGlobalParams(OperatorParams& params) const override;
  void EnableGlobalParams(OperatorParams& params, bool enable) override;
 private:
  float offset_    = 0.0f;
  float radius_    = 3.0f;
  float threshold_ = 0.0f;
};
}  // namespace alcedo
