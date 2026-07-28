// AlcedoAndroid - Vulkan context implementation.
// Owns the VkInstance, VkPhysicalDevice, VkDevice, compute queue, command pool
// and a shared descriptor pool. Exposed as a process-wide singleton.
// SPDX-License-Identifier: GPL-3.0-only
#include "vulkan/context/vulkan_context.hpp"

#include <algorithm>
#include <cstring>
#include <mutex>
#include <set>
#include <vector>

#include "utils/app_logging.hpp"
#include "vulkan/context/vulkan_device.hpp"

namespace alcedo {

namespace {

// Singleton state guarded by a mutex. The raw pointer is intentionally leaked on
// shutdown so any late callers still observe a (destroyed) singleton rather than
// a use-after-free; Shutdown() tears down Vulkan handles but keeps the object.
VulkanContext*             g_instance  = nullptr;
std::mutex                 g_instance_mtx;

VKAPI_ATTR VkBool32 VKAPI_CALL DebugCallback(
    VkDebugUtilsMessageSeverityFlagBitsEXT severity,
    VkDebugUtilsMessageTypeFlagsEXT /*type*/,
    const VkDebugUtilsMessengerCallbackDataEXT* data,
    void* /*user_data*/) {
  if (severity & VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT) {
    ALOGE("[Vulkan Validation] %s", data->pMessage);
  } else if (severity & VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT) {
    ALOGW("[Vulkan Validation] %s", data->pMessage);
  }
  return VK_FALSE;
}

bool PickComputeFamily(VkPhysicalDevice physical, uint32_t* out_family) {
  uint32_t count = 0;
  vkGetPhysicalDeviceQueueFamilyProperties(physical, &count, nullptr);
  std::vector<VkQueueFamilyProperties> families(count);
  vkGetPhysicalDeviceQueueFamilyProperties(physical, &count, families.data());

  for (uint32_t i = 0; i < count; ++i) {
    if (families[i].queueFlags & VK_QUEUE_COMPUTE_BIT) {
      *out_family = i;
      return true;
    }
  }
  return false;
}

int ScorePhysicalDevice(VkPhysicalDevice physical) {
  VkPhysicalDeviceProperties props{};
  vkGetPhysicalDeviceProperties(physical, &props);
  int score = 0;
  switch (props.deviceType) {
    case VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU:   score += 100; break;
    case VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU: score += 80;  break;
    case VK_PHYSICAL_DEVICE_TYPE_VIRTUAL_GPU:    score += 60;  break;
    default: break;
  }
  score += static_cast<int>(props.limits.maxComputeWorkGroupCount[0]);
  return score;
}

}  // namespace

VulkanContext::~VulkanContext() { Shutdown(); }

VulkanContext* VulkanContext::Get() {
  std::lock_guard<std::mutex> lk(g_instance_mtx);
  return g_instance;
}

VulkanContext* VulkanContext::Ensure() {
  std::lock_guard<std::mutex> lk(g_instance_mtx);
  if (!g_instance) {
    g_instance = new VulkanContext();
    g_instance->Initialize();
  }
  return g_instance;
}

bool VulkanContext::Initialize() {
  if (initialized_) return device_ != VK_NULL_HANDLE;

  if (!CreateInstance()) {
    ALOGE("VulkanContext: instance creation failed");
    return false;
  }
  if (!PickPhysicalDevice()) {
    ALOGE("VulkanContext: no suitable physical device");
    Shutdown();
    return false;
  }
  if (!CreateDevice()) {
    ALOGE("VulkanContext: device creation failed");
    Shutdown();
    return false;
  }
  if (!CreateCommandPool()) {
    ALOGE("VulkanContext: command pool creation failed");
    Shutdown();
    return false;
  }
  if (!CreateDescriptorPool()) {
    ALOGE("VulkanContext: descriptor pool creation failed");
    Shutdown();
    return false;
  }
  initialized_ = true;
  ALOGI("VulkanContext initialised: %s (Vulkan %u.%u)",
        physical_props_.deviceName,
        VK_API_VERSION_MAJOR(physical_props_.apiVersion),
        VK_API_VERSION_MINOR(physical_props_.apiVersion));
  return true;
}

void VulkanContext::Shutdown() {
  if (device_ != VK_NULL_HANDLE) vkDeviceWaitIdle(device_);

  if (descriptor_pool_ != VK_NULL_HANDLE) {
    vkDestroyDescriptorPool(device_, descriptor_pool_, nullptr);
    descriptor_pool_ = VK_NULL_HANDLE;
  }
  if (command_pool_ != VK_NULL_HANDLE) {
    vkDestroyCommandPool(device_, command_pool_, nullptr);
    command_pool_ = VK_NULL_HANDLE;
  }
  if (device_ != VK_NULL_HANDLE) {
    vkDestroyDevice(device_, nullptr);
    device_ = VK_NULL_HANDLE;
  }
  physical_device_ = VK_NULL_HANDLE;
  compute_queue_   = VK_NULL_HANDLE;

  if (instance_ != VK_NULL_HANDLE) {
    vkDestroyInstance(instance_, nullptr);
    instance_ = VK_NULL_HANDLE;
  }
  initialized_ = false;
}

bool VulkanContext::CreateInstance() {
  if (instance_ != VK_NULL_HANDLE) return true;

  VkApplicationInfo app{};
  app.sType              = VK_STRUCTURE_TYPE_APPLICATION_INFO;
  app.pApplicationName   = "AlcedoAndroid";
  app.applicationVersion = VK_MAKE_VERSION(1, 0, 0);
  app.pEngineName        = "Alcedo";
  app.engineVersion      = VK_MAKE_VERSION(1, 0, 0);
  app.apiVersion         = VK_API_VERSION_1_3;

  std::vector<const char*> enabled_layers;
  std::vector<const char*> enabled_exts;

  // Determine if validation layers are available in this build.
  uint32_t layer_count = 0;
  vkEnumerateInstanceLayerProperties(&layer_count, nullptr);
  std::vector<VkLayerProperties> layers(layer_count);
  vkEnumerateInstanceLayerProperties(&layer_count, layers.data());
  bool want_validation = false;
#ifndef NDEBUG
  for (const auto& l : layers) {
    if (std::strcmp(l.layerName, "VK_LAYER_KHRONOS_validation") == 0) {
      enabled_layers.push_back("VK_LAYER_KHRONOS_validation");
      want_validation = true;
      break;
    }
  }
#endif

  VkInstanceCreateInfo ci{};
  ci.sType                   = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
  ci.pApplicationInfo        = &app;
  ci.enabledLayerCount       = static_cast<uint32_t>(enabled_layers.size());
  ci.ppEnabledLayerNames     = enabled_layers.data();
  ci.enabledExtensionCount   = static_cast<uint32_t>(enabled_exts.size());
  ci.ppEnabledExtensionNames = enabled_exts.data();

  VkResult res = vkCreateInstance(&ci, nullptr, &instance_);
  if (res != VK_SUCCESS) {
    ALOGE("vkCreateInstance failed: %d", static_cast<int>(res));
    return false;
  }

  if (want_validation) {
    // Best-effort debug messenger; ignore failure.
    VkDebugUtilsMessengerCreateInfoEXT dci{};
    dci.sType = VK_STRUCTURE_TYPE_DEBUG_UTILS_MESSENGER_CREATE_INFO_EXT;
    dci.messageSeverity = VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT |
                          VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT;
    dci.messageType = VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT |
                      VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT |
                      VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT;
    dci.pfnUserCallback = DebugCallback;
    auto fn = reinterpret_cast<PFN_vkCreateDebugUtilsMessengerEXT>(
        vkGetInstanceProcAddr(instance_, "vkCreateDebugUtilsMessengerEXT"));
    if (fn) {
      VkDebugUtilsMessengerEXT messenger = VK_NULL_HANDLE;
      fn(instance_, &dci, nullptr, &messenger);
      // Messenger intentionally leaked for the process lifetime.
    }
  }
  return true;
}

bool VulkanContext::PickPhysicalDevice() {
  uint32_t count = 0;
  vkEnumeratePhysicalDevices(instance_, &count, nullptr);
  if (count == 0) return false;

  std::vector<VkPhysicalDevice> devices(count);
  vkEnumeratePhysicalDevices(instance_, &count, devices.data());

  VkPhysicalDevice best      = VK_NULL_HANDLE;
  int              best_score = -1;
  uint32_t         best_family = 0;
  for (auto dev : devices) {
    uint32_t family = 0;
    if (!PickComputeFamily(dev, &family)) continue;
    int score = ScorePhysicalDevice(dev);
    if (score > best_score) {
      best        = dev;
      best_score  = score;
      best_family = family;
    }
  }
  if (best == VK_NULL_HANDLE) return false;
  physical_device_ = best;
  compute_family_  = best_family;
  vkGetPhysicalDeviceProperties(physical_device_, &physical_props_);
  return true;
}

bool VulkanContext::CreateDevice() {
  float queue_priority = 1.0f;
  VkDeviceQueueCreateInfo qci{};
  qci.sType            = VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO;
  qci.queueFamilyIndex = compute_family_;
  qci.queueCount       = 1;
  qci.pQueuePriorities = &queue_priority;

  // Enable Vulkan 1.3 features used by the compute backends.
  VkPhysicalDeviceVulkan13Features features13{};
  features13.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_3_FEATURES;
  features13.synchronization2 = VK_TRUE;
  features13.dynamicRendering  = VK_TRUE;
  features13.maintenance4     = VK_TRUE;

  VkPhysicalDeviceFeatures2 features2{};
  features2.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FEATURES_2;
  features2.pNext = &features13;

  VkDeviceCreateInfo ci{};
  ci.sType                   = VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO;
  ci.pNext                   = &features2;
  ci.queueCreateInfoCount    = 1;
  ci.pQueueCreateInfos       = &qci;
  ci.enabledExtensionCount   = 0;
  ci.ppEnabledExtensionNames = nullptr;
  ci.pEnabledFeatures        = nullptr;  // features go via pNext (features2)

  if (vkCreateDevice(physical_device_, &ci, nullptr, &device_) != VK_SUCCESS) {
    ALOGE("vkCreateDevice failed");
    return false;
  }
  vkGetDeviceQueue(device_, compute_family_, 0, &compute_queue_);
  return device_ != VK_NULL_HANDLE;
}

bool VulkanContext::CreateCommandPool() {
  VkCommandPoolCreateInfo ci{};
  ci.sType            = VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO;
  ci.flags            = VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT;
  ci.queueFamilyIndex = compute_family_;
  return vkCreateCommandPool(device_, &ci, nullptr, &command_pool_) == VK_SUCCESS;
}

bool VulkanContext::CreateDescriptorPool() {
  VkDescriptorPoolSize sizes[2]{};
  sizes[0].type            = VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
  sizes[0].descriptorCount = 1024;
  sizes[1].type            = VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER;
  sizes[1].descriptorCount = 256;

  VkDescriptorPoolCreateInfo ci{};
  ci.sType         = VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO;
  ci.flags         = VK_DESCRIPTOR_POOL_CREATE_FREE_DESCRIPTOR_SET_BIT;
  ci.maxSets       = 512;
  ci.poolSizeCount = 2;
  ci.pPoolSizes    = sizes;
  return vkCreateDescriptorPool(device_, &ci, nullptr, &descriptor_pool_) == VK_SUCCESS;
}

bool VulkanContext::FindMemoryType(uint32_t type_bits, VkMemoryPropertyFlags props,
                                   uint32_t* out_index) const {
  VkPhysicalDeviceMemoryProperties mem{};
  vkGetPhysicalDeviceMemoryProperties(physical_device_, &mem);
  for (uint32_t i = 0; i < mem.memoryTypeCount; ++i) {
    if ((type_bits & (1u << i)) && (mem.memoryTypes[i].propertyFlags & props) == props) {
      *out_index = i;
      return true;
    }
  }
  return false;
}

VkCommandBuffer VulkanContext::BeginOneShotCompute() {
  VkCommandBufferAllocateInfo ai{};
  ai.sType              = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO;
  ai.commandPool        = command_pool_;
  ai.level             = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
  ai.commandBufferCount = 1;
  VkCommandBuffer cmd = VK_NULL_HANDLE;
  vkAllocateCommandBuffers(device_, &ai, &cmd);

  VkCommandBufferBeginInfo bi{};
  bi.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO;
  bi.flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
  vkBeginCommandBuffer(cmd, &bi);
  return cmd;
}

void VulkanContext::EndAndSubmitOneShot(VkCommandBuffer cmd) {
  vkEndCommandBuffer(cmd);

  std::lock_guard<std::mutex> lk(submit_mtx_);
  VkSubmitInfo si{};
  si.sType              = VK_STRUCTURE_TYPE_SUBMIT_INFO;
  si.commandBufferCount = 1;
  si.pCommandBuffers    = &cmd;
  vkQueueSubmit(compute_queue_, 1, &si, VK_NULL_HANDLE);
  vkQueueWaitIdle(compute_queue_);
  vkFreeCommandBuffers(device_, command_pool_, 1, &cmd);
}

VkDescriptorSet VulkanContext::AllocateDescriptorSet(VkDescriptorSetLayout layout) {
  VkDescriptorSetAllocateInfo ai{};
  ai.sType              = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO;
  ai.descriptorPool     = descriptor_pool_;
  ai.descriptorSetCount = 1;
  ai.pSetLayouts        = &layout;
  VkDescriptorSet set = VK_NULL_HANDLE;
  if (vkAllocateDescriptorSets(device_, &ai, &set) != VK_SUCCESS) {
    ALOGW("VulkanContext: descriptor set allocation failed");
    return VK_NULL_HANDLE;
  }
  return set;
}

// ---- OneShotCompute RAII ----
OneShotCompute::OneShotCompute(VulkanContext* ctx) : ctx_(ctx) {
  cmd_ = ctx_ ? ctx_->BeginOneShotCompute() : VK_NULL_HANDLE;
}
OneShotCompute::~OneShotCompute() {
  if (ctx_ && cmd_ != VK_NULL_HANDLE) ctx_->EndAndSubmitOneShot(cmd_);
}

}  // namespace alcedo
