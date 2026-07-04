#include "path_resolver.h"
#include "sleeve_base.h"
#include <algorithm>
#include <sstream>

namespace alcedo {

PathResolver::PathResolver(SleeveBase* sleeve_base) : sleeve_base_(sleeve_base) {}

std::shared_ptr<SleeveElement> PathResolver::Resolve(const std::string& path) {
    std::string normalized = NormalizePath(path);
    if (normalized.empty() || normalized == "/") {
        return sleeve_base_->AccessElementById(1);
    }

    auto cached = cache_.AccessElement(normalized);
    if (cached.has_value()) {
        auto elem = sleeve_base_->AccessElementById(cached.value());
        if (elem) {
            return elem;
        }
    }

    auto parts = SplitPath(normalized);
    auto result = ResolveFromRoot(parts);
    if (result) {
        cache_.RecordAccess(normalized, result->element_id);
    }
    return result;
}

std::shared_ptr<SleeveElement> PathResolver::ResolveParent(const std::string& path, std::string& out_name) {
    auto parts = SplitPath(NormalizePath(path));
    if (parts.empty()) {
        return nullptr;
    }

    out_name = parts.back();
    parts.pop_back();

    if (parts.empty()) {
        return sleeve_base_->AccessElementById(1);
    }

    return ResolveFromRoot(parts);
}

bool PathResolver::IsValidPath(const std::string& path) const {
    if (path.empty()) {
        return false;
    }
    if (path[0] != '/') {
        return false;
    }
    for (size_t i = 1; i < path.size(); ++i) {
        if (path[i] == '\0') {
            return false;
        }
    }
    return true;
}

bool PathResolver::IsSubPath(const std::string& parent, const std::string& child) const {
    std::string p = NormalizePath(parent);
    std::string c = NormalizePath(child);
    if (p == c) {
        return true;
    }
    if (p == "/") {
        return true;
    }
    if (c.size() <= p.size()) {
        return false;
    }
    if (c.compare(0, p.size(), p) == 0 && c[p.size()] == '/') {
        return true;
    }
    return false;
}

std::vector<std::string> PathResolver::SplitPath(const std::string& path) const {
    std::vector<std::string> parts;
    std::stringstream ss(path);
    std::string part;
    while (std::getline(ss, part, '/')) {
        if (!part.empty() && part != ".") {
            if (part == "..") {
                if (!parts.empty()) {
                    parts.pop_back();
                }
            } else {
                parts.push_back(part);
            }
        }
    }
    return parts;
}

std::string PathResolver::JoinPath(const std::vector<std::string>& parts) const {
    if (parts.empty()) {
        return "/";
    }
    std::string result;
    for (const auto& part : parts) {
        result += "/";
        result += part;
    }
    return result;
}

std::string PathResolver::NormalizePath(const std::string& path) const {
    auto parts = SplitPath(path);
    return JoinPath(parts);
}

void PathResolver::InvalidateCache(const std::string& path) {
    cache_.RemoveRecord(NormalizePath(path));
}

void PathResolver::InvalidateCacheRecursive(const std::string& path) {
    std::string normalized = NormalizePath(path);
    cache_.RemoveRecord(normalized);
}

DCacheManager& PathResolver::GetCache() {
    return cache_;
}

std::shared_ptr<SleeveElement> PathResolver::ResolveFromRoot(const std::vector<std::string>& parts) {
    auto current = sleeve_base_->AccessElementById(1);
    if (!current) {
        return nullptr;
    }

    for (const auto& part : parts) {
        if (!current->IsFolder()) {
            return nullptr;
        }
        auto folder = std::static_pointer_cast<SleeveFolder>(current);
        if (!folder->children_loaded) {
            sleeve_base_->EnsureChildrenLoaded(folder->element_id);
        }
        auto it = folder->contents.find(part);
        if (it == folder->contents.end()) {
            return nullptr;
        }
        current = sleeve_base_->AccessElementById(it->second);
        if (!current) {
            return nullptr;
        }
    }
    return current;
}

} // namespace alcedo
