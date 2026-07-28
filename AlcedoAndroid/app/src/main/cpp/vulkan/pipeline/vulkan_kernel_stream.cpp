// AlcedoAndroid - Fused Vulkan kernel stream implementation.
// Runs an ordered list of compute kernels over an input/output VulkanImage pair.
// Between consecutive in-place kernels a scratch buffer is used to avoid
// read-after-write hazards (ping-pong). Push constants carry the per-kernel
// parameter block (capped to the Vulkan-guaranteed 128-byte minimum).
// SPDX-License-Identifier: GPL-3.0-only
#include "vulkan/pipeline/vulkan_kernel_stream.hpp"

#include <algorithm>
#include <cstring>
#include <utility>
#include <vector>

#include "edit/operators/op_base.hpp"
#include "image/vulkan_image.hpp"
#include "utils/app_logging.hpp"
#include "vulkan/context/vulkan_context.hpp"
#include "vulkan/pipeline/vulkan_pipeline.hpp"

namespace alcedo {

VulkanKernelStream::~VulkanKernelStream() = default;

void VulkanKernelStream::Append(std::unique_ptr<VulkanKernelEntry> entry) {
  if (entry && entry->pipeline) {
    entries_.push_back(std::move(entry));
  }
}

void VulkanKernelStream::Clear() { entries_.clear(); }

bool VulkanKernelStream::Execute(VulkanContext* ctx, VulkanImage* input,
                                 VulkanImage* output, const OperatorParams& params,
                                 int workgroup_x, int workgroup_y) const {
  if (!ctx || !ctx->Valid() || !input || !output || entries_.empty()) return false;
  if (workgroup_x <= 0 || workgroup_y <= 0) return false;

  // Validate image dimensions match between input and output.
  if (input->Width() != output->Width() || input->Height() != output->Height()) {
    ALOGW("VulkanKernelStream: input/output dimension mismatch");
    return false;
  }

  const bool in_place = (input == output);
  VulkanImage scratch;
  if (in_place && entries_.size() > 1) {
    if (!scratch.Create(ctx, input->Width(), input->Height(), input->Channels())) {
      ALOGW("VulkanKernelStream: scratch allocation failed");
      return false;
    }
  }

  // Track allocated descriptor sets so they can be freed after submission.
  std::vector<std::pair<VkDescriptorSet, VkDescriptorSetLayout>> allocated_sets;

  VkCommandBuffer cmd = ctx->BeginOneShotCompute();

  VulkanImage* current_src = input;
  VulkanImage* current_dst = output;

  for (size_t i = 0; i < entries_.size(); ++i) {
    VulkanKernelEntry& entry = *entries_[i];
    if (!entry.pipeline || !entry.pipeline->Valid()) {
      ALOGW("VulkanKernelStream: entry %zu invalid", i);
      continue;
    }

    // Ping-pong target selection for in-place streams.
    if (in_place && entries_.size() > 1) {
      current_dst = (i % 2 == 0) ? &scratch : output;
      if (i == entries_.size() - 1 && current_dst != output) {
        current_dst = output;
        if (i > 0 && current_src == output) current_src = &scratch;
      }
      if (current_dst == current_src) {
        current_dst = (current_src == &scratch) ? output : &scratch;
      }
    }

    VkDescriptorSetLayout layout = entry.pipeline->Program()->set_layout_;
    VkDescriptorSet set = ctx->AllocateDescriptorSet(layout);
    if (set == VK_NULL_HANDLE) {
      ALOGW("VulkanKernelStream: descriptor set alloc failed for entry %zu", i);
      ctx->EndAndSubmitOneShot(cmd);
      return false;
    }
    allocated_sets.emplace_back(set, layout);
    if (!current_src->BindToDescriptor(set, 0) || !current_dst->BindToDescriptor(set, 1)) {
      ctx->EndAndSubmitOneShot(cmd);
      return false;
    }

    const void* pc_data = (entry.push_constant_bytes > 0) ? &params : nullptr;
    entry.pipeline->Dispatch(cmd, set, pc_data,
                             static_cast<uint32_t>(workgroup_x),
                             static_cast<uint32_t>(workgroup_y), 1);

    // Memory barrier so the next kernel observes the writes.
    VkMemoryBarrier mb{VK_STRUCTURE_TYPE_MEMORY_BARRIER};
    mb.srcAccessMask = VK_ACCESS_SHADER_WRITE_BIT;
    mb.dstAccessMask = VK_ACCESS_SHADER_READ_BIT | VK_ACCESS_SHADER_WRITE_BIT;
    vkCmdPipelineBarrier(cmd, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                         VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, 0, 1, &mb, 0,
                         nullptr, 0, nullptr);

    // Swap source for the next iteration.
    if (in_place && entries_.size() > 1) {
      current_src = current_dst;
    } else {
      current_src = current_dst;
      current_dst = output;
    }
  }

  // Submit + wait idle (EndAndSubmitOneShot blocks until the queue is idle).
  ctx->EndAndSubmitOneShot(cmd);

  // Now safe to free the descriptor sets back to the shared pool.
  for (auto& kv : allocated_sets) {
    vkFreeDescriptorSets(ctx->Device(), ctx->DescriptorPool(), 1, &kv.first);
  }
  return true;
}

}  // namespace alcedo
