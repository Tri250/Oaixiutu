#include "sleeve_manager.h"

namespace alcedo {

SleeveManager::SleeveManager() = default;
SleeveManager::~SleeveManager() = default;

bool SleeveManager::Initialize() {
    sleeve_base_ = std::make_shared<SleeveBase>();
    if (!sleeve_base_->InitializeRoot()) {
        return false;
    }
    filesystem_ = std::make_shared<FileSystem>(sleeve_base_);
    return true;
}

std::shared_ptr<FileSystem> SleeveManager::GetFilesystem() {
    return filesystem_;
}

std::shared_ptr<SleeveBase> SleeveManager::GetSleeveBase() {
    return sleeve_base_;
}

} // namespace alcedo
