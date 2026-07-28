// AlcedoAndroid - ImageMapper (Image <-> DuckDB rows).
// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <memory>
#include <string>
#include <vector>

#include "duckdb/duckdb_capi.hpp"
#include "image/image.hpp"
#include "storage/controller/controller_types.hpp"
#include "type/type.hpp"

namespace alcedo {

class ImageMapper {
 public:
  explicit ImageMapper(ConnectionGuard& guard) : guard_(guard) {}

  void Insert(const std::shared_ptr<Image>& image);
  void InsertBatch(const std::vector<std::shared_ptr<Image>>& images);
  void Update(const std::shared_ptr<Image>& image);
  void RemoveById(image_id_t id);
  void RemoveByType(ImageType type);
  void RemoveByPath(const std::string& path);

  auto SelectById(image_id_t id) -> std::shared_ptr<Image>;
  auto SelectByType(ImageType type) -> std::vector<std::shared_ptr<Image>>;
  auto SelectByName(const std::string& name) -> std::vector<std::shared_ptr<Image>>;
  auto SelectByPath(const std::string& path) -> std::vector<std::shared_ptr<Image>>;

 private:
  ConnectionGuard& guard_;
  auto Exec(const std::string& sql) -> bool;
};

}  // namespace alcedo
