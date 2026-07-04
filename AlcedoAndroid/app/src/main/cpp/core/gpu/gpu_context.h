#pragma once

#include <string>
#include <memory>
#include <mutex>
#include <unordered_map>
#include <functional>
#include <android/log.h>

#define LOG_TAG_GPU "AlcedoGPU"
#define GPU_LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG_GPU, __VA_ARGS__)
#define GPU_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG_GPU, __VA_ARGS__)
#define GPU_LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG_GPU, __VA_ARGS__)

namespace alcedo {
namespace gpu {

enum class GpuBackend {
    NONE = 0,
    OPENGL_ES = 1,
    VULKAN = 2,
    CPU = 3
};

inline const char* gpuBackendName(GpuBackend b) {
    switch (b) {
        case GpuBackend::NONE:      return "NONE";
        case GpuBackend::OPENGL_ES: return "OPENGL_ES";
        case GpuBackend::VULKAN:    return "VULKAN";
        case GpuBackend::CPU:       return "CPU";
    }
    return "UNKNOWN";
}

struct GpuDeviceInfo {
    std::string name;
    std::string vendor;
    std::string renderer;
    std::string version;
    int computeUnits = 0;
    int maxWorkGroupSize[3] = {0, 0, 0};
    int maxWorkGroupInvocations = 0;
    int maxTextureSize = 0;
    int maxShaderStorageBlockSize = 0;
    int maxComputeSharedMemorySize = 0;
    bool supportsComputeShaders = false;
    bool supportsEglImage = false;
    bool supportsFloat16 = false;
    bool supportsFloat32 = false;
    uint64_t totalMemoryBytes = 0;
};

class GpuContext {
public:
    virtual ~GpuContext() = default;

    virtual bool init(void* nativeWindow = nullptr) = 0;
    virtual void destroy() = 0;
    virtual bool isAvailable() const = 0;
    virtual GpuBackend backend() const = 0;
    virtual GpuDeviceInfo getDeviceInfo() const = 0;

    virtual void makeCurrent() = 0;
    virtual void swapBuffers() = 0;
    virtual void finish() = 0;

    bool initialized() const { return initialized_; }

protected:
    bool initialized_ = false;
    GpuDeviceInfo deviceInfo_;
};

using GpuContextPtr = std::shared_ptr<GpuContext>;

class GpuContextManager {
public:
    static GpuContextManager& instance() {
        static GpuContextManager mgr;
        return mgr;
    }

    GpuBackend detectAvailableBackends() {
        std::lock_guard<std::mutex> lock(mutex_);

        availableBackends_.clear();

#ifdef __ANDROID__
        // OpenGL ES is always available on Android
        availableBackends_.push_back(GpuBackend::OPENGL_ES);

        // Vulkan availability depends on Android version and device support
        void* vulkanLib = dlopen("libvulkan.so", RTLD_NOW);
        if (vulkanLib) {
            availableBackends_.push_back(GpuBackend::VULKAN);
            dlclose(vulkanLib);
        }
#endif

        // CPU is always available
        availableBackends_.push_back(GpuBackend::CPU);

        return preferredBackend(availableBackends_);
    }

    GpuContextPtr createContext(GpuBackend backend) {
        std::lock_guard<std::mutex> lock(mutex_);

        auto it = contexts_.find(backend);
        if (it != contexts_.end() && it->second->isAvailable()) {
            return it->second;
        }

        auto ctx = createContextImpl(backend);
        if (ctx) {
            contexts_[backend] = ctx;
        }
        return ctx;
    }

    GpuContextPtr getOrCreateCurrent() {
        return createContext(currentBackend_);
    }

    void setPreferredBackend(GpuBackend backend) {
        std::lock_guard<std::mutex> lock(mutex_);
        preferredBackend_ = backend;
    }

    GpuBackend getPreferredBackend() const {
        return preferredBackend_;
    }

    GpuBackend getCurrentBackend() const {
        return currentBackend_;
    }

    void setCurrentBackend(GpuBackend backend) {
        std::lock_guard<std::mutex> lock(mutex_);
        if (backend != currentBackend_) {
            GPU_LOGI("Switching GPU backend: %s -> %s",
                     gpuBackendName(currentBackend_),
                     gpuBackendName(backend));
            currentBackend_ = backend;
        }
    }

    const std::vector<GpuBackend>& getAvailableBackends() const {
        return availableBackends_;
    }

    GpuBackend preferredBackend(const std::vector<GpuBackend>& available) {
        for (auto b : available) {
            if (b == preferredBackend_) return b;
        }
        if (!available.empty()) return available.front();
        return GpuBackend::CPU;
    }

    void destroyAll() {
        std::lock_guard<std::mutex> lock(mutex_);
        for (auto& [_, ctx] : contexts_) {
            if (ctx) ctx->destroy();
        }
        contexts_.clear();
    }

private:
    GpuContextManager() = default;
    ~GpuContextManager() { destroyAll(); }

    GpuContextPtr createContextImpl(GpuBackend backend);

    GpuBackend preferredBackend_ = GpuBackend::OPENGL_ES;
    GpuBackend currentBackend_ = GpuBackend::NONE;
    std::vector<GpuBackend> availableBackends_;
    std::unordered_map<GpuBackend, GpuContextPtr> contexts_;
    std::mutex mutex_;
};

} // namespace gpu
} // namespace alcedo