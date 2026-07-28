// AlcedoAndroid - RAW decode operator (pipeline entry point).
// Delegates the actual RAW demosaic to the raw_processor; this operator is the
// descriptor that sits in the Image_Loading stage so the pipeline can serialize
// the RAW decode parameters.
// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "edit/operators/op_base.hpp"
#include "edit/operators/op_kernel.hpp"
namespace alcedo {
class RawDecodeOp : public OperatorBase<RawDecodeOp>, public PointOpTag {
 public:
  static constexpr PriorityLevel     priority_level_    = 0;
  static constexpr PipelineStageName affiliation_stage_ = PipelineStageName::Image_Loading;
  static constexpr std::string_view  canonical_name_    = "RAWDecode";
  static constexpr std::string_view  script_name_       = "raw_decode";
  static constexpr OperatorType      operator_type_     = OperatorType::RAW_DECODE;
  RawDecodeOp();
  explicit RawDecodeOp(const nlohmann::json& params);
  void Apply(std::shared_ptr<ImageBuffer> input) override;
  void ApplyGPU(std::shared_ptr<ImageBuffer> input) override;
  auto GetParams() const -> nlohmann::json override;
  void SetParams(const nlohmann::json& params) override;
  void SetGlobalParams(OperatorParams& params) const override;
  void EnableGlobalParams(OperatorParams& params, bool enable) override;
 private:
  int   demosaic_method_ = 0;  // 0=RCD, 1=AHD, 2=AMaZE, 3=Neural
  bool  half_res_        = false;
};
}  // namespace alcedo
