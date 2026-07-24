#pragma once

#include <vector>
#include <cmath>
#include <algorithm>

namespace alcedo {

enum class MaskType : int {
    Brush = 0,
    LinearGradient = 1,
    RadialGradient = 2,
    Luminosity = 3,
    ColorRange = 4,
    WholeImage = 5
};

struct MaskParams {
    MaskType type = MaskType::Brush;
    float opacity = 1.0f;          // 0.0 - 1.0
    bool inverted = false;
    float feather = 0.2f;          // blur radius for edge softening (normalized 0-1)

    // Brush mask — normalized x,y pairs with pressure
    struct BrushPoint {
        float x, y, pressure;
    };
    std::vector<BrushPoint> brush_points;
    float brush_size = 0.05f;      // normalized radius (0-1)
    float brush_hardness = 0.5f;   // 0=soft, 1=hard
    float brush_opacity = 1.0f;

    // Linear gradient
    float linear_start_x = 0.2f, linear_start_y = 0.2f;
    float linear_end_x = 0.8f, linear_end_y = 0.8f;

    // Radial gradient
    float radial_center_x = 0.5f, radial_center_y = 0.5f;
    float radial_radius_x = 0.4f;  // normalized
    float radial_radius_y = 0.4f;  // normalized

    // Luminosity
    float lum_min = 0.0f;
    float lum_max = 1.0f;

    // Color range
    float color_target_r = 0.5f;
    float color_target_g = 0.5f;
    float color_target_b = 0.5f;
    float color_range = 0.15f;
};

class MaskOperator {
public:
    // Generate mask buffer from params. mask_out must be pre-allocated with width*height floats.
    static void generate_mask(float* mask_out, int width, int height, const MaskParams& params);

    // Apply mask to blend original and edited pixels.
    // output[i] = original[i] * (1 - mask[i]) + edited[i] * mask[i]
    static void apply_mask(const float* original, const float* edited, float* output,
                           int width, int height, int channels, const float* mask);

    // Feather mask with repeated box blur (approximates Gaussian).
    // radius is in pixels.
    static void feather_mask(float* mask, int width, int height, float radius_px);

    // Invert mask: mask[i] = 1.0 - mask[i]
    static void invert_mask(float* mask, int width, int height);

    // Combine two masks
    enum class CombineMode { Add, Subtract, Intersect };
    static void combine_masks(float* mask_out, const float* mask_a, const float* mask_b,
                              int width, int height, CombineMode mode);

private:
    static void generate_brush_mask(float* mask, int width, int height, const MaskParams& params);
    static void generate_linear_gradient_mask(float* mask, int width, int height, const MaskParams& params);
    static void generate_radial_gradient_mask(float* mask, int width, int height, const MaskParams& params);
    static void generate_luminosity_mask(float* mask, int width, int height, const MaskParams& params);
    static void generate_color_range_mask(float* mask, int width, int height, const MaskParams& params);

    // Single-pass separable box blur (horizontal then vertical)
    static void box_blur_pass(float* data, int width, int height, int radius);
};

} // namespace alcedo
