// AlcedoAndroid - AiRatingInference implementation.
// Generates quality ratings for images via on-device model.
// SPDX-License-Identifier: GPL-3.0-only
#include "ai/ai.hpp"

#include "nn/safetensors.hpp"
#include "utils/app_logging.hpp"

namespace alcedo {

auto AiRatingInference::Infer(const std::shared_ptr<Image>& image) -> AiRatingResult {
  AiRatingResult result;
  if (!image) return result;

  if (!model_path_.empty()) {
    SafetensorsReader reader;
    if (reader.Open(model_path_)) {
      ALOGI("AiRating: loaded model from %s, %zu tensors",
            model_path_.c_str(), reader.ListTensors().size());
      reader.Close();
    }
  }

  // Placeholder; real inference outputs a 1-5 rating with rubric-based reasons.
  result.rating   = 0;
  result.rubric_id = "default";
  result.reasons  = "(rating pending)";
  return result;
}

}  // namespace alcedo
