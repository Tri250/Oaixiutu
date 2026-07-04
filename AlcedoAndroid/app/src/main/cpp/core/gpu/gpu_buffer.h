#pragma once

#include <cstdint>
#include <cstddef>
#include <memory>
#include <string>
#include <android/hardware_buffer.h>
#include <android/hardware_buffer_jni.h>
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES3/gl31.h>
#include <GLES3/gl3ext.h>

namespace alcedo {
namespace gpu {

enum class GpuBufferFormat {
    RGBA8 = 0,
    RGBA16F = 1,
    RGBA32F = 2,
    R8 = 3,
    R16F = 4,
    R32F = 5,
    RG8 = 6,
    RG16F = 7,
    RG32F = 8,
};

struct GpuBufferDesc {
    uint32_t width = 0;
    uint32_t height = 0;
    GpuBufferFormat format = GpuBufferFormat::RGBA8;
    uint32_t usage = AHARDWAREBUFFER_USAGE_GPU_SAMPLED_IMAGE
                   | AHARDWAREBUFFER_USAGE_GPU_COLOR_OUTPUT
                   | AHARDWAREBUFFER_USAGE_CPU_READ_OFTEN
                   | AHARDWAREBUFFER_USAGE_CPU_WRITE_OFTEN;
};

inline uint32_t gpuBufferFormatToAHB(GpuBufferFormat fmt) {
    switch (fmt) {
        case GpuBufferFormat::RGBA8:  return AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM;
        case GpuBufferFormat::RGBA16F: return AHARDWAREBUFFER_FORMAT_R16G16B16A16_FLOAT;
        case GpuBufferFormat::RGBA32F: return AHARDWAREBUFFER_FORMAT_R32G32B32A32_FLOAT;
        case GpuBufferFormat::R8:     return AHARDWAREBUFFER_FORMAT_R8_UNORM;
        case GpuBufferFormat::R16F:   return AHARDWAREBUFFER_FORMAT_R16_FLOAT;
        case GpuBufferFormat::R32F:   return AHARDWAREBUFFER_FORMAT_R32_FLOAT;
        case GpuBufferFormat::RG8:    return AHARDWAREBUFFER_FORMAT_R8G8_UNORM;
        case GpuBufferFormat::RG16F:  return AHARDWAREBUFFER_FORMAT_R16G16_FLOAT;
        case GpuBufferFormat::RG32F:  return AHARDWAREBUFFER_FORMAT_R32G32_FLOAT;
    }
    return AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM;
}

inline GLenum gpuBufferFormatToGLInternal(GpuBufferFormat fmt) {
    switch (fmt) {
        case GpuBufferFormat::RGBA8:  return GL_RGBA8;
        case GpuBufferFormat::RGBA16F: return GL_RGBA16F;
        case GpuBufferFormat::RGBA32F: return GL_RGBA32F;
        case GpuBufferFormat::R8:     return GL_R8;
        case GpuBufferFormat::R16F:   return GL_R16F;
        case GpuBufferFormat::R32F:   return GL_R32F;
        case GpuBufferFormat::RG8:    return GL_RG8;
        case GpuBufferFormat::RG16F:  return GL_RG16F;
        case GpuBufferFormat::RG32F:  return GL_RG32F;
    }
    return GL_RGBA8;
}

inline size_t gpuBufferFormatBytesPerPixel(GpuBufferFormat fmt) {
    switch (fmt) {
        case GpuBufferFormat::RGBA8:  return 4;
        case GpuBufferFormat::RGBA16F: return 8;
        case GpuBufferFormat::RGBA32F: return 16;
        case GpuBufferFormat::R8:     return 1;
        case GpuBufferFormat::R16F:   return 2;
        case GpuBufferFormat::R32F:   return 4;
        case GpuBufferFormat::RG8:    return 2;
        case GpuBufferFormat::RG16F:  return 4;
        case GpuBufferFormat::RG32F:  return 8;
    }
    return 4;
}

class GpuBuffer {
public:
    GpuBuffer() = default;
    ~GpuBuffer();

    GpuBuffer(const GpuBuffer&) = delete;
    GpuBuffer& operator=(const GpuBuffer&) = delete;
    GpuBuffer(GpuBuffer&& other) noexcept;
    GpuBuffer& operator=(GpuBuffer&& other) noexcept;

    bool allocate(const GpuBufferDesc& desc);
    void release();

    bool isValid() const { return ahb_ != nullptr; }
    bool isLocked() const { return locked_; }

    // CPU data transfer
    bool lockRead(void** outData, int32_t* outStride = nullptr);
    bool lockWrite(void** outData, int32_t* outStride = nullptr);
    bool unlock();

    // EGLImage
    EGLImageKHR createEglImage(EGLDisplay display);
    void destroyEglImage();
    EGLImageKHR getEglImage() const { return eglImage_; }

    // GL texture
    GLuint createGlTexture(GLenum target = GL_TEXTURE_2D);
    void destroyGlTexture();
    GLuint getGlTexture() const { return glTexture_; }

    // Vulkan
    bool createVulkanImage(void* vkDevice, void* vkPhysicalDevice);
    void destroyVulkanImage();
    uint64_t getVulkanImage() const { return vkImage_; }

    // Accessors
    uint32_t width() const { return desc_.width; }
    uint32_t height() const { return desc_.height; }
    GpuBufferFormat format() const { return desc_.format; }
    size_t rowStride() const { return rowStride_; }
    size_t totalBytes() const { return totalBytes_; }
    AHardwareBuffer* nativeHandle() const { return ahb_; }

    const GpuBufferDesc& desc() const { return desc_; }

private:
    AHardwareBuffer* ahb_ = nullptr;
    AHardwareBuffer_Desc ahbDesc_{};
    GpuBufferDesc desc_;
    size_t rowStride_ = 0;
    size_t totalBytes_ = 0;
    bool locked_ = false;

    EGLImageKHR eglImage_ = EGL_NO_IMAGE_KHR;
    GLuint glTexture_ = 0;
    uint64_t vkImage_ = 0;
};

using GpuBufferPtr = std::shared_ptr<GpuBuffer>;

} // namespace gpu
} // namespace alcedo