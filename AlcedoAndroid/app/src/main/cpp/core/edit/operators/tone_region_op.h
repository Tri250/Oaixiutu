#pragma once
#include <cstddef>

namespace alcedo {

class ToneRegionOperator {
public:
    // Tone region adjustments: independently adjust shadows, midtones, and highlights.
    // Uses luminance-based soft masks for smooth blending between regions.
    // shadows: -1.0 to 1.0 (brighten/darken shadows)
    // midtones: -1.0 to 1.0 (brighten/darken midtones)
    // highlights: -1.0 to 1.0 (brighten/darken highlights)
    // shadow_boundary: 0.1 to 0.4 (where shadow region ends, default 0.25)
    // highlight_boundary: 0.6 to 0.9 (where highlight region begins, default 0.75)
    // smoothness: controls transition sharpness between regions
    static void apply_rgb(float* pixels, int width, int height,
                          float shadows, float midtones, float highlights,
                          float shadow_boundary = 0.25f,
                          float highlight_boundary = 0.75f,
                          float smoothness = 1.0f);
    static void apply_rgba(float* pixels, int width, int height,
                          float shadows, float midtones, float highlights,
                          float shadow_boundary = 0.25f,
                          float highlight_boundary = 0.75f,
                          float smoothness = 1.0f);
};

} // namespace alcedo