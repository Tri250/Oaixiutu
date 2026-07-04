#include "gpu_buffer.h"
#include "gpu_context.h"
#include <cstring>

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
{
    other.ahb_ = nullptr;
    other.eglImage_ = EGL_NO_IMAGE_KHR;
    other.glTexture_ = 0;
    other.vkImage_ = 0;
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

        other.ahb_ = nullptr;
        other.eglImage_ = EGL_NO_IMAGE_KHR;
        other.glTexture_ = 0;
        other.vkImage_ = 0;
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

bool GpuBuffer::createVulkanImage(void* /*vkDevice*/, void* /*vkPhysicalDevice*/) {
    // Vulkan AHB integration requires VK_ANDROID_external_memory_android_hardware_buffer
    // Implementation depends on VMA or manual Vulkan memory management
    if (!ahb_) return false;
    GPU_LOGW("Vulkan image from AHardwareBuffer: stub - requires VK_KHR_external_memory_android_hardware_buffer");
    return false;
}

void GpuBuffer::destroyVulkanImage() {
    vkImage_ = 0;
}

} // namespace gpu
} // namespace alcedo