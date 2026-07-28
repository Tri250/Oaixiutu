// AlcedoAndroid - ProjectService implementation.
// SPDX-License-Identifier: GPL-3.0-only
#include "app/app_services.hpp"

#include <utility>

#include "utils/app_logging.hpp"

namespace alcedo {

ProjectService::ProjectService() = default;

auto ProjectService::Open(const std::filesystem::path& db_path) -> bool {
  Close();
  sleeve_ = std::make_unique<SleeveManager>();
  if (!sleeve_->Open(db_path)) {
    sleeve_.reset();
    ALOGE("ProjectService: failed to open project at %s", db_path.c_str());
    return false;
  }
  project_path_ = db_path;
  ALOGI("ProjectService: opened %s", db_path.c_str());
  return true;
}

void ProjectService::Close() {
  if (sleeve_) sleeve_->Close();
  sleeve_.reset();
  project_path_.clear();
}

auto ProjectService::IsOpen() const -> bool { return sleeve_ != nullptr && sleeve_->IsOpen(); }

auto ProjectService::GetSleeveManager() -> SleeveManager& { return *sleeve_; }

auto ProjectService::SaveAll() -> bool {
  if (!sleeve_) return false;
  // Flush modified elements to the DB via the storage layer.
  auto& fs = sleeve_->GetFileSystem();
  auto modified = fs.GetModifiedElements();
  auto& storage = sleeve_->GetStorageService();
  auto guard = DBController(storage.GetDBPath()).GetConnectionGuard();
  SleeveElementController ctrl(std::move(guard));
  for (auto& elem : modified) {
    if (elem) ctrl.PersistElement(*elem);
  }
  auto deleted = fs.GetDeletedElements();
  for (auto& elem : deleted) {
    if (elem) ctrl.DeleteElement(elem->element_id_);
  }
  fs.GarbageCollect();
  return true;
}

auto ProjectService::GetProjectPath() const -> const std::filesystem::path& { return project_path_; }

}  // namespace alcedo
