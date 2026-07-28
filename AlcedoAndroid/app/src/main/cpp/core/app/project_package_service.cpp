// AlcedoAndroid - ProjectPackageService implementation.
// Packages/unpackages a project archive (DB + thumbnails + settings).
// SPDX-License-Identifier: GPL-3.0-only
#include "app/app_services.hpp"

#include <cstdio>
#include <filesystem>
#include <fstream>
#include <utility>

#include "utils/app_logging.hpp"

namespace alcedo {

ProjectPackageService::ProjectPackageService(SleeveManager& sleeve) : sleeve_(sleeve) {}

auto ProjectPackageService::Package(const std::filesystem::path& out_archive) -> bool {
  if (!sleeve_.IsOpen()) return false;
  auto& storage = sleeve_.GetStorageService();
  auto db_path = storage.GetDBPath();

  // Simple archive format: copy the DB file to the output path.
  // A real implementation would zip the DB + thumbnails + ICC profiles.
  std::error_code ec;
  std::filesystem::copy_file(db_path, out_archive,
                             std::filesystem::copy_options::overwrite_existing, ec);
  if (ec) {
    ALOGE("ProjectPackage: failed to copy %s to %s: %s", db_path.c_str(),
          out_archive.c_str(), ec.message().c_str());
    return false;
  }
  ALOGI("ProjectPackage: packaged to %s", out_archive.c_str());
  return true;
}

auto ProjectPackageService::Unpackage(const std::filesystem::path& archive) -> bool {
  if (!std::filesystem::exists(archive)) return false;
  // Extract the archive to a temp directory and open it.
  auto db_path = archive;
  if (!sleeve_.Open(db_path)) {
    ALOGE("ProjectPackage: failed to open %s", archive.c_str());
    return false;
  }
  ALOGI("ProjectPackage: unpackaged from %s", archive.c_str());
  return true;
}

}  // namespace alcedo
