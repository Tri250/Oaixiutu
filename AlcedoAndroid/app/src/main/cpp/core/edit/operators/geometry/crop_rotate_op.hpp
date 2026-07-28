// AlcedoAndroid - Crop + Rotate operator.
// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "edit/operators/op_base.hpp"
#include "edit/operators/op_kernel.hpp"
namespace alcedo {
class CropRotateOp : public OperatorBase<CropRotateOp>, public NeighborOpTag {
 public:
  static constexpr PriorityLevel     priority_level_    = 0;
  static constexpr PipelineStageName affiliation_stage_ = PipelineStageName::Geometry_Adjustment;
  static constexpr std::string_view  canonical_name_    = "CropRotate";
  static constexpr std::string_view  script_name_       = "crop_rotate";
  static constexpr OperatorType      operator_type_     = OperatorType::CROP_ROTATE;
  CropRotateOp();
  explicit CropRotateOp(const nlohmann::json& params);
  void Apply(std::shared_ptr<ImageBuffer> input) override;
  void ApplyGPU(std::shared_ptr<ImageBuffer> input) override;
  auto GetParams() const -> nlohmann::json override;
  void SetParams(const nlohmann::json& params) override;
  void SetGlobalParams(OperatorParams& params) const override;
  void EnableGlobalParams(OperatorParams& params, bool enable) override;
 private:
  int   crop_x_ = 0, crop_y_ = 0, crop_w_ = 0, crop_h_ = 0;
  float angle_deg_ = 0.0f;  // 0/90/180/270 supported for fast path
};
}  // namespace alcedo
