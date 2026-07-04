#pragma once

#include <vector>

namespace alcedo {

class ExposureOperator {
public:
    static void apply(std::vector<float>& pixels, int width, int height, float exposure_stops);
};

} // namespace alcedo
