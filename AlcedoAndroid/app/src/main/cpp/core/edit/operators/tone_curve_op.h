#pragma once

#include <vector>

namespace alcedo {

class ToneCurveOperator {
public:
    static void apply(std::vector<float>& pixels, int width, int height,
                      const float* curve_x, const float* curve_y, int num_points);

private:
    static float interpolate_curve(float x, const float* xs, const float* ys, int n);
};

} // namespace alcedo
