// AlcedoAndroid - Vulkan-backed image storage for compute kernels.
// Holds a device-local VkBuffer (SSBO) of 32-bit floats mirroring the CPU
// FloatMat, plus a staging buffer for host<->device transfers. Used by
// ImageBuffer's GpuImageWrapper and by the Vulkan pipeline.
// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <vulkan/vulkan.h>

#include <cstdint>
#include <memory>
#include <vector>

namespace alcedo {

class VulkanContext;

class VulkanImage {
 public:
  VulkanImage() = default;
  ~VulkanImage();

  VulkanImage(const VulkanImage&)            = delete;
  VulkanImage& operator=(const VulkanImage&) = delete;
  VulkanImage(VulkanImage&&) noexcept;
  VulkanImage& operator=(VulkanImage&&) noexcept;

  // Allocate a device-local buffer of width*height*channels floats plus a
  // host-visible staging buffer. Requires an initialized VulkanContext.
  bool Create(VulkanContext* ctx, int width, int height, int channels);
  void Destroy();

  bool Valid() const { return device_buffer_ != VK_NULL_HANDLE; }
  int  Width() const { return width_; }
  int  Height() const { return height_; }
  int  Channels() const { return channels_; }
  size_t FloatCount() const {
    return static_cast<size_t>(width_) * height_ * channels_;
  }

  // Upload/download the full float buffer (round-trips through staging).
  bool Upload(const float* host_data);
  bool Download(float* host_data) const;

  // Bind this buffer to a compute descriptor set at the given binding.
  bool BindToDescriptor(VkDescriptorSet set, uint32_t binding) const;

  VkBuffer       DeviceBuffer() const { return device_buffer_; }
  VkDeviceMemory DeviceMemory() const { return device_memory_; }
  VkBuffer       StagingBuffer() const { return staging_buffer_; }
  VkDeviceMemory StagingMemory() const { return staging_memory_; }

 private:
  VulkanContext* ctx_           = nullptr;
  VkBuffer       device_buffer_ = VK_NULL_HANDLE;
  VkDeviceMemory device_memory_ = VK_NULL_HANDLE;
  VkBuffer       staging_buffer_ = VK_NULL_HANDLE;
  VkDeviceMemory staging_memory_ = VK_NULL_HANDLE;
  int            width_         = 0;
  int            height_        = 0;
  int            channels_      = 0;
};

}  // namespace alcedo
