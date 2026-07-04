#include "sleeve_base.h"
#include <chrono>

namespace alcedo {

ElementAccessGuard::ElementAccessGuard(std::shared_ptr<SleeveElement> element, bool write)
    : element_(std::move(element)), write_(write), active_(true) {
    if (element_) {
        element_->pinned = true;
        if (write_) {
            element_->sync_flag = SyncFlag::MODIFIED;
        }
    }
}

ElementAccessGuard::~ElementAccessGuard() {
    if (active_ && element_) {
        element_->pinned = false;
        active_ = false;
    }
}

ElementAccessGuard::ElementAccessGuard(ElementAccessGuard&& other) noexcept
    : element_(std::move(other.element_)), write_(other.write_), active_(other.active_) {
    other.active_ = false;
}

ElementAccessGuard& ElementAccessGuard::operator=(ElementAccessGuard&& other) noexcept {
    if (this != &other) {
        if (active_ && element_) {
            element_->pinned = false;
        }
        element_ = std::move(other.element_);
        write_ = other.write_;
        active_ = other.active_;
        other.active_ = false;
    }
    return *this;
}

SleeveElement* ElementAccessGuard::operator->() const {
    return element_.get();
}

std::shared_ptr<SleeveElement> ElementAccessGuard::Get() const {
    return element_;
}

bool ElementAccessGuard::IsWrite() const {
    return write_;
}

SleeveBase::SleeveBase() = default;
SleeveBase::~SleeveBase() = default;

bool SleeveBase::InitializeRoot() {
    std::lock_guard<std::mutex> lock(mutex_);
    if (storage_.find(1) != storage_.end()) {
        return false;
    }

    auto root = std::make_shared<SleeveFolder>(1, "");
    root->parent_id = 0;
    root->children_loaded = true;
    auto now = std::chrono::steady_clock::now().time_since_epoch().count();
    root->added_time = static_cast<uint64_t>(now);
    root->last_modified_time = static_cast<uint64_t>(now);

    storage_[1] = root;
    next_id_ = 2;
    dcache_.RecordAccess("/", 1);
    storage_service_.RegisterElement(1, root);
    return true;
}

std::shared_ptr<SleeveElement> SleeveBase::AccessElementById(uint32_t id) {
    std::lock_guard<std::mutex> lock(mutex_);
    auto it = storage_.find(id);
    if (it != storage_.end()) {
        return it->second;
    }
    auto from_storage = storage_service_.GetElement(id);
    if (from_storage) {
        storage_[id] = from_storage;
        return from_storage;
    }
    return nullptr;
}

std::shared_ptr<SleeveElement> SleeveBase::AccessElementByPath(const std::string& path) {
    if (path.empty() || path == "/") {
        return AccessElementById(1);
    }

    auto cached = dcache_.AccessElement(path);
    if (cached.has_value()) {
        auto elem = AccessElementById(cached.value());
        if (elem) {
            return elem;
        }
    }

    std::vector<std::string> parts;
    size_t start = 1;
    for (size_t i = 1; i <= path.size(); ++i) {
        if (i == path.size() || path[i] == '/') {
            if (i > start) {
                std::string part = path.substr(start, i - start);
                if (part != "." && part != "") {
                    if (part == "..") {
                        if (!parts.empty()) {
                            parts.pop_back();
                        }
                    } else {
                        parts.push_back(part);
                    }
                }
            }
            start = i + 1;
        }
    }

    auto current = AccessElementById(1);
    if (!current) {
        return nullptr;
    }

    for (const auto& part : parts) {
        if (!current->IsFolder()) {
            return nullptr;
        }
        auto folder = std::static_pointer_cast<SleeveFolder>(current);
        if (!folder->children_loaded) {
            EnsureChildrenLoaded(folder->element_id);
        }
        auto it = folder->contents.find(part);
        if (it == folder->contents.end()) {
            return nullptr;
        }
        current = AccessElementById(it->second);
        if (!current) {
            return nullptr;
        }
    }

    if (current) {
        dcache_.RecordAccess(path, current->element_id);
    }
    return current;
}

std::shared_ptr<SleeveElement> SleeveBase::CreateElementToPath(
    const std::string& path,
    const std::string& name,
    uint32_t type) {
    std::lock_guard<std::mutex> lock(mutex_);

    auto parent = AccessElementByPath(path);
    if (!parent || !parent->IsFolder()) {
        return nullptr;
    }

    auto folder = std::static_pointer_cast<SleeveFolder>(parent);
    if (!folder->children_loaded) {
        EnsureChildrenLoaded(folder->element_id);
    }

    if (folder->contents.find(name) != folder->contents.end()) {
        return nullptr;
    }

    return InternalCreateElement(folder->element_id, name, type);
}

bool SleeveBase::RemoveElementInPath(const std::string& path) {
    std::lock_guard<std::mutex> lock(mutex_);

    if (path == "/" || path.empty()) {
        return false;
    }

    auto elem = AccessElementByPath(path);
    if (!elem) {
        return false;
    }

    return InternalRemoveElement(elem->element_id, true);
}

bool SleeveBase::CopyElement(const std::string& src, const std::string& dest) {
    std::lock_guard<std::mutex> lock(mutex_);

    if (src == dest || src == "/" || dest == "/") {
        return false;
    }

    auto src_elem = AccessElementByPath(src);
    if (!src_elem) {
        return false;
    }

    std::string dest_parent_path;
    std::string dest_name;
    size_t last_slash = dest.find_last_of('/');
    if (last_slash == std::string::npos) {
        return false;
    }
    dest_parent_path = dest.substr(0, last_slash);
    if (dest_parent_path.empty()) {
        dest_parent_path = "/";
    }
    dest_name = dest.substr(last_slash + 1);

    auto dest_parent = AccessElementByPath(dest_parent_path);
    if (!dest_parent || !dest_parent->IsFolder()) {
        return false;
    }

    auto dest_folder = std::static_pointer_cast<SleeveFolder>(dest_parent);
    if (!dest_folder->children_loaded) {
        EnsureChildrenLoaded(dest_folder->element_id);
    }

    return InternalCopyElement(src_elem->element_id, dest_folder->element_id, dest_name);
}

bool SleeveBase::MoveElement(const std::string& src, const std::string& dest) {
    if (!CopyElement(src, dest)) {
        return false;
    }
    return RemoveElementInPath(src);
}

ElementAccessGuard SleeveBase::GetReadGuard(const std::string& path) {
    auto elem = AccessElementByPath(path);
    if (!elem) {
        return ElementAccessGuard(nullptr, false);
    }
    return ElementAccessGuard(elem, false);
}

ElementAccessGuard SleeveBase::GetWriteGuard(const std::string& path) {
    auto elem = AccessElementByPath(path);
    if (!elem) {
        return ElementAccessGuard(nullptr, true);
    }
    return ElementAccessGuard(elem, true);
}

DCacheManager& SleeveBase::GetDCacheManager() {
    return dcache_;
}

StorageService& SleeveBase::GetStorageService() {
    return storage_service_;
}

bool SleeveBase::EnsureChildrenLoaded(uint32_t folder_id) {
    return storage_service_.EnsureChildrenLoaded(folder_id);
}

uint32_t SleeveBase::GenerateNextId() {
    std::lock_guard<std::mutex> lock(mutex_);
    return next_id_++;
}

std::shared_ptr<SleeveElement> SleeveBase::InternalCreateElement(
    uint32_t parent_id,
    const std::string& name,
    uint32_t type) {
    uint32_t id = next_id_++;
    std::shared_ptr<SleeveElement> elem;

    if (type == SleeveElement::TYPE_FILE) {
        elem = std::make_shared<SleeveFile>(id, name);
    } else if (type == SleeveElement::TYPE_FOLDER) {
        elem = std::make_shared<SleeveFolder>(id, name);
        auto folder = std::static_pointer_cast<SleeveFolder>(elem);
        folder->children_loaded = true;
    } else {
        return nullptr;
    }

    auto now = std::chrono::steady_clock::now().time_since_epoch().count();
    elem->added_time = static_cast<uint64_t>(now);
    elem->last_modified_time = static_cast<uint64_t>(now);
    elem->parent_id = parent_id;
    elem->sync_flag = SyncFlag::MODIFIED;

    storage_[id] = elem;
    storage_service_.RegisterElement(id, elem);

    auto parent = storage_.find(parent_id);
    if (parent != storage_.end() && parent->second->IsFolder()) {
        auto parent_folder = std::static_pointer_cast<SleeveFolder>(parent->second);
        parent_folder->AddChild(name, id, elem->IsFile());
        parent_folder->last_modified_time = static_cast<uint64_t>(now);
    }

    return elem;
}

bool SleeveBase::InternalRemoveElement(uint32_t id, bool recursive) {
    auto it = storage_.find(id);
    if (it == storage_.end()) {
        return false;
    }

    auto elem = it->second;
    if (elem->pinned) {
        return false;
    }

    if (elem->IsFolder()) {
        auto folder = std::static_pointer_cast<SleeveFolder>(elem);
        if (recursive) {
            for (const auto& pair : folder->contents) {
                InternalRemoveElement(pair.second, true);
            }
        } else if (!folder->contents.empty()) {
            return false;
        }
    }

    if (elem->parent_id != 0) {
        auto parent_it = storage_.find(elem->parent_id);
        if (parent_it != storage_.end() && parent_it->second->IsFolder()) {
            auto parent_folder = std::static_pointer_cast<SleeveFolder>(parent_it->second);
            parent_folder->RemoveChild(elem->element_name, elem->IsFile());
            auto now = std::chrono::steady_clock::now().time_since_epoch().count();
            parent_folder->last_modified_time = static_cast<uint64_t>(now);
        }
    }

    elem->sync_flag = SyncFlag::DELETED;
    storage_.erase(it);
    storage_service_.UnregisterElement(id);
    dcache_.RemoveRecord("/" + elem->element_name);

    return true;
}

bool SleeveBase::InternalCopyElement(uint32_t src_id, uint32_t dest_parent_id, const std::string& dest_name) {
    auto src_it = storage_.find(src_id);
    if (src_it == storage_.end()) {
        return false;
    }

    auto src_elem = src_it->second;
    uint32_t new_id = next_id_++;
    std::shared_ptr<SleeveElement> new_elem;

    if (src_elem->IsFile()) {
        auto src_file = std::static_pointer_cast<SleeveFile>(src_elem);
        auto new_file = std::make_shared<SleeveFile>(new_id, dest_name);
        new_file->image_id = src_file->image_id;
        new_file->current_version_id = src_file->current_version_id;
        new_elem = new_file;
    } else {
        auto src_folder = std::static_pointer_cast<SleeveFolder>(src_elem);
        auto new_folder = std::make_shared<SleeveFolder>(new_id, dest_name);
        new_folder->children_loaded = true;
        new_elem = new_folder;
    }

    auto now = std::chrono::steady_clock::now().time_since_epoch().count();
    new_elem->added_time = static_cast<uint64_t>(now);
    new_elem->last_modified_time = static_cast<uint64_t>(now);
    new_elem->parent_id = dest_parent_id;
    new_elem->sync_flag = SyncFlag::MODIFIED;

    storage_[new_id] = new_elem;
    storage_service_.RegisterElement(new_id, new_elem);

    auto dest_parent_it = storage_.find(dest_parent_id);
    if (dest_parent_it != storage_.end() && dest_parent_it->second->IsFolder()) {
        auto dest_folder = std::static_pointer_cast<SleeveFolder>(dest_parent_it->second);
        dest_folder->AddChild(dest_name, new_id, new_elem->IsFile());
        dest_folder->last_modified_time = static_cast<uint64_t>(now);
    }

    if (src_elem->IsFolder()) {
        auto src_folder = std::static_pointer_cast<SleeveFolder>(src_elem);
        auto new_folder = std::static_pointer_cast<SleeveFolder>(new_elem);
        for (const auto& pair : src_folder->contents) {
            auto child = storage_.find(pair.second);
            if (child != storage_.end()) {
                InternalCopyElement(pair.second, new_id, child->second->element_name);
            }
        }
    }

    return true;
}

} // namespace alcedo
