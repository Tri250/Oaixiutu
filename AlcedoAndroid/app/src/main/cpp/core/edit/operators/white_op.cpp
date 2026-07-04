#include "white_op.h"

namespace alcedo {

void WhiteOperator::apply(float* pixels, int count, int channels, float white_point) {
    if (!pixels || count <= 0 || channels <= 0) return;
    if (white_point >= 1.0f) return;
    if (white_point <= 0.0f) {
        for (int i = 0; i < count * channels; ++i) {
            pixels[i] = 1.0f;
        }
        return;
    }

    float scale = 1.0f / white_point;
    int total = count * channels;
    for (int i = 0; i < total; ++i) {
        float v = pixels[i];
        if (v >= white_point) {
            pixels[i] = 1.0f;
        } else {
            pixels[i] = v * scale;
        }
    }
}

} // namespace alcedo
