#include "tone_curve_op.h"
#include <algorithm>

namespace alcedo {

void ToneCurveOperator::apply(std::vector<float>& pixels, int width, int height,
                              const float* curve_x, const float* curve_y, int num_points) {
    if (num_points <= 0 || !curve_x || !curve_y) return;
    int total = width * height;
    if (total <= 0) return;
    int channels = static_cast<int>(pixels.size()) / total;
    if (channels <= 0) return;
    // Apply curve only to RGB channels (skip alpha if present)
    int colorChannels = std::min(channels, 3);
    for (int i = 0; i < total; ++i) {
        int idx = i * channels;
        for (int c = 0; c < colorChannels; ++c) {
            pixels[idx + c] = interpolate_curve(pixels[idx + c], curve_x, curve_y, num_points);
        }
    }
}

float ToneCurveOperator::interpolate_curve(float x, const float* xs, const float* ys, int n) {
    if (n <= 0 || !xs || !ys) return x;
    if (n == 1) return ys[0];
    if (x <= xs[0]) return ys[0];
    if (x >= xs[n - 1]) return ys[n - 1];
    for (int i = 0; i < n - 1; ++i) {
        if (x >= xs[i] && x <= xs[i + 1]) {
            float denom = xs[i + 1] - xs[i];
            if (denom < 1e-10f) return ys[i]; // avoid division by zero for duplicate x values
            float t = (x - xs[i]) / denom;
            return ys[i] + t * (ys[i + 1] - ys[i]);
        }
    }
    return ys[n - 1];
}

} // namespace alcedo
