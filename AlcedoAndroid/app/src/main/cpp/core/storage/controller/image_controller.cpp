// AlcedoAndroid - ImageController implementation.
// SPDX-License-Identifier: GPL-3.0-only
#include "storage/controller/image_controller.hpp"

#include <utility>

namespace alcedo {

ImageController::ImageController(ConnectionGuard&& guard)
    : guard_(std::move(guard)), mapper_(guard_) {}

void ImageController::CaptureImagePool(const std::shared_ptr<ImagePoolManager> image_pool) {
  image_pool_ = image_pool;
}

void ImageController::AddImage(const std::shared_ptr<Image> image) {
  mapper_.Insert(image);
  if (image_pool_) image_pool_->Insert(image);
}

void ImageController::AddImages(const std::vector<std::shared_ptr<Image>>& images) {
  mapper_.InsertBatch(images);
  if (image_pool_) {
    for (const auto& img : images) image_pool_->Insert(img);
  }
}

void ImageController::RemoveImageById(image_id_t remove_id) {
  mapper_.RemoveById(remove_id);
}

void ImageController::RemoveImagesByIds(const std::vector<image_id_t>& remove_ids) {
  for (auto id : remove_ids) mapper_.RemoveById(id);
}

void ImageController::RemoveImageByType(ImageType type) {
  mapper_.RemoveByType(type);
}

void ImageController::RemoveImageByPath(const std::string& path) {
  mapper_.RemoveByPath(path);
}

void ImageController::UpdateImage(const std::shared_ptr<Image> image) {
  mapper_.Update(image);
}

auto ImageController::GetImageById(image_id_t id) -> std::shared_ptr<Image> {
  if (image_pool_ && image_pool_->PoolContains(id)) {
    return image_pool_->GetImage(id);
  }
  auto img = mapper_.SelectById(id);
  if (img && image_pool_) image_pool_->Insert(img);
  return img;
}

auto ImageController::GetImageByType(ImageType type) -> std::vector<std::shared_ptr<Image>> {
  return mapper_.SelectByType(type);
}

auto ImageController::GetImageByName(const std::string& name) -> std::vector<std::shared_ptr<Image>> {
  return mapper_.SelectByName(name);
}

auto ImageController::GetImageByPath(const std::string& path) -> std::vector<std::shared_ptr<Image>> {
  return mapper_.SelectByPath(path);
}

}  // namespace alcedo
