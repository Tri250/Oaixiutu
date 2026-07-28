// AlcedoAndroid - Vulkan compute pipeline implementation.
// Builds a VkShaderModule from SPIR-V, a VkDescriptorSetLayout with consecutive
// storage-buffer bindings, a VkPipelineLayout (optionally with push constants)
// and the VkPipeline. Exposes a Dispatch helper for recording.
// SPDX-License-Identifier: GPL-3.0-only
#include "vulkan/pipeline/vulkan_pipeline.hpp"

#include <vector>

#include "utils/app_logging.hpp"
#include "vulkan/context/vulkan_context.hpp"

namespace alcedo {

VulkanPipeline::~VulkanPipeline() { Destroy(); }

bool VulkanPipeline::Create(VulkanContext* ctx, const std::string& name,
                            const std::vector<uint32_t>& spirv,
                            uint32_t binding_count, uint32_t push_constant_bytes) {
  Destroy();
  if (!ctx || !ctx->Valid() || spirv.empty()) return false;
  ctx_ = ctx;
  program_ = std::make_unique<VulkanComputeProgram>();
  program_->name_ = name;
  program_->push_constant_bytes_ = push_constant_bytes;

  VkDevice dev = ctx->Device();

  // 1. Shader module.
  VkShaderModuleCreateInfo sci{};
  sci.sType    = VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO;
  sci.codeSize = spirv.size() * sizeof(uint32_t);
  sci.pCode    = spirv.data();
  if (vkCreateShaderModule(dev, &sci, nullptr, &program_->shader_) != VK_SUCCESS) {
    ALOGE("VulkanPipeline[%s]: shader module creation failed", name.c_str());
    Destroy();
    return false;
  }

  // 2. Descriptor set layout: consecutive storage-buffer bindings.
  std::vector<VkDescriptorSetLayoutBinding> bindings(binding_count);
  for (uint32_t i = 0; i < binding_count; ++i) {
    bindings[i].binding         = i;
    bindings[i].descriptorType  = VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
    bindings[i].descriptorCount = 1;
    bindings[i].stageFlags      = VK_SHADER_STAGE_COMPUTE_BIT;
    bindings[i].pImmutableSamplers = nullptr;
  }
  VkDescriptorSetLayoutCreateInfo dci{};
  dci.sType        = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO;
  dci.bindingCount = binding_count;
  dci.pBindings    = bindings.data();
  if (vkCreateDescriptorSetLayout(dev, &dci, nullptr, &program_->set_layout_) != VK_SUCCESS) {
    ALOGE("VulkanPipeline[%s]: descriptor set layout creation failed", name.c_str());
    Destroy();
    return false;
  }

  // 3. Pipeline layout (with optional push constants).
  VkPushConstantRange pc_range{};
  VkPipelineLayoutCreateInfo plci{};
  plci.sType          = VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO;
  plci.setLayoutCount = 1;
  plci.pSetLayouts    = &program_->set_layout_;
  if (push_constant_bytes > 0) {
    pc_range.stageFlags    = VK_SHADER_STAGE_COMPUTE_BIT;
    pc_range.offset        = 0;
    pc_range.size          = push_constant_bytes;
    plci.pushConstantRangeCount = 1;
    plci.pPushConstantRanges    = &pc_range;
  }
  if (vkCreatePipelineLayout(dev, &plci, nullptr, &program_->pipeline_layout_) != VK_SUCCESS) {
    ALOGE("VulkanPipeline[%s]: pipeline layout creation failed", name.c_str());
    Destroy();
    return false;
  }

  // 4. Compute pipeline.
  VkPipelineShaderStageCreateInfo stage{};
  stage.sType  = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
  stage.stage  = VK_SHADER_STAGE_COMPUTE_BIT;
  stage.module = program_->shader_;
  stage.pName  = "main";

  VkComputePipelineCreateInfo pci{};
  pci.sType  = VK_STRUCTURE_TYPE_COMPUTE_PIPELINE_CREATE_INFO;
  pci.stage  = stage;
  pci.layout = program_->pipeline_layout_;
  if (vkCreateComputePipelines(dev, VK_NULL_HANDLE, 1, &pci, nullptr,
                               &program_->pipeline_) != VK_SUCCESS) {
    ALOGE("VulkanPipeline[%s]: compute pipeline creation failed", name.c_str());
    Destroy();
    return false;
  }

  ALOGD("VulkanPipeline[%s]: created (%u bindings, %u pc bytes)",
        name.c_str(), binding_count, push_constant_bytes);
  return true;
}

void VulkanPipeline::Destroy() {
  if (!program_) return;
  VkDevice dev = ctx_ ? ctx_->Device() : VK_NULL_HANDLE;
  if (dev != VK_NULL_HANDLE) {
    if (program_->pipeline_ != VK_NULL_HANDLE) {
      vkDestroyPipeline(dev, program_->pipeline_, nullptr);
    }
    if (program_->pipeline_layout_ != VK_NULL_HANDLE) {
      vkDestroyPipelineLayout(dev, program_->pipeline_layout_, nullptr);
    }
    if (program_->set_layout_ != VK_NULL_HANDLE) {
      vkDestroyDescriptorSetLayout(dev, program_->set_layout_, nullptr);
    }
    if (program_->shader_ != VK_NULL_HANDLE) {
      vkDestroyShaderModule(dev, program_->shader_, nullptr);
    }
  }
  program_.reset();
}

void VulkanPipeline::Dispatch(VkCommandBuffer cmd, VkDescriptorSet set,
                              const void* push_constants,
                              uint32_t group_x, uint32_t group_y, uint32_t group_z) const {
  if (!program_ || program_->pipeline_ == VK_NULL_HANDLE) return;
  vkCmdBindPipeline(cmd, VK_PIPELINE_BIND_POINT_COMPUTE, program_->pipeline_);
  if (set != VK_NULL_HANDLE) {
    vkCmdBindDescriptorSets(cmd, VK_PIPELINE_BIND_POINT_COMPUTE,
                            program_->pipeline_layout_, 0, 1, &set, 0, nullptr);
  }
  if (push_constants != nullptr && program_->push_constant_bytes_ > 0) {
    vkCmdPushConstants(cmd, program_->pipeline_layout_, VK_SHADER_STAGE_COMPUTE_BIT,
                       0, program_->push_constant_bytes_, push_constants);
  }
  vkCmdDispatch(cmd, group_x, group_y, group_z);
}

}  // namespace alcedo
