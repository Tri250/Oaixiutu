#include "vibrance_op.h"
#include <cmath>
#include <algorithm>

namespace alcedo {

// Standard luminance weights (ITU-R BT.709)
static constexpr float kLumR = 0.2126f;
static constexpr float kLumG = 0.7152f;
static constexpr float kLumB = 0.0722f;

void VibranceOperator::apply_rgb(float* pixels, int width, int height, float amount) {
    if (!pixels || amount == 0.0f) return;
    int total = width * height;

    for (int i = 0; i < total; ++i) {
        int idx = i * 3;
        float r = pixels[idx];
        float g = pixels[idx + 1];
        float b = pixels[idx + 2];

        float lum = r * kLumR + g * kLumG + b * kLumB;

        // Compute chroma magnitude (distance from gray in RGB space)
        float dr = r - lum;
        float dg = g - lum;
        float db = b - lum;
        float chroma = std::sqrt(dr * dr + dg * dg + db * db);

        // Vibrance mask: boost low-chroma pixels more
        // Map chroma to [0,1] range, then compute mask = 1 - chroma
        float maxChroma = 0.5f; // approximate max chroma
        float chromaNorm = std::min(chroma / maxChroma, 1.0f);
        float mask = 1.0f - chromaNorm * chromaNorm; // quadratic falloff

        float scale = 1.0f + amount * mask;

        // Apply saturation boost with clamping
        pixels[idx]     = std::max(0.0f, std::min(1.0f, lum + dr * scale));
        pixels[idx + 1] = std::max(0.0f, std::min(1.0f, lum + dg * scale));
        pixels[idx + 2] = std::max(0.0f, std::min(1.0f, lum + db * scale));
    }
}

void VibranceOperator::apply_rgba(float* pixels, int width, int height, float amount) {
    if (!pixels || amount == 0.0f) return;
    int total = width * height;

    for (int i = 0; i < total; ++i) {
        int idx = i * 4;
        float r = pixels[idx];
        float g = pixels[idx + 1];
        float b = pixels[idx + 2];

        float lum = r * kLumR + g * kLumG + b * kLumB;

        float dr = r - lum;
        float dg = g - lum;
        float db = b - lum;
        float chroma = std::sqrt(dr * dr + dg * dg + db * db);

        float maxChroma = 0.5f;
        float chromaNorm = std::min(chroma / maxChroma, 1.0f);
        float mask = 1.0f - chromaNorm * chromaNorm;

        float scale = 1.0f + amount * mask;

        pixels[idx]     = std::max(0.0f, std::min(1.0f, lum + dr * scale));
        pixels[idx + 1] = std::max(0.0f, std::min(1.0f, lum + dg * scale));
        pixels[idx + 2] = std::max(0.0f, std::min(1.0f, lum + db * scale));
    }
}

} // namespace alcedo