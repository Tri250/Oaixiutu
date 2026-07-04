#pragma once
#include <cstddef>

namespace alcedo {

class ColorWheelOperator {
public:
    // CDL-style color wheels: lift, gamma, gain.
    // Applies ASC CDL (American Society of Cinematographers Color Decision List) transforms.
    //
    // Lift (shadows):  RGB offsets, typically small values like -0.1 to 0.1
    // Gamma (midtones):  RGB power functions, typically 0.5 to 1.5
    // Gain (highlights):  RGB multipliers, typically 0.5 to 1.5
    //
    // Each is a 3-component vector [r, g, b].
    // The standard CDL formula: output = (input * slope + offset)^power
    // where slope = gain, offset = lift, power = 1/gamma
    //
    // For general color wheels, we use:
    //   lift_rgb: additive offset applied to shadows
    //   gamma_rgb: power applied to midtones (1.0 = no change)
    //   gain_rgb: multiplicative scale applied to highlights
    //   mix: blending between lift and gain dominance (0 = all lift, 1 = all gain)
    static void apply_rgb(float* pixels, int width, int height,
                          float lift_r, float lift_g, float lift_b,
                          float gamma_r, float gamma_g, float gamma_b,
                          float gain_r, float gain_g, float gain_b);
    static void apply_rgba(float* pixels, int width, int height,
                          float lift_r, float lift_g, float lift_b,
                          float gamma_r, float gamma_g, float gamma_b,
                          float gain_r, float gain_g, float gain_b);

    // Full CDL formula: output = (input * slope + offset)^power
    // slope = gain, offset = lift, power = 1/gamma
    static void apply_cdl(float* pixels, int width, int height,
                          const float slope[3], const float offset[3],
                          const float power[3], int channels);
};

} // namespace alcedo