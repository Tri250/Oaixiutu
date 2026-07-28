// AlcedoAndroid - Vulkan device wrapper.
// A thin wrapper over VkDevice + queue selection. Most state lives in
// VulkanContext; this class isolates device-creation helpers and capability
// queries (subgroup size, extensions) used by the pipeline layer.
// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <vulkan/vulkan.h>

#include <cstdint>
#include <string>
#include <vector>

namespace alcedo {

class VulkanDevice {
 public:
  VulkanDevice() = default;
  ~VulkanDevice();

  // Create a logical device on the given physical device, requesting the
  // compute queue family. Vulkan 1.3 is requested when available.
  bool Create(VkPhysicalDevice physical, uint32_t compute_family,
              bool enable_validation = false);
  void Destroy();

  VkDevice         Handle() const { return device_; }
  VkQueue          Queue() const { return queue_; }
  uint32_t         Family() const { return family_; }
  VkPhysicalDevice Physical() const { return physical_; }

  // Subgroup properties (used to size compute workgroups).
  uint32_t SubgroupSize() const { return subgroup_size_; }

  // Check whether a device extension is enabled.
  bool HasExtension(const std::string& name) const;

 private:
  VkPhysicalDevice physical_      = VK_NULL_HANDLE;
  VkDevice         device_        = VK_NULL_HANDLE;
  VkQueue          queue_         = VK_NULL_HANDLE;
  uint32_t         family_        = 0;
  uint32_t         subgroup_size_ = 32;
  std::vector<std::string> extensions_;
};

}  // namespace alcedo
