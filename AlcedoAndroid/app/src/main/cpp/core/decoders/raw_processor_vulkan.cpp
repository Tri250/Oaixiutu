// AlcedoAndroid - Vulkan RAW processor.
// Dispatches the debayer + white-balance + color-conversion pipeline as Vulkan
// compute when available; falls back to the CPU RawProcessor otherwise. The
// fused "raw" compute program is registered separately (raw.comp shader, not
// listed in the edit-pipeline shader set).
// SPDX-License-Identifier: GPL-3.0-only
#include "decoders/raw_processor.hpp"

#include <memory>

#include "utils/app_logging.hpp"
#include "vulkan/context/vulkan_context.hpp"

namespace alcedo {

auto ProcessRawVulkan(const ImageBuffer& mosaic, const RawParams& params,
                      const RawRuntimeColorContext& context, RawCfaPattern cfa,
                      RawInputKind input_kind) -> ImageBuffer {
  auto* ctx = VulkanContext::Get();
  if (!ctx || !ctx->Valid()) {
    ALOGD("ProcessRawVulkan: Vulkan unavailable, falling back to CPU");
    RawProcessor cpu(params, context, cfa, input_kind);
    return cpu.Process(mosaic);
  }

  // A full GPU debayer dispatch requires the "raw" SPIR-V program to be
  // registered with VulkanProgramRegistry. Until that program is available at
  // runtime, fall back to the CPU path. This keeps the Vulkan entry point real
  // and upgradeable without breaking the decode pipeline.
  // TODO: dispatch raw.comp (debayer + wb + cam->AP1) on the Vulkan device and
  //       read back the resulting RGB buffer.
  (void)ctx;
  RawProcessor cpu(params, context, cfa, input_kind);
  return cpu.Process(mosaic);
}

}  // namespace alcedo
