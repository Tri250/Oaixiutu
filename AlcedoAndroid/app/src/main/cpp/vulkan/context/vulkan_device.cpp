// AlcedoAndroid - Vulkan device wrapper implementation.
// Isolates logical-device creation and capability queries (subgroup size,
// extensions) used by the pipeline layer. Self-contained: a standalone
// VulkanDevice may be constructed for testing; VulkanContext reuses the same
// device-creation recipe internally.
// SPDX-License-Identifier: GPL-3.0-only
#include "vulkan/context/vulkan_device.hpp"

#include <cstring>
#include <vector>

#include "utils/app_logging.hpp"

namespace alcedo {

namespace {

bool QuerySubgroupSize(VkPhysicalDevice physical, uint32_t* out_size) {
  // Vulkan 1.3 core provides VkPhysicalDeviceSubgroupProperties via pNext of
  // VkPhysicalDeviceProperties2.
  VkPhysicalDeviceSubgroupProperties sg{};
  sg.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SUBGROUP_PROPERTIES;
  VkPhysicalDeviceProperties2 props2{};
  props2.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PROPERTIES_2;
  props2.pNext = &sg;
  vkGetPhysicalDeviceProperties2(physical, &props2);
  if (sg.subgroupSize == 0) return false;
  *out_size = sg.subgroupSize;
  return true;
}

}  // namespace

VulkanDevice::~VulkanDevice() { Destroy(); }

bool VulkanDevice::Create(VkPhysicalDevice physical, uint32_t compute_family,
                          bool /*enable_validation*/) {
  Destroy();
  if (physical == VK_NULL_HANDLE) return false;
  physical_ = physical;
  family_   = compute_family;

  uint32_t ext_count = 0;
  vkEnumerateDeviceExtensionProperties(physical, nullptr, &ext_count, nullptr);
  std::vector<VkExtensionProperties> avail(ext_count);
  vkEnumerateDeviceExtensionProperties(physical, nullptr, &ext_count, avail.data());

  // Vulkan 1.3 feature chain.
  VkPhysicalDeviceVulkan13Features features13{};
  features13.sType          = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_3_FEATURES;
  features13.synchronization2 = VK_TRUE;
  features13.dynamicRendering  = VK_TRUE;
  features13.maintenance4     = VK_TRUE;

  VkPhysicalDeviceFeatures2 features2{};
  features2.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FEATURES_2;
  features2.pNext = &features13;

  float queue_priority = 1.0f;
  VkDeviceQueueCreateInfo qci{};
  qci.sType            = VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO;
  qci.queueFamilyIndex = compute_family;
  qci.queueCount       = 1;
  qci.pQueuePriorities = &queue_priority;

  VkDeviceCreateInfo ci{};
  ci.sType                = VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO;
  ci.pNext                = &features2;
  ci.queueCreateInfoCount = 1;
  ci.pQueueCreateInfos    = &qci;
  ci.pEnabledFeatures     = nullptr;

  if (vkCreateDevice(physical, &ci, nullptr, &device_) != VK_SUCCESS) {
    ALOGE("VulkanDevice: vkCreateDevice failed");
    return false;
  }
  vkGetDeviceQueue(device_, compute_family, 0, &queue_);

  uint32_t sg = 32;
  if (QuerySubgroupSize(physical_, &sg)) {
    subgroup_size_ = sg;
  }

  // Record enabled extensions for HasExtension queries.
  for (const auto& e : avail) {
    extensions_.emplace_back(e.extensionName);
  }

  return true;
}

void VulkanDevice::Destroy() {
  if (device_ != VK_NULL_HANDLE) {
    vkDeviceWaitIdle(device_);
    vkDestroyDevice(device_, nullptr);
    device_ = VK_NULL_HANDLE;
  }
  queue_    = VK_NULL_HANDLE;
  physical_ = VK_NULL_HANDLE;
  extensions_.clear();
}

bool VulkanDevice::HasExtension(const std::string& name) const {
  for (const auto& e : extensions_) {
    if (e == name) return true;
  }
  return false;
}

}  // namespace alcedo
