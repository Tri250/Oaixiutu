// AlcedoAndroid - AiDescriptionInference implementation.
// Generates natural-language image descriptions via on-device vision model.
// SPDX-License-Identifier: GPL-3.0-only
#include "ai/ai.hpp"

#include "nn/safetensors.hpp"
#include "utils/app_logging.hpp"

namespace alcedo {

auto AiDescriptionInference::Infer(const std::shared_ptr<Image>& image) -> AiDescriptionResult {
  AiDescriptionResult result;
  if (!image) return result;

  if (!model_path_.empty()) {
    SafetensorsReader reader;
    if (reader.Open(model_path_)) {
      ALOGI("AiDescription: loaded model from %s, %zu tensors",
            model_path_.c_str(), reader.ListTensors().size());
      // The actual inference runs the vision encoder + decoder. This is a
      // placeholder that returns a pending caption; the real forward pass is
      // implemented by the NN runtime (on-device TFLite/MNN via JNI).
      reader.Close();
    }
  }

  // Placeholder result; real inference fills caption/tags/scene.
  result.caption    = "(caption pending)";
  result.scene      = "unknown";
  result.confidence = 0.0;
  return result;
}

}  // namespace alcedo
