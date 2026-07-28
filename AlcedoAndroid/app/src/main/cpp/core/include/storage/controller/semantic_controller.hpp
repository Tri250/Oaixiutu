// AlcedoAndroid - SemanticStorageController (semantic embeddings/labels CRUD).
// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <cstdint>
#include <optional>
#include <string>
#include <vector>

#include "storage/controller/controller_types.hpp"
#include "type/type.hpp"

namespace alcedo {

struct SemanticModelRecord {
  std::string model_key;
  std::string model_id;
  std::string revision;
  int         embedding_dim = 0;
  int         image_size    = 0;
  bool        active        = false;
};

struct SemanticEmbeddingRecord {
  sl_element_id_t file_id;
  image_id_t      image_id;
  std::string     model_key;
  std::vector<float> embedding;
  int              embedding_dim       = 0;
  int              thumbnail_resolution = 0;
  std::string      status;
};

struct SemanticLabelRecord {
  sl_element_id_t file_id;
  std::string     model_key;
  std::string     label;
  double          score     = 0.0;
  bool            confident = false;
};

class SemanticStorageController {
 public:
  explicit SemanticStorageController(ConnectionGuard&& guard);
  void UpsertModel(const SemanticModelRecord& rec);
  auto SelectActiveModel() -> std::optional<SemanticModelRecord>;
  void UpsertEmbedding(const SemanticEmbeddingRecord& rec);
  auto SelectEmbedding(sl_element_id_t file_id, const std::string& model_key)
      -> std::optional<SemanticEmbeddingRecord>;
  void UpsertLabel(const SemanticLabelRecord& rec);
  auto SelectLabels(const std::string& model_key) -> std::vector<SemanticLabelRecord>;
  auto SelectLabel(sl_element_id_t file_id, const std::string& model_key)
      -> std::optional<SemanticLabelRecord>;

 private:
  ConnectionGuard guard_;
  auto Exec(const std::string& sql) -> bool;
};

}  // namespace alcedo
