#pragma once

#include <vector>

namespace alcedo {

class ContrastOperator {
public:
    static void apply(std::vector<float>& pixels, int width, int height, float contrast);
};

} // namespace alcedo
