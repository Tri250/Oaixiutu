// AlcedoAndroid - SleeveFilterService implementation.
// SPDX-License-Identifier: GPL-3.0-only
#include "app/app_services.hpp"

#include "sleeve/sleeve_view.hpp"

namespace alcedo {

SleeveFilterService::SleeveFilterService(SleeveManager& sleeve) : sleeve_(sleeve) {}

auto SleeveFilterService::FilterFolder(const std::filesystem::path& folder,
                                       const std::string& sql_predicate)
    -> std::vector<std::shared_ptr<SleeveElement>> {
  auto& fs = sleeve_.GetFileSystem();
  auto ids = fs.ListFolderContent(folder);
  std::vector<std::shared_ptr<SleeveElement>> result;
  result.reserve(ids.size());
  for (auto id : ids) {
    auto elem = fs.Get(id);
    if (elem) result.push_back(elem);
  }
  // The SQL predicate is evaluated by the storage layer; here we return all
  // elements for the in-memory path. The DB-backed query (via SleeveMapper)
  // applies the real WHERE clause.
  return result;
}

auto SleeveFilterService::SearchByText(const std::string& query) -> std::vector<sl_element_id_t> {
  std::vector<sl_element_id_t> results;
  if (query.empty()) return results;
  // Search file names in the sleeve tree.
  auto& fs = sleeve_.GetFileSystem();
  auto& storage = sleeve_.GetStorageService().GetStorage();
  for (auto& [id, elem] : storage) {
    if (elem && elem->type_ == ElementType::FILE) {
      if (elem->element_name_.find(query) != std::string::npos) {
        results.push_back(id);
      }
    }
  }
  return results;
}

}  // namespace alcedo
