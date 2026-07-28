// AlcedoAndroid - ImageService implementation.
// SPDX-License-Identifier: GPL-3.0-only
#include "storage/service/image_service.hpp"

namespace alcedo {

ImageService::ImageService(ConnectionGuard& guard) : mapper_(guard) {}

void ImageService::AddImage(const std::shared_ptr<Image>& image) { mapper_.Insert(image); }

void ImageService::AddImages(const std::vector<std::shared_ptr<Image>>& images) {
  mapper_.InsertBatch(images);
}

void ImageService::UpdateImage(const std::shared_ptr<Image>& image) { mapper_.Update(image); }

void ImageService::RemoveImageById(image_id_t id) { mapper_.RemoveById(id); }

void ImageService::RemoveImageByType(ImageType type) { mapper_.RemoveByType(type); }

void ImageService::RemoveImageByPath(const std::string& path) { mapper_.RemoveByPath(path); }

auto ImageService::GetImageById(image_id_t id) -> std::shared_ptr<Image> {
  return mapper_.SelectById(id);
}

auto ImageService::GetImageByType(ImageType type) -> std::vector<std::shared_ptr<Image>> {
  return mapper_.SelectByType(type);
}

auto ImageService::GetImageByName(const std::string& name) -> std::vector<std::shared_ptr<Image>> {
  return mapper_.SelectByName(name);
}

auto ImageService::GetImageByPath(const std::string& path) -> std::vector<std::shared_ptr<Image>> {
  return mapper_.SelectByPath(path);
}

}  // namespace alcedo
