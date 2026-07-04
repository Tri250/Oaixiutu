#pragma once

#include <vector>

namespace alcedo {

class WhiteBalanceOperator {
public:
    static void apply(std::vector<float>& pixels, int width, int height,
                      float temperature, float tint);
};

} // namespace alcedo
