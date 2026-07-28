// AlcedoAndroid - SleeveFilesystem (FileSystem).
// Higher-level sleeve filesystem over a StorageService, with create/link/
// unlink/duplicate/delete/list/filter operations. Self-contained Android port.
// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <filesystem>
#include <memory>
#include <string>
#include <unordered_map>
#include <vector>

#include "path_resolver.hpp"
#include "sleeve/sleeve_element/sleeve_element.hpp"
#include "sleeve/sleeve_element/sleeve_file.hpp"
#include "sleeve/sleeve_element/sleeve_folder.hpp"
#include "sleeve/sleeve_filter/filter_combo.hpp"
#include "storage_service.hpp"
#include "type/type.hpp"
#include "utils/id/id_generator.hpp"

namespace alcedo {

class FileSystem {
 public:
  FileSystem(std::filesystem::path db_path, StorageService& storage_service,
             sl_element_id_t start_id);

  auto InitRoot() -> bool;

  auto Create(std::filesystem::path dest, std::string filename, ElementType type)
      -> std::shared_ptr<SleeveElement>;
  auto CreateFileInLibrary(file_name_t name) -> std::shared_ptr<SleeveFile>;
  void LinkFileToFolder(sl_element_id_t file_id, sl_element_id_t folder_id);
  void UnlinkFileFromFolder(sl_element_id_t file_id, sl_element_id_t folder_id);
  auto UnlinkFilesFromFolder(const std::vector<sl_element_id_t>& file_ids,
                             sl_element_id_t folder_id) -> std::vector<sl_element_id_t>;
  auto DuplicateFileToFolder(sl_element_id_t file_id, sl_element_id_t folder_id)
      -> std::shared_ptr<SleeveFile>;
  void DeleteFileEverywhere(sl_element_id_t file_id);
  auto DeleteFilesEverywhere(const std::vector<sl_element_id_t>& file_ids)
      -> std::vector<sl_element_id_t>;
  void Delete(std::filesystem::path target);
  void Delete(sl_element_id_t target_id);
  auto Get(std::filesystem::path target, bool write) -> std::shared_ptr<SleeveElement>;
  auto Get(sl_element_id_t id) -> std::shared_ptr<SleeveElement>;
  auto ListFolderContent(const std::filesystem::path& folder_path, bool write = false)
      -> std::vector<sl_element_id_t>;
  auto ListFolderContent(sl_element_id_t folder_id) -> std::vector<sl_element_id_t>;

  auto ApplyFilterToFolder(const std::filesystem::path& folder_path,
                           const std::shared_ptr<FilterCombo> filter)
      -> std::vector<std::shared_ptr<SleeveElement>>;
  void Copy(std::filesystem::path from, std::filesystem::path dest);

  auto GetCurrentID() -> sl_element_id_t { return id_gen_.Peek(); }
  auto GetRoot() -> std::shared_ptr<SleeveFolder> { return root_; }
  auto GetResolver() -> PathResolver& { return resolver_; }

  auto GetModifiedElements() -> std::vector<std::shared_ptr<SleeveElement>>;
  auto GetUnsyncedElements() -> std::vector<std::shared_ptr<SleeveElement>>;
  auto GetDeletedElements() -> std::vector<std::shared_ptr<SleeveElement>>;
  void GarbageCollect();
  auto Tree(const std::filesystem::path& path) -> std::string;

 private:
  std::filesystem::path                                         db_path_;
  StorageService&                                               storage_service_;
  std::shared_ptr<SleeveFolder>                                 root_;
  IncrID::IDGenerator<sl_element_id_t>                          id_gen_;
  PathResolver                                                  resolver_;

  auto ResolveFolder(const std::filesystem::path& path) -> std::shared_ptr<SleeveFolder>;
};

}  // namespace alcedo
