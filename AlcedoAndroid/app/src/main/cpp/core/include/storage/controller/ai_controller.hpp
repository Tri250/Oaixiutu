// AlcedoAndroid - AiStorageController (AI image understanding + rating CRUD).
// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <cstdint>
#include <optional>
#include <string>
#include <vector>

#include "storage/controller/controller_types.hpp"
#include "type/type.hpp"

namespace alcedo {

struct AiImageUnderstandingRecord {
  sl_element_id_t file_id       = 0;
  std::string     task_id;
  std::string     provider_id;
  std::string     model_id;
  std::string     prompt_profile_id;
  std::string     rendition_kind;
  std::string     caption;
  std::string     tags_json;
  std::string     scene;
  double          confidence = 0.0;
  bool            active     = true;
};

struct AiImageRatingRecord {
  sl_element_id_t file_id = 0;
  std::string     task_id;
  std::string     provider_id;
  std::string     model_id;
  int             rating      = 0;
  std::string     rubric_id;
  std::string     rubric_version;
  std::string     reasons;
  bool            active      = true;
};

class AiStorageController {
 public:
  explicit AiStorageController(ConnectionGuard&& guard);
  void UpsertUnderstanding(const AiImageUnderstandingRecord& rec);
  auto SelectUnderstanding(sl_element_id_t file_id, const std::string& task_id)
      -> std::optional<AiImageUnderstandingRecord>;
  auto SelectActiveUnderstanding(sl_element_id_t file_id)
      -> std::optional<AiImageUnderstandingRecord>;
  void UpsertRating(const AiImageRatingRecord& rec);
  auto SelectRating(sl_element_id_t file_id, const std::string& task_id)
      -> std::optional<AiImageRatingRecord>;
  auto SelectActiveRating(sl_element_id_t file_id) -> std::optional<AiImageRatingRecord>;
  void UpsertFtsDocument(sl_element_id_t file_id, const std::string& body);
  auto SelectFtsDocument(sl_element_id_t file_id) -> std::optional<std::string>;

 private:
  ConnectionGuard guard_;
  auto Exec(const std::string& sql) -> bool;
};

}  // namespace alcedo
