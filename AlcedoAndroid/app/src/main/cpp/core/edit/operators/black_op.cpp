#include "black_op.h"

namespace alcedo {

void BlackOperator::apply(float* pixels, int count, int channels, float black_point) {
    if (!pixels || count <= 0 || channels <= 0) return;
    if (black_point <= 0.0f) return;
    if (black_point >= 1.0f) {
        for (int i = 0; i < count * channels; ++i) {
            pixels[i] = 0.0f;
        }
        return;
    }

    float scale = 1.0f / (1.0f - black_point);
    int total = count * channels;
    for (int i = 0; i < total; ++i) {
        float v = pixels[i];
        if (v <= black_point) {
            pixels[i] = 0.0f;
        } else {
            pixels[i] = (v - black_point) * scale;
        }
    }
}

} // namespace alcedo
