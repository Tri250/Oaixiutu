#pragma once
#include <cstddef>

namespace alcedo {

class HalationOperator {
public:
    // Halation effect: red-biased highlight bloom that simulates the scattering
    // of light in film emulsion layers. This is the characteristic red glow
    // around bright highlights in film photography.
    //
    // intensity: 0.0 to 1.0 - overall strength of the effect
    // threshold: 0.0 to 1.0 - luminance above which halation begins (default 0.8)
    // spread: 1.0 to 100.0 - spatial spread of the glow (default 10.0)
    // red_bias: 0.0 to 1.0 - how much the glow is biased toward red (default 0.7)
    static void apply_rgb(float* pixels, int width, int height,
                          float intensity, float threshold = 0.8f,
                          float spread = 10.0f, float red_bias = 0.7f);
    static void apply_rgba(float* pixels, int width, int height,
                          float intensity, float threshold = 0.8f,
                          float spread = 10.0f, float red_bias = 0.7f);

private:
    static void box_blur_h(float* src, float* dst, int width, int height, int radius);
    static void box_blur_v(float* src, float* dst, int width, int height, int radius);
    static void box_blur_rgb(float* src, float* dst, int width, int height, int radius);
};

} // namespace alcedo