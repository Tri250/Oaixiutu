#pragma once

#include <cstddef>
#include <cstdint>

namespace alcedo {

// Adaptive Homogeneity-Directed (AHD) demosaicing algorithm.
// Reference: H. S. Malvar, L. He, R. Cutler, "High-Quality Linear Interpolation
// for Demosaicing of Bayer-Patterned Color Images", 2004.
//
// AHD works by interpolating in two color-difference spaces (horizontal and vertical)
// and selecting the direction that produces more homogeneous results, reducing
// color artifacts and zipper effects.

class AHDDemosaicOperator {
public:
    // Demosaic a uint16 Bayer-pattern image to planar float RGB output.
    // bayer_pattern: 0=RGGB, 1=BGGR, 2=GRBG, 3=GBRG
    static void demosaic_uint16(const uint16_t* raw_data, int width, int height,
                                int bayer_pattern,
                                float* output_r, float* output_g, float* output_b,
                                uint16_t white_level = 65535,
                                uint16_t black_level = 0);

    // Demosaic a float Bayer-pattern image to planar float RGB output.
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

    // AHD core: interpolate in H and V directions and choose homogeneity
    static void ahd_interpolate(const float* src, int width, int height,
                                const BayerInfo& info,
                                float* output_r, float* output_g, float* output_b);
};

} // namespace alcedo
