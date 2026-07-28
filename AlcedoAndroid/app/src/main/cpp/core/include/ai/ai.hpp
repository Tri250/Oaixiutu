// AlcedoAndroid - AI inference headers (description + rating).
// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <optional>
#include <string>
#include <vector>

#include "image/image.hpp"
#include "type/type.hpp"

namespace alcedo {

struct AiDescriptionResult {
  std::string caption;
  std::vector<std::string> tags;
  std::string scene;
  double      confidence = 0.0;
};

struct AiRatingResult {
  int         rating   = 0;
  std::string rubric_id;
  std::string reasons;
};

// Generates a natural-language description of an image.
class AiDescriptionInference {
 public:
  auto Infer(const std::shared_ptr<Image>& image) -> AiDescriptionResult;
  void SetModelPath(const std::string& path) { model_path_ = path; }
 private:
  std::string model_path_;
};

// Generates a quality rating for an image.
class AiRatingInference {
 public:
  auto Infer(const std::shared_ptr<Image>& image) -> AiRatingResult;
  void SetModelPath(const std::string& path) { model_path_ = path; }
 private:
  std::string model_path_;
};

}  // namespace alcedo
