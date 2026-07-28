// AlcedoAndroid - Vulkan scope analyzer.
// Dispatches the "scope_analyzer" compute program for the histogram and falls
// back to the CPU analyzer for waveform/vectorscope and whenever the Vulkan
// path is unavailable (no SPIR-V registered, allocation failure, etc.).
// SPDX-License-Identifier: GPL-3.0-only
#include "edit/scope/scope_analyzer.hpp"

#include <vulkan/vulkan.h>

#include <cstdint>
#include <cstring>
#include <memory>
#include <mutex>
#include <utility>
#include <vector>

#include "utils/app_logging.hpp"
#include "vulkan/context/vulkan_context.hpp"
#include "vulkan/pipeline/vulkan_pipeline.hpp"
#include "vulkan/pipeline/vulkan_program_registry.hpp"

namespace alcedo {
namespace {

constexpr const char* kScopeProgram = "scope_analyzer";

struct ScopePushConstants {
  uint32_t width;
  uint32_t height;
  uint32_t channels;
  uint32_t bins;
  uint32_t pad0;
};

// Simple host-visible storage buffer used for both the input image upload and
// the histogram readback. Kept host-visible for simplicity (no staging copy).
struct HostBuffer {
  VkBuffer       buffer = VK_NULL_HANDLE;
  VkDeviceMemory memory = VK_NULL_HANDLE;
  VkDeviceSize   size   = 0;
  void*          mapped = nullptr;
};

void DestroyHostBuffer(VulkanContext* ctx, HostBuffer& b) {
  if (!ctx) return;
  VkDevice dev = ctx->Device();
  if (b.mapped) vkUnmapMemory(dev, b.memory);
  if (b.buffer) vkDestroyBuffer(dev, b.buffer, nullptr);
  if (b.memory) vkFreeMemory(dev, b.memory, nullptr);
  b = HostBuffer{};
}

bool CreateHostBuffer(VulkanContext* ctx, VkDeviceSize size, HostBuffer& out) {
  if (!ctx || !ctx->Valid() || size == 0) return false;
  VkDevice dev = ctx->Device();
  VkBufferCreateInfo bci{VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO};
  bci.size = size;
  bci.usage = VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT;
  bci.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
  if (vkCreateBuffer(dev, &bci, nullptr, &out.buffer) != VK_SUCCESS) return false;
  VkMemoryRequirements req;
  vkGetBufferMemoryRequirements(dev, out.buffer, &req);
  uint32_t type_index = 0;
  if (!ctx->FindMemoryType(req.memoryTypeBits,
                           VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
                           &type_index)) {
    vkDestroyBuffer(dev, out.buffer, nullptr);
    out.buffer = VK_NULL_HANDLE;
    return false;
  }
  VkMemoryAllocateInfo mai{VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO};
  mai.allocationSize = req.size;
  mai.memoryTypeIndex = type_index;
  if (vkAllocateMemory(dev, &mai, nullptr, &out.memory) != VK_SUCCESS) {
    vkDestroyBuffer(dev, out.buffer, nullptr);
    out.buffer = VK_NULL_HANDLE;
    return false;
  }
  vkBindBufferMemory(dev, out.buffer, out.memory, 0);
  if (vkMapMemory(dev, out.memory, 0, size, 0, &out.mapped) != VK_SUCCESS) {
    vkFreeMemory(dev, out.memory, nullptr);
    vkDestroyBuffer(dev, out.buffer, nullptr);
    out = HostBuffer{};
    return false;
  }
  out.size = size;
  return true;
}

class VulkanScopeAnalyzer final : public IScopeAnalyzer {
 public:
  VulkanScopeAnalyzer() : ctx_(VulkanContext::Get()) {
    cpu_fallback_ = CreateCpuScopeAnalyzer();
  }

  void SubmitFrame(const FinalDisplayFrameView& frame, const ScopeRequest& request) override {
    std::lock_guard<std::mutex> lock(mutex_);
    // Try the Vulkan histogram path; on any failure, delegate entirely to CPU.
    if (frame && frame.image && ctx_ && ctx_->Valid() &&
        TryVulkanHistogram(frame, request)) {
      // Waveform/vectorscope still computed on CPU for now.
      ScopeOutputSet cpu_out = cpu_fallback_->GetLatestOutput();
      cpu_fallback_->SubmitFrame(frame, request);
      ScopeOutputSet fresh = cpu_fallback_->GetLatestOutput();
      // Merge: keep Vulkan histogram, take CPU waveform/vectorscope.
      fresh.histogram_counts = std::move(output_.histogram_counts);
      fresh.histogram_bins   = output_.histogram_bins;
      fresh.histogram_valid  = output_.histogram_valid;
      output_                = std::move(fresh);
    } else {
      cpu_fallback_->SubmitFrame(frame, request);
      output_ = cpu_fallback_->GetLatestOutput();
    }
  }

  auto GetLatestOutput() -> ScopeOutputSet override {
    std::lock_guard<std::mutex> lock(mutex_);
    return output_;
  }

  void ResizeResources(const ScopeRequest& request) override {
    std::lock_guard<std::mutex> lock(mutex_);
    cpu_fallback_->ResizeResources(request);
    EnsureResources(request);
  }

  void ReleaseResources() override {
    std::lock_guard<std::mutex> lock(mutex_);
    cpu_fallback_->ReleaseResources();
    DestroyProgram();
    output_ = ScopeOutputSet{};
  }

 private:
  void EnsureResources(const ScopeRequest& request) {
    if (output_.histogram_bins != request.histogram_bins || output_.histogram_counts.empty()) {
      output_.histogram_counts.assign(static_cast<size_t>(request.histogram_bins) * 3U, 0U);
      output_.histogram_bins = request.histogram_bins;
    }
  }

  bool EnsureProgram() {
    if (pipeline_ && pipeline_->Valid()) return true;
    if (!ctx_ || !ctx_->Valid()) return false;
    auto spirv = VulkanProgramRegistry::Instance().Get(kScopeProgram);
    if (!spirv.has_value() || spirv->empty()) return false;
    pipeline_ = std::make_unique<VulkanPipeline>();
    // 2 bindings: input image floats, output histogram uints.
    if (!pipeline_->Create(ctx_, kScopeProgram, *spirv, 2, sizeof(ScopePushConstants))) {
      ALOGW("VulkanScopeAnalyzer: failed to build scope_analyzer pipeline");
      pipeline_.reset();
      return false;
    }
    return true;
  }

  void DestroyProgram() {
    if (pipeline_) {
      pipeline_->Destroy();
      pipeline_.reset();
    }
  }

  bool TryVulkanHistogram(const FinalDisplayFrameView& frame, const ScopeRequest& request) {
    if (!EnsureProgram()) return false;
    EnsureResources(request);
    // Ensure CPU data is available for upload.
    frame.image->SyncToCPU();
    const FloatMat& mat = frame.image->GetCPUData();
    if (mat.Empty()) return false;

    VkDevice dev = ctx_->Device();
    const VkDeviceSize image_bytes =
        static_cast<VkDeviceSize>(mat.Total()) * sizeof(float);
    const VkDeviceSize hist_bytes =
        static_cast<VkDeviceSize>(request.histogram_bins) * 3U * sizeof(uint32_t);

    HostBuffer in_buf{}, out_buf{};
    if (!CreateHostBuffer(ctx_, image_bytes, in_buf)) return false;
    if (!CreateHostBuffer(ctx_, hist_bytes, out_buf)) {
      DestroyHostBuffer(ctx_, in_buf);
      return false;
    }
    std::memcpy(in_buf.mapped, mat.Data(), static_cast<size_t>(image_bytes));
    std::memset(out_buf.mapped, 0, static_cast<size_t>(hist_bytes));

    VkDescriptorSetLayout set_layout = pipeline_->Program()->set_layout_;
    VkDescriptorSet set = ctx_->AllocateDescriptorSet(set_layout);
    if (set == VK_NULL_HANDLE) {
      DestroyHostBuffer(ctx_, in_buf);
      DestroyHostBuffer(ctx_, out_buf);
      return false;
    }
    VkDescriptorBufferInfo in_info{in_buf.buffer, 0, image_bytes};
    VkDescriptorBufferInfo out_info{out_buf.buffer, 0, hist_bytes};
    VkWriteDescriptorSet writes[2] = {};
    writes[0].sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;
    writes[0].dstSet = set;
    writes[0].dstBinding = 0;
    writes[0].descriptorCount = 1;
    writes[0].descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
    writes[0].pBufferInfo = &in_info;
    writes[1] = writes[0];
    writes[1].dstBinding = 1;
    writes[1].pBufferInfo = &out_info;
    vkUpdateDescriptorSets(dev, 2, writes, 0, nullptr);

    ScopePushConstants pc{static_cast<uint32_t>(mat.Width()),
                          static_cast<uint32_t>(mat.Height()),
                          static_cast<uint32_t>(mat.Channels()),
                          static_cast<uint32_t>(request.histogram_bins), 0};

    OneShotCompute scope(ctx_);
    VkCommandBuffer cmd = scope.Cmd();
    pipeline_->Dispatch(cmd, set, &pc,
                        (mat.Width() + 15) / 16, (mat.Height() + 15) / 16, 1);
    // scope's destructor submits.

    // Read back.
    std::memcpy(output_.histogram_counts.data(), out_buf.mapped,
                static_cast<size_t>(hist_bytes));
    output_.histogram_valid = true;

    DestroyHostBuffer(ctx_, in_buf);
    DestroyHostBuffer(ctx_, out_buf);
    return true;
  }

  VulkanContext*                       ctx_ = nullptr;
  std::shared_ptr<IScopeAnalyzer>      cpu_fallback_;
  std::unique_ptr<VulkanPipeline>      pipeline_;
  ScopeOutputSet                       output_;
  std::mutex                           mutex_;
};

}  // namespace

auto CreateVulkanScopeAnalyzer() -> std::shared_ptr<IScopeAnalyzer> {
  if (auto* ctx = VulkanContext::Get(); !ctx || !ctx->Valid()) {
    return CreateCpuScopeAnalyzer();
  }
  return std::make_shared<VulkanScopeAnalyzer>();
}

}  // namespace alcedo
