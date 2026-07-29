// AlcedoAndroid - SleeveView implementation.
// Read-only sorted/filtered view over a folder's contents.
// SPDX-License-Identifier: GPL-3.0-only
#include "sleeve/sleeve_view.hpp"

#include <algorithm>
#include <utility>

#include "sleeve/sleeve_element/sleeve_file.hpp"
#include "sleeve/sleeve_filesystem.hpp"
#include "utils/app_logging.hpp"

namespace alcedo {

SleeveView::SleeveView(FileSystem& fs) : fs_(fs) {}

void SleeveView::SortElements(std::vector<std::shared_ptr<SleeveElement>>& elems,
                              const SleeveViewOptions& opts) const {
  auto comparator = [opts](const std::shared_ptr<SleeveElement>& a,
                           const std::shared_ptr<SleeveElement>& b) -> bool {
    bool result = false;
    switch (opts.sort_key) {
      case SleeveSortKey::Name:
        result = a->element_name_ < b->element_name_;
        break;
      case SleeveSortKey::AddedTime:
        result = a->added_time_ < b->added_time_;
        break;
      case SleeveSortKey::LastModifiedTime:
        result = a->last_modified_time_ < b->last_modified_time_;
        break;
      case SleeveSortKey::FileType:
        result = static_cast<int>(a->type_) < static_cast<int>(b->type_);
        break;
    }
    return opts.sort_order == SleeveSortOrder::Asc ? result : !result;
  };

  if (opts.folders_first) {
    std::stable_sort(elems.begin(), elems.end(),
                     [](const std::shared_ptr<SleeveElement>& a,
                        const std::shared_ptr<SleeveElement>& b) {
                       if (a->type_ == b->type_) return false;
                       return a->type_ == ElementType::FOLDER;
                     });
    // Sort within groups while preserving the folder/file partition.
    auto folder_end = std::partition_point(
        elems.begin(), elems.end(),
        [](const std::shared_ptr<SleeveElement>& e) { return e->type_ == ElementType::FOLDER; });
    std::sort(elems.begin(), folder_end, comparator);
    std::sort(folder_end, elems.end(), comparator);
  } else {
    std::sort(elems.begin(), elems.end(), comparator);
  }
}

auto SleeveView::List(const std::filesystem::path& folder, const SleeveViewOptions& opts)
    -> std::vector<std::shared_ptr<SleeveElement>> {
  auto ids = fs_.ListFolderContent(folder, false);
  std::vector<std::shared_ptr<SleeveElement>> elems;
  elems.reserve(ids.size());
  for (auto id : ids) {
    auto e = fs_.Get(id);
    if (e) elems.push_back(e);
  }
  SortElements(elems, opts);
  return elems;
}

auto SleeveView::ListFiles(const std::filesystem::path& folder, const SleeveViewOptions& opts)
    -> std::vector<std::shared_ptr<SleeveFile>> {
  auto elems = List(folder, opts);
  std::vector<std::shared_ptr<SleeveFile>> files;
  files.reserve(elems.size());
  for (auto& e : elems) {
    if (e->type_ == ElementType::FILE) {
      files.push_back(std::static_pointer_cast<SleeveFile>(e));
    }
  }
  return files;
}

auto SleeveView::CountFiles(const std::filesystem::path& folder) -> size_t {
  auto ids = fs_.ListFolderContent(folder, false);
  size_t count = 0;
  for (auto id : ids) {
    auto e = fs_.Get(id);
    if (e && e->type_ == ElementType::FILE) ++count;
  }
  return count;
}

auto SleeveView::CountFolders(const std::filesystem::path& folder) -> size_t {
  auto ids = fs_.ListFolderContent(folder, false);
  size_t count = 0;
  for (auto id : ids) {
    auto e = fs_.Get(id);
    if (e && e->type_ == ElementType::FOLDER) ++count;
  }
  return count;
}

}  // namespace alcedo
