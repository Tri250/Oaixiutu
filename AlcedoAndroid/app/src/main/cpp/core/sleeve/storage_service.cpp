#include "storage_service.h"

namespace alcedo {

StorageService::StorageService() = default;
StorageService::~StorageService() = default;

std::shared_ptr<SleeveElement> StorageService::GetElement(uint32_t id) {
    auto it = memory_storage_.find(id);
    if (it != memory_storage_.end()) {
        return it->second.lock();
    }
    return nullptr;
}

std::shared_ptr<SleeveElement> StorageService::GetElement(const std::string& path) {
    (void)path;
    return nullptr;
}

void StorageService::RegisterElement(uint32_t id, std::shared_ptr<SleeveElement> element) {
    if (element) {
        memory_storage_[id] = element;
    }
}

void StorageService::UnregisterElement(uint32_t id) {
    memory_storage_.erase(id);
}

bool StorageService::EnsureChildrenLoaded(uint32_t folder_id) {
    auto folder_elem = GetElement(folder_id);
    if (!folder_elem || !folder_elem->IsFolder()) {
        return false;
    }

    auto folder = std::static_pointer_cast<SleeveFolder>(folder_elem);
    if (folder->children_loaded) {
        return true;
    }

    if (load_children_callback_) {
        auto children = load_children_callback_(folder_id);
        for (const auto& child : children) {
            if (child) {
                child->parent_id = folder_id;
                folder->contents[child->element_name] = child->element_id;
                RegisterElement(child->element_id, child);
                if (child->IsFile()) {
                    folder->file_count++;
                } else {
                    folder->folder_count++;
                }
            }
        }
        folder->children_loaded = true;
        return true;
    }

    return false;
}

bool StorageService::EnsureChildrenLoaded(const std::string& path) {
    (void)path;
    return false;
}

void StorageService::GarbageCollect() {
    for (auto it = memory_storage_.begin(); it != memory_storage_.end();) {
        if (it->second.expired()) {
            it = memory_storage_.erase(it);
        } else {
            ++it;
        }
    }
}

size_t StorageService::GetMemoryElementCount() const {
    return memory_storage_.size();
}

void StorageService::SetLoadChildrenCallback(LoadChildrenCallback callback) {
    load_children_callback_ = std::move(callback);
}

void StorageService::SetPersistCallback(PersistCallback callback) {
    persist_callback_ = std::move(callback);
}

} // namespace alcedo
