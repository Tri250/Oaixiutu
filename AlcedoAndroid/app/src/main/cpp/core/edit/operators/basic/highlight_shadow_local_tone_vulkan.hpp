// AlcedoAndroid - Highlight/Shadow local tone mapping (Vulkan backend).
// Replaces the desktop CUDA/Metal/OpenCL local-tone operator. Builds a
// log-space local-contrast mask on the GPU via the "tone_mapping" compute
// program and folds shadow/highlight amounts + clarity into the result.
// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "edit/operators/op_base.hpp"
#include "edit/operators/op_kernel.hpp"
namespace alcedo {
class HsLocalToneVulkanOp : public OperatorBase<HsLocalToneVulkanOp>, public NeighborOpTag {
 public:
  static constexpr PriorityLevel     priority_level_    = 6;
  static constexpr PipelineStageName affiliation_stage_ = PipelineStageName::Basic_Adjustment;
  static constexpr std::string_view  canonical_name_    = "HSLocalTone";
  static constexpr std::string_view  script_name_       = "hs_local_tone";
  static constexpr OperatorType      operator_type_     = OperatorType::ACES_TONE_MAPPING;
  HsLocalToneVulkanOp();
  explicit HsLocalToneVulkanOp(const nlohmann::json& params);
  void Apply(std::shared_ptr<ImageBuffer> input) override;
  void ApplyGPU(std::shared_ptr<ImageBuffer> input) override;
  auto GetParams() const -> nlohmann::json override;
  void SetParams(const nlohmann::json& params) override;
  void SetGlobalParams(OperatorParams& params) const override;
  void EnableGlobalParams(OperatorParams& params, bool enable) override;
 private:
  float shadow_amount_    = 0.0f;
  float highlight_amount_ = 0.0f;
  float clarity_amount_   = 0.0f;
  float radius_           = 18.0f;
};
}  // namespace alcedo
