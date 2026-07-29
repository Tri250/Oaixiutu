// AlcedoAndroid - ImageAnalysisService implementation.
// AI-powered image captioning and rating via on-device or cloud models.
// SPDX-License-Identifier: GPL-3.0-only
#include "app/app_services.hpp"

#include "ai/ai.hpp"
#include "utils/app_logging.hpp"

namespace alcedo {

ImageAnalysisService::ImageAnalysisService(AiStorageController& ai_ctrl) : ai_ctrl_(ai_ctrl) {}

auto ImageAnalysisService::Analyze(sl_element_id_t file_id, const std::shared_ptr<Image>& image)
    -> std::optional<AiImageUnderstandingRecord> {
  if (!image) return std::nullopt;
  // Encode the image for inference (sanity check + preprocessing for the model
  // path when configured).
  ImageAnalysisEncoder encoder;
  auto encoded = encoder.Encode(image, 224);
  if (encoded.empty()) return std::nullopt;

  // Run the actual description inference. AiDescriptionInference falls back to
  // a heuristic caption derived from visual statistics when no on-device model
  // runtime is available, so callers always receive a meaningful result.
  AiDescriptionInference desc_inference;
  auto desc_result = desc_inference.Infer(image);

  AiImageUnderstandingRecord rec;
  rec.file_id       = file_id;
  rec.task_id       = "caption";
  rec.provider_id   = "local";
  rec.model_id      = "default-vision";
  rec.caption       = desc_result.caption;
  // Serialise the tag list to a JSON array string for storage.
  nlohmann::json tags_json = nlohmann::json::array();
  for (const auto& tag : desc_result.tags) tags_json.push_back(tag);
  rec.tags_json     = tags_json.dump();
  rec.scene         = desc_result.scene;
  rec.confidence    = desc_result.confidence;
  rec.active        = true;
  ai_ctrl_.UpsertUnderstanding(rec);
  ALOGI("ImageAnalysis: stored caption for file %u (conf=%.3f)", file_id, rec.confidence);
  return rec;
}

auto ImageAnalysisService::Rate(sl_element_id_t file_id, const std::shared_ptr<Image>& image)
    -> std::optional<AiImageRatingRecord> {
  if (!image) return std::nullopt;

  // Run the actual rating inference. AiRatingInference produces a 1-5 score
  // with rubric-based reasons (heuristic when no model runtime is available).
  AiRatingInference rating_inference;
  auto rating_result = rating_inference.Infer(image);

  AiImageRatingRecord rec;
  rec.file_id    = file_id;
  rec.task_id    = "rating";
  rec.provider_id = "local";
  rec.model_id   = "default-rater";
  rec.rating     = rating_result.rating;
  rec.rubric_id  = rating_result.rubric_id;
  rec.reasons    = rating_result.reasons;
  rec.active     = true;
  ai_ctrl_.UpsertRating(rec);
  ALOGI("ImageAnalysis: stored rating for file %u (rating=%d)", file_id, rec.rating);
  return rec;
}

}  // namespace alcedo
