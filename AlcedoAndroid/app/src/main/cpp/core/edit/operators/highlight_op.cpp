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

    for (int i = 0; i < count; ++i) {
        float* pixel = pixels + i * channels;
        float lum = compute_luminance(pixel, channels);
        float highlight_compress = -highlight_amount * lum * lum;

        for (int c = 0; c < channels; ++c) {
            pixel[c] = std::max(0.0f, std::min(1.0f, pixel[c] + highlight_compress));
        }
    }
}

} // namespace alcedo
