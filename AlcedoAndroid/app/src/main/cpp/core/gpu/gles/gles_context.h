#pragma once

#include "../gpu_context.h"
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES3/gl31.h>
#include <GLES3/gl3ext.h>

namespace alcedo {
namespace gpu {

class GlesContext : public GpuContext {
public:
    GlesContext();
    ~GlesContext() override;

    bool init(void* nativeWindow = nullptr) override;
    void destroy() override;
    bool isAvailable() const override;
    GpuBackend backend() const override { return GpuBackend::OPENGL_ES; }
    GpuDeviceInfo getDeviceInfo() const override;

    void makeCurrent() override;
    void swapBuffers() override;
    void finish() override;

    EGLDisplay getDisplay() const { return display_; }
    EGLContext getContext() const { return context_; }
    EGLSurface getSurface() const { return surface_; }
    EGLConfig getConfig() const { return config_; }

    bool supportsCompute() const { return hasComputeShaders_; }

private:
    bool createPbufferSurface();
    bool queryDeviceInfo();
    bool chooseConfig();
    bool tryInitFallback(void* nativeWindow);

    EGLDisplay display_ = EGL_NO_DISPLAY;
    EGLContext context_ = EGL_NO_CONTEXT;
    EGLSurface surface_ = EGL_NO_SURFACE;
    EGLConfig config_ = nullptr;

    bool hasEglImage_ = false;
    bool hasComputeShaders_ = false;
};

} // namespace gpu
} // namespace alcedo