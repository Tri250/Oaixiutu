#include "white_op.h"
#include <algorithm>

namespace alcedo {

void WhiteOperator::apply(float* pixels, int count, int channels, float white_point) {
    if (!pixels || count <= 0 || channels <= 0) return;
    if (white_point >= 1.0f) return;
    if (white_point <= 0.0f) {
        // Only set RGB channels to 1.0
        int colorChannels = std::min(channels, 3);
        for (int i = 0; i < count; ++i) {
            int idx = i * channels;
            for (int c = 0; c < colorChannels; ++c) {
                pixels[idx + c] = 1.0f;
            }
        }
        return;
    }

    float scale = 1.0f / white_point;
    int colorChannels = std::min(channels, 3);
    for (int i = 0; i < count; ++i) {
        int idx = i * channels;
        for (int c = 0; c < colorChannels; ++c) {
            float v = pixels[idx + c];
            if (v >= white_point) {
                pixels[idx + c] = 1.0f;
            } else {
                pixels[idx + c] = std::clamp(v * scale, 0.0f, 1.0f);
            }
        }
    }
}

} // namespace alcedo
