#include "saturation_op.h"
#include <algorithm>

namespace alcedo {

void SaturationOperator::apply_rgb(std::vector<float>& pixels, int width, int height, float saturation) {
    if (pixels.empty()) return;
    if (pixels.size() % 3 != 0) return;

    // BT.709 luminance weights — consistent with all other operators
    const float lumR = 0.2126f, lumG = 0.7152f, lumB = 0.0722f;
    float s = 1.0f + std::clamp(saturation, -1.0f, 1.0f);

    for (size_t i = 0; i + 2 < pixels.size(); i += 3) {
        float r = pixels[i];
        float g = pixels[i + 1];
        float b = pixels[i + 2];
        float lum = r * lumR + g * lumG + b * lumB;
        pixels[i]     = std::clamp(lum + (r - lum) * s, 0.0f, 1.0f);
        pixels[i + 1] = std::clamp(lum + (g - lum) * s, 0.0f, 1.0f);
        pixels[i + 2] = std::clamp(lum + (b - lum) * s, 0.0f, 1.0f);
    }
}

} // namespace alcedo
