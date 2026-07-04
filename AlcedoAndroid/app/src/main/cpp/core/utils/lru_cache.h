#pragma once

#include <cstddef>
#include <list>
#include <mutex>
#include <optional>
#include <unordered_map>

namespace alcedo {

// ============================================================
// Thread-safe LRU Cache
// ============================================================
template <typename Key, typename Value>
class LRUCache {
public:
    explicit LRUCache(size_t capacity) : capacity_(capacity) {}

    void put(const Key& key, const Value& value) {
        std::lock_guard<std::mutex> lock(mutex_);
        auto it = map_.find(key);
        if (it != map_.end()) {
            // Update existing value and move to front.
            it->second->second = value;
            list_.splice(list_.begin(), list_, it->second);
            return;
        }

        if (list_.size() >= capacity_) {
            // Evict the least recently used item.
            const auto& lru_key = list_.back().first;
            map_.erase(lru_key);
            list_.pop_back();
        }

        list_.emplace_front(key, value);
        map_[key] = list_.begin();
    }

    std::optional<Value> get(const Key& key) {
        std::lock_guard<std::mutex> lock(mutex_);
        auto it = map_.find(key);
        if (it == map_.end()) {
            return std::nullopt;
        }
        // Move accessed item to the front.
        list_.splice(list_.begin(), list_, it->second);
        return it->second->second;
    }

    bool contains(const Key& key) const {
        std::lock_guard<std::mutex> lock(mutex_);
        return map_.find(key) != map_.end();
    }

    bool remove(const Key& key) {
        std::lock_guard<std::mutex> lock(mutex_);
        auto it = map_.find(key);
        if (it == map_.end()) {
            return false;
        }
        list_.erase(it->second);
        map_.erase(it);
        return true;
    }

    void clear() {
        std::lock_guard<std::mutex> lock(mutex_);
        list_.clear();
        map_.clear();
    }

    size_t size() const {
        std::lock_guard<std::mutex> lock(mutex_);
        return list_.size();
    }

    size_t capacity() const {
        std::lock_guard<std::mutex> lock(mutex_);
        return capacity_;
    }

private:
    size_t capacity_;
    std::list<std::pair<Key, Value>> list_;
    std::unordered_map<Key, typename std::list<std::pair<Key, Value>>::iterator> map_;
    mutable std::mutex mutex_;
};

} // namespace alcedo
