// AlcedoAndroid - FileSystem (sleeve_filesystem) implementation.
// Higher-level sleeve filesystem over a StorageService.
// SPDX-License-Identifier: GPL-3.0-only
#include "sleeve/sleeve_filesystem.hpp"

#include <algorithm>
#include <cctype>
#include <mutex>
#include <string>
#include <string_view>
#include <type_traits>
#include <utility>
#include <variant>

#include "sleeve/sleeve_element/sleeve_element_factory.hpp"
#include "utils/app_logging.hpp"

namespace alcedo {

namespace {

// ---- Best-effort in-memory filter evaluation ----
// ApplyFilterToFolder evaluates the FilterCombo predicate against each child
// element directly in memory. The authoritative SQL evaluation still happens at
// the storage/DB layer; this in-memory path covers the common name/extension
// and rating conditions so filtering is not silently a no-op. Conditions that
// require EXIF/date/semantic data (unavailable here) are treated as
// permissive (match) so files are not wrongly excluded.

double FilterValueAsNumber(const FilterValue& v) {
  return std::visit([](auto&& arg) -> double {
    using T = std::decay_t<decltype(arg)>;
    if constexpr (std::is_same_v<T, int64_t>) return static_cast<double>(arg);
    else if constexpr (std::is_same_v<T, double>) return arg;
    else if constexpr (std::is_same_v<T, bool>) return arg ? 1.0 : 0.0;
    else if constexpr (std::is_same_v<T, std::time_t>) return static_cast<double>(arg);
    else return 0.0;
  }, v);
}

std::string FilterValueAsString(const FilterValue& v) {
  return std::visit([](auto&& arg) -> std::string {
    using T = std::decay_t<decltype(arg)>;
    if constexpr (std::is_same_v<T, std::string>) return arg;
    else if constexpr (std::is_same_v<T, int64_t>) return std::to_string(arg);
    else if constexpr (std::is_same_v<T, double>) return std::to_string(arg);
    else if constexpr (std::is_same_v<T, bool>) return arg ? "true" : "false";
    else return std::string();
  }, v);
}

std::string ToLower(std::string s) {
  std::transform(s.begin(), s.end(), s.begin(),
                 [](unsigned char c) { return std::tolower(c); });
  return s;
}

bool CompareString(std::string_view field, std::string_view val, CompareOp op) {
  switch (op) {
    case CompareOp::EQUALS:       return field == val;
    case CompareOp::NOT_EQUALS:   return field != val;
    case CompareOp::CONTAINS:     return field.find(val) != std::string_view::npos;
    case CompareOp::NOT_CONTAINS: return field.find(val) == std::string_view::npos;
    case CompareOp::STARTS_WITH:
      return field.size() >= val.size() && field.compare(0, val.size(), val) == 0;
    case CompareOp::ENDS_WITH:
      return field.size() >= val.size() &&
             field.compare(field.size() - val.size(), val.size(), val) == 0;
    case CompareOp::REGEX:  // best-effort: fall back to substring match
      return field.find(val) != std::string_view::npos;
    default:  // numeric-only ops are permissive for string fields
      return true;
  }
}

bool CompareNumber(double field, double val, double val2, CompareOp op) {
  switch (op) {
    case CompareOp::EQUALS:        return field == val;
    case CompareOp::NOT_EQUALS:    return field != val;
    case CompareOp::GREATER_THAN:  return field > val;
    case CompareOp::LESS_THAN:     return field < val;
    case CompareOp::GREATER_EQUAL: return field >= val;
    case CompareOp::LESS_EQUAL:    return field <= val;
    case CompareOp::BETWEEN:       return field >= val && field <= val2;
    default:                       return true;
  }
}

std::string FileExtensionOf(const std::string& name) {
  auto pos = name.find_last_of('.');
  if (pos == std::string::npos) return {};
  return ToLower(name.substr(pos));  // includes the leading '.'
}

// The authoritative rating lives in the AI storage layer, which the sleeve-side
// facade cannot reach; unrated files are treated as rating 0.
double RatingOf(const SleeveElement& elem) {
  (void)elem;
  return 0.0;
}

bool EvaluateCondition(const FieldCondition& cond, const SleeveElement& elem) {
  const std::string& name = elem.element_name_;
  switch (cond.field_) {
    case FilterField::FileName:
      return CompareString(name, FilterValueAsString(cond.value_), cond.op_);
    case FilterField::FileExtension: {
      std::string val = FilterValueAsString(cond.value_);
      if (!val.empty() && val[0] != '.') val = "." + val;
      return CompareString(FileExtensionOf(name), ToLower(val), cond.op_);
    }
    case FilterField::ImagePath:
      return CompareString(name, FilterValueAsString(cond.value_), cond.op_);
    case FilterField::Rating: {
      double r = RatingOf(elem);
      double v = FilterValueAsNumber(cond.value_);
      double v2 = cond.second_value_.has_value() ? FilterValueAsNumber(*cond.second_value_) : 0.0;
      return CompareNumber(r, v, v2, cond.op_);
    }
    // EXIF/date/size/semantic fields need DB access unavailable in-memory.
    case FilterField::ExifCameraModel:
    case FilterField::ExifFocalLength:
    case FilterField::ExifAperture:
    case FilterField::ExifISO:
    case FilterField::CaptureDate:
    case FilterField::ImportDate:
    case FilterField::ImageSize:
    case FilterField::SemanticTags:
    default:
      return true;
  }
}

bool EvaluateFilter(const FilterNode& node, const SleeveElement& elem) {
  switch (node.type_) {
    case FilterNode::Type::Logical: {
      switch (node.op_) {
        case FilterOp::AND:
          for (const auto& child : node.children_)
            if (!EvaluateFilter(child, elem)) return false;
          return true;
        case FilterOp::OR:
          for (const auto& child : node.children_)
            if (EvaluateFilter(child, elem)) return true;
          return node.children_.empty();
        case FilterOp::NOT:
          if (node.children_.empty()) return true;
          return !EvaluateFilter(node.children_.front(), elem);
      }
      return true;
    }
    case FilterNode::Type::Condition:
      if (!node.condition_.has_value()) return true;  // empty condition => match all
      return EvaluateCondition(*node.condition_, elem);
    case FilterNode::Type::RawSQL:
    default:
      return true;  // raw SQL cannot be evaluated in-memory; be permissive
  }
}

}  // namespace

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
  std::lock_guard<std::mutex> lock(storage_service_.GetLiveStateLock());
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
  std::lock_guard<std::mutex> lock(storage_service_.GetLiveStateLock());
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
  const FilterNode& root = filter->GetRoot();
  std::vector<std::shared_ptr<SleeveElement>> result;
  for (auto cid : folder->ListElements()) {
    auto e = storage_service_.GetNodeStorageHandler().GetElement(cid);
    if (!e) continue;
    // Folders are always kept (so navigation survives image-oriented filters);
    // files are filtered through the in-memory predicate evaluator.
    if (e->type_ == ElementType::FOLDER || EvaluateFilter(root, *e)) {
      result.push_back(e);
    }
  }
  return result;
}

void FileSystem::Copy(std::filesystem::path from, std::filesystem::path dest) {
  auto src = resolver_.Resolve(from);
  if (!src) return;
  auto dest_str = dest.string();
  auto dest_slash = dest_str.find_last_of('/');
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
  std::lock_guard<std::mutex> lock(storage_service_.GetLiveStateLock());
  for (auto& [id, elem] : storage_service_.GetStorage()) {
    if (elem && elem->sync_flag_ == SyncFlag::MODIFIED) out.push_back(elem);
  }
  return out;
}

auto FileSystem::GetUnsyncedElements() -> std::vector<std::shared_ptr<SleeveElement>> {
  std::vector<std::shared_ptr<SleeveElement>> out;
  std::lock_guard<std::mutex> lock(storage_service_.GetLiveStateLock());
  for (auto& [id, elem] : storage_service_.GetStorage()) {
    if (elem && (elem->sync_flag_ == SyncFlag::UNSYNC || elem->sync_flag_ == SyncFlag::MODIFIED))
      out.push_back(elem);
  }
  return out;
}

auto FileSystem::GetDeletedElements() -> std::vector<std::shared_ptr<SleeveElement>> {
  std::vector<std::shared_ptr<SleeveElement>> out;
  std::lock_guard<std::mutex> lock(storage_service_.GetLiveStateLock());
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
