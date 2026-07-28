// AlcedoAndroid - HLS profile operator (per-hue H/L/S adjustments).
// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include <array>
#include "edit/operators/op_base.hpp"
#include "edit/operators/op_kernel.hpp"
namespace alcedo {
class HlsOp : public OperatorBase<HlsOp>, public PointOpTag {
 public:
  static constexpr PriorityLevel     priority_level_    = 13;
  static constexpr PipelineStageName affiliation_stage_ = PipelineStageName::Color_Adjustment;
  static constexpr std::string_view  canonical_name_    = "HLS";
  static constexpr std::string_view  script_name_       = "hls";
  static constexpr OperatorType      operator_type_     = OperatorType::HLS;
  static constexpr int kProfileCount = 8;
  HlsOp();
  explicit HlsOp(const nlohmann::json& params);
  void Apply(std::shared_ptr<ImageBuffer> input) override;
  void ApplyGPU(std::shared_ptr<ImageBuffer> input) override;
  auto GetParams() const -> nlohmann::json override;
  void SetParams(const nlohmann::json& params) override;
  void SetGlobalParams(OperatorParams& params) const override;
  void EnableGlobalParams(OperatorParams& params, bool enable) override;
  // Per-profile adjustments: [hue, lightness, saturation] deltas.
  std::array<std::array<float, 3>, kProfileCount> profile_adjustments_{};
  float hue_range_ = 45.0f;
 private:
  static float RgbToHue(float r, float g, float b);
};
}  // namespace alcedo
