#include "exposure_op.h"
#include <cmath>

namespace alcedo {

void ExposureOperator::apply(std::vector<float>& pixels, int width, int height, float exposure_stops) {
    if (pixels.empty()) return;
    float scale = std::pow(2.0f, exposure_stops);
    size_t total = static_cast<size_t>(width) * height;
    if (total == 0) return;
    int channels = static_cast<int>(pixels.size()) / static_cast<int>(total);
    // Only modify RGB channels (skip alpha if present)
    int colorChannels = std::min(std::max(channels, 1), 3);

    for (size_t i = 0; i < total; ++i) {
        size_t idx = i * channels;
        for (int c = 0; c < colorChannels; ++c) {
            pixels[idx + c] = std::clamp(pixels[idx + c] * scale, 0.0f, 1.0f);
        }
    }
}

} // namespace alcedo
