#pragma once

#include "sleeve_element.h"
#include <cstdint>
#include <string>

namespace alcedo {

class SleeveFile : public SleeveElement {
public:
    uint32_t image_id;
    std::string current_version_id;

    SleeveFile(uint32_t id, const std::string& name)
        : SleeveElement(id, name, TYPE_FILE),
          image_id(0), current_version_id() {}
};

} // namespace alcedo
