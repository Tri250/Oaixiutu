// AlcedoAndroid - AlbumBrowseService implementation.
// SPDX-License-Identifier: GPL-3.0-only
#include "app/app_services.hpp"

#include "sleeve/sleeve_view.hpp"

namespace alcedo {

AlbumBrowseService::AlbumBrowseService(SleeveManager& sleeve) : sleeve_(sleeve) {}

auto AlbumBrowseService::ListAlbums() -> std::vector<std::shared_ptr<SleeveElement>> {
  auto& fs = sleeve_.GetFileSystem();
  auto root = fs.GetRoot();
  if (!root) return {};
  std::vector<std::shared_ptr<SleeveElement>> albums;
  for (auto id : root->ListElements()) {
    auto elem = fs.Get(id);
    if (elem && elem->type_ == ElementType::FOLDER) albums.push_back(elem);
  }
  return albums;
}

auto AlbumBrowseService::BrowseFolder(const std::filesystem::path& folder, size_t offset,
                                      size_t limit) -> std::vector<std::shared_ptr<SleeveFile>> {
  SleeveView view(sleeve_.GetFileSystem());
  SleeveViewOptions opts;
  auto files = view.ListFiles(folder, opts);
  if (offset >= files.size()) return {};
  auto end = (offset + limit > files.size()) ? files.size() : offset + limit;
  return std::vector<std::shared_ptr<SleeveFile>>(files.begin() + offset, files.begin() + end);
}

}  // namespace alcedo
