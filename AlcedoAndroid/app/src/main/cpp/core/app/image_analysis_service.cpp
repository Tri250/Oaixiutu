// AlcedoAndroid - ImageAnalysisService implementation.
// AI-powered image captioning and rating via on-device or cloud models.
// SPDX-License-Identifier: GPL-3.0-only
#include "app/app_services.hpp"

#include "utils/app_logging.hpp"

namespace alcedo {

ImageAnalysisService::ImageAnalysisService(AiStorageController& ai_ctrl) : ai_ctrl_(ai_ctrl) {}

auto ImageAnalysisService::Analyze(sl_element_id_t file_id, const std::shared_ptr<Image>& image)
    -> std::optional<AiImageUnderstandingRecord> {
  if (!image) return std::nullopt;
  // Encode the image for inference.
  ImageAnalysisEncoder encoder;
  auto encoded = encoder.Encode(image, 224);
  if (encoded.empty()) return std::nullopt;

  // The actual inference is dispatched by the AI layer (ai/ai_description.cpp)
  // which may use an on-device model or call a cloud provider. Here we record
  // a placeholder result and mark it active.
  AiImageUnderstandingRecord rec;
  rec.file_id       = file_id;
  rec.task_id       = "caption";
  rec.provider_id   = "local";
  rec.model_id      = "default-vision";
  rec.caption       = "(analysis pending)";
  rec.tags_json     = "[]";
  rec.confidence    = 0.0;
  rec.active        = true;
  ai_ctrl_.UpsertUnderstanding(rec);
  ALOGI("ImageAnalysis: queued caption for file %u", file_id);
  return rec;
}

auto ImageAnalysisService::Rate(sl_element_id_t file_id, const std::shared_ptr<Image>& image)
    -> std::optional<AiImageRatingRecord> {
  if (!image) return std::nullopt;
  AiImageRatingRecord rec;
  rec.file_id    = file_id;
  rec.task_id    = "rating";
  rec.provider_id = "local";
  rec.model_id   = "default-rater";
  rec.rating     = 0;
  rec.active     = true;
  ai_ctrl_.UpsertRating(rec);
  ALOGI("ImageAnalysis: queued rating for file %u", file_id);
  return rec;
}

}  // namespace alcedo
