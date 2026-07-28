// AlcedoAndroid - Curve operator (monotonic-cubic tone curve on luminance).
// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include <vector>
#include "edit/operators/op_base.hpp"
#include "edit/operators/op_kernel.hpp"
namespace alcedo {
class CurveOp : public OperatorBase<CurveOp>, public PointOpTag {
 public:
  static constexpr PriorityLevel     priority_level_    = 7;
  static constexpr PipelineStageName affiliation_stage_ = PipelineStageName::Basic_Adjustment;
  static constexpr std::string_view  canonical_name_    = "Curve";
  static constexpr std::string_view  script_name_       = "curve";
  static constexpr OperatorType      operator_type_     = OperatorType::CURVE;
  CurveOp();
  explicit CurveOp(const nlohmann::json& params);
  void Apply(std::shared_ptr<ImageBuffer> input) override;
  void ApplyGPU(std::shared_ptr<ImageBuffer> input) override;
  auto GetParams() const -> nlohmann::json override;
  void SetParams(const nlohmann::json& params) override;
  void SetGlobalParams(OperatorParams& params) const override;
  void EnableGlobalParams(OperatorParams& params, bool enable) override;
  void SetControlPoints(const std::vector<CurvePoint>& pts);
  // Evaluate the monotone cubic curve at x in [0,1].
  float Evaluate(float x) const;
 private:
  std::vector<CurvePoint> ctrl_pts_;
  std::vector<float>      h_;   // segment widths
  std::vector<float>      m_;   // tangents
  void RebuildSpline();
};
}  // namespace alcedo
