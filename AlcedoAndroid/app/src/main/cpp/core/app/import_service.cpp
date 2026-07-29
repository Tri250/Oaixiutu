// AlcedoAndroid - ImportService implementation.
// SPDX-License-Identifier: GPL-3.0-only
#include "app/app_services.hpp"

#include <algorithm>
#include <cctype>
#include <filesystem>
#include <utility>

#include "utils/app_logging.hpp"

namespace alcedo {

ImportService::ImportService(SleeveManager& sleeve, ImageController& img_ctrl)
    : sleeve_(sleeve), img_ctrl_(img_ctrl) {}

auto ImportService::DetectImageType(const std::filesystem::path& path) -> ImageType {
  std::string ext = path.extension().string();
  std::transform(ext.begin(), ext.end(), ext.begin(), ::tolower);
  if (ext == ".arw")  return ImageType::ARW;
  if (ext == ".cr2")  return ImageType::CR2;
  if (ext == ".cr3")  return ImageType::CR3;
  if (ext == ".nef")  return ImageType::NEF;
  if (ext == ".dng")  return ImageType::DNG;
  if (ext == ".jpg" || ext == ".jpeg") return ImageType::JPEG;
  if (ext == ".png")  return ImageType::PNG;
  if (ext == ".tif" || ext == ".tiff") return ImageType::TIFF;
  return ImageType::DEFAULT;
}

auto ImportService::ImportImage(const std::filesystem::path& file_path) -> std::shared_ptr<SleeveFile> {
  auto type = DetectImageType(file_path);
  auto image = std::make_shared<Image>(file_path, type);
  image->image_name_ = file_path.filename().string();
  img_ctrl_.AddImage(image);

  auto file = sleeve_.InsertImage(image, file_path.filename().string());
  if (!file) {
    ALOGW("ImportService: failed to insert %s into sleeve", file_path.c_str());
  }
  return file;
}

auto ImportService::ImportBatch(const std::vector<std::filesystem::path>& paths)
    -> std::vector<std::shared_ptr<SleeveFile>> {
  std::vector<std::shared_ptr<SleeveFile>> results;
  results.reserve(paths.size());
  for (const auto& p : paths) {
    auto f = ImportImage(p);
    if (f) results.push_back(f);
  }
  return results;
}

}  // namespace alcedo
