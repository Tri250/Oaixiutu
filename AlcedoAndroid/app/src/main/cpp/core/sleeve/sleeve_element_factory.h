#pragma once

#include <cstdint>
#include <memory>
#include <string>
#include "sleeve_element.h"

namespace alcedo {

class SleeveElementFactory {
public:
    static std::shared_ptr<SleeveElement> CreateElement(uint32_t type, uint32_t id,
                                                        const std::string& name);
};

} // namespace alcedo
