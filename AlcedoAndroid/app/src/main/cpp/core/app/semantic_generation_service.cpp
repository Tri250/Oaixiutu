// AlcedoAndroid - SemanticGenerationService implementation.
// Generates and stores semantic embeddings for images.
// SPDX-License-Identifier: GPL-3.0-only
#include "app/app_services.hpp"

#include <algorithm>
#include <cmath>
#include <vector>

#include "utils/app_logging.hpp"

namespace alcedo {

SemanticGenerationService::SemanticGenerationService(SemanticStorageController& semantic_ctrl)
    : semantic_ctrl_(semantic_ctrl) {}

void SemanticGenerationService::GenerateEmbedding(sl_element_id_t file_id, image_id_t image_id,
                                                   const std::shared_ptr<Image>& thumb) {
  if (!thumb) return;

  // No on-device CLIP runtime is available in this C++ build. Instead of
  // storing the raw thumbnail bytes as a fake embedding, compute a basic
  // feature vector (color histograms + edge statistics) that captures enough
  // signal for nearest-neighbour lookups, and mark the status as "heuristic".
  std::vector<float> embedding;

  auto& buf = thumb->GetImageData();
  if (!buf.Empty()) {
    FloatMat& mat = buf.GetCPUData();
    if (!mat.Empty() && mat.Width() > 0 && mat.Height() > 0 && mat.Channels() >= 1) {
      const int w  = mat.Width();
      const int h  = mat.Height();
      const int ch = mat.Channels();
      const int step = std::max(1, static_cast<int>(std::sqrt(
          static_cast<double>(w) * h / 10000.0)));

      auto clamp01 = [](double v) -> double {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
      };
      auto lum_at = [&](const float* p) -> double {
        double r = ch > 0 ? clamp01(static_cast<double>(p[0])) : 0.0;
        double g = ch > 1 ? clamp01(static_cast<double>(p[1])) : r;
        double b = ch > 2 ? clamp01(static_cast<double>(p[2])) : r;
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
      };

      // 8-bin histograms for R, G, B and luminance (32 dims).
      std::vector<int> rgb_hist(24, 0);
      std::vector<int> lum_hist(8, 0);
      double sum_r = 0.0, sum_g = 0.0, sum_b = 0.0, sum_lum = 0.0, sum_lum_sq = 0.0;
      size_t sample_count = 0;

      for (int y = 0; y < h; y += step) {
        for (int x = 0; x < w; x += step) {
          const float* p = mat.Ptr(y, x);
          double r = ch > 0 ? clamp01(static_cast<double>(p[0])) : 0.0;
          double g = ch > 1 ? clamp01(static_cast<double>(p[1])) : r;
          double b = ch > 2 ? clamp01(static_cast<double>(p[2])) : r;
          double lum = 0.2126 * r + 0.7152 * g + 0.0722 * b;
          rgb_hist[std::min(7, static_cast<int>(r * 8.0))]++;
          rgb_hist[8 + std::min(7, static_cast<int>(g * 8.0))]++;
          rgb_hist[16 + std::min(7, static_cast<int>(b * 8.0))]++;
          lum_hist[std::min(7, static_cast<int>(lum * 8.0))]++;
          sum_r += r; sum_g += g; sum_b += b;
          sum_lum += lum; sum_lum_sq += lum * lum;
          ++sample_count;
        }
      }

      if (sample_count > 0) {
        const double inv = 1.0 / static_cast<double>(sample_count);
        for (int i = 0; i < 24; ++i) embedding.push_back(static_cast<float>(rgb_hist[i] * inv));
        for (int i = 0; i < 8; ++i)  embedding.push_back(static_cast<float>(lum_hist[i] * inv));

        // Average color + brightness (4 dims).
        embedding.push_back(static_cast<float>(sum_r * inv));
        embedding.push_back(static_cast<float>(sum_g * inv));
        embedding.push_back(static_cast<float>(sum_b * inv));
        embedding.push_back(static_cast<float>(sum_lum * inv));

        // Contrast: std-dev of luminance (1 dim).
        double mean_lum = sum_lum * inv;
        double var_lum  = (sum_lum_sq * inv) - (mean_lum * mean_lum);
        embedding.push_back(static_cast<float>(std::sqrt(var_lum < 0.0 ? 0.0 : var_lum)));

        // Edge statistics: Laplacian variance (sharpness proxy) + edge density.
        double sum_lap = 0.0, sum_lap_sq = 0.0;
        size_t edge_pixels = 0, lap_count = 0;
        for (int y = std::max(1, step); y < h - 1; y += step) {
          for (int x = std::max(1, step); x < w - 1; x += step) {
            double c = lum_at(mat.Ptr(y, x));
            double u = lum_at(mat.Ptr(y - 1, x));
            double d = lum_at(mat.Ptr(y + 1, x));
            double l = lum_at(mat.Ptr(y, x - 1));
            double r = lum_at(mat.Ptr(y, x + 1));
            double lap = 4.0 * c - u - d - l - r;
            sum_lap    += lap;
            sum_lap_sq += lap * lap;
            if (std::fabs(lap) > 0.05) ++edge_pixels;
            ++lap_count;
          }
        }
        if (lap_count > 0) {
          double mean_lap = sum_lap / lap_count;
          double var_lap  = (sum_lap_sq / lap_count) - (mean_lap * mean_lap);
          embedding.push_back(static_cast<float>(std::sqrt(var_lap < 0.0 ? 0.0 : var_lap)));
          embedding.push_back(static_cast<float>(static_cast<double>(edge_pixels) / lap_count));
        } else {
          embedding.push_back(0.0f);
          embedding.push_back(0.0f);
        }
      }
    }
  }

  SemanticEmbeddingRecord rec;
  rec.file_id     = file_id;
  rec.image_id    = image_id;
  rec.model_key   = "default";
  rec.embedding   = std::move(embedding);
  rec.embedding_dim       = static_cast<int>(rec.embedding.size());
  rec.thumbnail_resolution = 224;
  rec.status     = rec.embedding.empty() ? "failed" : "heuristic";
  semantic_ctrl_.UpsertEmbedding(rec);
  ALOGI("SemanticGeneration: stored heuristic embedding for file %u (dim=%d)",
        file_id, rec.embedding_dim);
}

}  // namespace alcedo
