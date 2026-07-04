#pragma once

#include "sleeve_element.h"
#include "sleeve_file.h"
#include "sleeve_folder.h"
#include <cstdint>
#include <string>
#include <memory>
#include <unordered_map>
#include <vector>
#include <functional>

namespace alcedo {

class StorageService {
public:
    StorageService();
    ~StorageService();

    std::shared_ptr<SleeveElement> GetElement(uint32_t id);
    std::shared_ptr<SleeveElement> GetElement(const std::string& path);

    void RegisterElement(uint32_t id, std::shared_ptr<SleeveElement> element);
    void UnregisterElement(uint32_t id);

    bool EnsureChildrenLoaded(uint32_t folder_id);
    bool EnsureChildrenLoaded(const std::string& path);

    void GarbageCollect();

    size_t GetMemoryElementCount() const;

    using LoadChildrenCallback = std::function<std::vector<std::shared_ptr<SleeveElement>>(uint32_t folder_id)>;
    void SetLoadChildrenCallback(LoadChildrenCallback callback);

    using PersistCallback = std::function<void(const std::shared_ptr<SleeveElement>&)>;
    void SetPersistCallback(PersistCallback callback);

private:
    std::unordered_map<uint32_t, std::weak_ptr<SleeveElement>> memory_storage_;
    LoadChildrenCallback load_children_callback_;
    PersistCallback persist_callback_;
};

} // namespace alcedo
