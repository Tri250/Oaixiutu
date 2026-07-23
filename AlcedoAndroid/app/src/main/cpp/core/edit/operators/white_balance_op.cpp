#include "white_balance_op.h"
#include <algorithm>

namespace alcedo {

void WhiteBalanceOperator::apply(std::vector<float>& pixels, int width, int height,
                                 float temperature, float tint) {
    if (pixels.empty() || pixels.size() % 3 != 0) return;

    // Temperature scaling using Planckian locus approximation
    // 6500K is neutral, lower is warmer (more red), higher is cooler (more blue)
    float temp_scale = temperature / 6500.0f;

    // Red multiplier: increases for warm (low temp), decreases for cool (high temp)
    float r_mult = 1.0f / std::max(0.3f, temp_scale);
    // Blue multiplier: increases for cool (high temp), decreases for warm (low temp)
    float b_mult = temp_scale;
    // Clamp multipliers to reasonable range
    r_mult = std::clamp(r_mult, 0.5f, 2.0f);
    b_mult = std::clamp(b_mult, 0.5f, 2.0f);

    // Green tint offset
    float g_offset = tint * 0.01f;

    for (size_t i = 0; i + 2 < pixels.size(); i += 3) {
        pixels[i]     *= r_mult;
        pixels[i + 1] += g_offset;
        pixels[i + 2] *= b_mult;
    }
}

} // namespace alcedo
