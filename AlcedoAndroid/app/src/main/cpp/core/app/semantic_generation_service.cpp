// AlcedoAndroid - SemanticGenerationService implementation.
// Generates and stores semantic embeddings for images.
// SPDX-License-Identifier: GPL-3.0-only
#include "app/app_services.hpp"

#include "utils/app_logging.hpp"

namespace alcedo {

SemanticGenerationService::SemanticGenerationService(SemanticStorageController& semantic_ctrl)
    : semantic_ctrl_(semantic_ctrl) {}

void SemanticGenerationService::GenerateEmbedding(sl_element_id_t file_id, image_id_t image_id,
                                                   const std::shared_ptr<Image>& thumb) {
  if (!thumb) return;
  // The actual embedding inference is performed by the NN layer (safetensors +
  // on-device model). Here we encode the thumbnail and store a placeholder
  // embedding marking the status as "pending" until inference completes.
  ImageAnalysisEncoder encoder;
  auto encoded = encoder.Encode(thumb, 224);

  SemanticEmbeddingRecord rec;
  rec.file_id     = file_id;
  rec.image_id    = image_id;
  rec.model_key   = "default";
  rec.embedding   = std::vector<float>(encoded.begin(), encoded.end());
  rec.embedding_dim       = static_cast<int>(rec.embedding.size());
  rec.thumbnail_resolution = 224;
  rec.status     = "pending";
  semantic_ctrl_.UpsertEmbedding(rec);
  ALOGI("SemanticGeneration: stored embedding for file %u (dim=%d)", file_id, rec.embedding_dim);
}

}  // namespace alcedo
