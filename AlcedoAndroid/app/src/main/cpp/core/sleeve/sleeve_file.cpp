#include <cstdint>
#include <string>

namespace alcedo {

struct SleeveFile {
    uint32_t element_id;
    std::string element_name;
    uint32_t image_id;
    std::string current_version_id;
};

} // namespace alcedo
