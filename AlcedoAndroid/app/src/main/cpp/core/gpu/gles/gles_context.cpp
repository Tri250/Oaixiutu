#include "gles_context.h"
#include <cstring>
#include <dlfcn.h>

namespace alcedo {
namespace gpu {

GlesContext::GlesContext() = default;

GlesContext::~GlesContext() {
    destroy();
}

bool GlesContext::init(void* nativeWindow) {
    if (initialized_) return true;

    display_ = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (display_ == EGL_NO_DISPLAY) {
        GPU_LOGE("Failed to get EGL display");
        return false;
    }

    EGLint major, minor;
    if (!eglInitialize(display_, &major, &minor)) {
        GPU_LOGE("Failed to initialize EGL: 0x%x", eglGetError());
        return false;
    }
    GPU_LOGI("EGL initialized: %d.%d", major, minor);

    if (!chooseConfig()) {
        return false;
    }

    const EGLint contextAttribs[] = {
        EGL_CONTEXT_CLIENT_VERSION, 3,
        EGL_NONE
    };

    context_ = eglCreateContext(display_, config_, EGL_NO_CONTEXT, contextAttribs);
    if (context_ == EGL_NO_CONTEXT) {
        GPU_LOGE("Failed to create GLES 3.0 context: 0x%x, attempting fallback", eglGetError());
        return tryInitFallback(nativeWindow);
    }

    if (nativeWindow) {
        surface_ = eglCreateWindowSurface(display_, config_,
                                          static_cast<EGLNativeWindowType>(nativeWindow),
                                          nullptr);
        if (surface_ == EGL_NO_SURFACE) {
            GPU_LOGW("Failed to create window surface, falling back to pbuffer: 0x%x", eglGetError());
            if (!createPbufferSurface()) {
                return false;
            }
        }
    } else {
        if (!createPbufferSurface()) {
            return false;
        }
    }

    if (!eglMakeCurrent(display_, surface_, surface_, context_)) {
        GPU_LOGE("Failed to make EGL context current: 0x%x", eglGetError());
        return tryInitFallback(nativeWindow);
    }

    // Check for GL errors after making context current
    GLenum glErr = glGetError();
    if (glErr != GL_NO_ERROR) {
        GPU_LOGW("GL error after eglMakeCurrent: 0x%x, attempting fallback", glErr);
        return tryInitFallback(nativeWindow);
    }

    // Query extensions
    const char* extensions = reinterpret_cast<const char*>(glGetString(GL_EXTENSIONS));
    hasEglImage_ = (extensions != nullptr && strstr(extensions, "GL_OES_EGL_image") != nullptr);
    hasComputeShaders_ = (extensions != nullptr && strstr(extensions, "GL_KHR_shader_subgroup") != nullptr);

    // Check for compute shader support via GL version
    const char* version = reinterpret_cast<const char*>(glGetString(GL_VERSION));
    if (version) {
        int glMajor = 0, glMinor = 0;
        sscanf(version, "OpenGL ES %d.%d", &glMajor, &glMinor);
        hasComputeShaders_ = (glMajor >= 3 && glMinor >= 1);
    }

    // Verify compute shader support more thoroughly
    if (hasComputeShaders_) {
        GLint maxComputeWorkGroupInvocations;
        glGetIntegerv(GL_MAX_COMPUTE_WORK_GROUP_INVOCATIONS, &maxComputeWorkGroupInvocations);
        if (maxComputeWorkGroupInvocations < 64) {
            GPU_LOGW("Compute shader support insufficient (max invocations: %d), disabling compute",
                     maxComputeWorkGroupInvocations);
            hasComputeShaders_ = false;
        } else {
            GPU_LOGI("Compute shader verified: max invocations = %d", maxComputeWorkGroupInvocations);
        }

        // Check max shared memory
        GLint maxSharedMem;
        glGetIntegerv(GL_MAX_COMPUTE_SHARED_MEMORY_SIZE, &maxSharedMem);
        GPU_LOGI("Max compute shared memory: %d bytes", maxSharedMem);

        // Adjust work group size based on device capabilities
        if (maxComputeWorkGroupInvocations < 256) {
            GPU_LOGI("Using conservative work group size (4x4) for limited GPU");
        }
    }

    queryDeviceInfo();

    initialized_ = true;
    GPU_LOGI("GLES context created: %s, %s", deviceInfo_.renderer.c_str(), version);
    return true;
}

void GlesContext::destroy() {
    if (surface_ != EGL_NO_SURFACE) {
        if (eglGetCurrentSurface(EGL_DRAW) == surface_) {
            eglMakeCurrent(display_, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        }
        eglDestroySurface(display_, surface_);
        surface_ = EGL_NO_SURFACE;
    }

    if (context_ != EGL_NO_CONTEXT) {
        eglDestroyContext(display_, context_);
        context_ = EGL_NO_CONTEXT;
    }

    if (display_ != EGL_NO_DISPLAY) {
        eglTerminate(display_);
        display_ = EGL_NO_DISPLAY;
    }

    initialized_ = false;
}

bool GlesContext::isAvailable() const {
    return initialized_ && display_ != EGL_NO_DISPLAY && context_ != EGL_NO_CONTEXT;
}

GpuDeviceInfo GlesContext::getDeviceInfo() const {
    return deviceInfo_;
}

void GlesContext::makeCurrent() {
    if (initialized_) {
        eglMakeCurrent(display_, surface_, surface_, context_);
    }
}

void GlesContext::swapBuffers() {
    if (initialized_ && surface_ != EGL_NO_SURFACE) {
        eglSwapBuffers(display_, surface_);
    }
}

void GlesContext::finish() {
    if (initialized_) {
        glFinish();
    }
}

bool GlesContext::createPbufferSurface() {
    const EGLint pbufferAttribs[] = {
        EGL_WIDTH, 64,
        EGL_HEIGHT, 64,
        EGL_NONE
    };

    surface_ = eglCreatePbufferSurface(display_, config_, pbufferAttribs);
    if (surface_ == EGL_NO_SURFACE) {
        GPU_LOGE("Failed to create pbuffer surface: 0x%x", eglGetError());
        return false;
    }
    GPU_LOGI("Created pbuffer surface for offscreen rendering");
    return true;
}

bool GlesContext::chooseConfig() {
    const EGLint configAttribs[] = {
        EGL_SURFACE_TYPE,    EGL_WINDOW_BIT | EGL_PBUFFER_BIT,
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT_KHR,
        EGL_RED_SIZE,        8,
        EGL_GREEN_SIZE,      8,
        EGL_BLUE_SIZE,       8,
        EGL_ALPHA_SIZE,      8,
        EGL_DEPTH_SIZE,      0,
        EGL_STENCIL_SIZE,    0,
        EGL_NONE
    };

    EGLint numConfigs = 0;
    if (!eglChooseConfig(display_, configAttribs, &config_, 1, &numConfigs) || numConfigs == 0) {
        GPU_LOGE("Failed to choose EGL config: 0x%x", eglGetError());
        return false;
    }
    return true;
}

bool GlesContext::queryDeviceInfo() {
    deviceInfo_.renderer = reinterpret_cast<const char*>(glGetString(GL_RENDERER));
    deviceInfo_.vendor   = reinterpret_cast<const char*>(glGetString(GL_VENDOR));
    deviceInfo_.version  = reinterpret_cast<const char*>(glGetString(GL_VERSION));

    // Extract GPU name from renderer string
    deviceInfo_.name = deviceInfo_.renderer;

    glGetIntegerv(GL_MAX_COMPUTE_WORK_GROUP_SIZE, deviceInfo_.maxWorkGroupSize);
    glGetIntegerv(GL_MAX_COMPUTE_WORK_GROUP_INVOCATIONS, &deviceInfo_.maxWorkGroupInvocations);
    glGetIntegerv(GL_MAX_TEXTURE_SIZE, &deviceInfo_.maxTextureSize);
    glGetIntegerv(GL_MAX_SHADER_STORAGE_BLOCK_SIZE, &deviceInfo_.maxShaderStorageBlockSize);
    glGetIntegerv(GL_MAX_COMPUTE_SHARED_MEMORY_SIZE, &deviceInfo_.maxComputeSharedMemorySize);

    deviceInfo_.computeUnits = 0;
    GLint maxComputeWorkGroupCount[3] = {0};
    glGetIntegeri_v(GL_MAX_COMPUTE_WORK_GROUP_COUNT, 0, &maxComputeWorkGroupCount[0]);
    glGetIntegeri_v(GL_MAX_COMPUTE_WORK_GROUP_COUNT, 1, &maxComputeWorkGroupCount[1]);
    glGetIntegeri_v(GL_MAX_COMPUTE_WORK_GROUP_COUNT, 2, &maxComputeWorkGroupCount[2]);
    deviceInfo_.computeUnits = maxComputeWorkGroupCount[0];

    deviceInfo_.supportsComputeShaders = hasComputeShaders_;
    deviceInfo_.supportsEglImage = hasEglImage_;

    GLint numExtensions = 0;
    glGetIntegerv(GL_NUM_EXTENSIONS, &numExtensions);
    for (GLint i = 0; i < numExtensions; ++i) {
        const char* ext = reinterpret_cast<const char*>(glGetStringi(GL_EXTENSIONS, i));
        if (ext) {
            if (strstr(ext, "GL_EXT_color_buffer_half_float")) deviceInfo_.supportsFloat16 = true;
            if (strstr(ext, "GL_EXT_color_buffer_float"))       deviceInfo_.supportsFloat32 = true;
        }
    }

    GPU_LOGI("Device: %s | Vendor: %s | Max work group: %d/%d/%d | Compute: %s",
             deviceInfo_.renderer.c_str(),
             deviceInfo_.vendor.c_str(),
             deviceInfo_.maxWorkGroupSize[0],
             deviceInfo_.maxWorkGroupSize[1],
             deviceInfo_.maxWorkGroupSize[2],
             deviceInfo_.supportsComputeShaders ? "YES" : "NO");

    return true;
}

bool GlesContext::tryInitFallback(void* nativeWindow) {
    // Clean up any partial state from the failed 3.0 attempt
    if (surface_ != EGL_NO_SURFACE) {
        eglDestroySurface(display_, surface_);
        surface_ = EGL_NO_SURFACE;
    }
    if (context_ != EGL_NO_CONTEXT) {
        eglDestroyContext(display_, context_);
        context_ = EGL_NO_CONTEXT;
    }

    GPU_LOGI("Attempting GLES 2.0 fallback context");

    const EGLint fallbackAttribs[] = {
        EGL_CONTEXT_CLIENT_VERSION, 2,
        EGL_NONE
    };

    context_ = eglCreateContext(display_, config_, EGL_NO_CONTEXT, fallbackAttribs);
    if (context_ == EGL_NO_CONTEXT) {
        GPU_LOGE("Failed to create GLES 2.0 fallback context: 0x%x", eglGetError());
        return false;
    }

    // Recreate surface
    if (nativeWindow) {
        surface_ = eglCreateWindowSurface(display_, config_,
                                          static_cast<EGLNativeWindowType>(nativeWindow),
                                          nullptr);
        if (surface_ == EGL_NO_SURFACE) {
            GPU_LOGW("Fallback: failed to create window surface, trying pbuffer: 0x%x", eglGetError());
            if (!createPbufferSurface()) {
                return false;
            }
        }
    } else {
        if (!createPbufferSurface()) {
            return false;
        }
    }

    if (!eglMakeCurrent(display_, surface_, surface_, context_)) {
        GPU_LOGE("Failed to make fallback EGL context current: 0x%x", eglGetError());
        return false;
    }

    // Query extensions with null check
    const char* extensions = reinterpret_cast<const char*>(glGetString(GL_EXTENSIONS));
    hasEglImage_ = (extensions != nullptr && strstr(extensions, "GL_OES_EGL_image") != nullptr);
    hasComputeShaders_ = false; // No compute shaders on GLES 2.0

    queryDeviceInfo();

    initialized_ = true;
    GPU_LOGI("GLES 2.0 fallback context created: %s", deviceInfo_.renderer.c_str());
    return true;
}

} // namespace gpu
} // namespace alcedo