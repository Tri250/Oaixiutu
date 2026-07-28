// AlcedoAndroid - VulkanImage implementation.
// SPDX-License-Identifier: GPL-3.0-only
#include "image/vulkan_image.hpp"

#include <cstring>

#include "utils/app_logging.hpp"
#include "vulkan/context/vulkan_context.hpp"

namespace alcedo {

VulkanImage::~VulkanImage() { Destroy(); }

VulkanImage::VulkanImage(VulkanImage&& other) noexcept { *this = std::move(other); }

VulkanImage& VulkanImage::operator=(VulkanImage&& other) noexcept {
  if (this != &other) {
    Destroy();
    ctx_            = other.ctx_;
    device_buffer_  = other.device_buffer_;
    device_memory_  = other.device_memory_;
    staging_buffer_ = other.staging_buffer_;
    staging_memory_ = other.staging_memory_;
    width_          = other.width_;
    height_         = other.height_;
    channels_       = other.channels_;
    other.ctx_ = nullptr;
    other.device_buffer_ = other.device_memory_ = VK_NULL_HANDLE;
    other.staging_buffer_ = other.staging_memory_ = VK_NULL_HANDLE;
    other.width_ = other.height_ = other.channels_ = 0;
  }
  return *this;
}

bool VulkanImage::Create(VulkanContext* ctx, int width, int height, int channels) {
  Destroy();
  if (!ctx || !ctx->Valid() || width <= 0 || height <= 0 || channels <= 0) return false;
  ctx_ = ctx;
  width_ = width;
  height_ = height;
  channels_ = channels;
  VkDevice dev = ctx->Device();
  VkDeviceSize size = FloatCount() * sizeof(float);

  auto create_buf = [&](VkBufferUsageFlags usage, VkMemoryPropertyFlags props,
                        VkBuffer& buf, VkDeviceMemory& mem) -> bool {
    VkBufferCreateInfo bci{VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO};
    bci.size = size;
    bci.usage = usage;
    bci.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
    if (vkCreateBuffer(dev, &bci, nullptr, &buf) != VK_SUCCESS) return false;
    VkMemoryRequirements req{};
    vkGetBufferMemoryRequirements(dev, buf, &req);
    uint32_t type = 0;
    if (!ctx->FindMemoryType(req.memoryTypeBits, props, &type)) {
      vkDestroyBuffer(dev, buf, nullptr);
      return false;
    }
    VkMemoryAllocateInfo mai{VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO};
    mai.allocationSize = req.size;
    mai.memoryTypeIndex = type;
    if (vkAllocateMemory(dev, &mai, nullptr, &mem) != VK_SUCCESS) {
      vkDestroyBuffer(dev, buf, nullptr);
      return false;
    }
    vkBindBufferMemory(dev, buf, mem, 0);
    return true;
  };

  if (!create_buf(VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT |
                      VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
                  VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, device_buffer_, device_memory_)) {
    ALOGW("VulkanImage: device buffer alloc failed");
    return false;
  }
  if (!create_buf(VK_BUFFER_USAGE_TRANSFER_SRC_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                  VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
                  staging_buffer_, staging_memory_)) {
    ALOGW("VulkanImage: staging buffer alloc failed");
    return false;
  }
  return true;
}

void VulkanImage::Destroy() {
  if (!ctx_ || !ctx_->Valid()) {
    ctx_ = nullptr;
    device_buffer_ = device_memory_ = staging_buffer_ = staging_memory_ = VK_NULL_HANDLE;
    width_ = height_ = channels_ = 0;
    return;
  }
  VkDevice dev = ctx_->Device();
  vkDeviceWaitIdle(dev);
  if (device_buffer_ != VK_NULL_HANDLE) { vkDestroyBuffer(dev, device_buffer_, nullptr); device_buffer_ = VK_NULL_HANDLE; }
  if (device_memory_ != VK_NULL_HANDLE) { vkFreeMemory(dev, device_memory_, nullptr); device_memory_ = VK_NULL_HANDLE; }
  if (staging_buffer_ != VK_NULL_HANDLE) { vkDestroyBuffer(dev, staging_buffer_, nullptr); staging_buffer_ = VK_NULL_HANDLE; }
  if (staging_memory_ != VK_NULL_HANDLE) { vkFreeMemory(dev, staging_memory_, nullptr); staging_memory_ = VK_NULL_HANDLE; }
  width_ = height_ = channels_ = 0;
  ctx_ = nullptr;
}

bool VulkanImage::Upload(const float* host_data) {
  if (!Valid() || !host_data) return false;
  VkDevice dev = ctx_->Device();
  VkDeviceSize size = FloatCount() * sizeof(float);
  void* mapped = nullptr;
  if (vkMapMemory(dev, staging_memory_, 0, size, 0, &mapped) != VK_SUCCESS) return false;
  std::memcpy(mapped, host_data, static_cast<size_t>(size));
  vkUnmapMemory(dev, staging_memory_);

  OneShotCompute scope(ctx_);
  VkCommandBuffer cmd = scope.Cmd();
  VkBufferCopy region{0, 0, size};
  vkCmdCopyBuffer(cmd, staging_buffer_, device_buffer_, 1, &region);
  VkMemoryBarrier mb{VK_STRUCTURE_TYPE_MEMORY_BARRIER};
  mb.srcAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
  mb.dstAccessMask = VK_ACCESS_SHADER_READ_BIT | VK_ACCESS_SHADER_WRITE_BIT;
  vkCmdPipelineBarrier(cmd, VK_PIPELINE_STAGE_TRANSFER_BIT,
                       VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, 0, 1, &mb, 0, nullptr, 0, nullptr);
  return true;
}

bool VulkanImage::Download(float* host_data) const {
  if (!Valid() || !host_data) return false;
  VkDevice dev = ctx_->Device();
  VkDeviceSize size = FloatCount() * sizeof(float);
  OneShotCompute scope(ctx_);
  VkCommandBuffer cmd = scope.Cmd();
  VkBufferCopy region{0, 0, size};
  vkCmdCopyBuffer(cmd, device_buffer_, staging_buffer_, 1, &region);
  VkMemoryBarrier mb{VK_STRUCTURE_TYPE_MEMORY_BARRIER};
  mb.srcAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
  mb.dstAccessMask = VK_ACCESS_HOST_READ_BIT;
  vkCmdPipelineBarrier(cmd, VK_PIPELINE_STAGE_TRANSFER_BIT,
                       VK_PIPELINE_STAGE_HOST_BIT, 0, 1, &mb, 0, nullptr, 0, nullptr);
  scope.~OneShotCompute();  // submit before mapping
  void* mapped = nullptr;
  if (vkMapMemory(dev, staging_memory_, 0, size, 0, &mapped) != VK_SUCCESS) return false;
  std::memcpy(host_data, mapped, static_cast<size_t>(size));
  vkUnmapMemory(dev, staging_memory_);
  return true;
}

bool VulkanImage::BindToDescriptor(VkDescriptorSet set, uint32_t binding) const {
  if (!Valid()) return false;
  VkDescriptorBufferInfo info{};
  info.buffer = device_buffer_;
  info.offset = 0;
  info.range  = VK_WHOLE_SIZE;
  VkWriteDescriptorSet w{VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET};
  w.dstSet = set;
  w.dstBinding = binding;
  w.descriptorCount = 1;
  w.descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
  w.pBufferInfo = &info;
  vkUpdateDescriptorSets(ctx_->Device(), 1, &w, 0, nullptr);
  return true;
}

}  // namespace alcedo
