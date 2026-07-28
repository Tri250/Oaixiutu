// AlcedoAndroid - PathResolver implementation.
// Resolves sleeve paths to SleeveElement pointers using an LRU directory cache.
// SPDX-License-Identifier: GPL-3.0-only
#include "path_resolver.hpp"

#include <algorithm>
#include <sstream>
#include <utility>

#include "sleeve/sleeve_element/sleeve_file.hpp"
#include "utils/app_logging.hpp"

namespace alcedo {

PathResolver::PathResolver() = default;

PathResolver::PathResolver(NodeStorageHandler& handler, IncrID::IDGenerator<uint32_t>& id_gen)
    : storage_handler_(&handler), id_gen_(&id_gen) {}

void PathResolver::SetRoot(std::shared_ptr<SleeveFolder> root) {
  root_ = std::move(root);
  directory_cache_.Clear();
  if (root_) {
    directory_cache_.Put("/", root_->element_id_);
  }
}

auto PathResolver::Normalize(const std::filesystem::path& raw_path) -> std::string {
  std::string s = raw_path.string();
  // Collapse multiple slashes and strip trailing slash (except root).
  std::string out;
  out.reserve(s.size());
  bool last_slash = false;
  for (char c : s) {
    if (c == '/') {
      if (!last_slash) out.push_back(c);
      last_slash = true;
    } else {
      out.push_back(c);
      last_slash = false;
    }
  }
  if (out.size() > 1 && out.back() == '/') out.pop_back();
  if (out.empty()) out = "/";
  return out;
}

auto PathResolver::SplitPath(const std::string& normalized) -> std::vector<std::string> {
  std::vector<std::string> parts;
  if (normalized.empty() || normalized == "/") return parts;
  std::string::size_type start = 0;
  if (normalized[0] == '/') start = 1;
  std::string::size_type pos = start;
  while (pos <= normalized.size()) {
    if (pos == normalized.size() || normalized[pos] == '/') {
      if (pos > start) parts.emplace_back(normalized.substr(start, pos - start));
      start = pos + 1;
    }
    ++pos;
  }
  return parts;
}

auto PathResolver::IsSubpath(const std::filesystem::path& base, const std::filesystem::path& target)
    -> bool {
  std::string b = Normalize(base);
  std::string t = Normalize(target);
  if (b == "/") return t != "/";
  if (t.size() <= b.size()) return false;
  return t.compare(0, b.size(), b) == 0 && t[b.size()] == '/';
}

auto PathResolver::Contains(const std::filesystem::path& path, ElementType type) -> bool {
  auto elem = Resolve(path);
  return elem && elem->type_ == type;
}

auto PathResolver::Contains(const std::filesystem::path& path) -> bool {
  return Resolve(path) != nullptr;
}

auto PathResolver::Resolve(const std::filesystem::path& path) -> std::shared_ptr<SleeveElement> {
  std::string normalized = Normalize(path);
  if (normalized == "/" || normalized.empty()) return root_;
  if (!root_) return nullptr;

  // Try directory cache for parent.
  auto slash = normalized.find_last_of('/');
  std::string parent_path = (slash == 0) ? "/" : normalized.substr(0, slash);
  std::string child_name  = normalized.substr(slash + 1);

  std::shared_ptr<SleeveFolder> parent = root_;
  if (auto cached = directory_cache_.Get(parent_path)) {
    if (storage_handler_) {
      auto elem = storage_handler_->GetElement(*cached);
      if (elem && elem->type_ == ElementType::FOLDER) {
        parent = std::static_pointer_cast<SleeveFolder>(elem);
      }
    }
  } else {
    // Walk from root.
    auto parts = SplitPath(normalized);
    if (parts.empty()) return root_;
    std::shared_ptr<SleeveFolder> current = root_;
    for (size_t i = 0; i + 1 < parts.size(); ++i) {
      auto id_opt = current->GetElementIdByName(parts[i]);
      if (!id_opt) return nullptr;
      if (storage_handler_) {
        auto elem = storage_handler_->GetElement(*id_opt);
        if (!elem || elem->type_ != ElementType::FOLDER) return nullptr;
        current = std::static_pointer_cast<SleeveFolder>(elem);
      } else {
        return nullptr;
      }
    }
    parent = current;
    directory_cache_.Put(parent_path, parent->element_id_);
  }

  auto child_id_opt = parent->GetElementIdByName(child_name);
  if (!child_id_opt) return nullptr;
  if (storage_handler_) return storage_handler_->GetElement(*child_id_opt);
  return nullptr;
}

auto PathResolver::ResolveForWrite(const std::filesystem::path& path) -> std::shared_ptr<SleeveElement> {
  auto elem = Resolve(path);
  if (elem) elem->SetSyncFlag(SyncFlag::MODIFIED);
  return elem;
}

void PathResolver::Invalidate(const std::filesystem::path& path) {
  std::string normalized = Normalize(path);
  directory_cache_.Erase(normalized);
  // Invalidate descendants too.
  // The LRU cache does not expose iteration; clients should Clear() on major
  // structural changes.
}

auto PathResolver::Tree(const std::filesystem::path& path) -> std::string {
  auto elem = Resolve(path);
  if (!elem) return "<not found>";
  std::ostringstream oss;
  oss << path.string() << " [" << (elem->type_ == ElementType::FOLDER ? "FOLDER" : "FILE")
      << "] id=" << elem->element_id_ << "\n";
  if (elem->type_ == ElementType::FOLDER) {
    auto folder = std::static_pointer_cast<SleeveFolder>(elem);
    for (auto child_id : folder->ListElements()) {
      oss << "  - " << child_id << "\n";
    }
  }
  return oss.str();
}

}  // namespace alcedo
