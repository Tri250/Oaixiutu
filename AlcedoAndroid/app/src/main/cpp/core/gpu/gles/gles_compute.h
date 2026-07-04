#pragma once

#include "../gpu_context.h"
#include <GLES3/gl31.h>
#include <GLES3/gl3ext.h>
#include <string>
#include <vector>
#include <unordered_map>
#include <memory>

namespace alcedo {
namespace gpu {

// ============================================================
// Shader object management
// ============================================================
class GlesShader {
public:
    GlesShader() = default;
    ~GlesShader() { release(); }

    GlesShader(const GlesShader&) = delete;
    GlesShader& operator=(const GlesShader&) = delete;

    bool compile(const std::string& source);
    bool compile(const std::string& source, const std::string& defines);
    void release();
    bool isValid() const { return shaderId_ != 0; }
    GLuint id() const { return shaderId_; }

private:
    GLuint shaderId_ = 0;
};

// ============================================================
// Compute program management
// ============================================================
class GlesComputeProgram {
public:
    GlesComputeProgram() = default;
    ~GlesComputeProgram() { release(); }

    GlesComputeProgram(const GlesComputeProgram&) = delete;
    GlesComputeProgram& operator=(const GlesComputeProgram&) = delete;

    bool create(const std::string& computeSource);
    bool create(const std::string& computeSource, const std::string& defines);
    bool createFromFile(const std::string& filePath);

    void release();
    bool isValid() const { return programId_ != 0; }
    GLuint id() const { return programId_; }

    // Uniform setters
    void setFloat(const std::string& name, float value);
    void setInt(const std::string& name, int value);
    void setVec2(const std::string& name, float x, float y);
    void setVec3(const std::string& name, float x, float y, float z);
    void setVec4(const std::string& name, float x, float y, float z, float w);
    void setMat3(const std::string& name, const float* matrix);
    void setMat4(const std::string& name, const float* matrix);

    // SSBO binding
    void bindSSBO(GLuint binding, GLuint buffer, GLsizeiptr offset = 0, GLsizeiptr size = 0);

    // Image binding
    void bindImage(GLuint binding, GLuint texture, GLint level = 0,
                   GLboolean layered = GL_FALSE, GLint layer = 0,
                   GLenum access = GL_READ_WRITE, GLenum format = GL_RGBA8);

    GLint getUniformLocation(const std::string& name);

    void use() const { glUseProgram(programId_); }

    std::string source() const { return source_; }

private:
    GLuint programId_ = 0;
    std::string source_;
    std::unordered_map<std::string, GLint> uniformCache_;
};

// ============================================================
// SSBO (Shader Storage Buffer Object) management
// ============================================================
class GlesSSBO {
public:
    GlesSSBO() = default;
    ~GlesSSBO() { release(); }

    GlesSSBO(const GlesSSBO&) = delete;
    GlesSSBO& operator=(const GlesSSBO&) = delete;

    bool create(size_t size, const void* data = nullptr, GLenum usage = GL_DYNAMIC_DRAW);
    void release();
    bool isValid() const { return bufferId_ != 0; }
    GLuint id() const { return bufferId_; }

    void bind(GLuint binding) const;
    void unbind() const;
    void update(const void* data, size_t size, size_t offset = 0);
    void* map(GLenum access = GL_READ_WRITE);
    void unmap();

    size_t size() const { return size_; }

private:
    GLuint bufferId_ = 0;
    size_t size_ = 0;
};

// ============================================================
// Compute pipeline dispatch
// ============================================================
class GlesComputePipeline {
public:
    GlesComputePipeline() = default;
    ~GlesComputePipeline() = default;

    struct DispatchParams {
        uint32_t groupsX = 1;
        uint32_t groupsY = 1;
        uint32_t groupsZ = 1;
        uint32_t workGroupSizeX = 16;
        uint32_t workGroupSizeY = 16;
        uint32_t workGroupSizeZ = 1;
    };

    // Compute dispatch groups from image dimensions
    static DispatchParams dispatchForImage(uint32_t width, uint32_t height,
                                           uint32_t workGroupX = 16,
                                           uint32_t workGroupY = 16) {
        DispatchParams p;
        p.workGroupSizeX = workGroupX;
        p.workGroupSizeY = workGroupY;
        p.workGroupSizeZ = 1;
        p.groupsX = (width  + workGroupX - 1) / workGroupX;
        p.groupsY = (height + workGroupY - 1) / workGroupY;
        p.groupsZ = 1;
        return p;
    }

    void dispatch(const DispatchParams& params) {
        glDispatchCompute(params.groupsX, params.groupsY, params.groupsZ);
    }

    void dispatch(uint32_t groupsX, uint32_t groupsY = 1, uint32_t groupsZ = 1) {
        glDispatchCompute(groupsX, groupsY, groupsZ);
    }

    void barrier(GLenum barrierBits = GL_SHADER_IMAGE_ACCESS_BARRIER_BIT |
                                      GL_SHADER_STORAGE_BARRIER_BIT) {
        glMemoryBarrier(barrierBits);
    }

    void finish() {
        glFinish();
    }

    void flush() {
        glFlush();
    }

    // Execute a compute shader on an image
    void execute(GlesComputeProgram& program, GLuint inTexture, GLuint outTexture,
                 uint32_t width, uint32_t height,
                 uint32_t wgX = 16, uint32_t wgY = 16) {
        program.use();
        program.bindImage(0, inTexture,  0, GL_FALSE, 0, GL_READ_ONLY, GL_RGBA8);
        program.bindImage(1, outTexture, 0, GL_FALSE, 0, GL_WRITE_ONLY, GL_RGBA8);

        auto params = dispatchForImage(width, height, wgX, wgY);
        dispatch(params);
        barrier();
    }
};

// ============================================================
// Utility: Check GL errors
// ============================================================
inline bool checkGLError(const char* op) {
    GLenum err;
    bool hadError = false;
    while ((err = glGetError()) != GL_NO_ERROR) {
        GPU_LOGE("GL error after %s: 0x%x", op, err);
        hadError = true;
    }
    return !hadError;
}

// ============================================================
// Inline implementations
// ============================================================

inline bool GlesShader::compile(const std::string& source) {
    return compile(source, "");
}

inline bool GlesShader::compile(const std::string& source, const std::string& defines) {
    release();

    shaderId_ = glCreateShader(GL_COMPUTE_SHADER);
    if (shaderId_ == 0) {
        GPU_LOGE("Failed to create compute shader");
        return false;
    }

    // Prepend defines
    std::string fullSource = "#version 310 es\n";
    if (!defines.empty()) {
        fullSource += defines + "\n";
    }
    fullSource += source;

    const char* srcPtr = fullSource.c_str();
    glShaderSource(shaderId_, 1, &srcPtr, nullptr);
    glCompileShader(shaderId_);

    GLint compiled = 0;
    glGetShaderiv(shaderId_, GL_COMPILE_STATUS, &compiled);
    if (!compiled) {
        GLint infoLen = 0;
        glGetShaderiv(shaderId_, GL_INFO_LOG_LENGTH, &infoLen);
        if (infoLen > 0) {
            std::vector<char> infoLog(infoLen);
            glGetShaderInfoLog(shaderId_, infoLen, nullptr, infoLog.data());
            GPU_LOGE("Shader compile error:\n%s", infoLog.data());
        }
        glDeleteShader(shaderId_);
        shaderId_ = 0;
        return false;
    }

    return true;
}

inline void GlesShader::release() {
    if (shaderId_ != 0) {
        glDeleteShader(shaderId_);
        shaderId_ = 0;
    }
}

inline bool GlesComputeProgram::create(const std::string& computeSource) {
    return create(computeSource, "");
}

inline bool GlesComputeProgram::create(const std::string& computeSource, const std::string& defines) {
    release();

    GlesShader shader;
    if (!shader.compile(computeSource, defines)) {
        return false;
    }

    programId_ = glCreateProgram();
    glAttachShader(programId_, shader.id());
    glLinkProgram(programId_);

    GLint linked = 0;
    glGetProgramiv(programId_, GL_LINK_STATUS, &linked);
    if (!linked) {
        GLint infoLen = 0;
        glGetProgramiv(programId_, GL_INFO_LOG_LENGTH, &infoLen);
        if (infoLen > 0) {
            std::vector<char> infoLog(infoLen);
            glGetProgramInfoLog(programId_, infoLen, nullptr, infoLog.data());
            GPU_LOGE("Program link error:\n%s", infoLog.data());
        }
        glDeleteProgram(programId_);
        programId_ = 0;
        return false;
    }

    source_ = computeSource;
    uniformCache_.clear();
    return true;
}

inline bool GlesComputeProgram::createFromFile(const std::string& /*filePath*/) {
    // File-based shader loading requires Android asset manager
    // Stub - use embedded shader strings instead
    GPU_LOGW("createFromFile: use embedded shader strings");
    return false;
}

inline void GlesComputeProgram::release() {
    if (programId_ != 0) {
        glDeleteProgram(programId_);
        programId_ = 0;
    }
    uniformCache_.clear();
}

inline void GlesComputeProgram::setFloat(const std::string& name, float value) {
    glProgramUniform1f(programId_, getUniformLocation(name), value);
}

inline void GlesComputeProgram::setInt(const std::string& name, int value) {
    glProgramUniform1i(programId_, getUniformLocation(name), value);
}

inline void GlesComputeProgram::setVec2(const std::string& name, float x, float y) {
    glProgramUniform2f(programId_, getUniformLocation(name), x, y);
}

inline void GlesComputeProgram::setVec3(const std::string& name, float x, float y, float z) {
    glProgramUniform3f(programId_, getUniformLocation(name), x, y, z);
}

inline void GlesComputeProgram::setVec4(const std::string& name, float x, float y, float z, float w) {
    glProgramUniform4f(programId_, getUniformLocation(name), x, y, z, w);
}

inline void GlesComputeProgram::setMat3(const std::string& name, const float* matrix) {
    glProgramUniformMatrix3fv(programId_, getUniformLocation(name), 1, GL_FALSE, matrix);
}

inline void GlesComputeProgram::setMat4(const std::string& name, const float* matrix) {
    glProgramUniformMatrix4fv(programId_, getUniformLocation(name), 1, GL_FALSE, matrix);
}

inline void GlesComputeProgram::bindSSBO(GLuint binding, GLuint buffer, GLsizeiptr offset, GLsizeiptr size) {
    glBindBufferRange(GL_SHADER_STORAGE_BUFFER, binding, buffer, offset, size);
}

inline void GlesComputeProgram::bindImage(GLuint binding, GLuint texture, GLint level,
                                          GLboolean layered, GLint layer,
                                          GLenum access, GLenum format) {
    glBindImageTexture(binding, texture, level, layered, layer, access, format);
}

inline GLint GlesComputeProgram::getUniformLocation(const std::string& name) {
    auto it = uniformCache_.find(name);
    if (it != uniformCache_.end()) {
        return it->second;
    }
    GLint loc = glGetUniformLocation(programId_, name.c_str());
    uniformCache_[name] = loc;
    return loc;
}

inline bool GlesSSBO::create(size_t size, const void* data, GLenum usage) {
    release();
    glGenBuffers(1, &bufferId_);
    glBindBuffer(GL_SHADER_STORAGE_BUFFER, bufferId_);
    glBufferData(GL_SHADER_STORAGE_BUFFER, size, data, usage);
    glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
    size_ = size;
    return bufferId_ != 0;
}

inline void GlesSSBO::release() {
    if (bufferId_ != 0) {
        glDeleteBuffers(1, &bufferId_);
        bufferId_ = 0;
    }
    size_ = 0;
}

inline void GlesSSBO::bind(GLuint binding) const {
    glBindBufferBase(GL_SHADER_STORAGE_BUFFER, binding, bufferId_);
}

inline void GlesSSBO::unbind() const {
    glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, 0);
}

inline void GlesSSBO::update(const void* data, size_t size, size_t offset) {
    glBindBuffer(GL_SHADER_STORAGE_BUFFER, bufferId_);
    glBufferSubData(GL_SHADER_STORAGE_BUFFER, offset, size, data);
    glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
}

inline void* GlesSSBO::map(GLenum access) {
    glBindBuffer(GL_SHADER_STORAGE_BUFFER, bufferId_);
    return glMapBufferRange(GL_SHADER_STORAGE_BUFFER, 0, size_, access);
}

inline void GlesSSBO::unmap() {
    glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
    glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
}

} // namespace gpu
} // namespace alcedo