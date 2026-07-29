// AlcedoAndroid - AiDescriptionInference implementation.
// Generates natural-language image descriptions via on-device vision model.
// SPDX-License-Identifier: GPL-3.0-only
#include "ai/ai.hpp"

#include <algorithm>
#include <cmath>
#include <string>

#include "nn/safetensors.hpp"
#include "utils/app_logging.hpp"

namespace alcedo {

namespace {

// Clamp a linear channel value to [0,1] for statistics.
inline double clamp01(double v) { return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v); }

}  // namespace

auto AiDescriptionInference::Infer(const std::shared_ptr<Image>& image) -> AiDescriptionResult {
  AiDescriptionResult result;
  if (!image) return result;  // null image -> default pending result

  bool model_loaded = false;
  bool model_load_failed = false;
  if (!model_path_.empty()) {
    SafetensorsReader reader;
    if (reader.Open(model_path_)) {
      model_loaded = true;
      ALOGI("AiDescription: loaded model from %s, %zu tensors",
            model_path_.c_str(), reader.ListTensors().size());
      // A real forward pass would run the vision encoder + decoder here via
      // the on-device NN runtime (TFLite/MNN/ONNX). That runtime is dispatched
      // through JNI and is not available in this C++ build, so we fall back to
      // a heuristic caption derived from basic visual statistics.
      reader.Close();
    } else {
      model_load_failed = true;
    }
  }

  // If a model path was supplied but the file could not be opened, we cannot
  // produce a meaningful caption. Only return the pending placeholder here or
  // when no pixel data is available.
  if (model_load_failed) {
    result.caption    = "(caption pending)";
    result.scene      = "unknown";
    result.confidence = 0.0;
    return result;
  }

  // ---- Heuristic caption from basic visual statistics ----
  auto& buf = image->GetImageData();
  if (buf.Empty()) {
    result.caption    = "(caption pending)";
    result.scene      = "unknown";
    result.confidence = 0.0;
    return result;
  }
  FloatMat& mat = buf.GetCPUData();
  if (mat.Empty() || mat.Width() <= 0 || mat.Height() <= 0 || mat.Channels() < 1) {
    result.caption    = "(caption pending)";
    result.scene      = "unknown";
    result.confidence = 0.0;
    return result;
  }

  const int w  = mat.Width();
  const int h  = mat.Height();
  const int ch = mat.Channels();

  // Sample pixels (step chosen so we visit ~10k pixels regardless of size).
  const int step = std::max(1, static_cast<int>(std::sqrt(
      static_cast<double>(w) * h / 10000.0)));

  double sum_r = 0.0, sum_g = 0.0, sum_b = 0.0;
  size_t sample_count = 0;
  for (int y = 0; y < h; y += step) {
    for (int x = 0; x < w; x += step) {
      const float* p = mat.Ptr(y, x);
      double r = ch > 0 ? clamp01(static_cast<double>(p[0])) : 0.0;
      double g = ch > 1 ? clamp01(static_cast<double>(p[1])) : r;
      double b = ch > 2 ? clamp01(static_cast<double>(p[2])) : r;
      sum_r += r; sum_g += g; sum_b += b;
      ++sample_count;
    }
  }
  if (sample_count == 0) {
    result.caption    = "(caption pending)";
    result.scene      = "unknown";
    result.confidence = 0.0;
    return result;
  }

  const double avg_r = sum_r / sample_count;
  const double avg_g = sum_g / sample_count;
  const double avg_b = sum_b / sample_count;
  // Rec. 709 luminance for linear RGB.
  const double brightness = 0.2126 * avg_r + 0.7152 * avg_g + 0.0722 * avg_b;

  // Warm vs. cool: positive => warm (reds/yellows dominate), negative => cool.
  const double warmth = (avg_r + avg_g * 0.5) - (avg_b + avg_g * 0.5);

  // Brightness descriptor.
  std::string brightness_desc;
  if (brightness < 0.15)      brightness_desc = "A dark image";
  else if (brightness < 0.35) brightness_desc = "A dim image";
  else if (brightness < 0.65) brightness_desc = "A well-exposed image";
  else if (brightness < 0.85) brightness_desc = "A bright image";
  else                        brightness_desc = "An overexposed image";

  // Tone descriptor.
  std::string tone_desc;
  if (warmth > 0.08)       tone_desc = "warm tones";
  else if (warmth < -0.08)  tone_desc = "cool blue tones";
  else                      tone_desc = "neutral tones";

  // Dominant channel tag.
  std::string dom_color;
  if (avg_r >= avg_g && avg_r >= avg_b)      dom_color = "red-dominant";
  else if (avg_g >= avg_r && avg_g >= avg_b)  dom_color = "green-dominant";
  else                                        dom_color = "blue-dominant";

  std::string key_desc =
      brightness < 0.35 ? "low-key" : (brightness > 0.65 ? "high-key" : "mid-key");

  result.caption = brightness_desc + " with " + tone_desc;
  result.tags    = {dom_color, key_desc};
  result.scene   = model_loaded ? "static" : "heuristic";
  // The forward pass is never actually run in this C++ build (the NN runtime
  // is dispatched via JNI), so the heuristic is the real source of the caption.
  // Be honest: confidence is low regardless of whether the model file opened.
  result.confidence = 0.15;

  ALOGI("AiDescription: heuristic caption=\"%s\" (brightness=%.3f, warmth=%.3f)",
        result.caption.c_str(), brightness, warmth);
  return result;
}

}  // namespace alcedo
