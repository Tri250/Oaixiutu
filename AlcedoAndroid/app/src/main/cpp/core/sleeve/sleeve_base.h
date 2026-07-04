#pragma once

#include "sleeve_element.h"
#include "sleeve_file.h"
#include "sleeve_folder.h"
#include "dentry_cache_manager.h"
#include "storage_service.h"
#include <cstdint>
#include <string>
#include <memory>
#include <unordered_map>
#include <mutex>

namespace alcedo {

class ElementAccessGuard {
public:
    ElementAccessGuard(std::shared_ptr<SleeveElement> element, bool write);
    ~ElementAccessGuard();

    ElementAccessGuard(const ElementAccessGuard&) = delete;
    ElementAccessGuard& operator=(const ElementAccessGuard&) = delete;
    ElementAccessGuard(ElementAccessGuard&& other) noexcept;
    ElementAccessGuard& operator=(ElementAccessGuard&& other) noexcept;

    SleeveElement* operator->() const;
    std::shared_ptr<SleeveElement> Get() const;
    bool IsWrite() const;

private:
    std::shared_ptr<SleeveElement> element_;
    bool write_;
    bool active_;
};

class SleeveBase {
public:
    SleeveBase();
    ~SleeveBase();

    bool InitializeRoot();

    std::shared_ptr<SleeveElement> AccessElementById(uint32_t id);
    std::shared_ptr<SleeveElement> AccessElementByPath(const std::string& path);

    std::shared_ptr<SleeveElement> CreateElementToPath(
        const std::string& path,
        const std::string& name,
        uint32_t type);

    bool RemoveElementInPath(const std::string& path);
    bool CopyElement(const std::string& src, const std::string& dest);
    bool MoveElement(const std::string& src, const std::string& dest);

    ElementAccessGuard GetReadGuard(const std::string& path);
    ElementAccessGuard GetWriteGuard(const std::string& path);

    DCacheManager& GetDCacheManager();
    StorageService& GetStorageService();

    bool EnsureChildrenLoaded(uint32_t folder_id);

    uint32_t GenerateNextId();

private:
    std::unordered_map<uint32_t, std::shared_ptr<SleeveElement>> storage_;
    DCacheManager dcache_;
    StorageService storage_service_;
    std::mutex mutex_;
    uint32_t next_id_ = 1;

    std::shared_ptr<SleeveElement> InternalCreateElement(
        uint32_t parent_id,
        const std::string& name,
        uint32_t type);

    bool InternalRemoveElement(uint32_t id, bool recursive);
    bool InternalCopyElement(uint32_t src_id, uint32_t dest_parent_id, const std::string& dest_name);
};

} // namespace alcedo
