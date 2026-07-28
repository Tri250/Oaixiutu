// AlcedoAndroid - Scope analyzer (histogram / waveform / vectorscope).
// Self-contained Android port: the desktop UI frame_sink dependency is replaced
// by a simple ImageBuffer-backed FinalDisplayFrameView, and the GPU backends
// collapse to a single Vulkan compute path (with CPU fallback).
// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <cstddef>
#include <cstdint>
#include <memory>
#include <vector>

#include "image/gpu_backend.hpp"
#include "image/image_buffer.hpp"

namespace alcedo {

enum class AnalysisDomain : uint32_t {
  DisplayEncoded = 0,
  LinearLight    = 1,
};

// A view of the final display frame submitted for scope analysis. Holds either
// a CPU FloatMat snapshot or a GPU (Vulkan) image; exactly one is typically
// populated by the renderer tap.
struct FinalDisplayFrameView {
  std::shared_ptr<ImageBuffer> image          = {};
  int                          width          = 0;
  int                          height         = 0;
  int                          channels       = 3;
  AnalysisDomain               domain         = AnalysisDomain::DisplayEncoded;
  GpuBackendKind               backend        = GpuBackendKind::None;
  uint64_t                     frame_id       = 0;

  explicit operator bool() const { return image != nullptr && width > 0 && height > 0; }
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

// Output set: CPU-readable result buffers. On Android the GPU path downloads
// its results into these host vectors so the UI reads a single representation.
struct ScopeOutputSet {
  std::vector<uint32_t> histogram_counts;   // bins * 3 (R,G,B)
  std::vector<float>    waveform_rgba;      // width * height * 4
  std::vector<float>    vectorscope_rgba;   // size * size * 4
  std::vector<float>    chromaticity_rgba;  // size * size * 4

  int  histogram_bins     = 0;
  int  waveform_width     = 0;
  int  waveform_height    = 0;
  int  vectorscope_size   = 0;
  int  chromaticity_size  = 0;

  bool histogram_valid    = false;
  bool waveform_valid     = false;
  bool vectorscope_valid  = false;
  bool chromaticity_valid = false;
  uint64_t generation     = 0;
};

struct ScopeHistogramRenderData {
  int                bins                   = 0;
  int                clip_tail_bins         = 0;
  float              shadow_clip_ratio      = 0.0f;
  float              highlight_clip_ratio   = 0.0f;
  bool               shadow_clip_warning    = false;
  bool               highlight_clip_warning = false;
  std::vector<float> rgb                    = {};
  bool               valid                  = false;
};

struct ScopeWaveformRenderData {
  int                width  = 0;
  int                height = 0;
  std::vector<float> rgba   = {};
  bool               valid  = false;
};

struct ScopeRenderSnapshot {
  ScopeHistogramRenderData histogram  = {};
  ScopeWaveformRenderData  waveform   = {};
  uint64_t                 generation = 0;
};

class IScopeAnalyzer {
 public:
  virtual ~IScopeAnalyzer() = default;
  virtual void SubmitFrame(const FinalDisplayFrameView& frame, const ScopeRequest& request) = 0;
  virtual auto GetLatestOutput() -> ScopeOutputSet                                       = 0;
  virtual void ResizeResources(const ScopeRequest& request)                              = 0;
  virtual void ReleaseResources()                                                        = 0;
};

class IFinalDisplayFrameProvider {
 public:
  virtual ~IFinalDisplayFrameProvider()                                          = default;
  virtual auto GetCurrentDisplayFrameView() const -> FinalDisplayFrameView       = 0;
};

// Factories.
auto CreateCpuScopeAnalyzer() -> std::shared_ptr<IScopeAnalyzer>;
auto CreateVulkanScopeAnalyzer() -> std::shared_ptr<IScopeAnalyzer>;
auto CreateDefaultScopeAnalyzer() -> std::shared_ptr<IScopeAnalyzer>;

// Normalize a raw output set into UI render data.
auto ReadScopeRenderSnapshot(const ScopeOutputSet& output) -> ScopeRenderSnapshot;

}  // namespace alcedo
