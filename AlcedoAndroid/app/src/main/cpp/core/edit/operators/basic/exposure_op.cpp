// AlcedoAndroid - ExposureOp implementation.
// SPDX-License-Identifier: GPL-3.0-only
#include "edit/operators/basic/exposure_op.hpp"

#include "image/image_buffer.hpp"
#include "vulkan/pipeline/vulkan_pipeline.hpp"
#include "vulkan/pipeline/vulkan_program_registry.hpp"

namespace alcedo {

ExposureOp::ExposureOp() : exposure_offset_(0.0f), scale_(0.0f) {}

ExposureOp::ExposureOp(float exposure_offset) : exposure_offset_(exposure_offset) {
  scale_ = exposure_offset_ / 17.52f;
}

ExposureOp::ExposureOp(const nlohmann::json& params) : ExposureOp() { SetParams(params); }

void ExposureOp::Apply(std::shared_ptr<ImageBuffer> input) {
  FloatMat& img = input->GetCPUData();
  const float s = scale_;
  img.ForEachPixel([s](Pixel& p, int, int) {
    p.r += s;
    p.g += s;
    p.b += s;
  });
}

void ExposureOp::ApplyGPU(std::shared_ptr<ImageBuffer> input) {
  // Stage 0 of the fused basic kernel; the actual dispatch is performed by the
  // pipeline's VulkanKernelStream using the "basic" program. Here we just
  // ensure the GPU image is present and up to date.
  input->SyncToGPU();
}

auto ExposureOp::GetParams() const -> nlohmann::json {
  nlohmann::json o;
  o[std::string(script_name_)] = exposure_offset_;
  return o;
}

void ExposureOp::SetParams(const nlohmann::json& params) {
  if (!params.contains(script_name_)) {
    exposure_offset_ = 0.0f;
  } else {
    exposure_offset_ = params[script_name_].get<float>();
  }
  scale_ = exposure_offset_ / 17.52f;
}

void ExposureOp::SetGlobalParams(OperatorParams& params) const {
  params.exposure_offset_ = scale_;
}

void ExposureOp::EnableGlobalParams(OperatorParams& params, bool enable) {
  params.exposure_enabled_ = enable;
}

}  // namespace alcedo
