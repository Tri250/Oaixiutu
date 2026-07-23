#include "color_wheel_op.h"
#include <cmath>
#include <algorithm>

namespace alcedo {

static constexpr float kLumR = 0.2126f;
static constexpr float kLumG = 0.7152f;
static constexpr float kLumB = 0.0722f;

void ColorWheelOperator::apply_rgb(float* pixels, int width, int height,
                                    float lift_r, float lift_g, float lift_b,
                                    float gamma_r, float gamma_g, float gamma_b,
                                    float gain_r, float gain_g, float gain_b) {
    if (!pixels) return;
    int total = width * height;

    // Precompute gamma exponents
    float power_r = 1.0f / std::max(0.001f, gamma_r);
    float power_g = 1.0f / std::max(0.001f, gamma_g);
    float power_b = 1.0f / std::max(0.001f, gamma_b);

    for (int i = 0; i < total; ++i) {
        int idx = i * 3;
        float r = std::max(0.0f, pixels[idx]);
        float g = std::max(0.0f, pixels[idx + 1]);
        float b = std::max(0.0f, pixels[idx + 2]);

        // Apply CDL formula: out = (in * gain + lift)^(1/gamma)
        // Clamp to avoid negative base for power
        float base_r = std::max(0.0f, r * gain_r + lift_r);
        float base_g = std::max(0.0f, g * gain_g + lift_g);
        float base_b = std::max(0.0f, b * gain_b + lift_b);

        pixels[idx]     = std::clamp(std::pow(base_r, power_r), 0.0f, 1.0f);
        pixels[idx + 1] = std::clamp(std::pow(base_g, power_g), 0.0f, 1.0f);
        pixels[idx + 2] = std::clamp(std::pow(base_b, power_b), 0.0f, 1.0f);
    }
}

void ColorWheelOperator::apply_rgba(float* pixels, int width, int height,
                                     float lift_r, float lift_g, float lift_b,
                                     float gamma_r, float gamma_g, float gamma_b,
                                     float gain_r, float gain_g, float gain_b) {
    if (!pixels) return;
    int total = width * height;

    float power_r = 1.0f / std::max(0.001f, gamma_r);
    float power_g = 1.0f / std::max(0.001f, gamma_g);
    float power_b = 1.0f / std::max(0.001f, gamma_b);

    for (int i = 0; i < total; ++i) {
        int idx = i * 4;
        float r = std::max(0.0f, pixels[idx]);
        float g = std::max(0.0f, pixels[idx + 1]);
        float b = std::max(0.0f, pixels[idx + 2]);

        float base_r = std::max(0.0f, r * gain_r + lift_r);
        float base_g = std::max(0.0f, g * gain_g + lift_g);
        float base_b = std::max(0.0f, b * gain_b + lift_b);

        pixels[idx]     = std::clamp(std::pow(base_r, power_r), 0.0f, 1.0f);
        pixels[idx + 1] = std::clamp(std::pow(base_g, power_g), 0.0f, 1.0f);
        pixels[idx + 2] = std::clamp(std::pow(base_b, power_b), 0.0f, 1.0f);
    }
}

void ColorWheelOperator::apply_cdl(float* pixels, int width, int height,
                                    const float slope[3], const float offset[3],
                                    const float power[3], int channels) {
    if (!pixels || channels < 3) return;
    int total = width * height;

    for (int i = 0; i < total; ++i) {
        int idx = i * channels;
        for (int c = 0; c < 3 && c < channels; ++c) {
            float v = std::max(0.0f, pixels[idx + c]);
            float base = std::max(0.0f, v * slope[c] + offset[c]);
            pixels[idx + c] = std::clamp(std::pow(base, power[c]), 0.0f, 1.0f);
        }
    }
}

} // namespace alcedo