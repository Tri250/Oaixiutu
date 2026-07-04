#pragma once

#include "../gpu_context.h"

// Forward declare Vulkan types to avoid header dependency
// In production, include <vulkan/vulkan.h> here
// We use forward declarations to keep compilation light without Vulkan SDK

namespace alcedo {
namespace gpu {

// Vulkan type wrappers (opaque handles)
// These map to: VkInstance, VkPhysicalDevice, VkDevice, VkQueue, etc.
using VkInstance_T = void*;
using VkPhysicalDevice_T = void*;
using VkDevice_T = void*;
using VkQueue_T = void*;
using VkCommandPool_T = void*;
using VkDescriptorPool_T = void*;

struct VulkanDeviceInfo {
    std::string deviceName;
    std::string driverVersion;
    uint32_t apiVersion = 0;
    uint32_t vendorID = 0;
    uint32_t deviceID = 0;
};

struct VulkanQueueFamily {
    int32_t computeIndex = -1;
    int32_t graphicsIndex = -1;
    int32_t transferIndex = -1;
};

class VulkanContext : public GpuContext {
public:
    VulkanContext();
    ~VulkanContext() override;

    bool init(void* nativeWindow = nullptr) override;
    void destroy() override;
    bool isAvailable() const override;
    GpuBackend backend() const override { return GpuBackend::VULKAN; }
    GpuDeviceInfo getDeviceInfo() const override;

    void makeCurrent() override;
    void swapBuffers() override;
    void finish() override;

    // Vulkan-specific accessors
    VkInstance_T       getInstance() const       { return instance_; }
    VkPhysicalDevice_T getPhysicalDevice() const { return physicalDevice_; }
    VkDevice_T         getDevice() const         { return device_; }
    VkQueue_T          getComputeQueue() const   { return computeQueue_; }
    VkCommandPool_T    getCommandPool() const    { return commandPool_; }
    VkDescriptorPool_T getDescriptorPool() const { return descriptorPool_; }

    const VulkanQueueFamily& getQueueFamily() const { return queueFamily_; }
    const VulkanDeviceInfo&  getVulkanDeviceInfo() const { return vkDeviceInfo_; }

    uint32_t getComputeQueueFamilyIndex() const {
        return static_cast<uint32_t>(queueFamily_.computeIndex);
    }

private:
    bool createInstance();
    bool pickPhysicalDevice();
    bool createLogicalDevice();
    bool createCommandPool();
    bool createDescriptorPool();
    bool queryDeviceInfo();

    VkInstance_T       instance_ = nullptr;
    VkPhysicalDevice_T physicalDevice_ = nullptr;
    VkDevice_T         device_ = nullptr;
    VkQueue_T          computeQueue_ = nullptr;
    VkCommandPool_T    commandPool_ = nullptr;
    VkDescriptorPool_T descriptorPool_ = nullptr;

    VulkanQueueFamily queueFamily_;
    VulkanDeviceInfo  vkDeviceInfo_;
};

} // namespace gpu
} // namespace alcedo