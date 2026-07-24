#pragma once
#include <cstddef>

namespace alcedo {

class DehazeOperator {
public:
    // Dehaze: remove/add atmospheric haze using dark channel prior.
    // amount: -1.0 to 1.0 (negative = add haze, positive = remove haze)
    // radius: patch radius for dark channel computation (default 7)
    static void apply_rgb(float* pixels, int width, int height, float amount, int radius);
    static void apply_rgba(float* pixels, int width, int height, float amount, int radius);

private:
    // Compute dark channel: min(R,G,B) over local patch
    static void compute_dark_channel(const float* pixels, int width, int height, int channels,
                                     int radius, float* dark_channel);
    // Estimate atmospheric light from top 0.1% brightest dark channel pixels
    static void estimate_atmospheric_light(const float* pixels, int width, int height, int channels,
                                           const float* dark_channel, float& A_r, float& A_g, float& A_b);
    // Box blur for transmission refinement
    static void box_blur(const float* src, float* dst, int width, int height, int radius);
};

} // namespace alcedo
