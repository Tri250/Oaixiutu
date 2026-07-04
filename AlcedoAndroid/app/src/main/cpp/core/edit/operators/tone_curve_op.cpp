#include "tone_curve_op.h"
#include <algorithm>

namespace alcedo {

void ToneCurveOperator::apply(std::vector<float>& pixels, int width, int height,
                              const float* curve_x, const float* curve_y, int num_points) {
    for (float& p : pixels) {
        p = interpolate_curve(p, curve_x, curve_y, num_points);
    }
}

float ToneCurveOperator::interpolate_curve(float x, const float* xs, const float* ys, int n) {
    if (x <= xs[0]) return ys[0];
    if (x >= xs[n - 1]) return ys[n - 1];
    for (int i = 0; i < n - 1; ++i) {
        if (x >= xs[i] && x <= xs[i + 1]) {
            float t = (x - xs[i]) / (xs[i + 1] - xs[i]);
            return ys[i] + t * (ys[i + 1] - ys[i]);
        }
    }
    return ys[n - 1];
}

} // namespace alcedo
