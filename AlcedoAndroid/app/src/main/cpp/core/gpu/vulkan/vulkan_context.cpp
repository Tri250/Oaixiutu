#include "vulkan_context.h"
#include <dlfcn.h>
#include <cstring>
#include <vector>
#include <algorithm>

namespace alcedo {
namespace gpu {

// ============================================================
// Vulkan API function pointers (loaded dynamically)
// ============================================================
namespace {

// We define minimal Vulkan API function pointers for dynamic loading
// In production, use volk or the Vulkan loader directly

typedef void* PFN_vkGetInstanceProcAddr(void* instance, const char* pName);

// Instance functions
typedef int VkResult;
#define VK_SUCCESS 0

typedef void* PFN_vkCreateInstance(const void* pCreateInfo, const void* pAllocator, void** pInstance);
typedef void  PFN_vkDestroyInstance(void* instance, const void* pAllocator);
typedef VkResult PFN_vkEnumeratePhysicalDevices(void* instance, uint32_t* pPhysicalDeviceCount, void** pPhysicalDevices);
typedef void  PFN_vkGetPhysicalDeviceProperties(void* physicalDevice, void* pProperties);
typedef void  PFN_vkGetPhysicalDeviceFeatures(void* physicalDevice, void* pFeatures);
typedef void  PFN_vkGetPhysicalDeviceQueueFamilyProperties(void* physicalDevice, uint32_t* pQueueFamilyPropertyCount, void* pQueueFamilyProperties);
typedef void  PFN_vkGetPhysicalDeviceMemoryProperties(void* physicalDevice, void* pMemoryProperties);

// Device functions
typedef VkResult PFN_vkCreateDevice(void* physicalDevice, const void* pCreateInfo, const void* pAllocator, void** pDevice);
typedef void  PFN_vkDestroyDevice(void* device, const void* pAllocator);
typedef void  PFN_vkGetDeviceQueue(void* device, uint32_t queueFamilyIndex, uint32_t queueIndex, void** pQueue);

// Command pool
typedef VkResult PFN_vkCreateCommandPool(void* device, const void* pCreateInfo, const void* pAllocator, void** pCommandPool);
typedef void  PFN_vkDestroyCommandPool(void* device, void* commandPool, const void* pAllocator);

// Descriptor pool
typedef VkResult PFN_vkCreateDescriptorPool(void* device, const void* pCreateInfo, const void* pAllocator, void** pDescriptorPool);
typedef void  PFN_vkDestroyDescriptorPool(void* device, void* descriptorPool, const void* pAllocator);

// Device wait
typedef VkResult PFN_vkDeviceWaitIdle(void* device);

struct VulkanAPI {
    void* libHandle = nullptr;

    PFN_vkGetInstanceProcAddr* vkGetInstanceProcAddr = nullptr;
    PFN_vkCreateInstance* vkCreateInstance = nullptr;
    PFN_vkDestroyInstance* vkDestroyInstance = nullptr;
    PFN_vkEnumeratePhysicalDevices* vkEnumeratePhysicalDevices = nullptr;
    PFN_vkGetPhysicalDeviceProperties* vkGetPhysicalDeviceProperties = nullptr;
    PFN_vkGetPhysicalDeviceFeatures* vkGetPhysicalDeviceFeatures = nullptr;
    PFN_vkGetPhysicalDeviceQueueFamilyProperties* vkGetPhysicalDeviceQueueFamilyProperties = nullptr;
    PFN_vkCreateDevice* vkCreateDevice = nullptr;
    PFN_vkDestroyDevice* vkDestroyDevice = nullptr;
    PFN_vkGetDeviceQueue* vkGetDeviceQueue = nullptr;
    PFN_vkCreateCommandPool* vkCreateCommandPool = nullptr;
    PFN_vkDestroyCommandPool* vkDestroyCommandPool = nullptr;
    PFN_vkCreateDescriptorPool* vkCreateDescriptorPool = nullptr;
    PFN_vkDestroyDescriptorPool* vkDestroyDescriptorPool = nullptr;
    PFN_vkDeviceWaitIdle* vkDeviceWaitIdle = nullptr;

    bool load() {
        libHandle = dlopen("libvulkan.so", RTLD_NOW);
        if (!libHandle) {
            GPU_LOGE("Failed to load libvulkan.so");
            return false;
        }

        vkGetInstanceProcAddr = reinterpret_cast<PFN_vkGetInstanceProcAddr*>(
            dlsym(libHandle, "vkGetInstanceProcAddr"));
        if (!vkGetInstanceProcAddr) {
            GPU_LOGE("Failed to find vkGetInstanceProcAddr");
            return false;
        }

        // Load instance-level functions
        vkCreateInstance = reinterpret_cast<PFN_vkCreateInstance*>(
            vkGetInstanceProcAddr(nullptr, "vkCreateInstance"));
        vkDestroyInstance = reinterpret_cast<PFN_vkDestroyInstance*>(
            vkGetInstanceProcAddr(nullptr, "vkDestroyInstance"));
        vkEnumeratePhysicalDevices = reinterpret_cast<PFN_vkEnumeratePhysicalDevices*>(
            vkGetInstanceProcAddr(nullptr, "vkEnumeratePhysicalDevices"));

        return true;
    }

    bool loadDeviceFunctions(void* instance) {
        if (!instance) return false;

        vkGetPhysicalDeviceProperties = reinterpret_cast<PFN_vkGetPhysicalDeviceProperties*>(
            vkGetInstanceProcAddr(instance, "vkGetPhysicalDeviceProperties"));
        vkGetPhysicalDeviceFeatures = reinterpret_cast<PFN_vkGetPhysicalDeviceFeatures*>(
            vkGetInstanceProcAddr(instance, "vkGetPhysicalDeviceFeatures"));
        vkGetPhysicalDeviceQueueFamilyProperties = reinterpret_cast<PFN_vkGetPhysicalDeviceQueueFamilyProperties*>(
            vkGetInstanceProcAddr(instance, "vkGetPhysicalDeviceQueueFamilyProperties"));
        vkCreateDevice = reinterpret_cast<PFN_vkCreateDevice*>(
            vkGetInstanceProcAddr(instance, "vkCreateDevice"));
        vkDestroyDevice = reinterpret_cast<PFN_vkDestroyDevice*>(
            vkGetInstanceProcAddr(instance, "vkDestroyDevice"));
        vkGetDeviceQueue = reinterpret_cast<PFN_vkGetDeviceQueue*>(
            vkGetInstanceProcAddr(instance, "vkGetDeviceQueue"));
        vkCreateCommandPool = reinterpret_cast<PFN_vkCreateCommandPool*>(
            vkGetInstanceProcAddr(instance, "vkCreateCommandPool"));
        vkDestroyCommandPool = reinterpret_cast<PFN_vkDestroyCommandPool*>(
            vkGetInstanceProcAddr(instance, "vkDestroyCommandPool"));
        vkCreateDescriptorPool = reinterpret_cast<PFN_vkCreateDescriptorPool*>(
            vkGetInstanceProcAddr(instance, "vkCreateDescriptorPool"));
        vkDestroyDescriptorPool = reinterpret_cast<PFN_vkDestroyDescriptorPool*>(
            vkGetInstanceProcAddr(instance, "vkDestroyDescriptorPool"));
        vkDeviceWaitIdle = reinterpret_cast<PFN_vkDeviceWaitIdle*>(
            vkGetInstanceProcAddr(instance, "vkDeviceWaitIdle"));

        return true;
    }

    void unload() {
        if (libHandle) {
            dlclose(libHandle);
            libHandle = nullptr;
        }
    }
};

static VulkanAPI g_vk;

// Vulkan struct layouts (simplified for dynamic loading)
// In production, these would be the actual Vulkan structs from vulkan.h

struct VkApplicationInfo {
    uint32_t sType = 0; // VK_STRUCTURE_TYPE_APPLICATION_INFO = 0
    const void* pNext = nullptr;
    const char* pApplicationName = nullptr;
    uint32_t applicationVersion = 0;
    const char* pEngineName = nullptr;
    uint32_t engineVersion = 0;
    uint32_t apiVersion = 0;
};

struct VkInstanceCreateInfo {
    uint32_t sType = 1; // VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO = 1
    const void* pNext = nullptr;
    uint32_t flags = 0;
    const VkApplicationInfo* pApplicationInfo = nullptr;
    uint32_t enabledLayerCount = 0;
    const char* const* ppEnabledLayerNames = nullptr;
    uint32_t enabledExtensionCount = 0;
    const char* const* ppEnabledExtensionNames = nullptr;
};

struct VkDeviceQueueCreateInfo {
    uint32_t sType = 2; // VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO = 2
    const void* pNext = nullptr;
    uint32_t flags = 0;
    uint32_t queueFamilyIndex = 0;
    uint32_t queueCount = 0;
    const float* pQueuePriorities = nullptr;
};

struct VkDeviceCreateInfo {
    uint32_t sType = 3; // VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO = 3
    const void* pNext = nullptr;
    uint32_t flags = 0;
    uint32_t queueCreateInfoCount = 0;
    const VkDeviceQueueCreateInfo* pQueueCreateInfos = nullptr;
    uint32_t enabledLayerCount = 0;
    const char* const* ppEnabledLayerNames = nullptr;
    uint32_t enabledExtensionCount = 0;
    const char* const* ppEnabledExtensionNames = nullptr;
    const void* pEnabledFeatures = nullptr;
};

struct VkCommandPoolCreateInfo {
    uint32_t sType = 25; // VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO = 25
    const void* pNext = nullptr;
    uint32_t flags = 0;
    uint32_t queueFamilyIndex = 0;
};

struct VkDescriptorPoolSize {
    uint32_t type = 0;
    uint32_t descriptorCount = 0;
};

struct VkDescriptorPoolCreateInfo {
    uint32_t sType = 30; // VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO = 30
    const void* pNext = nullptr;
    uint32_t flags = 0;
    uint32_t maxSets = 0;
    uint32_t poolSizeCount = 0;
    const VkDescriptorPoolSize* pPoolSizes = nullptr;
};

struct VkPhysicalDeviceProperties {
    uint32_t apiVersion = 0;
    uint32_t driverVersion = 0;
    uint32_t vendorID = 0;
    uint32_t deviceID = 0;
    uint32_t deviceType = 0;
    char deviceName[256] = {};
    uint8_t pipelineCacheUUID[16] = {};
    // limits...
    struct {
        uint32_t maxImageDimension2D = 0;
        uint32_t maxComputeWorkGroupCount[3] = {};
        uint32_t maxComputeWorkGroupInvocations = 0;
        uint32_t maxComputeWorkGroupSize[3] = {};
        uint32_t maxComputeSharedMemorySize = 0;
    } limits;
};

struct VkQueueFamilyProperties {
    uint32_t queueFlags = 0;
    uint32_t queueCount = 0;
    uint32_t timestampValidBits = 0;
    // ...
};

struct VkPhysicalDeviceFeatures {
    uint32_t shaderFloat64 = 0;
    uint32_t shaderInt64 = 0;
    uint32_t shaderInt16 = 0;
    // Many more...
};

// Constants
#define VK_QUEUE_COMPUTE_BIT 0x00000004
#define VK_QUEUE_GRAPHICS_BIT 0x00000001
#define VK_QUEUE_TRANSFER_BIT 0x00000008

} // anonymous namespace

// ============================================================
// VulkanContext implementation
// ============================================================

VulkanContext::VulkanContext() = default;

VulkanContext::~VulkanContext() {
    destroy();
}

bool VulkanContext::init(void* /*nativeWindow*/) {
    if (initialized_) return true;

    if (!g_vk.load()) {
        GPU_LOGE("Vulkan loader not available");
        return false;
    }

    if (!createInstance()) {
        GPU_LOGE("Failed to create Vulkan instance");
        return false;
    }

    if (!g_vk.loadDeviceFunctions(instance_)) {
        GPU_LOGE("Failed to load Vulkan device functions");
        return false;
    }

    if (!pickPhysicalDevice()) {
        GPU_LOGE("Failed to pick Vulkan physical device");
        return false;
    }

    if (!createLogicalDevice()) {
        GPU_LOGE("Failed to create Vulkan logical device");
        return false;
    }

    if (!createCommandPool()) {
        GPU_LOGE("Failed to create Vulkan command pool");
        return false;
    }

    if (!createDescriptorPool()) {
        GPU_LOGE("Failed to create Vulkan descriptor pool");
        return false;
    }

    queryDeviceInfo();
    initialized_ = true;

    GPU_LOGI("Vulkan context created: %s", vkDeviceInfo_.deviceName.c_str());
    return true;
}

void VulkanContext::destroy() {
    if (descriptorPool_ && g_vk.vkDestroyDescriptorPool) {
        g_vk.vkDestroyDescriptorPool(device_, descriptorPool_, nullptr);
        descriptorPool_ = nullptr;
    }

    if (commandPool_ && g_vk.vkDestroyCommandPool) {
        g_vk.vkDestroyCommandPool(device_, commandPool_, nullptr);
        commandPool_ = nullptr;
    }

    if (device_ && g_vk.vkDestroyDevice) {
        g_vk.vkDestroyDevice(device_, nullptr);
        device_ = nullptr;
    }

    if (instance_ && g_vk.vkDestroyInstance) {
        g_vk.vkDestroyInstance(instance_, nullptr);
        instance_ = nullptr;
    }

    g_vk.unload();
    initialized_ = false;
}

bool VulkanContext::isAvailable() const {
    return initialized_ && device_ != nullptr;
}

GpuDeviceInfo VulkanContext::getDeviceInfo() const {
    return deviceInfo_;
}

void VulkanContext::makeCurrent() {
    // Vulkan doesn't have a "make current" concept like OpenGL
    // No-op for Vulkan
}

void VulkanContext::swapBuffers() {
    // No-op for compute-only context
    // Swapchain presentation would be handled separately
}

void VulkanContext::finish() {
    if (device_ && g_vk.vkDeviceWaitIdle) {
        g_vk.vkDeviceWaitIdle(device_);
    }
}

bool VulkanContext::createInstance() {
    VkApplicationInfo appInfo;
    appInfo.sType = 0;
    appInfo.pApplicationName = "AlcedoStudio";
    appInfo.applicationVersion = VK_MAKE_VERSION(1, 0, 0);
    appInfo.pEngineName = "AlcedoEngine";
    appInfo.engineVersion = VK_MAKE_VERSION(1, 0, 0);
    appInfo.apiVersion = VK_API_VERSION_1_1;

    const char* extensions[] = {
        "VK_KHR_get_physical_device_properties2",
    };

    VkInstanceCreateInfo createInfo;
    createInfo.sType = 1;
    createInfo.pApplicationInfo = &appInfo;
    createInfo.enabledExtensionCount = 1;
    createInfo.ppEnabledExtensionNames = extensions;
    createInfo.enabledLayerCount = 0;

    VkResult result = g_vk.vkCreateInstance(&createInfo, nullptr, &instance_);
    if (result != VK_SUCCESS) {
        GPU_LOGE("vkCreateInstance failed: %d", result);
        return false;
    }

    return true;
}

bool VulkanContext::pickPhysicalDevice() {
    uint32_t deviceCount = 0;
    g_vk.vkEnumeratePhysicalDevices(instance_, &deviceCount, nullptr);

    if (deviceCount == 0) {
        GPU_LOGE("No Vulkan physical devices found");
        return false;
    }

    std::vector<void*> devices(deviceCount);
    g_vk.vkEnumeratePhysicalDevices(instance_, &deviceCount, devices.data());

    // Pick the first device with compute capability
    for (uint32_t i = 0; i < deviceCount; i++) {
        void* device = devices[i];

        VkPhysicalDeviceProperties props;
        g_vk.vkGetPhysicalDeviceProperties(device, &props);

        uint32_t queueFamilyCount = 0;
        g_vk.vkGetPhysicalDeviceQueueFamilyProperties(device, &queueFamilyCount, nullptr);

        std::vector<VkQueueFamilyProperties> queueFamilies(queueFamilyCount);
        g_vk.vkGetPhysicalDeviceQueueFamilyProperties(device, &queueFamilyCount, queueFamilies.data());

        queueFamily_.computeIndex = -1;
        queueFamily_.graphicsIndex = -1;
        queueFamily_.transferIndex = -1;

        for (uint32_t j = 0; j < queueFamilyCount; j++) {
            if (queueFamilies[j].queueFlags & VK_QUEUE_COMPUTE_BIT) {
                queueFamily_.computeIndex = static_cast<int32_t>(j);
            }
            if (queueFamilies[j].queueFlags & VK_QUEUE_GRAPHICS_BIT) {
                queueFamily_.graphicsIndex = static_cast<int32_t>(j);
            }
            if (queueFamilies[j].queueFlags & VK_QUEUE_TRANSFER_BIT) {
                queueFamily_.transferIndex = static_cast<int32_t>(j);
            }
        }

        if (queueFamily_.computeIndex >= 0) {
            physicalDevice_ = device;
            vkDeviceInfo_.deviceName = props.deviceName;
            vkDeviceInfo_.apiVersion = props.apiVersion;
            vkDeviceInfo_.driverVersion = props.driverVersion;
            vkDeviceInfo_.vendorID = props.vendorID;
            vkDeviceInfo_.deviceID = props.deviceID;

            GPU_LOGI("Selected Vulkan device: %s (compute queue: %d)",
                     vkDeviceInfo_.deviceName.c_str(), queueFamily_.computeIndex);
            return true;
        }
    }

    GPU_LOGE("No Vulkan device with compute capability found");
    return false;
}

bool VulkanContext::createLogicalDevice() {
    float queuePriority = 1.0f;

    VkDeviceQueueCreateInfo queueCreateInfo;
    queueCreateInfo.sType = 2;
    queueCreateInfo.queueFamilyIndex = static_cast<uint32_t>(queueFamily_.computeIndex);
    queueCreateInfo.queueCount = 1;
    queueCreateInfo.pQueuePriorities = &queuePriority;

    const char* deviceExtensions[] = {
        "VK_KHR_storage_buffer_storage_class",
        "VK_KHR_16bit_storage",
        "VK_KHR_shader_float16_int8",
    };

    VkPhysicalDeviceFeatures deviceFeatures = {};

    VkDeviceCreateInfo createInfo;
    createInfo.sType = 3;
    createInfo.queueCreateInfoCount = 1;
    createInfo.pQueueCreateInfos = &queueCreateInfo;
    createInfo.enabledExtensionCount = 3;
    createInfo.ppEnabledExtensionNames = deviceExtensions;
    createInfo.pEnabledFeatures = &deviceFeatures;

    VkResult result = g_vk.vkCreateDevice(physicalDevice_, &createInfo, nullptr, &device_);
    if (result != VK_SUCCESS) {
        GPU_LOGE("vkCreateDevice failed: %d", result);
        return false;
    }

    g_vk.vkGetDeviceQueue(device_, static_cast<uint32_t>(queueFamily_.computeIndex), 0, &computeQueue_);

    return true;
}

bool VulkanContext::createCommandPool() {
    VkCommandPoolCreateInfo poolInfo;
    poolInfo.sType = 25;
    poolInfo.queueFamilyIndex = static_cast<uint32_t>(queueFamily_.computeIndex);
    poolInfo.flags = 0; // VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT = 0x1

    VkResult result = g_vk.vkCreateCommandPool(device_, &poolInfo, nullptr, &commandPool_);
    if (result != VK_SUCCESS) {
        GPU_LOGE("vkCreateCommandPool failed: %d", result);
        return false;
    }

    return true;
}

bool VulkanContext::createDescriptorPool() {
    VkDescriptorPoolSize poolSizes[3] = {};
    poolSizes[0].type = 0; // VK_DESCRIPTOR_TYPE_STORAGE_BUFFER = 0
    poolSizes[0].descriptorCount = 16;
    poolSizes[1].type = 1; // VK_DESCRIPTOR_TYPE_STORAGE_IMAGE = 1
    poolSizes[1].descriptorCount = 16;
    poolSizes[2].type = 2; // VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER = 2
    poolSizes[2].descriptorCount = 16;

    VkDescriptorPoolCreateInfo poolInfo;
    poolInfo.sType = 30;
    poolInfo.maxSets = 32;
    poolInfo.poolSizeCount = 3;
    poolInfo.pPoolSizes = poolSizes;

    VkResult result = g_vk.vkCreateDescriptorPool(device_, &poolInfo, nullptr, &descriptorPool_);
    if (result != VK_SUCCESS) {
        GPU_LOGE("vkCreateDescriptorPool failed: %d", result);
        return false;
    }

    return true;
}

bool VulkanContext::queryDeviceInfo() {
    deviceInfo_.name = vkDeviceInfo_.deviceName;
    deviceInfo_.vendor = std::to_string(vkDeviceInfo_.vendorID);
    deviceInfo_.version = std::to_string(vkDeviceInfo_.apiVersion);

    VkPhysicalDeviceProperties props;
    g_vk.vkGetPhysicalDeviceProperties(physicalDevice_, &props);

    deviceInfo_.maxWorkGroupSize[0] = static_cast<int>(props.limits.maxComputeWorkGroupSize[0]);
    deviceInfo_.maxWorkGroupSize[1] = static_cast<int>(props.limits.maxComputeWorkGroupSize[1]);
    deviceInfo_.maxWorkGroupSize[2] = static_cast<int>(props.limits.maxComputeWorkGroupSize[2]);
    deviceInfo_.maxWorkGroupInvocations = static_cast<int>(props.limits.maxComputeWorkGroupInvocations);
    deviceInfo_.maxTextureSize = static_cast<int>(props.limits.maxImageDimension2D);
    deviceInfo_.maxComputeSharedMemorySize = static_cast<int>(props.limits.maxComputeSharedMemorySize);
    deviceInfo_.computeUnits = static_cast<int>(props.limits.maxComputeWorkGroupCount[0]);
    deviceInfo_.supportsComputeShaders = true;

    return true;
}

} // namespace gpu
} // namespace alcedo