#pragma once

#include <cstdint>
#include <string>
#include <unordered_map>
#include <list>
#include <optional>

namespace alcedo {

class DCacheManager {
public:
    explicit DCacheManager(size_t capacity = 1024);

    void RecordAccess(const std::string& path, uint32_t id);
    std::optional<uint32_t> AccessElement(const std::string& path);
    void Flush();
    void RemoveRecord(const std::string& path);
    size_t Size() const;

private:
    using ListIter = std::list<std::string>::iterator;

    size_t capacity_;
    std::list<std::string> lru_list_;
    std::unordered_map<std::string, std::pair<uint32_t, ListIter>> cache_;

    void Touch(const std::string& path);
    void EvictIfNeeded();
};

} // namespace alcedo
