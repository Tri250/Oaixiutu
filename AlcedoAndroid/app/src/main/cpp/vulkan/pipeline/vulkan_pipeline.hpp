// AlcedoAndroid - Vulkan compute pipeline.
// Builds VkShaderModule from SPIR-V, VkPipelineLayout + VkPipeline for a
// compute program, and exposes a dispatch helper that binds a descriptor set
// (input/output storage buffers + push constants).
// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <vulkan/vulkan.h>

#include <cstdint>
#include <memory>
#include <string>
#include <vector>

namespace alcedo {

class VulkanContext;

struct VulkanComputeProgram {
  VkShaderModule      shader_       = VK_NULL_HANDLE;
  VkDescriptorSetLayout set_layout_ = VK_NULL_HANDLE;
  VkPipelineLayout    pipeline_layout_ = VK_NULL_HANDLE;
  VkPipeline          pipeline_     = VK_NULL_HANDLE;
  uint32_t            push_constant_bytes_ = 0;
  std::string         name_;
};

class VulkanPipeline {
 public:
  VulkanPipeline() = default;
  ~VulkanPipeline();

  // Build a compute program from SPIR-V bytes. push_constant_bytes is the size
  // of the push constant block (0 if none). descriptor bindings are inferred
  // from binding_count as consecutive storage-buffer bindings.
  bool Create(VulkanContext* ctx, const std::string& name,
              const std::vector<uint32_t>& spirv,
              uint32_t binding_count, uint32_t push_constant_bytes);
  void Destroy();

  VulkanComputeProgram* Program() { return program_.get(); }
  bool Valid() const { return program_ && program_->pipeline_ != VK_NULL_HANDLE; }

  // Record a dispatch with the given push-constant data and a pre-bound
  // descriptor set. group counts are the compute workgroup counts.
  void Dispatch(VkCommandBuffer cmd, VkDescriptorSet set, const void* push_constants,
                uint32_t group_x, uint32_t group_y, uint32_t group_z = 1) const;

 private:
  VulkanContext* ctx_ = nullptr;
  std::unique_ptr<VulkanComputeProgram> program_;
};

}  // namespace alcedo
