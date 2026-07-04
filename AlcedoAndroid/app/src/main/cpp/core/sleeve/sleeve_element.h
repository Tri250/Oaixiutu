#pragma once

#include <cstdint>
#include <string>

namespace alcedo {

enum class SyncFlag : uint32_t {
    UNSYNC = 0,
    MODIFIED = 1,
    DELETED = 2,
    SYNCED = 3
};

class SleeveElement {
public:
    uint32_t element_id;
    std::string element_name;
    uint32_t element_type;
    uint64_t added_time;
    uint64_t last_modified_time;
    uint32_t ref_count;
    bool pinned;
    SyncFlag sync_flag;
    uint32_t parent_id;

    static constexpr uint32_t TYPE_FILE = 1;
    static constexpr uint32_t TYPE_FOLDER = 2;

    SleeveElement(uint32_t id, const std::string& name, uint32_t type)
        : element_id(id), element_name(name), element_type(type),
          added_time(0), last_modified_time(0), ref_count(0),
          pinned(false), sync_flag(SyncFlag::UNSYNC), parent_id(0) {}

    virtual ~SleeveElement() = default;

    bool IsFile() const { return element_type == TYPE_FILE; }
    bool IsFolder() const { return element_type == TYPE_FOLDER; }
};

} // namespace alcedo
