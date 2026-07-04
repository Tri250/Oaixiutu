#pragma once

#include "gpu_context.h"
#include "gpu_buffer.h"
#include <memory>
#include <string>
#include <vector>
#include <functional>
#include <unordered_map>
#include <chrono>

namespace alcedo {
namespace gpu {

// ============================================================
// GPU operator types
// ============================================================
enum class GpuOperatorType {
    EXPOSURE = 0,
    CONTRAST,
    SATURATION,
    WHITE_BALANCE,
    TONE_CURVE,
    HSL,
    COLOR_WHEEL,
    SHARPEN,
    FILM_GRAIN,
    HALATION,
    COLOR_SCIENCE,
    GEOMETRY,
    LUT3D,
    HIGHLIGHT_RECON,
    RCD_DEMOSAIC,
    COUNT
};

inline const char* gpuOpName(GpuOperatorType op) {
    switch (op) {
        case GpuOperatorType::EXPOSURE:        return "Exposure";
        case GpuOperatorType::CONTRAST:         return "Contrast";
        case GpuOperatorType::SATURATION:       return "Saturation";
        case GpuOperatorType::WHITE_BALANCE:    return "WhiteBalance";
        case GpuOperatorType::TONE_CURVE:       return "ToneCurve";
        case GpuOperatorType::HSL:              return "HSL";
        case GpuOperatorType::COLOR_WHEEL:      return "ColorWheel";
        case GpuOperatorType::SHARPEN:          return "Sharpen";
        case GpuOperatorType::FILM_GRAIN:       return "FilmGrain";
        case GpuOperatorType::HALATION:         return "Halation";
        case GpuOperatorType::COLOR_SCIENCE:    return "ColorScience";
        case GpuOperatorType::GEOMETRY:         return "Geometry";
        case GpuOperatorType::LUT3D:            return "LUT3D";
        case GpuOperatorType::HIGHLIGHT_RECON:  return "HighlightRecon";
        case GpuOperatorType::RCD_DEMOSAIC:     return "RCDDemosaic";
        default: return "Unknown";
    }
}

// ============================================================
// Performance metrics
// ============================================================
struct GpuPerfMetrics {
    double elapsedMs = 0.0;
    GpuBackend backend = GpuBackend::NONE;
    GpuOperatorType operation = GpuOperatorType::EXPOSURE;
    uint32_t width = 0;
    uint32_t height = 0;
    bool success = false;

    double megapixelsPerSecond() const {
        if (elapsedMs <= 0.0) return 0.0;
        double mp = (double)(width * height) / 1e6;
        return mp / (elapsedMs / 1000.0);
    }
};

// ============================================================
// Operator parameters
// ============================================================
struct GpuOperatorParams {
    union {
        struct { float exposureStops; };
        struct { float contrast; };
        struct { float saturation; };
        struct { float temperature; float tint; };
        struct { float blacks[4]; float curveStrength; };
        struct { float hueShift; float saturationAdj; float lightnessAdj; };
        struct { float shadowColor[3]; float midtoneColor[3]; float highlightColor[3]; float lift[3]; float gamma[3]; float gain[3]; };
        struct { float sharpenAmount; float sharpenRadius; float sharpenThreshold; };
        struct { float grainAmount; float grainSize; float roughness; float seed; };
        struct { float halationAmount; float spread; float strength; };
        struct { float csExposure; int tonemapMode; float outputGamma; };
        struct { float rotation; float scale; float offset[2]; int flipH; int flipV; };
        struct { float lutIntensity; int lutSize; };
        struct { float hrClipThreshold; int hrMethod; };
        struct { int bayerPattern; float blackLevel; float whiteLevel; };
    };
};

// ============================================================
// GPU operator interface
// ============================================================
class IGpuOperator {
public:
    virtual ~IGpuOperator() = default;
    virtual GpuOperatorType type() const = 0;
    virtual bool execute(GpuBuffer* input, GpuBuffer* output, const GpuOperatorParams& params) = 0;
    virtual bool supportsBackend(GpuBackend backend) const = 0;
};

// ============================================================
// GPU pipeline orchestrator
// ============================================================
class GpuPipeline {
public:
    static GpuPipeline& instance() {
        static GpuPipeline pipeline;
        return pipeline;
    }

    // Initialize with context
    bool initialize();
    void shutdown();

    // Register operator implementations
    void registerOperator(std::unique_ptr<IGpuOperator> op);
    bool hasOperator(GpuOperatorType type) const;

    // Execute with current backend
    bool execute(GpuOperatorType type,
                 GpuBuffer* input, GpuBuffer* output,
                 const GpuOperatorParams& params);

    // Execute with explicit backend
    bool executeWithBackend(GpuBackend backend,
                            GpuOperatorType type,
                            GpuBuffer* input, GpuBuffer* output,
                            const GpuOperatorParams& params);

    // Execute a chain of operators
    bool executeChain(const std::vector<std::pair<GpuOperatorType, GpuOperatorParams>>& operations,
                      GpuBuffer* input, GpuBuffer* output);

    // Backend management
    void setBackend(GpuBackend backend);
    GpuBackend getBackend() const { return currentBackend_; }
    bool isBackendAvailable(GpuBackend backend) const;

    // Performance metrics
    const std::vector<GpuPerfMetrics>& getPerformanceHistory() const { return perfHistory_; }
    void clearPerformanceHistory() { perfHistory_.clear(); }
    double getAverageTimeMs(GpuOperatorType type) const;

    // CPU fallback check
    bool shouldFallbackToCpu() const { return fallbackToCpu_; }
    void setFallbackToCpu(bool fallback) { fallbackToCpu_ = fallback; }

private:
    GpuPipeline() = default;
    ~GpuPipeline() { shutdown(); }

    GpuContextPtr getContext(GpuBackend backend);

    GpuBackend currentBackend_ = GpuBackend::NONE;
    bool initialized_ = false;
    bool fallbackToCpu_ = false;

    std::unordered_map<GpuOperatorType, std::vector<std::unique_ptr<IGpuOperator>>> operators_;
    std::vector<GpuPerfMetrics> perfHistory_;
    static constexpr size_t kMaxPerfHistory = 100;
};

// ============================================================
// CPU fallback operator implementations
// ============================================================
class CpuFallbackOperator : public IGpuOperator {
public:
    explicit CpuFallbackOperator(GpuOperatorType type) : type_(type) {}

    GpuOperatorType type() const override { return type_; }
    bool supportsBackend(GpuBackend backend) const override { return backend == GpuBackend::CPU; }

    bool execute(GpuBuffer* input, GpuBuffer* output, const GpuOperatorParams& params) override;

private:
    GpuOperatorType type_;

    void executeExposure(float* pixels, int w, int h, const GpuOperatorParams& p);
    void executeContrast(float* pixels, int w, int h, const GpuOperatorParams& p);
    void executeSaturation(float* pixels, int w, int h, const GpuOperatorParams& p);
    void executeWhiteBalance(float* pixels, int w, int h, const GpuOperatorParams& p);
    void executeToneCurve(float* pixels, int w, int h, const GpuOperatorParams& p);
    void executeHSL(float* pixels, int w, int h, const GpuOperatorParams& p);
    void executeSharpen(float* pixels, int w, int h, const GpuOperatorParams& p);
    void executeFilmGrain(float* pixels, int w, int h, const GpuOperatorParams& p);
};

} // namespace gpu
} // namespace alcedo