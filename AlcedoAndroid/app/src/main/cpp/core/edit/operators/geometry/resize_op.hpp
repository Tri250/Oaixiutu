// AlcedoAndroid - Resize operator (bilinear/bicubic downsample).
// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "edit/operators/op_base.hpp"
#include "edit/operators/op_kernel.hpp"
#include "edit/operators/geometry/resize_algorithm.hpp"
namespace alcedo {
class ResizeOp : public OperatorBase<ResizeOp>, public NeighborOpTag {
 public:
  static constexpr PriorityLevel     priority_level_    = 0;
  static constexpr PipelineStageName affiliation_stage_ = PipelineStageName::Geometry_Adjustment;
  static constexpr std::string_view  canonical_name_    = "Resize";
  static constexpr std::string_view  script_name_       = "resize";
  static constexpr OperatorType      operator_type_     = OperatorType::RESIZE;
  ResizeOp();
  ResizeOp(int target_width, int target_height,
           ResizeDownsampleAlgorithm algo = ResizeDownsampleAlgorithm::Bilinear);
  explicit ResizeOp(const nlohmann::json& params);
  void Apply(std::shared_ptr<ImageBuffer> input) override;
  void ApplyGPU(std::shared_ptr<ImageBuffer> input) override;
  auto GetParams() const -> nlohmann::json override;
  void SetParams(const nlohmann::json& params) override;
  void SetGlobalParams(OperatorParams& params) const override;
  void EnableGlobalParams(OperatorParams& params, bool enable) override;
 private:
  int                       target_w_ = 0;
  int                       target_h_ = 0;
  ResizeDownsampleAlgorithm algo_     = ResizeDownsampleAlgorithm::Bilinear;
};
}  // namespace alcedo
