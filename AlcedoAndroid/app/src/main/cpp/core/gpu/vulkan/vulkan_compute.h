#pragma once

#include "vulkan_context.h"
#include <string>
#include <vector>
#include <unordered_map>
#include <memory>
#include <dlfcn.h>

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
    // Compile GLSL compute shader source to SPIR-V binary.
    // Uses shaderc via dynamic loading for runtime compilation.
    // Falls back to pass-through if the input is already pre-compiled SPIR-V.
    static bool compileGlslToSpirv(const std::string& glslSource,
                                   std::vector<uint32_t>& outSpirv) {
        // Fallback: detect pre-compiled SPIR-V by its magic number (0x07230203)
        if (glslSource.size() >= sizeof(uint32_t)) {
            const auto* bytes = reinterpret_cast<const uint8_t*>(glslSource.data());
            bool isSpirvLE = (bytes[0] == 0x03 && bytes[1] == 0x02 &&
                              bytes[2] == 0x23 && bytes[3] == 0x07);
            bool isSpirvBE = (bytes[0] == 0x07 && bytes[1] == 0x23 &&
                              bytes[2] == 0x02 && bytes[3] == 0x03);
            if (isSpirvLE || isSpirvBE) {
                size_t wordCount = glslSource.size() / sizeof(uint32_t);
                outSpirv.assign(
                    reinterpret_cast<const uint32_t*>(glslSource.data()),
                    reinterpret_cast<const uint32_t*>(glslSource.data()) + wordCount);
                GPU_LOGI("SpirvCompiler: input is pre-compiled SPIR-V (%zu words)", wordCount);
                return true;
            }
        }

        // Load shaderc library at runtime
        void* shadercLib = dlopen("libshaderc.so", RTLD_NOW);
        if (!shadercLib) {
            shadercLib = dlopen("libshaderc_shared.so", RTLD_NOW);
        }
        if (!shadercLib) {
            GPU_LOGE("SpirvCompiler: shaderc library not found; "
                     "provide pre-compiled SPIR-V binaries instead of GLSL source");
            return false;
        }

        // Resolve shaderc API symbols
        using PFN_CompilerInit     = void* (*)(void);
        using PFN_CompilerDispose  = void (*)(void*);
        using PFN_OptionsInit      = void* (*)(void);
        using PFN_OptionsDispose   = void (*)(void*);
        using PFN_CompileIntoSpv   = void* (*)(void*, const char*, size_t, int,
                                               const char*, const char*, void*);
        using PFN_ResultLength     = size_t (*)(const void*);
        using PFN_ResultData       = const char* (*)(const void*);
        using PFN_ResultStatus     = int (*)(const void*);
        using PFN_ResultErrorMsg   = const char* (*)(const void*);
        using PFN_ResultRelease    = void (*)(void*);

        auto fnInit       = reinterpret_cast<PFN_CompilerInit>(
            dlsym(shadercLib, "shaderc_compiler_initialize"));
        auto fnDispose    = reinterpret_cast<PFN_CompilerDispose>(
            dlsym(shadercLib, "shaderc_compiler_dispose"));
        auto fnOptInit    = reinterpret_cast<PFN_OptionsInit>(
            dlsym(shadercLib, "shaderc_compile_options_initialize"));
        auto fnOptDispose = reinterpret_cast<PFN_OptionsDispose>(
            dlsym(shadercLib, "shaderc_compile_options_dispose"));
        auto fnCompile    = reinterpret_cast<PFN_CompileIntoSpv>(
            dlsym(shadercLib, "shaderc_compile_into_spv"));
        auto fnResLen     = reinterpret_cast<PFN_ResultLength>(
            dlsym(shadercLib, "shaderc_result_get_length"));
        auto fnResData    = reinterpret_cast<PFN_ResultData>(
            dlsym(shadercLib, "shaderc_result_get_data"));
        auto fnResStatus  = reinterpret_cast<PFN_ResultStatus>(
            dlsym(shadercLib, "shaderc_result_get_compilation_status"));
        auto fnResErr     = reinterpret_cast<PFN_ResultErrorMsg>(
            dlsym(shadercLib, "shaderc_result_get_error_message"));
        auto fnResRelease = reinterpret_cast<PFN_ResultRelease>(
            dlsym(shadercLib, "shaderc_result_release"));

        if (!fnInit || !fnDispose || !fnCompile || !fnResLen ||
            !fnResData || !fnResStatus || !fnResRelease) {
            GPU_LOGE("SpirvCompiler: failed to resolve required shaderc symbols");
            dlclose(shadercLib);
            return false;
        }

        void* compiler = fnInit();
        if (!compiler) {
            GPU_LOGE("SpirvCompiler: shaderc_compiler_initialize failed");
            dlclose(shadercLib);
            return false;
        }

        // Compile GLSL compute shader to SPIR-V (shaderc_glsl_compute_shader = 2)
        void* options = fnOptInit ? fnOptInit() : nullptr;
        void* result  = fnCompile(compiler, glslSource.c_str(), glslSource.size(),
                                  2 /* shaderc_glsl_compute_shader */,
                                  "compute.glsl", "main", options);

        int status = fnResStatus(result);
        if (status != 0) {
            const char* errMsg = fnResErr ? fnResErr(result) : "unknown compilation error";
            GPU_LOGE("SpirvCompiler: shaderc compilation failed (status=%d): %s",
                     status, errMsg);
            fnResRelease(result);
            if (fnOptDispose && options) fnOptDispose(options);
            fnDispose(compiler);
            dlclose(shadercLib);
            return false;
        }

        size_t spirvLen   = fnResLen(result);
        const char* data  = fnResData(result);
        size_t wordCount  = spirvLen / sizeof(uint32_t);
        outSpirv.assign(reinterpret_cast<const uint32_t*>(data),
                        reinterpret_cast<const uint32_t*>(data) + wordCount);

        GPU_LOGI("SpirvCompiler: compiled GLSL to SPIR-V (%zu bytes, %zu words)",
                 spirvLen, wordCount);

        fnResRelease(result);
        if (fnOptDispose && options) fnOptDispose(options);
        fnDispose(compiler);
        dlclose(shadercLib);
        return true;
    }
};

} // namespace gpu
} // namespace alcedo