// AlcedoAndroid - SleeveView.
// A read-only view over a folder's contents, supporting sorted/filtered
// listing for the album UI. Self-contained Android port.
// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <filesystem>
#include <memory>
#include <string>
#include <vector>

#include "sleeve/sleeve_element/sleeve_element.hpp"
#include "sleeve/sleeve_element/sleeve_file.hpp"
#include "sleeve/sleeve_filter/filter_combo.hpp"
#include "type/type.hpp"

namespace alcedo {

class FileSystem;

enum class SleeveSortKey { Name, AddedTime, LastModifiedTime, FileType };

enum class SleeveSortOrder { Asc, Desc };

struct SleeveViewOptions {
  SleeveSortKey   sort_key   = SleeveSortKey::AddedTime;
  SleeveSortOrder sort_order = SleeveSortOrder::Desc;
  std::shared_ptr<FilterCombo> filter = nullptr;
  bool                        folders_first = true;
};

class SleeveView {
 public:
  explicit SleeveView(FileSystem& fs);

  // List the (optionally filtered/sorted) contents of a folder.
  auto List(const std::filesystem::path& folder, const SleeveViewOptions& opts)
      -> std::vector<std::shared_ptr<SleeveElement>>;

  // List only files.
  auto ListFiles(const std::filesystem::path& folder, const SleeveViewOptions& opts)
      -> std::vector<std::shared_ptr<SleeveFile>>;

  // Count helpers for the UI.
  auto CountFiles(const std::filesystem::path& folder) -> size_t;
  auto CountFolders(const std::filesystem::path& folder) -> size_t;

 private:
  FileSystem& fs_;
  void SortElements(std::vector<std::shared_ptr<SleeveElement>>& elems,
                    const SleeveViewOptions& opts) const;
};

}  // namespace alcedo
