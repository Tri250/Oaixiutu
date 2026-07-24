#pragma once
#include <cstddef>

namespace alcedo {

class TextureOperator {
public:
    // Texture: local contrast / fine detail enhancement.
    // amount: -1.0 to 1.0 (negative = smooth, positive = enhance texture)
    // radius: blur radius for detail extraction (default 2)
    static void apply_rgb(float* pixels, int width, int height, float amount, int radius);
    static void apply_rgba(float* pixels, int width, int height, float amount, int radius);

private:
    static void box_blur_h(float* src, float* dst, int width, int height, int channels, int radius);
    static void box_blur_v(float* src, float* dst, int width, int height, int channels, int radius);
    static void box_blur(float* src, float* dst, int width, int height, int channels, int radius);
};

} // namespace alcedo
