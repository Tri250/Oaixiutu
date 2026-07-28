// AlcedoAndroid - Look Modification Transform operator (LMT).
// Applies a creative LUT / look on top of the working space. On Android the
// OCIO dependency is replaced with a 1D/3D LUT sampler loaded from a .cube
// file path (or a no-op identity look when none is set).
// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include <filesystem>
#include <string>
#include "edit/operators/op_base.hpp"
#include "edit/operators/op_kernel.hpp"
namespace alcedo {
class LmtOp : public OperatorBase<LmtOp>, public PointOpTag {
 public:
  static constexpr PriorityLevel     priority_level_    = 0;
  static constexpr PipelineStageName affiliation_stage_ = PipelineStageName::Output_Transform;
  static constexpr std::string_view  canonical_name_    = "LMT";
  static constexpr std::string_view  script_name_       = "lmt";
  static constexpr OperatorType      operator_type_     = OperatorType::LMT;
  LmtOp();
  explicit LmtOp(const nlohmann::json& params);
  void Apply(std::shared_ptr<ImageBuffer> input) override;
  void ApplyGPU(std::shared_ptr<ImageBuffer> input) override;
  auto GetParams() const -> nlohmann::json override;
  void SetParams(const nlohmann::json& params) override;
  void SetGlobalParams(OperatorParams& params) const override;
  void EnableGlobalParams(OperatorParams& params, bool enable) override;
  bool LoadLut(const std::filesystem::path& path);
 private:
  std::filesystem::path lut_path_;
  float strength_ = 1.0f;
  bool  enabled_  = false;
  // Simple 33^3 LUT (identity until loaded).
  static constexpr int kLutSize = 33;
  std::vector<float> lut_;  // size kLutSize^3 * 3
  bool lut_loaded_ = false;
};
}  // namespace alcedo
