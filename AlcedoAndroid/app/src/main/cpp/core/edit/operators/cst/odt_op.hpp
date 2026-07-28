// AlcedoAndroid - Output Display Transform operator (delegates to ACES 2.0 / OpenDRT).
// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "edit/operators/op_base.hpp"
#include "edit/operators/op_kernel.hpp"
#include "edit/operators/cst/aces_odt_cpu.hpp"
#include "edit/operators/cst/open_drt_cpu.hpp"
namespace alcedo {
class OdtOp : public OperatorBase<OdtOp>, public PointOpTag {
 public:
  static constexpr PriorityLevel     priority_level_    = 0;
  static constexpr PipelineStageName affiliation_stage_ = PipelineStageName::Output_Transform;
  static constexpr std::string_view  canonical_name_    = "ODT";
  static constexpr std::string_view  script_name_       = "odt";
  static constexpr OperatorType      operator_type_     = OperatorType::ODT;
  OdtOp();
  explicit OdtOp(const nlohmann::json& params);
  void Apply(std::shared_ptr<ImageBuffer> input) override;
  void ApplyGPU(std::shared_ptr<ImageBuffer> input) override;
  auto GetParams() const -> nlohmann::json override;
  void SetParams(const nlohmann::json& params) override;
  void SetGlobalParams(OperatorParams& params) const override;
  void EnableGlobalParams(OperatorParams& params, bool enable) override;
 private:
  bool  use_open_drt_      = true;
  float display_white_l_   = 1.0f;
  float display_black_l_   = 0.0f;
  AcesOdtCpu   aces_;
  OpenDrtCpu   drt_;
};
}  // namespace alcedo
