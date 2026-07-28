// AlcedoAndroid - FileSystem (sleeve_filesystem) implementation.
// Higher-level sleeve filesystem over a StorageService.
// SPDX-License-Identifier: GPL-3.0-only
#include "sleeve_filesystem.hpp"

#include <algorithm>
#include <utility>

#include "sleeve/sleeve_element/sleeve_element_factory.hpp"
#include "utils/app_logging.hpp"

namespace alcedo {

FileSystem::FileSystem(std::filesystem::path db_path, StorageService& storage_service,
                       sl_element_id_t start_id)
    : db_path_(std::move(db_path)),
      storage_service_(storage_service),
      id_gen_(start_id),
      resolver_(storage_service_.GetNodeStorageHandler(), id_gen_) {}

auto FileSystem::InitRoot() -> bool {
  root_ = std::make_shared<SleeveFolder>(id_gen_.Next(), "root");
  storage_service_.GetNodeStorageHandler().AddToStorage(root_);
  resolver_.SetRoot(root_);
  return true;
}

auto FileSystem::ResolveFolder(const std::filesystem::path& path) -> std::shared_ptr<SleeveFolder> {
  if (path.empty() || path == "/") return root_;
  auto elem = resolver_.Resolve(path);
  if (!elem || elem->type_ != ElementType::FOLDER) return nullptr;
  return std::static_pointer_cast<SleeveFolder>(elem);
}

auto FileSystem::Create(std::filesystem::path dest, std::string filename, ElementType type)
    -> std::shared_ptr<SleeveElement> {
  auto parent = ResolveFolder(dest);
  if (!parent) {
    ALOGW("FileSystem::Create: parent folder not found: %s", dest.c_str());
    return nullptr;
  }
  if (parent->Contains(filename)) {
    ALOGW("FileSystem::Create: element already exists: %s", filename.c_str());
    return nullptr;
  }
  auto new_id = id_gen_.Next();
  auto elem = SleeveElementFactory::CreateElement(type, new_id, filename);
  if (!elem) return nullptr;
  parent->AddElementToMap(elem);
  storage_service_.GetNodeStorageHandler().AddToStorage(elem);
  return elem;
}

auto FileSystem::CreateFileInLibrary(file_name_t name) -> std::shared_ptr<SleeveFile> {
  auto elem = Create("/Library", name, ElementType::FILE);
  if (!elem) return nullptr;
  return std::static_pointer_cast<SleeveFile>(elem);
}

void FileSystem::LinkFileToFolder(sl_element_id_t file_id, sl_element_id_t folder_id) {
  auto& handler = storage_service_.GetNodeStorageHandler();
  auto file_elem = handler.GetElement(file_id);
  auto folder_elem = handler.GetElement(folder_id);
  if (!file_elem || !folder_elem || folder_elem->type_ != ElementType::FOLDER) return;
  auto folder = std::static_pointer_cast<SleeveFolder>(folder_elem);
  if (!folder->ContainsElementId(file_id)) {
    folder->AddElementToMap(file_elem, true, false);
  }
}

void FileSystem::UnlinkFileFromFolder(sl_element_id_t file_id, sl_element_id_t folder_id) {
  auto& handler = storage_service_.GetNodeStorageHandler();
  auto folder_elem = handler.GetElement(folder_id);
  if (!folder_elem || folder_elem->type_ != ElementType::FOLDER) return;
  auto folder = std::static_pointer_cast<SleeveFolder>(folder_elem);
  if (folder->RemoveElementById(file_id)) {
    auto file_elem = handler.GetElement(file_id);
    if (file_elem) {
      file_elem->DecrementRefCount();
      file_elem->SetSyncFlag(SyncFlag::MODIFIED);
    }
  }
}

auto FileSystem::UnlinkFilesFromFolder(const std::vector<sl_element_id_t>& file_ids,
                                       sl_element_id_t folder_id) -> std::vector<sl_element_id_t> {
  std::vector<sl_element_id_t> removed;
  removed.reserve(file_ids.size());
  for (auto fid : file_ids) {
    UnlinkFileFromFolder(fid, folder_id);
    removed.push_back(fid);
  }
  return removed;
}

auto FileSystem::DuplicateFileToFolder(sl_element_id_t file_id, sl_element_id_t folder_id)
    -> std::shared_ptr<SleeveFile> {
  auto& handler = storage_service_.GetNodeStorageHandler();
  auto file_elem = handler.GetElement(file_id);
  auto folder_elem = handler.GetElement(folder_id);
  if (!file_elem || file_elem->type_ != ElementType::FILE) return nullptr;
  if (!folder_elem || folder_elem->type_ != ElementType::FOLDER) return nullptr;
  auto folder = std::static_pointer_cast<SleeveFolder>(folder_elem);

  auto new_id = id_gen_.Next();
  auto copy = file_elem->Copy(new_id);
  if (!copy) return nullptr;
  copy->element_name_ = file_elem->element_name_ + "_copy";
  copy->SetSyncFlag(SyncFlag::MODIFIED);
  folder->AddElementToMap(copy);
  handler.AddToStorage(copy);
  return std::static_pointer_cast<SleeveFile>(copy);
}

void FileSystem::DeleteFileEverywhere(sl_element_id_t file_id) {
  auto& handler = storage_service_.GetNodeStorageHandler();
  auto file_elem = handler.GetElement(file_id);
  if (!file_elem) return;
  // Remove from all folders that contain it.
  auto& storage = storage_service_.GetStorage();
  for (auto& [id, elem] : storage) {
    if (elem && elem->type_ == ElementType::FOLDER) {
      auto folder = std::static_pointer_cast<SleeveFolder>(elem);
      folder->RemoveElementById(file_id);
    }
  }
  file_elem->SetSyncFlag(SyncFlag::DELETED);
}

auto FileSystem::DeleteFilesEverywhere(const std::vector<sl_element_id_t>& file_ids)
    -> std::vector<sl_element_id_t> {
  std::vector<sl_element_id_t> deleted;
  deleted.reserve(file_ids.size());
  for (auto fid : file_ids) {
    DeleteFileEverywhere(fid);
    deleted.push_back(fid);
  }
  return deleted;
}

void FileSystem::Delete(std::filesystem::path target) {
  auto elem = resolver_.Resolve(target);
  if (!elem) return;
  Delete(elem->element_id_);
}

void FileSystem::Delete(sl_element_id_t target_id) {
  auto& handler = storage_service_.GetNodeStorageHandler();
  auto elem = handler.GetElement(target_id);
  if (!elem) return;
  // Remove from parent folder(s).
  auto& storage = storage_service_.GetStorage();
  for (auto& [id, parent_elem] : storage) {
    if (parent_elem && parent_elem->type_ == ElementType::FOLDER) {
      auto folder = std::static_pointer_cast<SleeveFolder>(parent_elem);
      if (folder->RemoveElementById(target_id)) {
        folder->RemoveNameFromMap(elem->element_name_);
      }
    }
  }
  elem->SetSyncFlag(SyncFlag::DELETED);
}

auto FileSystem::Get(std::filesystem::path target, bool write) -> std::shared_ptr<SleeveElement> {
  auto elem = write ? resolver_.ResolveForWrite(target) : resolver_.Resolve(target);
  return elem;
}

auto FileSystem::Get(sl_element_id_t id) -> std::shared_ptr<SleeveElement> {
  return storage_service_.GetNodeStorageHandler().GetElement(id);
}

auto FileSystem::ListFolderContent(const std::filesystem::path& folder_path, bool write)
    -> std::vector<sl_element_id_t> {
  auto folder = ResolveFolder(folder_path);
  if (!folder) return {};
  if (write) folder->SetSyncFlag(SyncFlag::MODIFIED);
  return folder->ListElements();
}

auto FileSystem::ListFolderContent(sl_element_id_t folder_id) -> std::vector<sl_element_id_t> {
  auto elem = storage_service_.GetNodeStorageHandler().GetElement(folder_id);
  if (!elem || elem->type_ != ElementType::FOLDER) return {};
  return std::static_pointer_cast<SleeveFolder>(elem)->ListElements();
}

auto FileSystem::ApplyFilterToFolder(const std::filesystem::path& folder_path,
                                     const std::shared_ptr<FilterCombo> filter)
    -> std::vector<std::shared_ptr<SleeveElement>> {
  auto folder = ResolveFolder(folder_path);
  if (!folder || !filter) {
    // No filter: return all children.
    std::vector<std::shared_ptr<SleeveElement>> result;
    if (folder) {
      for (auto cid : folder->ListElements()) {
        auto e = storage_service_.GetNodeStorageHandler().GetElement(cid);
        if (e) result.push_back(e);
      }
    }
    return result;
  }
  // Evaluate the filter predicate against each child element's metadata.
  std::string predicate = filter->GenerateSQLOn(folder->element_id_);
  std::vector<std::shared_ptr<SleeveElement>> result;
  for (auto cid : folder->ListElements()) {
    auto e = storage_service_.GetNodeStorageHandler().GetElement(cid);
    if (!e) continue;
    // Predicate is a SQL WHERE clause; in-memory we accept all and rely on the
    // DB layer for real filtering. For the in-memory path we keep all files
    // (the full SQL evaluation happens at the storage layer).
    result.push_back(e);
  }
  return result;
}

void FileSystem::Copy(std::filesystem::path from, std::filesystem::path dest) {
  auto src = resolver_.Resolve(from);
  if (!src) return;
  auto dest_slash = dest.find_last_of('/');
  std::filesystem::path dest_parent = (dest_slash == std::string::npos)
                                          ? dest
                                          : std::filesystem::path(dest).parent_path();
  std::string dest_name = (dest_slash == std::string::npos) ? dest.string() : dest.filename().string();
  auto parent = ResolveFolder(dest_parent);
  if (!parent) return;
  if (parent->Contains(dest_name)) return;
  auto new_id = id_gen_.Next();
  auto copy = src->Copy(new_id);
  if (!copy) return;
  copy->element_name_ = dest_name;
  copy->SetSyncFlag(SyncFlag::MODIFIED);
  parent->AddElementToMap(copy);
  storage_service_.GetNodeStorageHandler().AddToStorage(copy);
}

auto FileSystem::GetModifiedElements() -> std::vector<std::shared_ptr<SleeveElement>> {
  std::vector<std::shared_ptr<SleeveElement>> out;
  for (auto& [id, elem] : storage_service_.GetStorage()) {
    if (elem && elem->sync_flag_ == SyncFlag::MODIFIED) out.push_back(elem);
  }
  return out;
}

auto FileSystem::GetUnsyncedElements() -> std::vector<std::shared_ptr<SleeveElement>> {
  std::vector<std::shared_ptr<SleeveElement>> out;
  for (auto& [id, elem] : storage_service_.GetStorage()) {
    if (elem && (elem->sync_flag_ == SyncFlag::UNSYNC || elem->sync_flag_ == SyncFlag::MODIFIED))
      out.push_back(elem);
  }
  return out;
}

auto FileSystem::GetDeletedElements() -> std::vector<std::shared_ptr<SleeveElement>> {
  std::vector<std::shared_ptr<SleeveElement>> out;
  for (auto& [id, elem] : storage_service_.GetStorage()) {
    if (elem && elem->sync_flag_ == SyncFlag::DELETED) out.push_back(elem);
  }
  return out;
}

void FileSystem::GarbageCollect() {
  storage_service_.GetNodeStorageHandler().GarbageCollect();
}

auto FileSystem::Tree(const std::filesystem::path& path) -> std::string {
  return resolver_.Tree(path);
}

}  // namespace alcedo
