// AlcedoAndroid - SleeveManager implementation.
// Top-level sleeve manager owning StorageService + FileSystem.
// SPDX-License-Identifier: GPL-3.0-only
#include "sleeve_manager.hpp"

#include <utility>

#include "sleeve/sleeve_element/sleeve_file.hpp"
#include "sleeve_view.hpp"
#include "utils/app_logging.hpp"

namespace alcedo {

SleeveManager::SleeveManager() = default;
SleeveManager::~SleeveManager() = default;

auto SleeveManager::Open(const std::filesystem::path& db_path) -> bool {
  storage_ = std::make_unique<StorageService>(db_path);
  fs_ = std::make_unique<FileSystem>(db_path, *storage_, 1);
  if (!fs_->InitRoot()) {
    ALOGE("SleeveManager: failed to init root");
    return false;
  }
  ALOGI("SleeveManager: opened project at %s", db_path.c_str());
  return true;
}

void SleeveManager::Close() {
  if (fs_) fs_->GarbageCollect();
  fs_.reset();
  storage_.reset();
}

auto SleeveManager::InsertImage(std::shared_ptr<Image> image, const file_name_t& name)
    -> std::shared_ptr<SleeveFile> {
  if (!fs_) return nullptr;
  auto file = fs_->CreateFileInLibrary(name);
  if (!file) return nullptr;
  file->SetImage(std::move(image));
  return file;
}

auto SleeveManager::GetFile(sl_element_id_t id) -> std::shared_ptr<SleeveFile> {
  if (!fs_) return nullptr;
  auto elem = fs_->Get(id);
  if (!elem || elem->type_ != ElementType::FILE) return nullptr;
  return std::static_pointer_cast<SleeveFile>(elem);
}

auto SleeveManager::ListFiles(const std::filesystem::path& folder)
    -> std::vector<std::shared_ptr<SleeveFile>> {
  if (!fs_) return {};
  SleeveView view(*fs_);
  SleeveViewOptions opts;
  auto elems = view.ListFiles(folder, opts);
  return elems;
}

auto SleeveManager::RemoveFile(sl_element_id_t id) -> bool {
  if (!fs_) return false;
  auto file = GetFile(id);
  if (!file) return false;
  fs_->DeleteFileEverywhere(id);
  return true;
}

}  // namespace alcedo
