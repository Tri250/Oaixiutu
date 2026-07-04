#pragma once
#include <cstddef>

namespace alcedo {

class ClarityOperator {
public:
    // Clarity: local contrast enhancement using unsharp mask technique.
    // Applies a moderate-radius blur, then subtracts from the original to extract local detail.
    // amount: 0.0 to 1.0 (strength of clarity effect)
    // radius: 1.0 to 100.0 (pixel radius of local contrast, typical 10-30)
    static void apply_rgb(float* pixels, int width, int height, float amount, float radius);
    static void apply_rgba(float* pixels, int width, int height, float amount, float radius);

private:
    static void box_blur_h(float* src, float* dst, int width, int height, int channels, int radius);
    static void box_blur_v(float* src, float* dst, int width, int height, int channels, int radius);
    static void box_blur(float* src, float* dst, int width, int height, int channels, int radius);
};

} // namespace alcedo