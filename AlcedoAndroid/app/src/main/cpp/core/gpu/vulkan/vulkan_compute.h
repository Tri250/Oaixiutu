#pragma once

#include "vulkan_context.h"
#include <string>
#include <vector>
#include <unordered_map>
#include <memory>

namespace alcedo {
namespace gpu {

// ============================================================
// Vulkan buffer (wraps VkBuffer + VkDeviceMemory)
// ============================================================
class VulkanBuffer {
public:
    VulkanBuffer() = default;
    ~VulkanBuffer() { release(); }

    VulkanBuffer(const VulkanBuffer&) = delete;
    VulkanBuffer& operator=(const VulkanBuffer&) = delete;

    bool create(VulkanContext* ctx, size_t size, uint32_t usageFlags);
    void release();
    bool isValid() const { return buffer_ != nullptr; }

    void* map(size_t size = 0, size_t offset = 0);
    void unmap();

    void upload(const void* data, size_t size);
    void download(void* data, size_t size);

    void* getBuffer() const { return buffer_; }
    size_t size() const { return size_; }

private:
    VulkanContext* context_ = nullptr;
    void* buffer_ = nullptr;
    void* memory_ = nullptr;
    size_t size_ = 0;
};

// ============================================================
// Vulkan compute pipeline
// ============================================================
class VulkanComputePipeline {
public:
    VulkanComputePipeline() = default;
    ~VulkanComputePipeline() { release(); }

    VulkanComputePipeline(const VulkanComputePipeline&) = delete;
    VulkanComputePipeline& operator=(const VulkanComputePipeline&) = delete;

    struct PipelineDesc {
        std::string shaderSource;     // GLSL compute source (will be compiled to SPIR-V)
        const uint32_t* spirvCode = nullptr;
        size_t spirvSize = 0;
        std::vector<VulkanBuffer*> storageBuffers;
        std::vector<void*> storageImages;
        uint32_t pushConstantSize = 0;
    };

    bool create(VulkanContext* ctx, const PipelineDesc& desc);
    void release();
    bool isValid() const { return pipeline_ != nullptr; }

    void bind();
    void dispatch(uint32_t groupsX, uint32_t groupsY = 1, uint32_t groupsZ = 1);
    void barrier();

    void pushConstants(const void* data, uint32_t size, uint32_t offset = 0);

    void finish();

private:
    VulkanContext* context_ = nullptr;
    void* pipeline_ = nullptr;
    void* pipelineLayout_ = nullptr;
    void* descriptorSetLayout_ = nullptr;
    void* descriptorSet_ = nullptr;
    void* shaderModule_ = nullptr;
};

// ============================================================
// Vulkan command buffer helper
// ============================================================
class VulkanCommandBuffer {
public:
    VulkanCommandBuffer() = default;
    ~VulkanCommandBuffer() { release(); }

    VulkanCommandBuffer(const VulkanCommandBuffer&) = delete;
    VulkanCommandBuffer& operator=(const VulkanCommandBuffer&) = delete;

    bool create(VulkanContext* ctx);
    void release();
    bool isValid() const { return cmdBuffer_ != nullptr; }

    void begin();
    void end();
    void submit();
    void reset();

    void* get() const { return cmdBuffer_; }

private:
    VulkanContext* context_ = nullptr;
    void* cmdBuffer_ = nullptr;
    void* fence_ = nullptr;
};

// ============================================================
// SPIR-V compilation helper (GLSL → SPIR-V)
// ============================================================
class SpirvCompiler {
public:
    // Compile GLSL compute shader source to SPIR-V binary
    // On Android, this typically uses shaderc or glslang
    // For now, we provide a stub that expects pre-compiled SPIR-V
    static bool compileGlslToSpirv(const std::string& glslSource,
                                   std::vector<uint32_t>& outSpirv);
};

} // namespace gpu
} // namespace alcedo