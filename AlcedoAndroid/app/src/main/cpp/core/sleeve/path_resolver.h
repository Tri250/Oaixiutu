#pragma once

#include "sleeve_element.h"
#include "dentry_cache_manager.h"
#include <cstdint>
#include <string>
#include <memory>
#include <vector>
#include <unordered_set>

namespace alcedo {

class SleeveBase;

class PathResolver {
public:
    explicit PathResolver(SleeveBase* sleeve_base);

    std::shared_ptr<SleeveElement> Resolve(const std::string& path);
    std::shared_ptr<SleeveElement> ResolveParent(const std::string& path, std::string& out_name);

    bool IsValidPath(const std::string& path) const;
    bool IsSubPath(const std::string& parent, const std::string& child) const;

    std::vector<std::string> SplitPath(const std::string& path) const;
    std::string JoinPath(const std::vector<std::string>& parts) const;
    std::string NormalizePath(const std::string& path) const;

    void InvalidateCache(const std::string& path);
    void InvalidateCacheRecursive(const std::string& path);

    DCacheManager& GetCache();

private:
    SleeveBase* sleeve_base_;
    DCacheManager cache_;

    std::shared_ptr<SleeveElement> ResolveFromRoot(const std::vector<std::string>& parts);
};

} // namespace alcedo
