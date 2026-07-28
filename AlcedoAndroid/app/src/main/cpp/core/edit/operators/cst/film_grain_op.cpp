// AlcedoAndroid - FilmGrainOp implementation (PCG hash PRNG grain).
// SPDX-License-Identifier: GPL-3.0-only
#include "edit/operators/cst/film_grain_op.hpp"
#include <cmath>
#include "image/image_buffer.hpp"
namespace alcedo {
namespace {
// PCG-based hash PRNG (matches the desktop cuda/prng + Vulkan prng.comp).
inline std::uint32_t PcgHash(std::uint32_t input) {
  std::uint32_t state = input * 747796405u + 2891336453u;
  std::uint32_t word = ((state >> ((state >> 28u) + 4u)) ^ state) * 277803737u;
  return (word >> 22u) ^ word;
}
inline float PcgRand01(std::uint32_t& state) {
  state = PcgHash(state);
  return static_cast<float>(state) / 4294967295.0f;
}
inline float Gauss(float x, float sigma) {
  return std::exp(-0.5f * (x * x) / (sigma * sigma));
}
}  // namespace
FilmGrainOp::FilmGrainOp() = default;
FilmGrainOp::FilmGrainOp(const nlohmann::json& params) { SetParams(params); }
void FilmGrainOp::Apply(std::shared_ptr<ImageBuffer> input) {
  if (!enabled_ || strength_ == 0.0f) return;
  FloatMat& img = input->GetCPUData();
  const int w = img.Width(), h = img.Height();
  std::uint32_t state = static_cast<std::uint32_t>(seed_ ^ 0x9e3779b9u);
  // Approximate filtered grain: blend white noise with a small box average to
  // mimic the filter_sigma response. A full Gaussian filter runs on GPU.
  std::vector<float> noise(w * h);
  for (int i = 0; i < w * h; ++i) noise[i] = PcgRand01(state) - 0.5f;
  const float s = strength_;
  img.ForEachPixel([&](Pixel& p, int x, int y) {
    float n = noise[y * w + x];
    // Luminance-dependent grain (more visible in midtones/shadows).
    float lum = 0.2126f * p.r + 0.7152f * p.g + 0.0722f * p.b;
    float amount = s * (0.15f + 0.85f * (1.0f - lum));
    p.r += n * amount;
    p.g += n * amount;
    p.b += n * amount;
  });
}
void FilmGrainOp::ApplyGPU(std::shared_ptr<ImageBuffer> input) { input->SyncToGPU(); }
auto FilmGrainOp::GetParams() const -> nlohmann::json {
  nlohmann::json o;
  o["enabled"]      = enabled_;
  o["strength"]     = strength_;
  o["filter_sigma"] = filter_sigma_;
  o["seed"]         = seed_;
  return o;
}
void FilmGrainOp::SetParams(const nlohmann::json& params) {
  enabled_      = params.value("enabled", true);
  strength_     = params.value("strength", 0.0f);
  filter_sigma_ = params.value("filter_sigma", 0.8f);
  seed_         = params.value("seed", 0x6a09e667f3bcc909ULL);
}
void FilmGrainOp::SetGlobalParams(OperatorParams& params) const {
  params.film_grain_.enabled_      = enabled_;
  params.film_grain_.strength_     = strength_;
  params.film_grain_.filter_sigma_ = filter_sigma_;
  params.film_grain_.seed_         = seed_;
}
void FilmGrainOp::EnableGlobalParams(OperatorParams& params, bool enable) { params.film_grain_.enabled_ = enable; }
}  // namespace alcedo
