// Ported from AlcedoStudio desktop: edit/scope/scope_analyzer.hpp
// SPDX-License-Identifier: GPL-3.0-only
//
// Scope analysis surface. The desktop ships CUDA/Metal/OpenCL scope analyzers
// that tap into the GPU display frame. On Android the GLES pipeline already
// owns the frame, so this port provides a self-contained CPU implementation
// that accepts a plain RGBA float* frame (the same buffer the pipeline emits)
// and produces histogram / waveform / vectorscope render data. The interface
// matches the desktop so a future GLES-backed analyzer can drop in.

#pragma once

#include <cstddef>
#include <cstdint>
#include <memory>
#include <vector>

namespace alcedo {

enum class FramePixelFormat : uint32_t {
    RGBA32F = 0,
    RGBA16F = 1,
    RGBA8   = 2,
};

struct ViewerDisplayConfig {
    float exposure_stops = 0.0f;
    float gamma          = 2.2f;
};

enum class AnalysisDomain : uint32_t {
    DisplayEncoded = 0,
    LinearLight    = 1,
};

struct FinalDisplayFrameView {
    const float*         pixels    = nullptr;  // RGBA32F, row-major, tightly packed
    int                  width     = 0;
    int                  height    = 0;
    int                  channels  = 4;
    FramePixelFormat     format    = FramePixelFormat::RGBA32F;
    ViewerDisplayConfig  display_config = {};
    AnalysisDomain       domain    = AnalysisDomain::DisplayEncoded;
    uint64_t             frame_id  = 0;

    explicit operator bool() const { return pixels != nullptr && width > 0 && height > 0; }
};

enum class ScopeType : uint32_t {
    Histogram    = 1u << 0,
    Waveform     = 1u << 1,
    Vectorscope  = 1u << 2,
    Chromaticity = 1u << 3,
};

struct ScopeRequest {
    uint32_t enabled_mask =
        static_cast<uint32_t>(ScopeType::Histogram) | static_cast<uint32_t>(ScopeType::Waveform);
    int histogram_bins      = 256;
    int waveform_width      = 384;
    int waveform_height     = 192;
    int vectorscope_size    = 256;
    int chromaticity_size   = 256;
    int analysis_downsample = 4;
    int target_fps          = 20;
};

struct ScopeHistogramRenderData {
    int                bins                   = 0;
    int                clip_tail_bins         = 0;
    float              shadow_clip_ratio      = 0.0f;
    float              highlight_clip_ratio   = 0.0f;
    bool               shadow_clip_warning    = false;
    bool               highlight_clip_warning = false;
    std::vector<float> rgb;  // size = bins * 3 (interleaved R,G,B)
    bool               valid                  = false;
};

struct ScopeWaveformRenderData {
    int                width  = 0;
    int                height = 0;
    std::vector<float> rgba;  // size = width * height * 4
    bool               valid  = false;
};

struct ScopeVectorscopeRenderData {
    int                size  = 0;
    std::vector<float> rgba;  // size = size * size * 4
    bool               valid  = false;
};

struct ScopeRenderSnapshot {
    ScopeHistogramRenderData  histogram  = {};
    ScopeWaveformRenderData   waveform   = {};
    ScopeVectorscopeRenderData vectorscope = {};
    uint64_t                  generation = 0;
};

class IScopeAnalyzer {
public:
    virtual ~IScopeAnalyzer() = default;

    virtual void SubmitFrame(const FinalDisplayFrameView& frame, const ScopeRequest& request) = 0;
    virtual ScopeRenderSnapshot GetLatestSnapshot() = 0;
    virtual void ReleaseResources() = 0;
};

// CPU default implementation (no GPU dependency).
std::shared_ptr<IScopeAnalyzer> CreateDefaultScopeAnalyzer();

// Convenience: read a snapshot from the latest submit.
ScopeRenderSnapshot ReadScopeRenderSnapshot(const FinalDisplayFrameView& frame,
                                            const ScopeRequest& request);

}  // namespace alcedo
