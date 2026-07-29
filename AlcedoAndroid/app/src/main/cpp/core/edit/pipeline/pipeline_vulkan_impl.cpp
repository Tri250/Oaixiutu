// AlcedoAndroid - Vulkan pipeline stage execution (fused kernel stream).
// Implements RunVulkanStage(): uploads the stage input to a Vulkan image,
// dispatches the per-stage fused compute program, and downloads the result.
// When SPIR-V for a stage is not registered, falls back to CPU.
// SPDX-License-Identifier: GPL-3.0-only
#include "edit/pipeline/pipeline_stage.hpp"

#include <memory>

#include "image/image_buffer.hpp"
#include "image/vulkan_image.hpp"
#include "utils/app_logging.hpp"
#include "vulkan/context/vulkan_context.hpp"
#include "vulkan/pipeline/vulkan_kernel_stream.hpp"
#include "vulkan/pipeline/vulkan_pipeline.hpp"
#include "vulkan/pipeline/vulkan_program_registry.hpp"

namespace alcedo {

namespace {

constexpr int kWorkgroupSize = 16;

const char* StageProgramName(PipelineStageName stage) {
  switch (stage) {
    case PipelineStageName::Basic_Adjustment:  return "basic";
    case PipelineStageName::Color_Adjustment:  return "color";
    case PipelineStageName::Output_Transform:  return "cst";
    case PipelineStageName::Detail_Adjustment: return "detail";
    case PipelineStageName::To_WorkingSpace:   return "tone_mapping";
    default: return nullptr;
  }
}

}  // namespace

std::shared_ptr<ImageBuffer> RunVulkanStage(PipelineStage& stage,
                                            std::shared_ptr<ImageBuffer> input,
                                            OperatorParams& global_params) {
  VulkanContext* ctx = VulkanContext::Get();
  if (!ctx || !ctx->Valid() || !input) return nullptr;
  const char* prog_name = StageProgramName(stage.stage_);
  if (!prog_name) return nullptr;

  auto spirv = VulkanProgramRegistry::Instance().Get(prog_name);
  if (!spirv) {
    // No SPIR-V registered yet; fall back to per-operator CPU path.
    return nullptr;
  }

  // Ensure GPU image is up to date.
  input->SyncToGPU(GpuBackendKind::Vulkan);
  VulkanImage* vin = input->GetVulkanImage();
  if (!vin || !vin->Valid()) return nullptr;

  // Build (or reuse) a pipeline for this stage.
  // For a production build the VulkanKernelStream is owned by the executor and
  // rebuilt only when the program changes. Here we construct a transient
  // pipeline to keep this stage self-contained.
  VulkanPipeline pipe;
  if (!pipe.Create(ctx, prog_name, *spirv, /*binding_count=*/2,
                   /*push_constant_bytes=*/sizeof(OperatorParams))) {
    ALOGW("RunVulkanStage: pipeline create failed for %s", prog_name);
    return nullptr;
  }

  // Allocate output image.
  auto output = std::make_shared<ImageBuffer>(input->Width(), input->Height(),
                                              input->Channels());
  output->SyncToGPU(GpuBackendKind::Vulkan);
  VulkanImage* vout = output->GetVulkanImage();
  if (!vout || !vout->Valid()) return nullptr;

  // Descriptor set + bindings.
  VkDescriptorSetLayout layout = pipe.Program()->set_layout_;
  VkDescriptorSet set = ctx->AllocateDescriptorSet(layout);
  if (set == VK_NULL_HANDLE) return nullptr;
  vin->BindToDescriptor(set, 0);
  vout->BindToDescriptor(set, 1);

  // Dispatch.
  VkCommandBuffer cmd = ctx->BeginOneShotCompute();
  pipe.Dispatch(cmd, set, &global_params,
                (input->Width() + kWorkgroupSize - 1) / kWorkgroupSize,
                (input->Height() + kWorkgroupSize - 1) / kWorkgroupSize, 1);
  ctx->EndAndSubmitOneShot(cmd);

  output->SetGPUDataValid(true);
  output->SyncToCPU();
  return output;
}

}  // namespace alcedo
