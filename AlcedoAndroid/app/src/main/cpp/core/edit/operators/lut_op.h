#pragma once
#include <cstddef>
#include <cstdint>
#include <string>

namespace alcedo {

class LutOperator {
public:
    // 3D CUBE LUT application using trilinear interpolation.
    // Supports standard .cube format LUTs.
    //
    // The LUT is a 3D grid of size x size x size, stored as:
    //   lut_data[i] where i = (r_idx * size * size + g_idx * size + b_idx) * 3 + channel
    //
    // size: grid dimension (typically 17, 33, or 65)
    // lut_data: flat array of size*size*size*3 floats (RGB interleaved)
    //
    // input_min/max: input range (typically 0.0 to 1.0)
    // output_min/max: output range (typically 0.0 to 1.0)
    static void apply_rgb(float* pixels, int width, int height,
                          const float* lut_data, int lut_size,
                          float input_min = 0.0f, float input_max = 1.0f,
                          float output_min = 0.0f, float output_max = 1.0f);
    static void apply_rgba(float* pixels, int width, int height,
                          const float* lut_data, int lut_size,
                          float input_min = 0.0f, float input_max = 1.0f,
                          float output_min = 0.0f, float output_max = 1.0f);

    // Parse a .cube LUT file and return the data
    // Returns false if parsing fails
    static bool parse_cube_file(const std::string& path,
                                float*& lut_data, int& lut_size);
    static void free_parsed_lut(float* lut_data);

    static float trilinear_lookup(const float* lut_data, int size,
                                  float r, float g, float b);
    static float clamp(float v, float lo, float hi);
};

} // namespace alcedo