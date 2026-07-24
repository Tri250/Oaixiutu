#include "contrast_op.h"
#include <algorithm>

namespace alcedo {

void ContrastOperator::apply(std::vector<float>& pixels, int width, int height, float contrast) {
    if (pixels.empty()) return;
    float scale = 1.0f + contrast;
    float offset = -0.5f * scale + 0.5f;
    size_t total = static_cast<size_t>(width) * height;
    if (total == 0) return;
    int channels = static_cast<int>(pixels.size()) / static_cast<int>(total);
    // Only modify RGB channels (skip alpha if present)
    int colorChannels = std::min(std::max(channels, 1), 3);
    for (size_t i = 0; i < total; ++i) {
        size_t idx = i * channels;
        for (int c = 0; c < colorChannels; ++c) {
            pixels[idx + c] = std::clamp(pixels[idx + c] * scale + offset, 0.0f, 1.0f);
        }
    }
}

} // namespace alcedo
