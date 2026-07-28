// AlcedoAndroid - RawDecodeOp implementation.
// SPDX-License-Identifier: GPL-3.0-only
#include "edit/operators/raw/raw_decode_op.hpp"
#include "image/image_buffer.hpp"
#include "utils/app_logging.hpp"
namespace alcedo {
RawDecodeOp::RawDecodeOp() = default;
RawDecodeOp::RawDecodeOp(const nlohmann::json& params) { SetParams(params); }
void RawDecodeOp::Apply(std::shared_ptr<ImageBuffer> input) {
  // The RAW buffer is decoded upstream by the raw_processor; this stage is a
  // no-op on an already-demosaiced float buffer. Kept so the pipeline graph is
  // complete and the stage can be marked in the export profile.
  (void)input;
}
void RawDecodeOp::ApplyGPU(std::shared_ptr<ImageBuffer> input) { (void)input; }
auto RawDecodeOp::GetParams() const -> nlohmann::json {
  nlohmann::json o;
  o["demosaic_method"] = demosaic_method_;
  o["half_res"]        = half_res_;
  return o;
}
void RawDecodeOp::SetParams(const nlohmann::json& params) {
  demosaic_method_ = params.value("demosaic_method", 0);
  half_res_        = params.value("half_res", false);
}
void RawDecodeOp::SetGlobalParams(OperatorParams& params) const {
  (void)params;
}
void RawDecodeOp::EnableGlobalParams(OperatorParams& params, bool enable) { (void)params; (void)enable; }
}  // namespace alcedo
