#pragma once
#include <cstddef>

namespace alcedo {

class ChannelMixerOperator {
public:
    // RGB channel mixing: each output channel is a weighted sum of all input channels.
    // Uses a 3x3 matrix where:
    //   R_out = matrix[0]*R_in + matrix[1]*G_in + matrix[2]*B_in
    //   G_out = matrix[3]*R_in + matrix[4]*G_in + matrix[5]*B_in
    //   B_out = matrix[6]*R_in + matrix[7]*G_in + matrix[8]*B_in
    //
    // Common presets:
    //   Identity: {1,0,0, 0,1,0, 0,0,1}
    //   B&W Red Filter: {0.393,0.769,0.189, 0.349,0.686,0.168, 0.272,0.534,0.131}
    //   B&W Green Filter: {0.17,0.5,0.3, 0.17,0.5,0.3, 0.17,0.5,0.3}
    //   B&W Blue Filter: {0.131,0.534,0.272, 0.168,0.686,0.349, 0.189,0.769,0.393}
    //
    // monochrome: if true, output is grayscale (all channels same value)
    static void apply_rgb(float* pixels, int width, int height,
                          const float matrix[9], bool monochrome = false);
    static void apply_rgba(float* pixels, int width, int height,
                          const float matrix[9], bool monochrome = false);

    // Generate preset matrices
    static void preset_identity(float matrix[9]);
    static void preset_bw_red_filter(float matrix[9]);
    static void preset_bw_green_filter(float matrix[9]);
    static void preset_bw_blue_filter(float matrix[9]);
    static void preset_bw_average(float matrix[9]);
    static void preset_bw_luminance(float matrix[9]);
};

} // namespace alcedo