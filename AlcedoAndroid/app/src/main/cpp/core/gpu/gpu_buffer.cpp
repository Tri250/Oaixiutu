#include "gpu_buffer.h"
#include "gpu_context.h"
#include <vulkan/vulkan.h>
#include <vulkan/vulkan_android.h>
#include <cstring>
#include <dlfcn.h>

namespace alcedo {
namespace gpu {

GpuBuffer::~GpuBuffer() {
    release();
}

GpuBuffer::GpuBuffer(GpuBuffer&& other) noexcept
    : ahb_(other.ahb_)
    , ahbDesc_(other.ahbDesc_)
    , desc_(other.desc_)
    , rowStride_(other.rowStride_)
    , totalBytes_(other.totalBytes_)
    , locked_(other.locked_)
    , eglImage_(other.eglImage_)
    , glTexture_(other.glTexture_)
    , vkImage_(other.vkImage_)
    , vkDevice_(other.vkDevice_)
    , vkDeviceMemory_(other.vkDeviceMemory_)
{
    other.ahb_ = nullptr;
    other.eglImage_ = EGL_NO_IMAGE_KHR;
    other.glTexture_ = 0;
    other.vkImage_ = 0;
    other.vkDevice_ = nullptr;
    other.vkDeviceMemory_ = 0;
    other.locked_ = false;
}

GpuBuffer& GpuBuffer::operator=(GpuBuffer&& other) noexcept {
    if (this != &other) {
        release();
        ahb_ = other.ahb_;
        ahbDesc_ = other.ahbDesc_;
        desc_ = other.desc_;
        rowStride_ = other.rowStride_;
        totalBytes_ = other.totalBytes_;
        locked_ = other.locked_;
        eglImage_ = other.eglImage_;
        glTexture_ = other.glTexture_;
        vkImage_ = other.vkImage_;
        vkDevice_ = other.vkDevice_;
        vkDeviceMemory_ = other.vkDeviceMemory_;

        other.ahb_ = nullptr;
        other.eglImage_ = EGL_NO_IMAGE_KHR;
        other.glTexture_ = 0;
        other.vkImage_ = 0;
        other.vkDevice_ = nullptr;
        other.vkDeviceMemory_ = 0;
        other.locked_ = false;
    }
    return *this;
}

bool GpuBuffer::allocate(const GpuBufferDesc& desc) {
    release();
    desc_ = desc;

    ahbDesc_.width = desc.width;
    ahbDesc_.height = desc.height;
    ahbDesc_.layers = 1;
    ahbDesc_.format = gpuBufferFormatToAHB(desc.format);
    ahbDesc_.usage = desc.usage;
    ahbDesc_.stride = 0;
    ahbDesc_.rfu0 = 0;
    ahbDesc_.rfu1 = 0;

    int result = AHardwareBuffer_allocate(&ahbDesc_, &ahb_);
    if (result != 0 || ahb_ == nullptr) {
        GPU_LOGE("Failed to allocate AHardwareBuffer: %d", result);
        return false;
    }

    AHardwareBuffer_describe(ahb_, &ahbDesc_);
    rowStride_ = ahbDesc_.stride;
    totalBytes_ = (desc_.format == GpuBufferFormat::RGBA8
                   || desc_.format == GpuBufferFormat::RGBA16F
                   || desc_.format == GpuBufferFormat::RGBA32F)
        ? 4 : 1;
    totalBytes_ = rowStride_ * desc.height;

    GPU_LOGI("Allocated GpuBuffer %dx%d, stride=%zu, bytes=%zu",
             desc.width, desc.height, rowStride_, totalBytes_);
    return true;
}

void GpuBuffer::release() {
    destroyGlTexture();
    destroyEglImage();
    destroyVulkanImage();

    if (locked_) {
        unlock();
    }
    if (ahb_) {
        AHardwareBuffer_release(ahb_);
        ahb_ = nullptr;
    }
    rowStride_ = 0;
    totalBytes_ = 0;
}

bool GpuBuffer::lockRead(void** outData, int32_t* outStride) {
    if (!ahb_) return false;
    int result = AHardwareBuffer_lock(ahb_, AHARDWAREBUFFER_USAGE_CPU_READ_OFTEN, -1, nullptr, outData);
    if (result != 0) {
        GPU_LOGE("AHardwareBuffer lockRead failed: %d", result);
        return false;
    }
    locked_ = true;
    if (outStride) *outStride = static_cast<int32_t>(rowStride_);
    return true;
}

bool GpuBuffer::lockWrite(void** outData, int32_t* outStride) {
    if (!ahb_) return false;
    int result = AHardwareBuffer_lock(ahb_, AHARDWAREBUFFER_USAGE_CPU_WRITE_OFTEN, -1, nullptr, outData);
    if (result != 0) {
        GPU_LOGE("AHardwareBuffer lockWrite failed: %d", result);
        return false;
    }
    locked_ = true;
    if (outStride) *outStride = static_cast<int32_t>(rowStride_);
    return true;
}

bool GpuBuffer::unlock() {
    if (!ahb_ || !locked_) return false;
    int result = AHardwareBuffer_unlock(ahb_, nullptr);
    if (result != 0) {
        GPU_LOGE("AHardwareBuffer unlock failed: %d", result);
        return false;
    }
    locked_ = false;
    return true;
}

EGLImageKHR GpuBuffer::createEglImage(EGLDisplay display) {
    if (eglImage_ != EGL_NO_IMAGE_KHR) return eglImage_;
    if (!ahb_) return EGL_NO_IMAGE_KHR;

    EGLint eglAttribs[] = {
        EGL_IMAGE_PRESERVED_KHR, EGL_TRUE,
        EGL_NONE
    };

    eglImage_ = eglCreateImageKHR(display, EGL_NO_CONTEXT,
                                  EGL_NATIVE_BUFFER_ANDROID,
                                  eglGetNativeClientBufferANDROID(ahb_),
                                  eglAttribs);
    if (eglImage_ == EGL_NO_IMAGE_KHR) {
        GPU_LOGE("Failed to create EGLImage from AHardwareBuffer: 0x%x", eglGetError());
    }
    return eglImage_;
}

void GpuBuffer::destroyEglImage() {
    if (eglImage_ != EGL_NO_IMAGE_KHR) {
        EGLDisplay display = eglGetCurrentDisplay();
        if (display != EGL_NO_DISPLAY) {
            eglDestroyImageKHR(display, eglImage_);
        }
        eglImage_ = EGL_NO_IMAGE_KHR;
    }
}

GLuint GpuBuffer::createGlTexture(GLenum target) {
    if (glTexture_ != 0) return glTexture_;
    if (!ahb_) return 0;

    EGLDisplay display = eglGetCurrentDisplay();
    if (display == EGL_NO_DISPLAY) return 0;

    createEglImage(display);
    if (eglImage_ == EGL_NO_IMAGE_KHR) return 0;

    glGenTextures(1, &glTexture_);
    glBindTexture(target, glTexture_);
    glEGLImageTargetTexture2DOES(target, eglImage_);
    glTexParameteri(target, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(target, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(target, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(target, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glBindTexture(target, 0);

    GPU_LOGI("Created GL texture %u from EGLImage", glTexture_);
    return glTexture_;
}

void GpuBuffer::destroyGlTexture() {
    if (glTexture_ != 0) {
        glDeleteTextures(1, &glTexture_);
        glTexture_ = 0;
    }
}

namespace {

VkFormat gpuBufferFormatToVk(GpuBufferFormat fmt) {
    switch (fmt) {
        case GpuBufferFormat::RGBA8:  return VK_FORMAT_R8G8B8A8_UNORM;
        case GpuBufferFormat::RGBA16F: return VK_FORMAT_R16G16B16A16_SFLOAT;
        case GpuBufferFormat::RGBA32F: return VK_FORMAT_R32G32B32A32_SFLOAT;
        case GpuBufferFormat::R8:     return VK_FORMAT_R8_UNORM;
        case GpuBufferFormat::R16F:   return VK_FORMAT_R16_SFLOAT;
        case GpuBufferFormat::R32F:   return VK_FORMAT_R32_SFLOAT;
        case GpuBufferFormat::RG8:    return VK_FORMAT_R8G8_UNORM;
        case GpuBufferFormat::RG16F:  return VK_FORMAT_R16G16_SFLOAT;
        case GpuBufferFormat::RG32F:  return VK_FORMAT_R32G32_SFLOAT;
    }
    return VK_FORMAT_R8G8B8A8_UNORM;
}

VkImageUsageFlags ahbUsageToVkImageUsage(uint32_t ahbUsage) {
    VkImageUsageFlags vkUsage = VK_IMAGE_USAGE_SAMPLED_BIT;
    if (ahbUsage & AHARDWAREBUFFER_USAGE_GPU_COLOR_OUTPUT) {
        vkUsage |= VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;
    }
    if (ahbUsage & AHARDWAREBUFFER_USAGE_CPU_READ_OFTEN ||
        ahbUsage & AHARDWAREBUFFER_USAGE_CPU_WRITE_OFTEN) {
        vkUsage |= VK_IMAGE_USAGE_TRANSFER_SRC_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT;
    }
    return vkUsage;
}

} // anonymous namespace

bool GpuBuffer::createVulkanImage(void* vkDevice, void* vkPhysicalDevice) {
    if (!ahb_) return false;

    VkDevice device = static_cast<VkDevice>(vkDevice);
    VkPhysicalDevice physicalDevice = static_cast<VkPhysicalDevice>(vkPhysicalDevice);

    // Determine Vulkan format and usage from the buffer description
    VkFormat vkFormat = gpuBufferFormatToVk(desc_.format);
    VkImageUsageFlags vkUsage = ahbUsageToVkImageUsage(desc_.usage);

    // 1. Create VkImage backed by external AHardwareBuffer memory
    VkExternalMemoryImageCreateInfo externalMemInfo{};
    externalMemInfo.sType = VK_STRUCTURE_TYPE_EXTERNAL_MEMORY_IMAGE_CREATE_INFO;
    externalMemInfo.pNext = nullptr;
    externalMemInfo.handleTypes = VK_EXTERNAL_MEMORY_HANDLE_TYPE_ANDROID_HARDWARE_BUFFER_BIT_ANDROID;

    VkImageCreateInfo imageInfo{};
    imageInfo.sType = VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO;
    imageInfo.pNext = &externalMemInfo;
    imageInfo.imageType = VK_IMAGE_TYPE_2D;
    imageInfo.format = vkFormat;
    imageInfo.extent = { desc_.width, desc_.height, 1 };
    imageInfo.mipLevels = 1;
    imageInfo.arrayLayers = 1;
    imageInfo.samples = VK_SAMPLE_COUNT_1_BIT;
    imageInfo.tiling = VK_IMAGE_TILING_OPTIMAL;
    imageInfo.usage = vkUsage;
    imageInfo.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
    imageInfo.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;

    VkImage image = VK_NULL_HANDLE;
    VkResult result = vkCreateImage(device, &imageInfo, nullptr, &image);
    if (result != VK_SUCCESS) {
        GPU_LOGE("createVulkanImage: vkCreateImage failed (%d)", result);
        return false;
    }

    // 2. Query AHardwareBuffer memory properties to determine allocation size and memory type
    PFN_vkGetAndroidHardwareBufferPropertiesANDROID vkGetAHBProps =
        reinterpret_cast<PFN_vkGetAndroidHardwareBufferPropertiesANDROID>(
            vkGetDeviceProcAddr(device, "vkGetAndroidHardwareBufferPropertiesANDROID"));
    if (!vkGetAHBProps) {
        GPU_LOGE("createVulkanImage: vkGetAndroidHardwareBufferPropertiesANDROID extension not available");
        vkDestroyImage(device, image, nullptr);
        return false;
    }

    VkAndroidHardwareBufferPropertiesANDROID ahbProps{};
    ahbProps.sType = VK_STRUCTURE_TYPE_ANDROID_HARDWARE_BUFFER_PROPERTIES_ANDROID;
    ahbProps.pNext = nullptr;

    result = vkGetAHBProps(device, ahb_, &ahbProps);
    if (result != VK_SUCCESS) {
        GPU_LOGE("createVulkanImage: vkGetAndroidHardwareBufferPropertiesANDROID failed (%d)", result);
        vkDestroyImage(device, image, nullptr);
        return false;
    }

    // 3. Find a compatible memory type (prefer device-local)
    VkPhysicalDeviceMemoryProperties memProps;
    vkGetPhysicalDeviceMemoryProperties(physicalDevice, &memProps);

    uint32_t memoryTypeIndex = UINT32_MAX;
    for (uint32_t i = 0; i < memProps.memoryTypeCount; i++) {
        if ((ahbProps.memoryTypeBits & (1u << i)) &&
            (memProps.memoryTypes[i].propertyFlags & VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT)) {
            memoryTypeIndex = i;
            break;
        }
    }
    if (memoryTypeIndex == UINT32_MAX) {
        // Fallback: pick any compatible memory type
        for (uint32_t i = 0; i < 32; i++) {
            if (ahbProps.memoryTypeBits & (1u << i)) {
                memoryTypeIndex = i;
                break;
            }
        }
    }
    if (memoryTypeIndex == UINT32_MAX) {
        GPU_LOGE("createVulkanImage: no compatible memory type found (typeBits=0x%x)",
                 ahbProps.memoryTypeBits);
        vkDestroyImage(device, image, nullptr);
        return false;
    }

    // 4. Import AHardwareBuffer memory with a dedicated allocation
    VkImportAndroidHardwareBufferInfoANDROID importInfo{};
    importInfo.sType = VK_STRUCTURE_TYPE_IMPORT_ANDROID_HARDWARE_BUFFER_INFO_ANDROID;
    importInfo.pNext = nullptr;
    importInfo.buffer = ahb_;

    VkMemoryDedicatedAllocateInfo dedicatedInfo{};
    dedicatedInfo.sType = VK_STRUCTURE_TYPE_MEMORY_DEDICATED_ALLOCATE_INFO;
    dedicatedInfo.pNext = &importInfo;
    dedicatedInfo.image = image;
    dedicatedInfo.buffer = VK_NULL_HANDLE;

    VkMemoryAllocateInfo allocInfo{};
    allocInfo.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
    allocInfo.pNext = &dedicatedInfo;
    allocInfo.allocationSize = ahbProps.allocationSize;
    allocInfo.memoryTypeIndex = memoryTypeIndex;

    VkDeviceMemory memory = VK_NULL_HANDLE;
    result = vkAllocateMemory(device, &allocInfo, nullptr, &memory);
    if (result != VK_SUCCESS) {
        GPU_LOGE("createVulkanImage: vkAllocateMemory failed (%d)", result);
        vkDestroyImage(device, image, nullptr);
        return false;
    }

    // 5. Bind the imported memory to the image
    result = vkBindImageMemory(device, image, memory, 0);
    if (result != VK_SUCCESS) {
        GPU_LOGE("createVulkanImage: vkBindImageMemory failed (%d)", result);
        vkFreeMemory(device, memory, nullptr);
        vkDestroyImage(device, image, nullptr);
        return false;
    }

    // Store handles for later cleanup
    vkImage_ = reinterpret_cast<uint64_t>(image);
    vkDeviceMemory_ = reinterpret_cast<uint64_t>(memory);
    vkDevice_ = vkDevice;

    GPU_LOGI("createVulkanImage: created VkImage from AHardwareBuffer %ux%u (format=%d, memoryType=%u)",
             desc_.width, desc_.height, static_cast<int>(vkFormat), memoryTypeIndex);

    return true;
}

void GpuBuffer::destroyVulkanImage() {
    if (vkImage_ != 0 || vkDeviceMemory_ != 0) {
        VkDevice device = static_cast<VkDevice>(vkDevice_);
        if (device != VK_NULL_HANDLE) {
            if (vkImage_ != 0) {
                vkDestroyImage(device, reinterpret_cast<VkImage>(vkImage_), nullptr);
            }
            if (vkDeviceMemory_ != 0) {
                vkFreeMemory(device, reinterpret_cast<VkDeviceMemory>(vkDeviceMemory_), nullptr);
            }
        }
    }
    vkImage_ = 0;
    vkDeviceMemory_ = 0;
    vkDevice_ = nullptr;
}

} // namespace gpu
} // namespace alcedo