#pragma once
#include <cstddef>

namespace alcedo {

class HSLOperator {
public:
    // HSL separation: adjust hue, saturation, and luminance independently for
    // 8 color ranges (Red, Orange, Yellow, Green, Aqua, Blue, Purple, Magenta).
    //
    // Each channel has:
    //   hue_shift: degrees (-180 to 180) to shift the hue
    //   saturation_scale: 0.0 to 2.0 multiplicative saturation
    //   luminance_scale: 0.0 to 2.0 multiplicative luminance
    //
    // hue_ranges: array of 8 hue center angles in degrees [0, 60, 120, 180, 240, 300, ...]
    // hue_width: width of each range in degrees (typically 30-60)
    // hue_shift[8], saturation_scale[8], luminance_scale[8]
    static void apply_rgb(float* pixels, int width, int height,
                          const float hue_ranges[8], float hue_width,
                          const float hue_shift[8],
                          const float saturation_scale[8],
                          const float luminance_scale[8]);
    static void apply_rgba(float* pixels, int width, int height,
                          const float hue_ranges[8], float hue_width,
                          const float hue_shift[8],
                          const float saturation_scale[8],
                          const float luminance_scale[8]);
};

} // namespace alcedo