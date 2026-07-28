// AlcedoAndroid - Lens calibration operator (distortion correction).
// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "edit/operators/op_base.hpp"
#include "edit/operators/op_kernel.hpp"
#include "edit/operators/geometry/lens_calib_runtime.hpp"
namespace alcedo {
class LensCalibOp : public OperatorBase<LensCalibOp>, public NeighborOpTag {
 public:
  static constexpr PriorityLevel     priority_level_    = 0;
  static constexpr PipelineStageName affiliation_stage_ = PipelineStageName::Geometry_Adjustment;
  static constexpr std::string_view  canonical_name_    = "LensCalibration";
  static constexpr std::string_view  script_name_       = "lens_calib";
  static constexpr OperatorType      operator_type_     = OperatorType::LENS_CALIBRATION;
  LensCalibOp();
  explicit LensCalibOp(const nlohmann::json& params);
  void Apply(std::shared_ptr<ImageBuffer> input) override;
  void ApplyGPU(std::shared_ptr<ImageBuffer> input) override;
  auto GetParams() const -> nlohmann::json override;
  void SetParams(const nlohmann::json& params) override;
  void SetGlobalParams(OperatorParams& params) const override;
  void EnableGlobalParams(OperatorParams& params, bool enable) override;
  void SetRuntimeParams(const LensCalibGpuParams& p) { runtime_ = p; }
 private:
  LensCalibGpuParams runtime_;
};
}  // namespace alcedo
