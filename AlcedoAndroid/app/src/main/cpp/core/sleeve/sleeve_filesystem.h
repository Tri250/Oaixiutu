#pragma once

#include "sleeve_base.h"
#include "sleeve_element.h"
#include <cstdint>
#include <string>
#include <memory>
#include <vector>
#include <map>

namespace alcedo {

struct FolderEntry {
    std::string name;
    uint32_t element_id;
    uint32_t element_type;
    SyncFlag sync_flag;
};

class FileSystem {
public:
    explicit FileSystem(std::shared_ptr<SleeveBase> sleeve_base);
    ~FileSystem();

    std::shared_ptr<SleeveElement> Get(const std::string& path);

    std::shared_ptr<SleeveElement> Create(
        const std::string& parent_path,
        const std::string& name,
        uint32_t type);

    bool Delete(const std::string& path);
    bool Copy(const std::string& src, const std::string& dest);
    bool Move(const std::string& src, const std::string& dest);

    std::vector<FolderEntry> ListFolderContent(const std::string& path);

    SyncFlag GetSyncFlag(const std::string& path);
    bool SetSyncFlag(const std::string& path, SyncFlag flag);

    bool Exists(const std::string& path);
    bool IsFile(const std::string& path);
    bool IsFolder(const std::string& path);

private:
    std::shared_ptr<SleeveBase> sleeve_base_;
};

} // namespace alcedo
