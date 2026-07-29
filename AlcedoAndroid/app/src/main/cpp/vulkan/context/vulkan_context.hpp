// AlcedoAndroid - Vulkan device / context management.
// Owns the VkInstance, VkPhysicalDevice, VkDevice, compute queue and command
// pool. Exposed as a process-wide singleton because Android typically has a
// single Vulkan device. The pipeline and image layers obtain the context via
// VulkanContext::Get().
// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <vulkan/vulkan.h>

#include <cstdint>
#include <memory>
#include <mutex>
#include <string>
#include <vector>

namespace alcedo {

class VulkanDevice;

class VulkanContext {
 public:
  ~VulkanContext();
  VulkanContext(const VulkanContext&)            = delete;
  VulkanContext& operator=(const VulkanContext&) = delete;

  // Initialize (idempotent). Returns true on success or if already initialized.
  bool Initialize();
  void Shutdown();

  // Process-wide singleton. Returns nullptr if not initialized.
  static VulkanContext* Get();
  // Lazily create + initialize the singleton.
  static VulkanContext* Ensure();

  bool         Valid() const { return device_ != VK_NULL_HANDLE; }
  VkInstance       Instance() const { return instance_; }
  VkPhysicalDevice Physical() const { return physical_device_; }
  VkPhysicalDeviceProperties PhysicalProps() const { return physical_props_; }
  VkDevice         Device() const { return device_; }
  uint32_t         ComputeFamily() const { return compute_family_; }
  VkQueue          ComputeQueue() const { return compute_queue_; }
  VkCommandPool    CommandPool() const { return command_pool_; }
  VkDescriptorPool DescriptorPool() const { return descriptor_pool_; }

  // Memory type index helper. Returns false if no type satisfies the mask+flags.
  bool FindMemoryType(uint32_t type_bits, VkMemoryPropertyFlags props,
                      uint32_t* out_index) const;

  // One-shot compute command buffer helpers.
  VkCommandBuffer BeginOneShotCompute();
  void            EndAndSubmitOneShot(VkCommandBuffer cmd);

  // Allocate a descriptor set from the shared pool. Returns VK_NULL_HANDLE on failure.
  VkDescriptorSet AllocateDescriptorSet(VkDescriptorSetLayout layout);

 private:
  VulkanContext() = default;
  bool CreateInstance();
  bool PickPhysicalDevice();
  bool CreateDevice();
  bool CreateCommandPool();
  bool CreateDescriptorPool();

  VkInstance                  instance_         = VK_NULL_HANDLE;
  VkPhysicalDevice            physical_device_  = VK_NULL_HANDLE;
  VkPhysicalDeviceProperties  physical_props_{};
  VkDevice                    device_           = VK_NULL_HANDLE;
  uint32_t                    compute_family_   = 0;
  VkQueue                     compute_queue_    = VK_NULL_HANDLE;
  VkCommandPool               command_pool_     = VK_NULL_HANDLE;
  VkDescriptorPool            descriptor_pool_  = VK_NULL_HANDLE;
  VkDebugUtilsMessengerEXT    debug_messenger_  = VK_NULL_HANDLE;
  bool                        initialized_      = false;

  std::mutex                  submit_mtx_;
};

// RAII one-shot compute scope.
class OneShotCompute {
 public:
  explicit OneShotCompute(VulkanContext* ctx);
  ~OneShotCompute();
  VkCommandBuffer Cmd() const { return cmd_; }
  // Submit the recorded command buffer early without destroying the scope. The
  // destructor becomes a no-op once Submit() has been called, which lets callers
  // force an ordered submit before reusing resources (e.g. mapping staging
  // memory) without triggering a double-destruct at end of scope.
  void Submit();
 private:
  VulkanContext* ctx_;
  VkCommandBuffer cmd_;
};

}  // namespace alcedo
