#include "dentry_cache_manager.h"

namespace alcedo {

DCacheManager::DCacheManager(size_t capacity) : capacity_(capacity) {}

void DCacheManager::RecordAccess(const std::string& path, uint32_t id) {
    auto it = cache_.find(path);
    if (it != cache_.end()) {
        it->second.first = id;
        Touch(path);
        return;
    }

    EvictIfNeeded();
    lru_list_.push_front(path);
    cache_[path] = {id, lru_list_.begin()};
}

std::optional<uint32_t> DCacheManager::AccessElement(const std::string& path) {
    auto it = cache_.find(path);
    if (it == cache_.end()) {
        return std::nullopt;
    }
    Touch(path);
    return it->second.first;
}

void DCacheManager::Flush() {
    cache_.clear();
    lru_list_.clear();
}

void DCacheManager::RemoveRecord(const std::string& path) {
    auto it = cache_.find(path);
    if (it != cache_.end()) {
        lru_list_.erase(it->second.second);
        cache_.erase(it);
    }
}

size_t DCacheManager::Size() const {
    return cache_.size();
}

void DCacheManager::Touch(const std::string& path) {
    auto it = cache_.find(path);
    if (it != cache_.end()) {
        lru_list_.erase(it->second.second);
        lru_list_.push_front(path);
        it->second.second = lru_list_.begin();
    }
}

void DCacheManager::EvictIfNeeded() {
    if (cache_.size() >= capacity_ && !lru_list_.empty()) {
        const std::string& oldest = lru_list_.back();
        cache_.erase(oldest);
        lru_list_.pop_back();
    }
}

} // namespace alcedo
