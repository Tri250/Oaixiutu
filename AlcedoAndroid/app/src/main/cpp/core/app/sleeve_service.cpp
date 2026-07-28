// AlcedoAndroid - SleeveAppService implementation.
// SPDX-License-Identifier: GPL-3.0-only
#include "app/app_services.hpp"

#include "sleeve/sleeve_element/sleeve_element_factory.hpp"
#include "utils/app_logging.hpp"

namespace alcedo {

SleeveAppService::SleeveAppService(SleeveManager& sleeve) : sleeve_(sleeve) {}

auto SleeveAppService::CreateFolder(const std::filesystem::path& parent, const std::string& name)
    -> bool {
  auto& fs = sleeve_.GetFileSystem();
  auto elem = fs.Create(parent, name, ElementType::FOLDER);
  return elem != nullptr;
}

auto SleeveAppService::MoveElement(const std::filesystem::path& src,
                                   const std::filesystem::path& dest) -> bool {
  auto& fs = sleeve_.GetFileSystem();
  auto elem = fs.Get(src, false);
  if (!elem) return false;
  fs.Copy(src, dest);
  fs.Delete(src);
  return true;
}

auto SleeveAppService::DeleteElement(const std::filesystem::path& path) -> bool {
  auto& fs = sleeve_.GetFileSystem();
  auto elem = fs.Get(path, false);
  if (!elem) return false;
  fs.Delete(path);
  return true;
}

}  // namespace alcedo
