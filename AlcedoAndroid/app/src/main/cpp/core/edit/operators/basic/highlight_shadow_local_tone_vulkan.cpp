// AlcedoAndroid - HsLocalToneVulkanOp implementation.
// GPU path dispatches the "tone_mapping" program; CPU fallback applies a
// simplified log-space local-contrast compression using a box blur of the
// luminance as the local average.
// SPDX-License-Identifier: GPL-3.0-only
#include "edit/operators/basic/highlight_shadow_local_tone_vulkan.hpp"

#include <algorithm>
#include <cmath>
#include <vector>

#include "image/image_buffer.hpp"
#include "vulkan/context/vulkan_context.hpp"
#include "vulkan/pipeline/vulkan_pipeline.hpp"
#include "vulkan/pipeline/vulkan_program_registry.hpp"

namespace alcedo {

HsLocalToneVulkanOp::HsLocalToneVulkanOp() = default;
HsLocalToneVulkanOp::HsLocalToneVulkanOp(const nlohmann::json& params) { SetParams(params); }

void HsLocalToneVulkanOp::Apply(std::shared_ptr<ImageBuffer> input) {
  FloatMat& img = input->GetCPUData();
  const int w = img.Width(), h = img.Height();
  if (w == 0 || h == 0) return;
  // Local average via a separable box filter of the luminance.
  std::vector<float> lum(w * h);
  for (int y = 0; y < h; ++y) {
    for (int x = 0; x < w; ++x) {
      const Pixel& p = img.PixelAt(y, x);
      lum[y * w + x] = 0.2126f * p.r + 0.7152f * p.g + 0.0722f * p.b;
    }
  }
  int r = std::max(1, static_cast<int>(radius_));
  std::vector<float> avg(w * h, 0.0f);
  for (int y = 0; y < h; ++y) {
    for (int x = 0; x < w; ++x) {
      float s = 0.0f; int n = 0;
      for (int dy = -r; dy <= r; ++dy) {
        int yy = y + dy; if (yy < 0 || yy >= h) continue;
        for (int dx = -r; dx <= r; ++dx) {
          int xx = x + dx; if (xx < 0 || xx >= w) continue;
          s += lum[yy * w + xx]; ++n;
        }
      }
      avg[y * w + x] = n ? s / n : 0.0f;
    }
  }
  const float sa = shadow_amount_;
  const float ha = highlight_amount_;
  const float ca = clarity_amount_;
  img.ForEachPixel([&](Pixel& p, int x, int y) {
    float L = 0.2126f * p.r + 0.7152f * p.g + 0.0722f * p.b;
    float local = avg[y * w + x];
    float logL = std::log2(std::max(L, 1e-5f));
    float logLocal = std::log2(std::max(local, 1e-5f));
    float detail = logL - logLocal;
    // Shadow lift where local is dark, highlight compression where local is bright.
    float shadow_gain = std::max(0.0f, -logLocal) / 4.0f;
    shadow_gain = std::min(1.0f, shadow_gain) * sa;
    float highlight_gain = std::max(0.0f, logLocal) / 4.0f;
    highlight_gain = std::min(1.0f, highlight_gain) * ha;
    float newLogL = logL + shadow_gain - highlight_gain + detail * ca;
    float scale = (L > 1e-5f) ? std::exp2(newLogL - logL) : 1.0f;
    p.r *= scale; p.g *= scale; p.b *= scale;
  });
}

void HsLocalToneVulkanOp::ApplyGPU(std::shared_ptr<ImageBuffer> input) {
  VulkanContext* ctx = VulkanContext::Ensure();
  if (!ctx || !ctx->Valid()) { Apply(input); return; }
  input->SyncToGPU();
  // The actual "tone_mapping" compute dispatch is orchestrated by the pipeline's
  // VulkanKernelStream which owns the fused scratch buffers; this entry point
  // keeps the GPU image in sync for the standalone operator path.
}

auto HsLocalToneVulkanOp::GetParams() const -> nlohmann::json {
  nlohmann::json o;
  o["shadow_amount"]    = shadow_amount_;
  o["highlight_amount"] = highlight_amount_;
  o["clarity_amount"]   = clarity_amount_;
  o["radius"]           = radius_;
  return o;
}

void HsLocalToneVulkanOp::SetParams(const nlohmann::json& params) {
  shadow_amount_    = params.value("shadow_amount", 0.0f);
  highlight_amount_ = params.value("highlight_amount", 0.0f);
  clarity_amount_   = params.value("clarity_amount", 0.0f);
  radius_           = params.value("radius", 18.0f);
}

void HsLocalToneVulkanOp::SetGlobalParams(OperatorParams& params) const {
  params.tone_mapping_.shadow_amount_    = shadow_amount_;
  params.tone_mapping_.highlight_amount_ = highlight_amount_;
  params.tone_mapping_.clarity_amount_   = clarity_amount_;
  params.tone_mapping_.local_radius_     = radius_;
  params.tone_mapping_.slider_input_.shadows_operator_present_    = shadow_amount_ != 0.0f;
  params.tone_mapping_.slider_input_.highlights_operator_present_ = highlight_amount_ != 0.0f;
  params.tone_mapping_.slider_input_.clarity_operator_present_    = clarity_amount_ != 0.0f;
}

void HsLocalToneVulkanOp::EnableGlobalParams(OperatorParams& params, bool enable) {
  params.tone_mapping_.shadows_enabled_    = enable;
  params.tone_mapping_.highlights_enabled_ = enable;
  params.tone_mapping_.clarity_enabled_    = enable;
}

}  // namespace alcedo
