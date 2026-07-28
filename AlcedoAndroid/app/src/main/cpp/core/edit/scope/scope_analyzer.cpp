// AlcedoAndroid - CPU scope analyzer + factory + render snapshot reader.
// SPDX-License-Identifier: GPL-3.0-only
#include "edit/scope/scope_analyzer.hpp"

#include <algorithm>
#include <cmath>
#include <limits>
#include <mutex>
#include <numeric>
#include <utility>
#include <vector>

#include "utils/app_logging.hpp"
#include "vulkan/context/vulkan_context.hpp"

namespace alcedo {
namespace {

constexpr float kHistogramClipWarningRatio = 0.02f;

auto HistogramTailBins(int bins) -> int {
  if (bins <= 0) return 0;
  return std::clamp(bins / 64, 1, 4);
}

auto AverageTailRatio(const std::vector<uint32_t>& counts, int bins, int tail_bins,
                      bool highlight_tail) -> float {
  if (bins <= 0 || tail_bins <= 0 ||
      counts.size() < static_cast<size_t>(bins) * 3U) {
    return 0.0f;
  }
  float max_ratio = 0.0f;
  for (int channel = 0; channel < 3; ++channel) {
    const size_t channel_offset = static_cast<size_t>(channel) * static_cast<size_t>(bins);
    uint64_t     total_count    = 0U;
    uint64_t     tail_count     = 0U;
    for (int bin = 0; bin < bins; ++bin) {
      const uint32_t count = counts[channel_offset + static_cast<size_t>(bin)];
      total_count += static_cast<uint64_t>(count);
      const bool in_tail = highlight_tail ? (bin >= bins - tail_bins) : (bin < tail_bins);
      if (in_tail) tail_count += static_cast<uint64_t>(count);
    }
    if (total_count > 0U) {
      max_ratio =
          std::max(max_ratio, static_cast<float>(tail_count) / static_cast<float>(total_count));
    }
  }
  return max_ratio;
}

auto NormalizeHistogramToUnitRange(const std::vector<uint32_t>& counts, int bins)
    -> ScopeHistogramRenderData {
  ScopeHistogramRenderData data;
  if (bins <= 0 || counts.size() < static_cast<size_t>(bins) * 3U) return data;

  data.bins                   = bins;
  data.clip_tail_bins         = HistogramTailBins(bins);
  data.shadow_clip_ratio      = AverageTailRatio(counts, bins, data.clip_tail_bins, false);
  data.highlight_clip_ratio   = AverageTailRatio(counts, bins, data.clip_tail_bins, true);
  data.shadow_clip_warning    = data.shadow_clip_ratio >= kHistogramClipWarningRatio;
  data.highlight_clip_warning = data.highlight_clip_ratio >= kHistogramClipWarningRatio;

  uint32_t denom_count = 0U;
  for (int channel = 0; channel < 3; ++channel) {
    const size_t channel_offset = static_cast<size_t>(channel) * static_cast<size_t>(bins);
    for (int bin = 0; bin < bins; ++bin) {
      const bool skip_shadow    = data.shadow_clip_warning && bin < data.clip_tail_bins;
      const bool skip_highlight = data.highlight_clip_warning && bin >= bins - data.clip_tail_bins;
      if (skip_shadow || skip_highlight) continue;
      denom_count = std::max(denom_count, counts[channel_offset + static_cast<size_t>(bin)]);
    }
  }
  if (denom_count == 0U) {
    const auto max_it =
        std::max_element(counts.begin(), counts.begin() + static_cast<size_t>(bins * 3));
    if (max_it != counts.end()) denom_count = *max_it;
  }
  const float denom = denom_count > 0U ? static_cast<float>(denom_count) : 1.0f;
  data.rgb.resize(static_cast<size_t>(bins) * 3U, 0.0f);
  for (size_t i = 0; i < data.rgb.size(); ++i) {
    data.rgb[i] = std::clamp(static_cast<float>(counts[i]) / denom, 0.0f, 1.0f);
  }
  data.valid = true;
  return data;
}

auto NormalizeWaveformToUnitRange(const std::vector<float>& rgba, int width, int height)
    -> ScopeWaveformRenderData {
  ScopeWaveformRenderData data;
  if (width <= 0 || height <= 0 ||
      rgba.size() < static_cast<size_t>(width) * static_cast<size_t>(height) * 4U) {
    return data;
  }
  float max_value = 0.0f;
  for (float value : rgba) max_value = std::max(max_value, value);
  if (max_value <= std::numeric_limits<float>::epsilon()) max_value = 1.0f;
  data.width  = width;
  data.height = height;
  data.rgba.resize(static_cast<size_t>(width) * static_cast<size_t>(height) * 4U, 0.0f);
  for (size_t i = 0; i < data.rgba.size(); ++i) {
    data.rgba[i] = std::clamp(rgba[i] / max_value, 0.0f, 1.0f);
  }
  data.valid = true;
  return data;
}

// Pure-CPU analyzer: iterates the FloatMat and accumulates histogram/waveform.
class CpuScopeAnalyzer final : public IScopeAnalyzer {
 public:
  void SubmitFrame(const FinalDisplayFrameView& frame, const ScopeRequest& request) override {
    if (!frame || !frame.image) return;
    std::lock_guard<std::mutex> lock(mutex_);
    EnsureResources(request);

    if (request.enabled_mask & static_cast<uint32_t>(ScopeType::Histogram)) {
      ComputeHistogram(*frame.image, request);
    }
    if (request.enabled_mask & static_cast<uint32_t>(ScopeType::Waveform)) {
      ComputeWaveform(*frame.image, request);
    }
    output_.generation++;
  }

  auto GetLatestOutput() -> ScopeOutputSet override {
    std::lock_guard<std::mutex> lock(mutex_);
    return output_;
  }

  void ResizeResources(const ScopeRequest& request) override {
    std::lock_guard<std::mutex> lock(mutex_);
    EnsureResources(request);
  }

  void ReleaseResources() override {
    std::lock_guard<std::mutex> lock(mutex_);
    output_ = ScopeOutputSet{};
  }

 private:
  void EnsureResources(const ScopeRequest& request) {
    if (output_.histogram_bins != request.histogram_bins || output_.histogram_counts.empty()) {
      output_.histogram_counts.assign(static_cast<size_t>(request.histogram_bins) * 3U, 0U);
      output_.histogram_bins = request.histogram_bins;
      output_.histogram_valid = false;
    } else {
      std::fill(output_.histogram_counts.begin(), output_.histogram_counts.end(), 0U);
    }
    if (output_.waveform_width != request.waveform_width ||
        output_.waveform_height != request.waveform_height || output_.waveform_rgba.empty()) {
      output_.waveform_rgba.assign(
          static_cast<size_t>(request.waveform_width) * request.waveform_height * 4U, 0.0f);
      output_.waveform_width  = request.waveform_width;
      output_.waveform_height = request.waveform_height;
      output_.waveform_valid  = false;
    } else {
      std::fill(output_.waveform_rgba.begin(), output_.waveform_rgba.end(), 0.0f);
    }
  }

  void ComputeHistogram(const ImageBuffer& img, const ScopeRequest& request) {
    const FloatMat& mat = img.GetCPUData();
    if (mat.Empty()) return;
    const int bins = request.histogram_bins;
    const int step = std::max(1, request.analysis_downsample);
    const float scale = static_cast<float>(bins - 1);
    for (int y = 0; y < mat.Height(); y += step) {
      const float* row = mat.Ptr(y, 0);
      for (int x = 0; x < mat.Width(); x += step) {
        for (int c = 0; c < 3 && c < mat.Channels(); ++c) {
          float v = row[x * mat.Channels() + c];
          v = std::clamp(v, 0.0f, 1.0f);
          int bin = static_cast<int>(v * scale + 0.5f);
          bin = std::clamp(bin, 0, bins - 1);
          ++output_.histogram_counts[static_cast<size_t>(c) * static_cast<size_t>(bins) +
                                      static_cast<size_t>(bin)];
        }
      }
    }
    output_.histogram_valid = true;
  }

  void ComputeWaveform(const ImageBuffer& img, const ScopeRequest& request) {
    const FloatMat& mat = img.GetCPUData();
    if (mat.Empty()) return;
    const int ww = request.waveform_width;
    const int wh = request.waveform_height;
    const int step = std::max(1, request.analysis_downsample);
    // Column-bucket luminance accumulation.
    std::vector<uint32_t> counts(static_cast<size_t>(ww) * wh * 3U, 0U);
    for (int y = 0; y < mat.Height(); y += step) {
      const float* row = mat.Ptr(y, 0);
      for (int x = 0; x < mat.Width(); x += step) {
        int col = static_cast<int>(static_cast<float>(x) / mat.Width() * ww);
        col = std::clamp(col, 0, ww - 1);
        for (int c = 0; c < 3 && c < mat.Channels(); ++c) {
          float v = row[x * mat.Channels() + c];
          v = std::clamp(v, 0.0f, 1.0f);
          int row_bin = static_cast<int>((1.0f - v) * (wh - 1));
          row_bin = std::clamp(row_bin, 0, wh - 1);
          ++counts[(static_cast<size_t>(row_bin) * ww + col) * 3U + c];
        }
      }
    }
    // Pack into RGBA float buffer; pixel intensity = normalized count.
    uint32_t max_count = *std::max_element(counts.begin(), counts.end());
    const float denom = max_count > 0U ? static_cast<float>(max_count) : 1.0f;
    for (int y = 0; y < wh; ++y) {
      for (int x = 0; x < ww; ++x) {
        const size_t idx = (static_cast<size_t>(y) * ww + x) * 3U;
        const size_t out = (static_cast<size_t>(y) * ww + x) * 4U;
        output_.waveform_rgba[out + 0] = std::clamp(static_cast<float>(counts[idx + 0]) / denom, 0.0f, 1.0f);
        output_.waveform_rgba[out + 1] = std::clamp(static_cast<float>(counts[idx + 1]) / denom, 0.0f, 1.0f);
        output_.waveform_rgba[out + 2] = std::clamp(static_cast<float>(counts[idx + 2]) / denom, 0.0f, 1.0f);
        output_.waveform_rgba[out + 3] = 1.0f;
      }
    }
    output_.waveform_valid = true;
  }

  std::mutex      mutex_;
  ScopeOutputSet  output_;
};

class NullScopeAnalyzer final : public IScopeAnalyzer {
 public:
  void SubmitFrame(const FinalDisplayFrameView&, const ScopeRequest&) override {}
  auto GetLatestOutput() -> ScopeOutputSet override { return {}; }
  void ResizeResources(const ScopeRequest&) override {}
  void ReleaseResources() override {}
};

}  // namespace

auto CreateCpuScopeAnalyzer() -> std::shared_ptr<IScopeAnalyzer> {
  return std::make_shared<CpuScopeAnalyzer>();
}

// Defined in vulkan_scope_analyzer.cpp; falls back to null when Vulkan is
// unavailable at runtime.
auto CreateVulkanScopeAnalyzer() -> std::shared_ptr<IScopeAnalyzer>;

auto CreateDefaultScopeAnalyzer() -> std::shared_ptr<IScopeAnalyzer> {
  if (auto* ctx = VulkanContext::Get(); ctx && ctx->Valid()) {
    auto vulkan = CreateVulkanScopeAnalyzer();
    if (vulkan) return vulkan;
  }
  return CreateCpuScopeAnalyzer();
}

auto ReadScopeRenderSnapshot(const ScopeOutputSet& output) -> ScopeRenderSnapshot {
  ScopeRenderSnapshot snapshot;
  snapshot.generation = output.generation;
  if (output.histogram_valid) {
    snapshot.histogram = NormalizeHistogramToUnitRange(output.histogram_counts, output.histogram_bins);
  }
  if (output.waveform_valid) {
    snapshot.waveform =
        NormalizeWaveformToUnitRange(output.waveform_rgba, output.waveform_width, output.waveform_height);
  }
  return snapshot;
}

}  // namespace alcedo
