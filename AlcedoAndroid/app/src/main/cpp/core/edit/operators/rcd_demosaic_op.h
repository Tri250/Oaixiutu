#pragma once
#include <cstddef>
#include <cstdint>

namespace alcedo {

class RCDDemosaicOperator {
public:
    // RCD (Ratio-Corrected Demosaicing) algorithm.
    // Based on the paper "Ratio-Corrected Demosaicing" by Luis San Martín and
    // Javier Portilla (2012). This is a high-quality demosaicing algorithm that
    // preserves edges and reduces artifacts compared to simpler methods.
    //
    // The algorithm works in two passes:
    // 1. Initial interpolation of G channel using Hamilton-Adams edge-directed method
    // 2. R and B channels reconstructed using color difference ratios
    //
    // bayer_pattern: RGGB=0, BGGR=1, GRBG=2, GBRG=3
    // raw_data: single-channel Bayer pattern data (uint16_t or float)
    // output_r: red channel output (width*height floats)
    // output_g: green channel output
    // output_b: blue channel output
    static void demosaic_uint16(const uint16_t* raw_data, int width, int height,
                                int bayer_pattern,
                                float* output_r, float* output_g, float* output_b,
                                uint16_t white_level = 65535,
                                uint16_t black_level = 0);
    static void demosaic_float(const float* raw_data, int width, int height,
                              int bayer_pattern,
                              float* output_r, float* output_g, float* output_b,
                              float white_level = 1.0f,
                              float black_level = 0.0f);

    enum CFAPattern {
        RGGB = 0,
        BGGR = 1,
        GRBG = 2,
        GBRG = 3
    };

private:
    struct BayerInfo {
        int red_row, red_col;
        int blue_row, blue_col;
    };
    static BayerInfo get_bayer_info(int pattern);
    static int color_at(int y, int x, const BayerInfo& info);
    static void interpolate_green(const float* src, int width, int height,
                                  const BayerInfo& info, float* g);
    static void interpolate_red_blue(const float* src, const float* g,
                                     int width, int height,
                                     const BayerInfo& info,
                                     float* r, float* b);
};

} // namespace alcedo