#pragma once

#include <algorithm>
#include <vector>

namespace alcedo {

class SaturationOperator {
public:
    static void apply_rgb(std::vector<float>& pixels, int width, int height, float saturation);
};

} // namespace alcedo
