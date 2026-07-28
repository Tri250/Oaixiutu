// AlcedoAndroid - PathResolver.
// Resolves sleeve paths (e.g. "/Library/Folder/image.arw") to SleeveElement
// pointers, with an LRU directory cache. Self-contained Android port.
// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <filesystem>
#include <memory>
#include <string>

#include "dentry_cache_manager.hpp"
#include "sleeve/sleeve_element/sleeve_element.hpp"
#include "sleeve/sleeve_element/sleeve_folder.hpp"
#include "storage_service.hpp"
#include "type/type.hpp"
#include "utils/cache/lru_cache.hpp"
#include "utils/id/id_generator.hpp"

namespace alcedo {

class PathResolver {
 public:
  PathResolver();
  PathResolver(NodeStorageHandler& handler, IncrID::IDGenerator<uint32_t>& id_gen);
  void SetRoot(std::shared_ptr<SleeveFolder> root);

  static auto Normalize(const std::filesystem::path& raw_path) -> std::string;
  static auto SplitPath(const std::string& normalized) -> std::vector<std::string>;

  auto IsSubpath(const std::filesystem::path& base, const std::filesystem::path& target) -> bool;
  auto Contains(const std::filesystem::path& path, ElementType type) -> bool;
  auto Contains(const std::filesystem::path& path) -> bool;
  auto Resolve(const std::filesystem::path& path) -> std::shared_ptr<SleeveElement>;
  auto ResolveForWrite(const std::filesystem::path& path) -> std::shared_ptr<SleeveElement>;
  void Invalidate(const std::filesystem::path& path);

  auto Tree(const std::filesystem::path& path) -> std::string;

 private:
  std::shared_ptr<SleeveFolder>        root_;
  LRUCache<sl_path_t, sl_element_id_t> directory_cache_{256};
  NodeStorageHandler*                  storage_handler_ = nullptr;
  IncrID::IDGenerator<uint32_t>*       id_gen_          = nullptr;
};

}  // namespace alcedo
