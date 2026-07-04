#pragma once
#include <cstddef>

namespace alcedo {

class VibranceOperator {
public:
    // Vibrance: smart saturation that boosts less saturated colors more than already saturated ones.
    // Uses a per-pixel saturation mask to avoid clipping skin tones and already-vivid colors.
    // amount: -1.0 to 1.0 (0 = no change)
    static void apply_rgb(float* pixels, int width, int height, float amount);
    static void apply_rgba(float* pixels, int width, int height, float amount);
};

} // namespace alcedo