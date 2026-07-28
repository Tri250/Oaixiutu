// AlcedoAndroid - ImageService (persistence service for images).
// Thin orchestration over ImageMapper used by ImageController.
// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <memory>
#include <vector>

#include "image/image.hpp"
#include "storage/controller/controller_types.hpp"
#include "storage/mapper/image_mapper.hpp"
#include "type/type.hpp"

namespace alcedo {

class ImageService {
 public:
  explicit ImageService(ConnectionGuard& guard);
  void AddImage(const std::shared_ptr<Image>& image);
  void AddImages(const std::vector<std::shared_ptr<Image>>& images);
  void UpdateImage(const std::shared_ptr<Image>& image);
  void RemoveImageById(image_id_t id);
  void RemoveImageByType(ImageType type);
  void RemoveImageByPath(const std::string& path);
  auto GetImageById(image_id_t id) -> std::shared_ptr<Image>;
  auto GetImageByType(ImageType type) -> std::vector<std::shared_ptr<Image>>;
  auto GetImageByName(const std::string& name) -> std::vector<std::shared_ptr<Image>>;
  auto GetImageByPath(const std::string& path) -> std::vector<std::shared_ptr<Image>>;

 private:
  ImageMapper mapper_;
};

}  // namespace alcedo
