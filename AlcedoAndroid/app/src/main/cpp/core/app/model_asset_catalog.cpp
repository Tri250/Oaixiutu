// AlcedoAndroid - ModelAssetCatalog implementation.
// Tracks locally available ML model assets.
// SPDX-License-Identifier: GPL-3.0-only
#include "app/app_services.hpp"

namespace alcedo {

void ModelAssetCatalog::RegisterModel(const std::string& model_key, const std::string& asset_path,
                                      int64_t size_bytes) {
  assets_[model_key] = {asset_path, size_bytes};
}

auto ModelAssetCatalog::GetModelPath(const std::string& model_key) const
    -> std::optional<std::string> {
  auto it = assets_.find(model_key);
  if (it == assets_.end()) return std::nullopt;
  return it->second.path;
}

auto ModelAssetCatalog::ListModels() const -> std::vector<std::string> {
  std::vector<std::string> keys;
  keys.reserve(assets_.size());
  for (const auto& [key, _] : assets_) keys.push_back(key);
  return keys;
}

}  // namespace alcedo
