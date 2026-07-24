// Ported from AlcedoStudio desktop: edit/scope/scope_analyzer.cpp
// SPDX-License-Identifier: GPL-3.0-only
//
// CPU default scope analyzer. Computes histogram / waveform / vectorscope
// render data from a CPU RGBA32F frame. Mirrors the desktop's
// ReadScopeRenderSnapshot + CpuScopeAnalyzer logic without any GPU dependency.

#include "scope_analyzer.h"

#include <algorithm>
#include <cmath>
#include <cstring>

namespace alcedo {

namespace {

inline uint8_t Quantize(float v) {
    if (v <= 0.0f) return 0;
    if (v >= 1.0f) return 255;
    return static_cast<uint8_t>(v * 255.0f + 0.5f);
}

void ComputeHistogram(const FinalDisplayFrameView& frame, const ScopeRequest& req,
                      ScopeHistogramRenderData& out) {
    int bins = std::max(1, req.histogram_bins);
    out.bins = bins;
    out.rgb.assign(static_cast<size_t>(bins) * 3, 0.0f);

    if (!frame) {
        out.valid = false;
        return;
    }

    const float* p = frame.pixels;
    size_t total = static_cast<size_t>(frame.width) * frame.height;
    int channels = frame.channels;
    if (channels < 3) channels = 4;

    // Per-channel accumulation into integer counters (avoid float races).
    std::vector<uint64_t> r_hist(bins, 0), g_hist(bins, 0), b_hist(bins, 0);
    for (size_t i = 0; i < total; ++i) {
        float r = std::clamp(p[i * channels + 0], 0.0f, 1.0f);
        float g = std::clamp(p[i * channels + 1], 0.0f, 1.0f);
        float b = std::clamp(p[i * channels + 2], 0.0f, 1.0f);
        int ri = std::min(bins - 1, static_cast<int>(r * bins));
        int gi = std::min(bins - 1, static_cast<int>(g * bins));
        int bi = std::min(bins - 1, static_cast<int>(b * bins));
        r_hist[ri]++; g_hist[gi]++; b_hist[bi]++;
    }

    // Normalize to peak = 1.0 for display.
    uint64_t peak = 1;
    for (int i = 0; i < bins; ++i) {
        peak = std::max(peak, std::max({r_hist[i], g_hist[i], b_hist[i]}));
    }
    for (int i = 0; i < bins; ++i) {
        out.rgb[i * 3 + 0] = static_cast<float>(r_hist[i]) / static_cast<float>(peak);
        out.rgb[i * 3 + 1] = static_cast<float>(g_hist[i]) / static_cast<float>(peak);
        out.rgb[i * 3 + 2] = static_cast<float>(b_hist[i]) / static_cast<float>(peak);
    }

    // Clip-tail warnings (>= 95% saturation in either tail).
    float clip_threshold = 0.95f * static_cast<float>(total);
    out.shadow_clip_ratio    = 0.0f;
    out.highlight_clip_ratio = 0.0f;
    out.shadow_clip_warning    = false;
    out.highlight_clip_warning = false;
    out.clip_tail_bins = std::max(1, bins / 32);
    uint64_t shadow_sum = 0, highlight_sum = 0;
    for (int i = 0; i < out.clip_tail_bins; ++i) {
        shadow_sum += r_hist[i] + g_hist[i] + b_hist[i];
        highlight_sum += r_hist[bins - 1 - i] + g_hist[bins - 1 - i] + b_hist[bins - 1 - i];
    }
    float shadow_ratio    = static_cast<float>(shadow_sum) / (3.0f * static_cast<float>(total));
    float highlight_ratio = static_cast<float>(highlight_sum) / (3.0f * static_cast<float>(total));
    out.shadow_clip_ratio     = shadow_ratio;
    out.highlight_clip_ratio  = highlight_ratio;
    out.shadow_clip_warning    = shadow_ratio > 0.10f;
    out.highlight_clip_warning = highlight_ratio > 0.10f;
    out.valid = true;
}

void ComputeWaveform(const FinalDisplayFrameView& frame, const ScopeRequest& req,
                     ScopeWaveformRenderData& out) {
    int w = std::max(1, req.waveform_width);
    int h = std::max(1, req.waveform_height);
    out.width  = w;
    out.height = h;
    out.rgba.assign(static_cast<size_t>(w) * h * 4, 0.0f);

    if (!frame) {
        out.valid = false;
        return;
    }

    const float* p = frame.pixels;
    int channels = frame.channels;
    if (channels < 3) channels = 4;
    int fw = frame.width, fh = frame.height;

    // For each destination column, sample a vertical strip of source pixels
    // and accumulate luminance into the corresponding row buckets.
    std::vector<uint64_t> counts(static_cast<size_t>(w) * h, 0);
    for (int x = 0; x < fw; ++x) {
        int dst_x = (w * x) / std::max(1, fw);
        if (dst_x >= w) dst_x = w - 1;
        for (int y = 0; y < fh; ++y) {
            int idx = (y * fw + x) * channels;
            float r = std::clamp(p[idx + 0], 0.0f, 1.0f);
            float g = std::clamp(p[idx + 1], 0.0f, 1.0f);
            float b = std::clamp(p[idx + 2], 0.0f, 1.0f);
            float lum = 0.299f * r + 0.587f * g + 0.114f * b;
            int dst_y = static_cast<int>((1.0f - lum) * h);
            if (dst_y < 0) dst_y = 0;
            if (dst_y >= h) dst_y = h - 1;
            counts[dst_y * w + dst_x]++;
        }
    }
    uint64_t peak = 1;
    for (auto c : counts) peak = std::max(peak, c);
    for (int y = 0; y < h; ++y) {
        for (int x = 0; x < w; ++x) {
            float v = static_cast<float>(counts[y * w + x]) / static_cast<float>(peak);
            size_t base = (y * w + x) * 4;
            out.rgba[base + 0] = v;
            out.rgba[base + 1] = v;
            out.rgba[base + 2] = v;
            out.rgba[base + 3] = v;
        }
    }
    out.valid = true;
}

void ComputeVectorscope(const FinalDisplayFrameView& frame, const ScopeRequest& req,
                        ScopeVectorscopeRenderData& out) {
    int s = std::max(1, req.vectorscope_size);
    out.size = s;
    out.rgba.assign(static_cast<size_t>(s) * s * 4, 0.0f);

    if (!frame) {
        out.valid = false;
        return;
    }

    const float* p = frame.pixels;
    int channels = frame.channels;
    if (channels < 3) channels = 4;
    size_t total = static_cast<size_t>(frame.width) * frame.height;

    std::vector<uint64_t> counts(s * s, 0);
    // Cb/Cr plane: cb = B - Y, cr = R - Y, where Y = 0.299R+0.587G+0.114B.
    // Map [-0.5, 0.5] -> [0, s-1].
    for (size_t i = 0; i < total; ++i) {
        size_t idx = i * channels;
        float r = std::clamp(p[idx + 0], 0.0f, 1.0f);
        float g = std::clamp(p[idx + 1], 0.0f, 1.0f);
        float b = std::clamp(p[idx + 2], 0.0f, 1.0f);
        float y = 0.299f * r + 0.587f * g + 0.114f * b;
        float cb = (b - y);
        float cr = (r - y);
        int vx = static_cast<int>((cr + 0.5f) * s);
        int vy = static_cast<int>((0.5f - cb) * s);  // invert Y so it reads naturally
        if (vx < 0) vx = 0; else if (vx >= s) vx = s - 1;
        if (vy < 0) vy = 0; else if (vy >= s) vy = s - 1;
        counts[vy * s + vx]++;
    }
    uint64_t peak = 1;
    for (auto c : counts) peak = std::max(peak, c);
    for (int y = 0; y < s; ++y) {
        for (int x = 0; x < s; ++x) {
            float v = static_cast<float>(counts[y * s + x]) / static_cast<float>(peak);
            size_t base = (y * s + x) * 4;
            out.rgba[base + 0] = v;
            out.rgba[base + 1] = v;
            out.rgba[base + 2] = v;
            out.rgba[base + 3] = v;
        }
    }
    out.valid = true;
}

class CpuScopeAnalyzer : public IScopeAnalyzer {
public:
    void SubmitFrame(const FinalDisplayFrameView& frame, const ScopeRequest& req) override {
        if (static_cast<uint32_t>(req.enabled_mask) & static_cast<uint32_t>(ScopeType::Histogram)) {
            ComputeHistogram(frame, req, snapshot_.histogram);
        }
        if (static_cast<uint32_t>(req.enabled_mask) & static_cast<uint32_t>(ScopeType::Waveform)) {
            ComputeWaveform(frame, req, snapshot_.waveform);
        }
        if (static_cast<uint32_t>(req.enabled_mask) & static_cast<uint32_t>(ScopeType::Vectorscope)) {
            ComputeVectorscope(frame, req, snapshot_.vectorscope);
        }
        ++snapshot_.generation;
    }

    ScopeRenderSnapshot GetLatestSnapshot() override { return snapshot_; }

    void ReleaseResources() override {
        snapshot_ = {};
    }

private:
    ScopeRenderSnapshot snapshot_{};
};

}  // namespace

std::shared_ptr<IScopeAnalyzer> CreateDefaultScopeAnalyzer() {
    return std::make_shared<CpuScopeAnalyzer>();
}

ScopeRenderSnapshot ReadScopeRenderSnapshot(const FinalDisplayFrameView& frame,
                                            const ScopeRequest& request) {
    ScopeRenderSnapshot snap;
    ComputeHistogram(frame, request, snap.histogram);
    ComputeWaveform(frame, request, snap.waveform);
    ComputeVectorscope(frame, request, snap.vectorscope);
    snap.generation = frame.frame_id;
    return snap;
}

}  // namespace alcedo
