// AlcedoAndroid - ImageController (image CRUD over ImageMapper + pool).
// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <memory>
#include <vector>

#include "image/image.hpp"
#include "storage/controller/controller_types.hpp"
#include "storage/image_pool/image_pool_manager.hpp"
#include "storage/mapper/image_mapper.hpp"
#include "type/type.hpp"

namespace alcedo {

class ImageController {
 public:
  explicit ImageController(ConnectionGuard&& guard);
  void CaptureImagePool(const std::shared_ptr<ImagePoolManager> image_pool);
  void AddImage(const std::shared_ptr<Image> image);
  void AddImages(const std::vector<std::shared_ptr<Image>>& images);
  void RemoveImageById(image_id_t remove_id);
  void RemoveImagesByIds(const std::vector<image_id_t>& remove_ids);
  void RemoveImageByType(ImageType type);
  void RemoveImageByPath(const std::string& path);
  void UpdateImage(const std::shared_ptr<Image> image);
  auto GetImageById(image_id_t id) -> std::shared_ptr<Image>;
  auto GetImageByType(ImageType type) -> std::vector<std::shared_ptr<Image>>;
  auto GetImageByName(const std::string& name) -> std::vector<std::shared_ptr<Image>>;
  auto GetImageByPath(const std::string& path) -> std::vector<std::shared_ptr<Image>>;

 private:
  ConnectionGuard                  guard_;
  ImageMapper                       mapper_;
  std::shared_ptr<ImagePoolManager> image_pool_;
};

}  // namespace alcedo
