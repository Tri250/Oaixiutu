#pragma once

#include <vector>

namespace alcedo {

class SharpenOperator {
public:
    static void apply_rgb(std::vector<float>& pixels, int width, int height, float amount);
};

} // namespace alcedo
