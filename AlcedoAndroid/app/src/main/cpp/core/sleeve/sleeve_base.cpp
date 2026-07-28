// AlcedoAndroid - SleeveBase implementation (in-memory element tree).
// SPDX-License-Identifier: GPL-3.0-only
#include "sleeve_base.hpp"

#include <algorithm>
#include <functional>
#include <queue>
#include <sstream>
#include <utility>

#include "sleeve/sleeve_element/sleeve_element_factory.hpp"
#include "utils/app_logging.hpp"

namespace alcedo {

ElementAccessGuard::ElementAccessGuard(std::shared_ptr<SleeveElement> element)
    : access_element_(std::move(element)) {}
ElementAccessGuard::~ElementAccessGuard() = default;

// ---- SleeveBase ----

SleeveBase::SleeveBase(sleeve_id_t id) : sleeve_id_(id) {}

void SleeveBase::InitializeRoot() {
  root_ = std::make_shared<SleeveFolder>(GetNextElementId(), "root");
  storage_[root_->element_id_] = root_;
  dentry_cache_.RecordAccess("/", root_->element_id_);
}

auto SleeveBase::GetStorage() -> std::unordered_map<sl_element_id_t, std::shared_ptr<SleeveElement>>& {
  return storage_;
}

auto SleeveBase::GetFilterStorage() -> std::unordered_map<filter_id_t, std::shared_ptr<FilterCombo>>& {
  return filter_storage_;
}

auto SleeveBase::AccessElementById(sl_element_id_t id) const
    -> std::optional<std::shared_ptr<SleeveElement>> {
  auto it = storage_.find(id);
  if (it == storage_.end()) return std::nullopt;
  return it->second;
}

auto SleeveBase::AccessElementByPath(const sl_path_t& path)
    -> std::optional<std::shared_ptr<SleeveElement>> {
  if (path.empty() || path == "/") return root_;
  if (auto cached = dentry_cache_.AccessElement(path)) {
    return AccessElementById(*cached);
  }
  // Walk the tree.
  if (!root_) return std::nullopt;
  std::shared_ptr<SleeveFolder> current = root_;
  std::string::size_type pos = (path[0] == '/') ? 1 : 0;
  while (pos < path.size()) {
    auto slash = path.find('/', pos);
    std::string segment = (slash == std::string::npos) ? path.substr(pos) : path.substr(pos, slash - pos);
    if (segment.empty()) {
      if (slash == std::string::npos) break;
      pos = slash + 1;
      continue;
    }
    auto id_opt = current->GetElementIdByName(segment);
    if (!id_opt) return std::nullopt;
    auto it = storage_.find(*id_opt);
    if (it == storage_.end()) return std::nullopt;
    if (slash == std::string::npos) {
      dentry_cache_.RecordAccess(path, *id_opt);
      return it->second;
    }
    if (it->second->type_ != ElementType::FOLDER) return std::nullopt;
    current = std::static_pointer_cast<SleeveFolder>(it->second);
    pos = slash + 1;
  }
  return std::nullopt;
}

auto SleeveBase::CreateElementToPath(const sl_path_t& path, const file_name_t& file_name,
                                     ElementType type)
    -> std::optional<std::shared_ptr<SleeveElement>> {
  // Resolve the parent folder.
  if (!root_) return std::nullopt;
  std::shared_ptr<SleeveFolder> parent = root_;
  if (path != "/" && !path.empty()) {
    auto parent_opt = AccessElementByPath(path);
    if (!parent_opt || !*parent_opt) return std::nullopt;
    auto& elem = *parent_opt;
    if (elem->type_ != ElementType::FOLDER) return std::nullopt;
    parent = std::static_pointer_cast<SleeveFolder>(elem);
  }
  if (parent->Contains(file_name)) return std::nullopt;  // already exists

  auto new_id = GetNextElementId();
  auto new_elem = SleeveElementFactory::CreateElement(type, new_id, file_name);
  if (!new_elem) return std::nullopt;
  parent->AddElementToMap(new_elem);
  storage_[new_id] = new_elem;
  ++size_;
  return new_elem;
}

auto SleeveBase::RemoveElementInPath(const sl_path_t& target)
    -> std::optional<std::shared_ptr<SleeveElement>> {
  auto elem_opt = AccessElementByPath(target);
  if (!elem_opt || !*elem_opt) return std::nullopt;
  auto elem = *elem_opt;
  // Remove from parent.
  auto slash = target.find_last_of('/');
  sl_path_t parent_path = (slash == 0) ? "/" : target.substr(0, slash);
  file_name_t name = (slash == std::string::npos) ? target : target.substr(slash + 1);
  auto parent_opt = AccessElementByPath(parent_path);
  if (parent_opt && *parent_opt && (*parent_opt)->type_ == ElementType::FOLDER) {
    auto parent = std::static_pointer_cast<SleeveFolder>(*parent_opt);
    parent->RemoveElementById(elem->element_id_);
    parent->RemoveNameFromMap(name);
  }
  dentry_cache_.RemoveRecord(target);
  elem->SetSyncFlag(SyncFlag::DELETED);
  if (size_ > 0) --size_;
  return elem;
}

auto SleeveBase::RemoveElementInPath(const sl_path_t& path, const file_name_t& file_name)
    -> std::optional<std::shared_ptr<SleeveElement>> {
  sl_path_t full = path;
  if (full.empty() || full.back() != '/') full += "/";
  full += file_name;
  return RemoveElementInPath(full);
}

auto SleeveBase::CopyElement(const sl_path_t& src, const sl_path_t& dest)
    -> std::optional<std::shared_ptr<SleeveElement>> {
  auto src_opt = AccessElementByPath(src);
  if (!src_opt || !*src_opt) return std::nullopt;
  auto& src_elem = *src_opt;
  auto slash = dest.find_last_of('/');
  sl_path_t dest_parent = (slash == 0) ? "/" : dest.substr(0, slash);
  file_name_t dest_name = (slash == std::string::npos) ? dest : dest.substr(slash + 1);
  auto parent_opt = AccessElementByPath(dest_parent);
  if (!parent_opt || !*parent_opt || (*parent_opt)->type_ != ElementType::FOLDER) return std::nullopt;
  auto parent = std::static_pointer_cast<SleeveFolder>(*parent_opt);
  if (parent->Contains(dest_name)) return std::nullopt;
  auto new_id = GetNextElementId();
  auto copy = src_elem->Copy(new_id);
  if (!copy) return std::nullopt;
  copy->element_name_ = dest_name;
  copy->SetSyncFlag(SyncFlag::MODIFIED);
  parent->AddElementToMap(copy);
  storage_[new_id] = copy;
  ++size_;
  return copy;
}

auto SleeveBase::MoveElement(const sl_path_t& src, const sl_path_t& dest)
    -> std::optional<std::shared_ptr<SleeveElement>> {
  // Move = copy + remove source (preserves element identity semantics by
  // re-parenting the same shared_ptr).
  auto elem_opt = AccessElementByPath(src);
  if (!elem_opt || !*elem_opt) return std::nullopt;
  auto elem = *elem_opt;

  auto src_slash = src.find_last_of('/');
  sl_path_t src_parent = (src_slash == 0) ? "/" : src.substr(0, src_slash);
  file_name_t src_name = (src_slash == std::string::npos) ? src : src.substr(src_slash + 1);

  auto dest_slash = dest.find_last_of('/');
  sl_path_t dest_parent = (dest_slash == 0) ? "/" : dest.substr(0, dest_slash);
  file_name_t dest_name = (dest_slash == std::string::npos) ? dest : dest.substr(dest_slash + 1);

  auto src_parent_opt = AccessElementByPath(src_parent);
  auto dest_parent_opt = AccessElementByPath(dest_parent);
  if (!dest_parent_opt || !*dest_parent_opt || (*dest_parent_opt)->type_ != ElementType::FOLDER)
    return std::nullopt;
  auto dest_folder = std::static_pointer_cast<SleeveFolder>(*dest_parent_opt);
  if (dest_folder->Contains(dest_name)) return std::nullopt;

  if (src_parent_opt && *src_parent_opt && (*src_parent_opt)->type_ == ElementType::FOLDER) {
    auto src_folder = std::static_pointer_cast<SleeveFolder>(*src_parent_opt);
    src_folder->RemoveElementById(elem->element_id_);
    src_folder->RemoveNameFromMap(src_name);
  }
  dentry_cache_.RemoveRecord(src);
  elem->element_name_ = dest_name;
  elem->SetSyncFlag(SyncFlag::MODIFIED);
  dest_folder->AddElementToMap(elem, false, false);
  dentry_cache_.RecordAccess(dest, elem->element_id_);
  return elem;
}

auto SleeveBase::GetReadGuard(const sl_path_t& target) -> std::optional<ElementAccessGuard> {
  auto elem_opt = AccessElementByPath(target);
  if (!elem_opt || !*elem_opt) return std::nullopt;
  return ElementAccessGuard(*elem_opt);
}

auto SleeveBase::GetWriteGuard(const sl_path_t& target) -> std::optional<ElementAccessGuard> {
  auto elem_opt = AccessElementByPath(target);
  if (!elem_opt || !*elem_opt) return std::nullopt;
  (*elem_opt)->SetSyncFlag(SyncFlag::MODIFIED);
  return ElementAccessGuard(*elem_opt);
}

auto SleeveBase::GetWriteGuard(const sl_path_t& parent_folder_path, const file_name_t& file_name)
    -> std::optional<ElementAccessGuard> {
  sl_path_t full = parent_folder_path;
  if (full.empty() || full.back() != '/') full += "/";
  full += file_name;
  return GetWriteGuard(full);
}

void SleeveBase::GarbageCollect() {
  for (auto it = storage_.begin(); it != storage_.end();) {
    auto& elem = it->second;
    if (!elem || (elem->sync_flag_ == SyncFlag::DELETED && elem.use_count() == 1)) {
      it = storage_.erase(it);
    } else {
      ++it;
    }
  }
}

auto SleeveBase::IsSubFolder(const std::shared_ptr<SleeveFolder> folder_a,
                             const sl_path_t& path_b) const -> bool {
  if (!folder_a) return false;
  // Check whether path_b is inside folder_a's subtree by walking from root.
  auto opt = const_cast<SleeveBase*>(this)->AccessElementByPath(path_b);
  if (!opt || !*opt) return false;
  auto elem_b = *opt;
  // Walk up from elem_b is not directly supported (no parent pointer), so
  // compare by checking if elem_b's id is reachable from folder_a.
  if (elem_b->element_id_ == folder_a->element_id_) return true;
  std::queue<std::shared_ptr<SleeveFolder>> q;
  q.push(folder_a);
  while (!q.empty()) {
    auto f = q.front();
    q.pop();
    for (auto cid : f->ListElements()) {
      if (cid == elem_b->element_id_) return true;
      auto it = storage_.find(cid);
      if (it != storage_.end() && it->second->type_ == ElementType::FOLDER) {
        q.push(std::static_pointer_cast<SleeveFolder>(it->second));
      }
    }
  }
  return false;
}

auto SleeveBase::Tree(const sl_path_t& path) -> std::string {
  auto elem_opt = AccessElementByPath(path);
  if (!elem_opt || !*elem_opt) return "<not found>\n";
  std::ostringstream oss;
  std::function<void(const std::shared_ptr<SleeveElement>&, int)> walk =
      [&](const std::shared_ptr<SleeveElement>& e, int depth) {
        for (int i = 0; i < depth; ++i) oss << "  ";
        oss << e->element_name_ << " [" << (e->type_ == ElementType::FOLDER ? "D" : "F")
            << "] id=" << e->element_id_ << "\n";
        if (e->type_ == ElementType::FOLDER) {
          auto folder = std::static_pointer_cast<SleeveFolder>(e);
          for (auto cid : folder->ListElements()) {
            auto it = storage_.find(cid);
            if (it != storage_.end()) walk(it->second, depth + 1);
          }
        }
      };
  walk(*elem_opt, 0);
  return oss.str();
}

auto SleeveBase::TreeBFS(const sl_path_t& path) -> std::string {
  auto elem_opt = AccessElementByPath(path);
  if (!elem_opt || !*elem_opt) return "<not found>\n";
  std::ostringstream oss;
  std::queue<std::pair<std::shared_ptr<SleeveElement>, int>> q;
  q.push({*elem_opt, 0});
  while (!q.empty()) {
    auto [e, depth] = q.front();
    q.pop();
    for (int i = 0; i < depth; ++i) oss << "  ";
    oss << e->element_name_ << " [" << (e->type_ == ElementType::FOLDER ? "D" : "F")
        << "] id=" << e->element_id_ << "\n";
    if (e->type_ == ElementType::FOLDER) {
      auto folder = std::static_pointer_cast<SleeveFolder>(e);
      for (auto cid : folder->ListElements()) {
        auto it = storage_.find(cid);
        if (it != storage_.end()) q.push({it->second, depth + 1});
      }
    }
  }
  return oss.str();
}

}  // namespace alcedo
