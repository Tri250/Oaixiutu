#pragma once
#include <cstddef>

namespace alcedo {

class LensCorrectionOperator {
public:
    // Lens distortion correction using Brown-Conrady model.
    // Corrects both radial and tangential distortion.
    //
    // Radial distortion: r_d = r * (1 + k1*r^2 + k2*r^4 + k3*r^6)
    // Tangential distortion:
    //   x_d = x + 2*p1*x*y + p2*(r^2 + 2*x^2)
    //   y_d = y + p1*(r^2 + 2*y^2) + 2*p2*x*y
    //
    // k1, k2, k3: radial distortion coefficients
    // p1, p2: tangential distortion coefficients
    // cx, cy: principal point (optical center), normalized to [0,1]
    // focal_length_ratio: fx/fy ratio for aspect correction
    //
    // The operator applies the inverse distortion model.
    static void apply_rgb(float* pixels, int width, int height,
                          float k1, float k2, float k3,
                          float p1, float p2,
                          float cx = 0.5f, float cy = 0.5f,
                          float focal_length_ratio = 1.0f);
    static void apply_rgba(float* pixels, int width, int height,
                          float k1, float k2, float k3,
                          float p1, float p2,
                          float cx = 0.5f, float cy = 0.5f,
                          float focal_length_ratio = 1.0f);

    // Vignette correction
    // strength: 0.0 to 1.0 (amount of correction)
    // midpoint: 0.0 to 1.0 (where the vignette starts, 0 = center)
    // roundness: controls shape (0 = circular, 1 = square)
    static void correct_vignette_rgb(float* pixels, int width, int height,
                                     float strength, float midpoint = 0.3f,
                                     float roundness = 0.0f);
    static void correct_vignette_rgba(float* pixels, int width, int height,
                                     float strength, float midpoint = 0.3f,
                                     float roundness = 0.0f);

private:
    static void bilinear_sample(const float* src, int width, int height, int channels,
                                float x, float y, float* out);
};

} // namespace alcedo