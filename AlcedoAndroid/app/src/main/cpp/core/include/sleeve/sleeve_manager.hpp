// AlcedoAndroid - SleeveManager.
// Top-level sleeve manager owning a StorageService + FileSystem and exposing
// project-wide sleeve operations (open/save/insert/query). Self-contained port.
// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <filesystem>
#include <memory>
#include <string>
#include <vector>

#include "sleeve/sleeve_element/sleeve_element.hpp"
#include "sleeve/sleeve_filesystem.hpp"
#include "storage_service.hpp"
#include "type/type.hpp"

namespace alcedo {

class Image;
class EditHistory;

class SleeveManager {
 public:
  SleeveManager();
  ~SleeveManager();

  // Open / create a project sleeve rooted at the given DB path.
  auto Open(const std::filesystem::path& db_path) -> bool;
  void Close();

  auto GetFileSystem() -> FileSystem& { return *fs_; }
  auto GetStorageService() -> StorageService& { return *storage_; }
  auto IsOpen() const -> bool { return fs_ != nullptr; }

  // Convenience: insert an image file into the library root.
  auto InsertImage(std::shared_ptr<Image> image, const file_name_t& name)
      -> std::shared_ptr<SleeveFile>;
  // Look up a file by element id.
  auto GetFile(sl_element_id_t id) -> std::shared_ptr<SleeveFile>;
  // List all files in a folder.
  auto ListFiles(const std::filesystem::path& folder) -> std::vector<std::shared_ptr<SleeveFile>>;
  // Remove a file element.
  auto RemoveFile(sl_element_id_t id) -> bool;

 private:
  std::unique_ptr<StorageService> storage_;
  std::unique_ptr<FileSystem>     fs_;
};

}  // namespace alcedo
