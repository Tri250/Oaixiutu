#include "black_op.h"
#include <algorithm>

namespace alcedo {

void BlackOperator::apply(float* pixels, int count, int channels, float black_point) {
    if (!pixels || count <= 0 || channels <= 0) return;
    if (black_point <= 0.0f) return;
    if (black_point >= 1.0f) {
        // Only zero out RGB channels
        int colorChannels = std::min(channels, 3);
        for (int i = 0; i < count; ++i) {
            int idx = i * channels;
            for (int c = 0; c < colorChannels; ++c) {
                pixels[idx + c] = 0.0f;
            }
        }
        return;
    }

    float scale = 1.0f / (1.0f - black_point);
    int colorChannels = std::min(channels, 3);
    for (int i = 0; i < count; ++i) {
        int idx = i * channels;
        for (int c = 0; c < colorChannels; ++c) {
            float v = pixels[idx + c];
            if (v <= black_point) {
                pixels[idx + c] = 0.0f;
            } else {
                pixels[idx + c] = std::clamp((v - black_point) * scale, 0.0f, 1.0f);
            }
        }
    }
}

} // namespace alcedo
