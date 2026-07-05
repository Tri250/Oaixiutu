#include "image_buffer.h"
#include <cmath>
#include <algorithm>
#include <cstring>
#include <mutex>
#include <list>
#include <android/log.h>
#if defined(__ANDROID__)
#include <GLES3/gl31.h>
#endif

#define LOG_TAG "AlcedoImgBuf"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

namespace alcedo {

// ============================================================
// ImageBuffer Implementation
// ============================================================

ImageBuffer::ImageBuffer(int w, int h, PixelFormat fmt) {
    allocate(w, h, fmt);
}

void ImageBuffer::allocate(int w, int h, PixelFormat fmt) {
    width = w;
    height = h;
    format = fmt;
    backend = BufferBackend::CPU;
    gpu_data_valid = false;

    switch (fmt) {
        case PixelFormat::FLOAT32_RGBA: channels = 4; break;
        case PixelFormat::FLOAT16_RGBA: channels = 4; break;
        case PixelFormat::UINT8_RGBA:   channels = 4; break;
        case PixelFormat::UINT16_RGBA:  channels = 4; break;
        case PixelFormat::FLOAT32_RGB:  channels = 3; break;
        case PixelFormat::UINT8_RGB:    channels = 3; break;
        case PixelFormat::UINT16_RAW:   channels = 1; break;
        case PixelFormat::FLOAT32_RAW:  channels = 1; break;
        case PixelFormat::FLOAT32_PLANAR: channels = 0; break;
        default: channels = 4; break;
    }

    if (fmt != PixelFormat::FLOAT32_PLANAR) {
        size_t bytes = static_cast<size_t>(w) * h * channels * pixel_size();
        cpu_data.resize(bytes);
        row_stride = w * channels * pixel_size();
    }
    LOGI("ImageBuffer allocated: %dx%d, channels=%d, format=%d, bytes=%zu", w, h, channels, (int)fmt, cpu_data.size());
}

void ImageBuffer::allocate_planes(int w, int h, int num_planes) {
    width = w;
    height = h;
    channels = 0;
    format = PixelFormat::FLOAT32_PLANAR;
    planes.resize(num_planes);
    for (auto& p : planes) {
        p.width = w;
        p.height = h;
        p.row_stride = w * sizeof(float);
        p.data.resize(p.row_stride * h);
    }
}

void ImageBuffer::release() {
    release_gpu();
    cpu_data.clear();
    planes.clear();
    width = height = 0;
    channels = 0;
}

int ImageBuffer::pixel_size() const {
    switch (format) {
        case PixelFormat::FLOAT32_RGBA:
        case PixelFormat::FLOAT32_RGB:
        case PixelFormat::FLOAT32_RAW:
        case PixelFormat::FLOAT32_PLANAR: return 4;
        case PixelFormat::FLOAT16_RGBA:   return 2;
        case PixelFormat::UINT16_RGBA:
        case PixelFormat::UINT16_RAW:     return 2;
        case PixelFormat::UINT8_RGBA:
        case PixelFormat::UINT8_RGB:      return 1;
        default: return 4;
    }
}

size_t ImageBuffer::total_bytes() const {
    return cpu_data.size();
}

bool ImageBuffer::is_valid() const {
    return width > 0 && height > 0 && !cpu_data.empty();
}

float* ImageBuffer::float_data() {
    return reinterpret_cast<float*>(cpu_data.data());
}

const float* ImageBuffer::float_data() const {
    return reinterpret_cast<const float*>(cpu_data.data());
}

int ImageBuffer::float_count() const {
    return cpu_data.size() / sizeof(float);
}

uint8_t* ImageBuffer::uint8_data() {
    return cpu_data.data();
}

const uint8_t* ImageBuffer::uint8_data() const {
    return cpu_data.data();
}

uint16_t* ImageBuffer::uint16_data() {
    return reinterpret_cast<uint16_t*>(cpu_data.data());
}

const uint16_t* ImageBuffer::uint16_data() const {
    return reinterpret_cast<const uint16_t*>(cpu_data.data());
}

void ImageBuffer::convert_to_float32() {
    if (format == PixelFormat::FLOAT32_RGBA || format == PixelFormat::FLOAT32_RGB ||
        format == PixelFormat::FLOAT32_RAW) return;

    size_t total = static_cast<size_t>(width) * height * channels;
    std::vector<uint8_t> new_data(total * sizeof(float));
    float* dst = reinterpret_cast<float*>(new_data.data());

    switch (format) {
        case PixelFormat::UINT8_RGBA:
        case PixelFormat::UINT8_RGB: {
            for (size_t i = 0; i < total; ++i) {
                dst[i] = cpu_data[i] / 255.0f;
            }
            break;
        }
        case PixelFormat::UINT16_RGBA:
        case PixelFormat::UINT16_RAW: {
            uint16_t* src = uint16_data();
            for (size_t i = 0; i < total; ++i) {
                dst[i] = src[i] / 65535.0f;
            }
            break;
        }
        case PixelFormat::FLOAT16_RGBA: {
            // Half-float to float conversion
            uint16_t* src = uint16_data();
            for (size_t i = 0; i < total; ++i) {
                uint16_t h = src[i];
                uint32_t h_exp = (h >> 10) & 0x1F;
                uint32_t h_mant = h & 0x3FF;
                uint32_t sign = (h >> 15) & 1;
                uint32_t f_bits;
                if (h_exp == 0) {
                    f_bits = (sign << 31) | (h_mant << 13); // Denormal
                } else if (h_exp == 0x1F) {
                    f_bits = (sign << 31) | (0xFF << 23) | (h_mant << 13); // Inf/NaN
                } else {
                    f_bits = (sign << 31) | ((h_exp + 127 - 15) << 23) | (h_mant << 13);
                }
                std::memcpy(&dst[i], &f_bits, sizeof(float));
            }
            break;
        }
        default: break;
    }

    cpu_data = std::move(new_data);
    format = (channels == 3) ? PixelFormat::FLOAT32_RGB : PixelFormat::FLOAT32_RGBA;
    row_stride = width * channels * sizeof(float);
}

void ImageBuffer::convert_to_uint8() {
    if (format == PixelFormat::UINT8_RGBA || format == PixelFormat::UINT8_RGB) return;

    convert_to_float32();
    size_t total = static_cast<size_t>(width) * height * channels;
    std::vector<uint8_t> new_data(total);
    float* src = float_data();
    for (size_t i = 0; i < total; ++i) {
        new_data[i] = static_cast<uint8_t>(std::max(0.0f, std::min(1.0f, src[i])) * 255.0f);
    }
    cpu_data = std::move(new_data);
    format = (channels == 3) ? PixelFormat::UINT8_RGB : PixelFormat::UINT8_RGBA;
    row_stride = width * channels;
}

void ImageBuffer::convert_to_float16() {
    if (format == PixelFormat::FLOAT16_RGBA) return;

    convert_to_float32();
    size_t total = static_cast<size_t>(width) * height * channels;
    std::vector<uint8_t> new_data(total * sizeof(uint16_t));
    uint16_t* dst = reinterpret_cast<uint16_t*>(new_data.data());
    float* src = float_data();
    for (size_t i = 0; i < total; ++i) {
        uint32_t f_bits;
        std::memcpy(&f_bits, &src[i], sizeof(float));
        uint32_t sign = (f_bits >> 31) & 1;
        int32_t exp = ((f_bits >> 23) & 0xFF) - 127 + 15;
        uint32_t mant = (f_bits >> 13) & 0x3FF;
        if (exp <= 0) {
            dst[i] = static_cast<uint16_t>((sign << 15) | (mant >> (1 - exp)));
        } else if (exp >= 0x1F) {
            dst[i] = static_cast<uint16_t>((sign << 15) | (0x1F << 10) | mant);
        } else {
            dst[i] = static_cast<uint16_t>((sign << 15) | (exp << 10) | mant);
        }
    }
    cpu_data = std::move(new_data);
    format = PixelFormat::FLOAT16_RGBA;
    row_stride = width * 4 * sizeof(uint16_t);
}

void ImageBuffer::convert_to_uint16() {
    if (format == PixelFormat::UINT16_RGBA || format == PixelFormat::UINT16_RAW) return;
    convert_to_float32();
    size_t total = static_cast<size_t>(width) * height * channels;
    std::vector<uint8_t> new_data(total * sizeof(uint16_t));
    uint16_t* dst = reinterpret_cast<uint16_t*>(new_data.data());
    float* src = float_data();
    for (size_t i = 0; i < total; ++i) {
        dst[i] = static_cast<uint16_t>(std::max(0.0f, std::min(1.0f, src[i])) * 65535.0f);
    }
    cpu_data = std::move(new_data);
    format = (channels == 1) ? PixelFormat::UINT16_RAW : PixelFormat::UINT16_RGBA;
    row_stride = width * channels * sizeof(uint16_t);
}

void ImageBuffer::copy_from(const ImageBuffer& other) {
    allocate(other.width, other.height, other.format);
    std::copy(other.cpu_data.begin(), other.cpu_data.end(), cpu_data.begin());
    color_space = other.color_space;
    display_peak_luminance = other.display_peak_luminance;
    is_hdr = other.is_hdr;
    is_raw = other.is_raw;
}

ImageBuffer ImageBuffer::clone() const {
    ImageBuffer result;
    result.copy_from(*this);
    return result;
}

void ImageBuffer::upload_to_gpu() {
    if (gpu_data_valid) return;
    if (cpu_data.empty()) {
        LOGW("GPU upload skipped: no CPU data");
        return;
    }

#if defined(__ANDROID__)
    // Lazily allocate a GL texture handle (stored as GLuint in the void*).
    GLuint tex = static_cast<GLuint>(reinterpret_cast<uintptr_t>(gpu_texture_id));
    if (tex == 0) {
        glGenTextures(1, &tex);
        if (tex == 0) {
            LOGW("GPU upload: glGenTextures failed (no GL context?)");
            gpu_data_valid = false;
            return;
        }
        gpu_texture_id = reinterpret_cast<void*>(static_cast<uintptr_t>(tex));
    }

    GLenum glFormat = GL_RGBA;
    GLenum glType = GL_UNSIGNED_BYTE;
    switch (format) {
        case PixelFormat::UINT8_RGBA:   glFormat = GL_RGBA; glType = GL_UNSIGNED_BYTE;    break;
        case PixelFormat::UINT8_RGB:    glFormat = GL_RGB;  glType = GL_UNSIGNED_BYTE;    break;
        case PixelFormat::FLOAT32_RGBA: glFormat = GL_RGBA; glType = GL_FLOAT;            break;
        case PixelFormat::FLOAT32_RGB:  glFormat = GL_RGB;  glType = GL_FLOAT;            break;
        case PixelFormat::FLOAT32_RAW:  glFormat = GL_RED;  glType = GL_FLOAT;            break;
        case PixelFormat::UINT16_RAW:   glFormat = GL_RED;  glType = GL_UNSIGNED_SHORT;   break;
        case PixelFormat::UINT16_RGBA:  glFormat = GL_RGBA; glType = GL_UNSIGNED_SHORT;   break;
        case PixelFormat::FLOAT16_RGBA: glFormat = GL_RGBA; glType = GL_HALF_FLOAT;       break;
        default:                        glFormat = GL_RGBA; glType = GL_UNSIGNED_BYTE;    break;
    }

    glBindTexture(GL_TEXTURE_2D, tex);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glPixelStorei(GL_UNPACK_ALIGNMENT, 1);

    glTexImage2D(GL_TEXTURE_2D, 0, glFormat, width, height, 0,
                 glFormat, glType, cpu_data.data());
    GLenum err = glGetError();
    if (err != GL_NO_ERROR) {
        LOGW("GPU upload: glTexImage2D failed (GL error 0x%x, %dx%d fmt=%d)",
             err, width, height, static_cast<int>(format));
        gpu_data_valid = false;
        return;
    }
    gpu_data_valid = true;
    LOGI("GPU upload: %dx%d tex=%u channels=%d", width, height, tex, channels);
#else
    LOGW("GPU upload: OpenGL ES unavailable (non-Android build)");
    gpu_data_valid = false;
#endif
}

void ImageBuffer::download_from_gpu() {
    if (!gpu_data_valid) return;

#if defined(__ANDROID__)
    GLuint tex = static_cast<GLuint>(reinterpret_cast<uintptr_t>(gpu_texture_id));
    if (tex == 0) {
        LOGW("GPU download: no texture handle");
        return;
    }

    GLenum glFormat = GL_RGBA;
    GLenum glType = GL_UNSIGNED_BYTE;
    switch (format) {
        case PixelFormat::UINT8_RGBA:   glFormat = GL_RGBA; glType = GL_UNSIGNED_BYTE;    break;
        case PixelFormat::UINT8_RGB:    glFormat = GL_RGB;  glType = GL_UNSIGNED_BYTE;    break;
        case PixelFormat::FLOAT32_RGBA: glFormat = GL_RGBA; glType = GL_FLOAT;            break;
        case PixelFormat::FLOAT32_RGB:  glFormat = GL_RGB;  glType = GL_FLOAT;            break;
        case PixelFormat::FLOAT32_RAW:  glFormat = GL_RED;  glType = GL_FLOAT;            break;
        case PixelFormat::UINT16_RAW:   glFormat = GL_RED;  glType = GL_UNSIGNED_SHORT;   break;
        case PixelFormat::UINT16_RGBA:  glFormat = GL_RGBA; glType = GL_UNSIGNED_SHORT;   break;
        case PixelFormat::FLOAT16_RGBA: glFormat = GL_RGBA; glType = GL_HALF_FLOAT;       break;
        default:                        glFormat = GL_RGBA; glType = GL_UNSIGNED_BYTE;    break;
    }

    // OpenGL ES has no glGetTexImage; read back via a Framebuffer Object.
    GLuint fbo = 0;
    glGenFramebuffers(1, &fbo);
    if (fbo == 0) {
        LOGW("GPU download: glGenFramebuffers failed");
        return;
    }
    glBindFramebuffer(GL_FRAMEBUFFER, fbo);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0,
                           GL_TEXTURE_2D, tex, 0);
    GLenum status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
    if (status != GL_FRAMEBUFFER_COMPLETE) {
        LOGW("GPU download: FBO incomplete (0x%x)", status);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glDeleteFramebuffers(1, &fbo);
        return;
    }

    glPixelStorei(GL_PACK_ALIGNMENT, 1);
    glReadPixels(0, 0, width, height, glFormat, glType, cpu_data.data());
    GLenum err = glGetError();
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glDeleteFramebuffers(1, &fbo);

    if (err != GL_NO_ERROR) {
        LOGW("GPU download: glReadPixels failed (GL error 0x%x)", err);
        return;
    }
    // Note: OpenGL's origin is bottom-left; callers needing top-left ordering
    // must flip the rows of cpu_data afterwards.
    LOGI("GPU download: %dx%d tex=%u", width, height, tex);
#else
    LOGW("GPU download: OpenGL ES unavailable (non-Android build)");
#endif
}

void ImageBuffer::release_gpu() {
    if (gpu_texture_id) {
        // glDeleteTextures / vkDestroyImage
        gpu_texture_id = nullptr;
    }
    gpu_buffer_handle = nullptr;
    gpu_data_valid = false;
}

// Simple memory pool for buffer reuse
static std::mutex g_pool_mutex;
static std::list<ImageBuffer*> g_buffer_pool;

ImageBuffer* ImageBuffer::acquire_from_pool(int w, int h, PixelFormat fmt) {
    std::lock_guard<std::mutex> lock(g_pool_mutex);
    for (auto it = g_buffer_pool.begin(); it != g_buffer_pool.end(); ++it) {
        if ((*it)->width == w && (*it)->height == h && (*it)->format == fmt) {
            ImageBuffer* buf = *it;
            g_buffer_pool.erase(it);
            return buf;
        }
    }
    return new ImageBuffer(w, h, fmt);
}

void ImageBuffer::return_to_pool(ImageBuffer* buf) {
    if (!buf) return;
    std::lock_guard<std::mutex> lock(g_pool_mutex);
    g_buffer_pool.push_back(buf);
}

void ImageBuffer::clear_pool() {
    std::lock_guard<std::mutex> lock(g_pool_mutex);
    for (auto* buf : g_buffer_pool) {
        delete buf;
    }
    g_buffer_pool.clear();
}

} // namespace alcedo