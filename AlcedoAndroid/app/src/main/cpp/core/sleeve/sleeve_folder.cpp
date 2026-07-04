#include <cstdint>
#include <string>
#include <map>

namespace alcedo {

struct SleeveFolder {
    uint32_t element_id;
    std::string element_name;
    std::map<std::string, uint32_t> contents;
    uint32_t file_count = 0;
    uint32_t folder_count = 0;
    bool children_loaded = false;
};

} // namespace alcedo
