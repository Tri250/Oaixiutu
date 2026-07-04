#include <cstdint>
#include <string>

namespace alcedo {

class SleeveElement {
public:
    uint32_t element_id;
    std::string element_name;
    uint32_t element_type;
    uint64_t added_time;
    uint64_t last_modified_time;
    uint32_t ref_count;
    bool pinned;
    uint32_t sync_flag;

    SleeveElement(uint32_t id, const std::string& name, uint32_t type)
        : element_id(id), element_name(name), element_type(type),
          added_time(0), last_modified_time(0), ref_count(0),
          pinned(false), sync_flag(0) {}
};

} // namespace alcedo
