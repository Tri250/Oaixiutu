#pragma once
#include <cstddef>

namespace alcedo {

class TintOperator {
public:
    // Tint: shifts the color balance in highlight, midtone, and shadow regions.
    // Uses a split-toning approach with luminance-based weighting.
    // highlight_hue: hue shift in degrees for highlights
    // highlight_strength: 0.0 to 1.0
    // shadow_hue: hue shift in degrees for shadows
    // shadow_strength: 0.0 to 1.0
    // balance: -1.0 to 1.0, controls the split point between shadows and highlights
    static void apply_rgb(float* pixels, int width, int height,
                          float highlight_hue, float highlight_strength,
                          float shadow_hue, float shadow_strength,
                          float balance);
    static void apply_rgba(float* pixels, int width, int height,
                          float highlight_hue, float highlight_strength,
                          float shadow_hue, float shadow_strength,
                          float balance);
};

} // namespace alcedo