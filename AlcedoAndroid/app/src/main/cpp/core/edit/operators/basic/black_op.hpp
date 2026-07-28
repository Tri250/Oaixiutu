// AlcedoAndroid - Black point operator.
// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "edit/operators/op_base.hpp"
#include "edit/operators/op_kernel.hpp"
namespace alcedo {
class BlackOp : public OperatorBase<BlackOp>, public PointOpTag {
 public:
  static constexpr PriorityLevel     priority_level_    = 2;
  static constexpr PipelineStageName affiliation_stage_ = PipelineStageName::Basic_Adjustment;
  static constexpr std::string_view  canonical_name_    = "Black";
  static constexpr std::string_view  script_name_       = "black";
  static constexpr OperatorType      operator_type_     = OperatorType::BLACK;
  BlackOp();
  explicit BlackOp(float black_point);
  explicit BlackOp(const nlohmann::json& params);
  void Apply(std::shared_ptr<ImageBuffer> input) override;
  void ApplyGPU(std::shared_ptr<ImageBuffer> input) override;
  auto GetParams() const -> nlohmann::json override;
  void SetParams(const nlohmann::json& params) override;
  void SetGlobalParams(OperatorParams& params) const override;
  void EnableGlobalParams(OperatorParams& params, bool enable) override;
 private:
  float black_point_ = 0.0f;
};
}  // namespace alcedo
