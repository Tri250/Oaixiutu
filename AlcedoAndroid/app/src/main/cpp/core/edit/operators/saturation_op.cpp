#include "saturation_op.h"

namespace alcedo {

void SaturationOperator::apply_rgb(std::vector<float>& pixels, int width, int height, float saturation) {
    if (pixels.empty()) return;
    if (pixels.size() % 3 != 0) return;

    float lumR = 0.299f, lumG = 0.587f, lumB = 0.114f;
    float s = 1.0f + std::clamp(saturation, -1.0f, 1.0f);

    for (size_t i = 0; i + 2 < pixels.size(); i += 3) {
        float r = pixels[i];
        float g = pixels[i + 1];
        float b = pixels[i + 2];
        float lum = r * lumR + g * lumG + b * lumB;
        pixels[i]     = lum + (r - lum) * s;
        pixels[i + 1] = lum + (g - lum) * s;
        pixels[i + 2] = lum + (b - lum) * s;
    }
}

} // namespace alcedo
