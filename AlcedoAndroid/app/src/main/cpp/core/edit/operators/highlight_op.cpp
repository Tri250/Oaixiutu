#include "highlight_op.h"
#include <algorithm>

namespace alcedo {

namespace {
    inline float compute_luminance(const float* pixel, int channels) {
        if (channels == 1) {
            return pixel[0];
        } else if (channels >= 3) {
            return 0.2126f * pixel[0] + 0.7152f * pixel[1] + 0.0722f * pixel[2];
        }
        return pixel[0];
    }
}

void HighlightOperator::apply(float* pixels, int count, int channels, float highlight_amount) {
    if (!pixels || count <= 0 || channels <= 0 || highlight_amount == 0.0f) return;

    const float highlight_threshold = 0.75f;

    for (int i = 0; i < count; ++i) {
        float* pixel = pixels + i * channels;
        float lum = compute_luminance(pixel, channels);

        if (lum > highlight_threshold) {
            float mask = (lum - highlight_threshold) / (1.0f - highlight_threshold);
            float adjustment = highlight_amount * mask;
            for (int c = 0; c < channels; ++c) {
                float v = pixel[c] + adjustment;
                pixel[c] = std::max(0.0f, std::min(1.0f, v));
            }
        }
    }
}

} // namespace alcedo
