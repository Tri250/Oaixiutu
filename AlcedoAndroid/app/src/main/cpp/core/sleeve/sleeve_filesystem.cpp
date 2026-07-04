#include "sleeve_filesystem.h"

namespace alcedo {

FileSystem::FileSystem(std::shared_ptr<SleeveBase> sleeve_base)
    : sleeve_base_(std::move(sleeve_base)) {}

FileSystem::~FileSystem() = default;

std::shared_ptr<SleeveElement> FileSystem::Get(const std::string& path) {
    if (!sleeve_base_) {
        return nullptr;
    }
    return sleeve_base_->AccessElementByPath(path);
}

std::shared_ptr<SleeveElement> FileSystem::Create(
    const std::string& parent_path,
    const std::string& name,
    uint32_t type) {
    if (!sleeve_base_) {
        return nullptr;
    }
    return sleeve_base_->CreateElementToPath(parent_path, name, type);
}

bool FileSystem::Delete(const std::string& path) {
    if (!sleeve_base_) {
        return false;
    }
    return sleeve_base_->RemoveElementInPath(path);
}

bool FileSystem::Copy(const std::string& src, const std::string& dest) {
    if (!sleeve_base_) {
        return false;
    }
    return sleeve_base_->CopyElement(src, dest);
}

bool FileSystem::Move(const std::string& src, const std::string& dest) {
    if (!sleeve_base_) {
        return false;
    }
    return sleeve_base_->MoveElement(src, dest);
}

std::vector<FolderEntry> FileSystem::ListFolderContent(const std::string& path) {
    std::vector<FolderEntry> result;
    if (!sleeve_base_) {
        return result;
    }

    auto elem = sleeve_base_->AccessElementByPath(path);
    if (!elem || !elem->IsFolder()) {
        return result;
    }

    auto folder = std::static_pointer_cast<SleeveFolder>(elem);
    if (!folder->children_loaded) {
        sleeve_base_->EnsureChildrenLoaded(folder->element_id);
    }

    for (const auto& pair : folder->contents) {
        auto child = sleeve_base_->AccessElementById(pair.second);
        if (child) {
            FolderEntry entry;
            entry.name = pair.first;
            entry.element_id = child->element_id;
            entry.element_type = child->element_type;
            entry.sync_flag = child->sync_flag;
            result.push_back(entry);
        }
    }

    return result;
}

SyncFlag FileSystem::GetSyncFlag(const std::string& path) {
    if (!sleeve_base_) {
        return SyncFlag::UNSYNC;
    }
    auto elem = sleeve_base_->AccessElementByPath(path);
    if (!elem) {
        return SyncFlag::UNSYNC;
    }
    return elem->sync_flag;
}

bool FileSystem::SetSyncFlag(const std::string& path, SyncFlag flag) {
    if (!sleeve_base_) {
        return false;
    }
    auto elem = sleeve_base_->AccessElementByPath(path);
    if (!elem) {
        return false;
    }
    elem->sync_flag = flag;
    return true;
}

bool FileSystem::Exists(const std::string& path) {
    return Get(path) != nullptr;
}

bool FileSystem::IsFile(const std::string& path) {
    auto elem = Get(path);
    return elem != nullptr && elem->IsFile();
}

bool FileSystem::IsFolder(const std::string& path) {
    auto elem = Get(path);
    return elem != nullptr && elem->IsFolder();
}

} // namespace alcedo
