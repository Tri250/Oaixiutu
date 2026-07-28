// AlcedoAndroid - Fused kernel stream for Vulkan.
// Mirrors the desktop StaticKernelStream: stages that are individually
// streamable are fused into a single compute dispatch per stage group. The
// VulkanKernelStream holds the per-stage VulkanPipeline + a parameter block
// uploaded as push constants / a UBO, and runs them in order over the input
// VulkanImage, leaving the result in the output image (often the same buffer).
// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <cstdint>
#include <memory>
#include <string>
#include <vector>

namespace alcedo {

class VulkanContext;
class VulkanPipeline;
class VulkanImage;
struct OperatorParams;

// A single entry in the fused stream: a named program + its binding slots.
struct VulkanKernelEntry {
  std::string                name;
  std::unique_ptr<VulkanPipeline> pipeline;
  uint32_t                   binding_count = 2;  // input + output
  uint32_t                   push_constant_bytes = 0;
};

class VulkanKernelStream {
 public:
  VulkanKernelStream() = default;
  ~VulkanKernelStream();

  // Append a kernel program (takes ownership). Programs are dispatched in
  // append order over the same image pair.
  void Append(std::unique_ptr<VulkanKernelEntry> entry);
  void Clear();
  size_t Size() const { return entries_.size(); }

  // Execute the fused stream. input and output may be the same VulkanImage for
  // in-place stages; a scratch buffer is used when a kernel needs both.
  bool Execute(VulkanContext* ctx, VulkanImage* input, VulkanImage* output,
               const OperatorParams& params, int workgroup_x, int workgroup_y) const;

  // Access for pipeline layer.
  std::vector<std::unique_ptr<VulkanKernelEntry>>& Entries() { return entries_; }

 private:
  std::vector<std::unique_ptr<VulkanKernelEntry>> entries_;
};

}  // namespace alcedo
