#include "gpu_pipeline.h"
#include "gles/gles_context.h"
#include "gles/gles_compute.h"
#include "vulkan/vulkan_context.h"
#include <cmath>
#include <algorithm>
#include <cstring>
#include <utility>

namespace alcedo {
namespace gpu {

// ============================================================
// GpuContextManager context factory
// ============================================================
GpuContextPtr GpuContextManager::createContextImpl(GpuBackend backend) {
    switch (backend) {
        case GpuBackend::OPENGL_ES: {
            auto ctx = std::make_shared<GlesContext>();
            if (ctx->init(nullptr)) {
                return ctx;
            }
            GPU_LOGE("Failed to initialize GLES context");
            return nullptr;
        }
        case GpuBackend::VULKAN: {
            auto ctx = std::make_shared<VulkanContext>();
            if (ctx->init(nullptr)) {
                return ctx;
            }
            GPU_LOGE("Failed to initialize Vulkan context");
            return nullptr;
        }
        case GpuBackend::CPU:
            return nullptr; // CPU doesn't need a GPU context
        default:
            return nullptr;
    }
}

// ============================================================
// GpuPipeline implementation
// ============================================================

bool GpuPipeline::initialize() {
    if (initialized_) return true;

    auto& ctxMgr = GpuContextManager::instance();
    GpuBackend preferred = ctxMgr.detectAvailableBackends();
    GPU_LOGI("Detected backends. Preferred: %s", gpuBackendName(preferred));

    // Register CPU fallback for all operators
    for (int i = 0; i < static_cast<int>(GpuOperatorType::COUNT); ++i) {
        auto type = static_cast<GpuOperatorType>(i);
        registerOperator(std::make_unique<CpuFallbackOperator>(type));
    }

    currentBackend_ = preferred;
    initialized_ = true;
    return true;
}

void GpuPipeline::shutdown() {
    operators_.clear();
    GpuContextManager::instance().destroyAll();
    initialized_ = false;
}

void GpuPipeline::registerOperator(std::unique_ptr<IGpuOperator> op) {
    auto type = op->type();
    operators_[type].push_back(std::move(op));
}

bool GpuPipeline::hasOperator(GpuOperatorType type) const {
    auto it = operators_.find(type);
    return it != operators_.end() && !it->second.empty();
}

bool GpuPipeline::execute(GpuOperatorType type,
                          GpuBuffer* input, GpuBuffer* output,
                          const GpuOperatorParams& params) {
    return executeWithBackend(currentBackend_, type, input, output, params);
}

bool GpuPipeline::executeWithBackend(GpuBackend backend,
                                     GpuOperatorType type,
                                     GpuBuffer* input, GpuBuffer* output,
                                     const GpuOperatorParams& params) {
    if (!input || !output || !input->isValid() || !output->isValid()) {
        GPU_LOGE("Invalid input/output buffers for %s", gpuOpName(type));
        return false;
    }

    auto startTime = std::chrono::high_resolution_clock::now();

    GpuPerfMetrics metrics;
    metrics.operation = type;
    metrics.backend = backend;
    metrics.width = input->width();
    metrics.height = input->height();

    bool success = false;

    // Try to find an operator for the requested backend
    auto it = operators_.find(type);
    if (it != operators_.end()) {
        for (auto& op : it->second) {
            if (op->supportsBackend(backend)) {
                success = op->execute(input, output, params);
                if (success) break;
            }
        }
    }

    // Fallback to CPU if needed
    if (!success && backend != GpuBackend::CPU) {
        GPU_LOGW("GPU operator %s failed on %s, falling back to CPU",
                 gpuOpName(type), gpuBackendName(backend));
        metrics.backend = GpuBackend::CPU;

        if (it != operators_.end()) {
            for (auto& op : it->second) {
                if (op->supportsBackend(GpuBackend::CPU)) {
                    success = op->execute(input, output, params);
                    if (success) break;
                }
            }
        }
    }

    auto endTime = std::chrono::high_resolution_clock::now();
    metrics.elapsedMs = std::chrono::duration<double, std::milli>(endTime - startTime).count();
    metrics.success = success;

    // Record performance
    perfHistory_.push_back(metrics);
    if (perfHistory_.size() > kMaxPerfHistory) {
        perfHistory_.erase(perfHistory_.begin());
    }

    if (success) {
        GPU_LOGI("Operator %s executed on %s in %.2f ms (%.2f MP/s)",
                 gpuOpName(type), gpuBackendName(metrics.backend),
                 metrics.elapsedMs, metrics.megapixelsPerSecond());
    } else {
        GPU_LOGE("Operator %s failed on all backends", gpuOpName(type));
    }

    return success;
}

bool GpuPipeline::executeChain(
    const std::vector<std::pair<GpuOperatorType, GpuOperatorParams>>& operations,
    GpuBuffer* input, GpuBuffer* output) {

    if (operations.empty()) {
        // Copy input to output
        if (input != output) {
            void* srcData = nullptr;
            void* dstData = nullptr;
            if (input->lockRead(&srcData) && output->lockWrite(&dstData)) {
                memcpy(dstData, srcData, input->totalBytes());
                output->unlock();
                input->unlock();
            }
        }
        return true;
    }

    // For single operation, execute directly
    if (operations.size() == 1) {
        return execute(operations[0].first, input, output, operations[0].second);
    }

    // For multiple operations, use ping-pong buffers
    auto& ctxMgr = GpuContextManager::instance();
    auto ctx = ctxMgr.getOrCreateCurrent();

    GpuBufferDesc desc = input->desc();
    GpuBuffer ping, pong;

    if (!ping.allocate(desc)) {
        GPU_LOGE("Failed to allocate ping buffer");
        return false;
    }
    if (!pong.allocate(desc)) {
        GPU_LOGE("Failed to allocate pong buffer");
        return false;
    }

    // Copy input to ping
    {
        void* src = nullptr;
        void* dst = nullptr;
        if (input->lockRead(&src) && ping.lockWrite(&dst)) {
            memcpy(dst, src, input->totalBytes());
            ping.unlock();
            input->unlock();
        }
    }

    GpuBuffer* current = &ping;
    GpuBuffer* next = &pong;
    bool success = true;

    for (size_t i = 0; i < operations.size(); ++i) {
        GpuBuffer* result = (i == operations.size() - 1) ? output : next;
        success = execute(operations[i].first, current, result, operations[i].second);
        if (!success) break;

        // Swap buffers for next iteration (only when we wrote to the intermediate buffer)
        if (result == next) {
            std::swap(current, next);
        }
    }

    // The last operation wrote directly to output, so no copy is needed.
    // If the chain failed mid-way, output is left in an undefined state.

    return success;
}

void GpuPipeline::setBackend(GpuBackend backend) {
    if (backend != currentBackend_) {
        GPU_LOGI("Switching GPU pipeline backend: %s -> %s",
                 gpuBackendName(currentBackend_),
                 gpuBackendName(backend));
        currentBackend_ = backend;
        GpuContextManager::instance().setCurrentBackend(backend);
    }
}

bool GpuPipeline::isBackendAvailable(GpuBackend backend) const {
    if (backend == GpuBackend::CPU) return true;
    const auto& available = GpuContextManager::instance().getAvailableBackends();
    return std::find(available.begin(), available.end(), backend) != available.end();
}

double GpuPipeline::getAverageTimeMs(GpuOperatorType type) const {
    double total = 0.0;
    int count = 0;
    for (const auto& m : perfHistory_) {
        if (m.operation == type && m.success) {
            total += m.elapsedMs;
            count++;
        }
    }
    return count > 0 ? total / count : 0.0;
}

// ============================================================
// CpuFallbackOperator implementation
// ============================================================

bool CpuFallbackOperator::execute(GpuBuffer* input, GpuBuffer* output,
                                  const GpuOperatorParams& params) {
    if (!input || !output) return false;

    void* inData = nullptr;
    void* outData = nullptr;
    int32_t stride = 0;

    if (!input->lockRead(&inData, &stride)) {
        GPU_LOGE("CPU fallback: failed to lock input buffer");
        return false;
    }

    if (!output->lockWrite(&outData)) {
        input->unlock();
        GPU_LOGE("CPU fallback: failed to lock output buffer");
        return false;
    }

    int w = input->width();
    int h = input->height();
    int pixelCount = w * h;

    // Copy input to output first
    memcpy(outData, inData, input->totalBytes());

    float* pixels = static_cast<float*>(outData);

    switch (type_) {
        case GpuOperatorType::EXPOSURE:
            executeExposure(pixels, w, h, params);
            break;
        case GpuOperatorType::CONTRAST:
            executeContrast(pixels, w, h, params);
            break;
        case GpuOperatorType::SATURATION:
            executeSaturation(pixels, w, h, params);
            break;
        case GpuOperatorType::WHITE_BALANCE:
            executeWhiteBalance(pixels, w, h, params);
            break;
        case GpuOperatorType::TONE_CURVE:
            executeToneCurve(pixels, w, h, params);
            break;
        case GpuOperatorType::HSL:
            executeHSL(pixels, w, h, params);
            break;
        case GpuOperatorType::SHARPEN:
            executeSharpen(pixels, w, h, params);
            break;
        case GpuOperatorType::FILM_GRAIN:
            executeFilmGrain(pixels, w, h, params);
            break;
        default:
            // CPU 回退：根据算子类型执行对应的 CPU 实现
            switch (type_) {
                case GpuOperatorType::COLOR_WHEEL: {
                    // CPU 色彩调色：lift/gamma/gain
                    int count = w * h * 4;
                    for (int i = 0; i < count; i += 4) {
                        for (int c = 0; c < 3; ++c) {
                            float lift = params.lift[c];
                            float gain = params.gain[c];
                            float gamma = (params.gamma[c] > 0.0f) ? params.gamma[c] : 1.0f;
                            float val = pixels[i + c] / 255.0f;
                            val = gain * std::pow(std::max((val + lift) / (1.0f + lift), 0.0f), gamma);
                            pixels[i + c] = std::clamp(val * 255.0f, 0.0f, 255.0f);
                        }
                    }
                    break;
                }
                case GpuOperatorType::HALATION: {
                    // CPU 光晕：增强高光区域
                    float amount = params.halationAmount;
                    int count = w * h * 4;
                    for (int i = 0; i < count; i += 4) {
                        for (int c = 0; c < 3; ++c) {
                            float val = pixels[i + c];
                            if (val > 180.0f) {
                                pixels[i + c] = std::clamp(val + (val - 180.0f) * amount, 0.0f, 255.0f);
                            }
                        }
                    }
                    break;
                }
                case GpuOperatorType::COLOR_SCIENCE: {
                    // CPU 色彩科学：曝光 + gamma
                    float scale = std::pow(2.0f, params.csExposure);
                    float gamma = (params.outputGamma > 0.0f) ? params.outputGamma : 1.0f;
                    int count = w * h * 4;
                    for (int i = 0; i < count; i += 4) {
                        for (int c = 0; c < 3; ++c) {
                            float val = pixels[i + c] / 255.0f;
                            val = std::pow(std::max(val * scale, 0.0f), 1.0f / gamma);
                            pixels[i + c] = std::clamp(val * 255.0f, 0.0f, 255.0f);
                        }
                    }
                    break;
                }
                case GpuOperatorType::GEOMETRY: {
                    // CPU 几何：水平/垂直翻转
                    if (params.flipH) {
                        for (int y = 0; y < h; ++y) {
                            for (int x = 0; x < w / 2; ++x) {
                                int i1 = (y * w + x) * 4;
                                int i2 = (y * w + (w - 1 - x)) * 4;
                                for (int c = 0; c < 4; ++c) std::swap(pixels[i1 + c], pixels[i2 + c]);
                            }
                        }
                    }
                    if (params.flipV) {
                        for (int y = 0; y < h / 2; ++y) {
                            for (int x = 0; x < w; ++x) {
                                int i1 = (y * w + x) * 4;
                                int i2 = ((h - 1 - y) * w + x) * 4;
                                for (int c = 0; c < 4; ++c) std::swap(pixels[i1 + c], pixels[i2 + c]);
                            }
                        }
                    }
                    break;
                }
                case GpuOperatorType::LUT3D: {
                    // CPU LUT：无 LUT 数据时保持不变（占位实现）
                    break;
                }
                case GpuOperatorType::HIGHLIGHT_RECON: {
                    // CPU 高光重建：裁剪高光
                    float thresh = params.hrClipThreshold * 255.0f;
                    int count = w * h * 4;
                    for (int i = 0; i < count; i += 4) {
                        for (int c = 0; c < 3; ++c) {
                            if (pixels[i + c] > thresh) pixels[i + c] = thresh;
                        }
                    }
                    break;
                }
                case GpuOperatorType::RCD_DEMOSAIC: {
                    // CPU 去马赛克：减黑电平并归一化（简化）
                    float black = params.blackLevel;
                    float white = params.whiteLevel;
                    if (white > black) {
                        float norm = 255.0f / (white - black);
                        int count = w * h * 4;
                        for (int i = 0; i < count; i += 4) {
                            for (int c = 0; c < 3; ++c) {
                                pixels[i + c] = std::clamp((pixels[i + c] - black) * norm, 0.0f, 255.0f);
                            }
                        }
                    }
                    break;
                }
                default:
                    GPU_LOGW("无 CPU 回退实现: %s", gpuOpName(type_));
                    break;
            }
            break;
    }

    output->unlock();
    input->unlock();
    return true;
}

void CpuFallbackOperator::executeExposure(float* pixels, int w, int h, const GpuOperatorParams& p) {
    float scale = std::pow(2.0f, p.exposureStops);
    int count = w * h * 4;
    for (int i = 0; i < count; i += 4) {
        pixels[i]     *= scale;
        pixels[i + 1] *= scale;
        pixels[i + 2] *= scale;
    }
}

void CpuFallbackOperator::executeContrast(float* pixels, int w, int h, const GpuOperatorParams& p) {
    float mid = 128.0f;
    float factor = std::tan((p.contrast + 1.0f) * 0.785398f);
    int count = w * h * 4;
    for (int i = 0; i < count; i += 4) {
        for (int j = 0; j < 3; ++j) {
            float val = pixels[i + j];
            pixels[i + j] = std::clamp(mid + (val - mid) * factor, 0.0f, 255.0f);
        }
    }
}

void CpuFallbackOperator::executeSaturation(float* pixels, int w, int h, const GpuOperatorParams& p) {
    const float lumR = 0.299f, lumG = 0.587f, lumB = 0.114f;
    float sat = p.saturation + 1.0f;
    int count = w * h * 4;
    for (int i = 0; i < count; i += 4) {
        float lum = pixels[i] * lumR + pixels[i + 1] * lumG + pixels[i + 2] * lumB;
        pixels[i]     = std::clamp(lum + (pixels[i]     - lum) * sat, 0.0f, 255.0f);
        pixels[i + 1] = std::clamp(lum + (pixels[i + 1] - lum) * sat, 0.0f, 255.0f);
        pixels[i + 2] = std::clamp(lum + (pixels[i + 2] - lum) * sat, 0.0f, 255.0f);
    }
}

void CpuFallbackOperator::executeWhiteBalance(float* pixels, int w, int h, const GpuOperatorParams& p) {
    float temp = std::clamp(p.temperature, 2000.0f, 50000.0f) / 100.0f;
    float r, g, b;

    if (temp <= 66.0f) {
        r = 255.0f;
        g = 99.4708025861f * std::log(temp) - 161.1195681661f;
        b = (temp <= 19.0f) ? 0.0f : 138.5177312231f * std::log(temp - 10.0f) - 305.0447927307f;
    } else {
        r = 329.698727446f * std::pow(temp - 60.0f, -0.1332047592f);
        g = 288.1221695283f * std::pow(temp - 60.0f, -0.0755148492f);
        b = 255.0f;
    }

    r = std::clamp(r / 255.0f, 0.0f, 1.0f);
    g = std::clamp(g / 255.0f, 0.0f, 1.0f);
    b = std::clamp(b / 255.0f, 0.0f, 1.0f);

    g *= 1.0f + p.tint * 0.5f;
    r *= 1.0f - p.tint * 0.25f;
    b *= 1.0f - p.tint * 0.25f;

    float lumScale = r * 0.2126f + g * 0.7152f + b * 0.0722f;
    if (lumScale > 0.001f) {
        r /= lumScale;
        g /= lumScale;
        b /= lumScale;
    }

    int count = w * h * 4;
    for (int i = 0; i < count; i += 4) {
        pixels[i]     = std::clamp(pixels[i]     * r, 0.0f, 255.0f);
        pixels[i + 1] = std::clamp(pixels[i + 1] * g, 0.0f, 255.0f);
        pixels[i + 2] = std::clamp(pixels[i + 2] * b, 0.0f, 255.0f);
    }
}

void CpuFallbackOperator::executeToneCurve(float* pixels, int w, int h, const GpuOperatorParams& p) {
    float s = -p.blacks[0];
    float hl = p.blacks[3];
    int count = w * h * 4;
    for (int i = 0; i < count; i += 4) {
        for (int j = 0; j < 3; ++j) {
            float x = pixels[i + j] / 255.0f;
            float t = x * 2.0f - 1.0f;
            float curve = (t < 0.0f) ? (t * (1.0f - s)) / (t - s) : (t * (1.0f + hl)) / (t + hl);
            curve = (curve + 1.0f) * 0.5f;
            pixels[i + j] = std::clamp(curve * 255.0f, 0.0f, 255.0f);
        }
    }
}

void CpuFallbackOperator::executeHSL(float* pixels, int w, int h, const GpuOperatorParams& p) {
    int count = w * h * 4;

    auto rgbToHsl = [](float r, float g, float b, float& h, float& s, float& l) {
        float minC = std::min({r, g, b});
        float maxC = std::max({r, g, b});
        float delta = maxC - minC;
        l = (maxC + minC) * 0.5f;
        s = 0.0f;
        h = 0.0f;
        if (delta > 0.0001f) {
            s = l < 0.5f ? delta / (maxC + minC) : delta / (2.0f - maxC - minC);
            if (r == maxC)      h = (g - b) / delta;
            else if (g == maxC) h = 2.0f + (b - r) / delta;
            else                h = 4.0f + (r - g) / delta;
            h /= 6.0f;
            if (h < 0.0f) h += 1.0f;
        }
    };

    auto hueToRgb = [](float v1, float v2, float h) {
        if (h < 0.0f) h += 1.0f;
        if (h > 1.0f) h -= 1.0f;
        if (6.0f * h < 1.0f) return v1 + (v2 - v1) * 6.0f * h;
        if (2.0f * h < 1.0f) return v2;
        if (3.0f * h < 2.0f) return v1 + (v2 - v1) * ((2.0f / 3.0f) - h) * 6.0f;
        return v1;
    };

    for (int i = 0; i < count; i += 4) {
        float r = pixels[i] / 255.0f;
        float g = pixels[i + 1] / 255.0f;
        float b = pixels[i + 2] / 255.0f;

        float h, s, l;
        rgbToHsl(r, g, b, h, s, l);

        h = std::fmod(h + p.hueShift / 360.0f, 1.0f);
        s = std::clamp(s + p.saturationAdj, 0.0f, 1.0f);
        l = std::clamp(l + p.lightnessAdj, 0.0f, 1.0f);

        float v2 = l < 0.5f ? l * (1.0f + s) : l + s - l * s;
        float v1 = 2.0f * l - v2;

        pixels[i]     = std::clamp(hueToRgb(v1, v2, h + 1.0f / 3.0f) * 255.0f, 0.0f, 255.0f);
        pixels[i + 1] = std::clamp(hueToRgb(v1, v2, h) * 255.0f, 0.0f, 255.0f);
        pixels[i + 2] = std::clamp(hueToRgb(v1, v2, h - 1.0f / 3.0f) * 255.0f, 0.0f, 255.0f);
    }
}

void CpuFallbackOperator::executeSharpen(float* pixels, int w, int h, const GpuOperatorParams& p) {
    if (p.sharpenAmount < 0.001f) return;

    std::vector<float> blurred(w * h * 4);
    // Simple 3x3 box blur
    const float kernel[9] = {1.0f/16, 2.0f/16, 1.0f/16, 2.0f/16, 4.0f/16, 2.0f/16, 1.0f/16, 2.0f/16, 1.0f/16};

    for (int y = 1; y < h - 1; ++y) {
        for (int x = 1; x < w - 1; ++x) {
            for (int c = 0; c < 3; ++c) {
                float sum = 0.0f;
                int ki = 0;
                for (int dy = -1; dy <= 1; ++dy) {
                    for (int dx = -1; dx <= 1; ++dx) {
                        int idx = ((y + dy) * w + (x + dx)) * 4 + c;
                        sum += pixels[idx] * kernel[ki++];
                    }
                }
                blurred[(y * w + x) * 4 + c] = sum;
            }
        }
    }

    for (int i = 0; i < w * h * 4; i += 4) {
        for (int c = 0; c < 3; ++c) {
            float detail = pixels[i + c] - blurred[i + c];
            float threshold = p.sharpenThreshold * 255.0f;
            if (std::abs(detail) < threshold) detail = 0.0f;
            pixels[i + c] = std::clamp(pixels[i + c] + detail * p.sharpenAmount * 2.0f, 0.0f, 255.0f);
        }
    }
}

void CpuFallbackOperator::executeFilmGrain(float* pixels, int w, int h, const GpuOperatorParams& p) {
    if (p.grainAmount < 0.001f) return;

    auto hash = [](float x, float y) {
        float h = x * 127.1f + y * 311.7f;
        return std::fmod(std::sin(h) * 43758.5453f, 1.0f);
    };

    for (int y = 0; y < h; ++y) {
        for (int x = 0; x < w; ++x) {
            int idx = (y * w + x) * 4;
            float grain = (hash(x / p.grainSize + p.seed, y / p.grainSize + p.seed * 1.37f) * 2.0f - 1.0f);

            float r = pixels[idx] / 255.0f;
            float g = pixels[idx + 1] / 255.0f;
            float b = pixels[idx + 2] / 255.0f;
            float lum = 0.2126f * r + 0.7152f * g + 0.0722f * b;

            float grainStrength = grain * p.grainAmount * 0.15f * (1.0f - lum * 0.5f);

            pixels[idx]     = std::clamp(pixels[idx]     + grainStrength * 255.0f, 0.0f, 255.0f);
            pixels[idx + 1] = std::clamp(pixels[idx + 1] + grainStrength * 255.0f, 0.0f, 255.0f);
            pixels[idx + 2] = std::clamp(pixels[idx + 2] + grainStrength * 255.0f, 0.0f, 255.0f);
        }
    }
}

} // namespace gpu
} // namespace alcedo