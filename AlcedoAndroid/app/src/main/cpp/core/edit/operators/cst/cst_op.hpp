// AlcedoAndroid - Color space transform operator (camera -> working space).
// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "edit/operators/op_base.hpp"
#include "edit/operators/op_kernel.hpp"
namespace alcedo {
class CstOp : public OperatorBase<CstOp>, public PointOpTag {
 public:
  static constexpr PriorityLevel     priority_level_    = 0;
  static constexpr PipelineStageName affiliation_stage_ = PipelineStageName::To_WorkingSpace;
  static constexpr std::string_view  canonical_name_    = "CST";
  static constexpr std::string_view  script_name_       = "cst";
  static constexpr OperatorType      operator_type_     = OperatorType::CST;
  CstOp();
  explicit CstOp(const nlohmann::json& params);
  void Apply(std::shared_ptr<ImageBuffer> input) override;
  void ApplyGPU(std::shared_ptr<ImageBuffer> input) override;
  auto GetParams() const -> nlohmann::json override;
  void SetParams(const nlohmann::json& params) override;
  void SetGlobalParams(OperatorParams& params) const override;
  void EnableGlobalParams(OperatorParams& params, bool enable) override;
 private:
  // 3x3 camera->AP1 matrix (row-major).
  float matrix_[9] = {1,0,0,0,1,0,0,0,1};
};
}  // namespace alcedo
