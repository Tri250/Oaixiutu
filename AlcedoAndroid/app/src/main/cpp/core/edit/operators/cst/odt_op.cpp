// AlcedoAndroid - OdtOp implementation.
// SPDX-License-Identifier: GPL-3.0-only
#include "edit/operators/cst/odt_op.hpp"

#include "image/image_buffer.hpp"

namespace alcedo {

OdtOp::OdtOp() = default;
OdtOp::OdtOp(const nlohmann::json& params) { SetParams(params); }

void OdtOp::Apply(std::shared_ptr<ImageBuffer> input) {
  FloatMat& img = input->GetCPUData();
  if (use_open_drt_) {
    drt_.Apply(img, display_white_l_, display_black_l_);
  } else {
    aces_.Apply(img, display_white_l_, display_black_l_);
  }
}
void OdtOp::ApplyGPU(std::shared_ptr<ImageBuffer> input) {
  input->SyncToCPU();
  Apply(input);
  input->SyncToGPU();
}

auto OdtOp::GetParams() const -> nlohmann::json {
  nlohmann::json o;
  o["use_open_drt"]    = use_open_drt_;
  o["display_white_l"] = display_white_l_;
  o["display_black_l"] = display_black_l_;
  return o;
}
void OdtOp::SetParams(const nlohmann::json& params) {
  use_open_drt_    = params.value("use_open_drt", true);
  display_white_l_ = params.value("display_white_l", 1.0f);
  display_black_l_ = params.value("display_black_l", 0.0f);
}
void OdtOp::SetGlobalParams(OperatorParams& params) const {
  params.to_output_params_.use_open_drt_    = use_open_drt_;
  params.to_output_params_.display_white_l_ = display_white_l_;
  params.to_output_params_.display_black_l_ = display_black_l_;
  params.to_output_dirty_ = true;
}
void OdtOp::EnableGlobalParams(OperatorParams& params, bool enable) { params.to_output_enabled_ = enable; }

}  // namespace alcedo
