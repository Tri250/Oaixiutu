#pragma once

#include <cstddef>
#include <cstdint>

namespace alcedo {

// AMAZE (Aliasing Minimization and Zipper Elimination) demosaicing algorithm.
// A popular high-quality demosaicing algorithm known for excellent detail
// preservation and artifact reduction.
//
// The algorithm uses edge-directed green channel interpolation combined with
// iterative refinement to minimize aliasing and zipper artifacts.

class AMAZEDemosaicOperator {
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

    // AMAZE core: edge-directed interpolation with aliasing minimization
    static void amaze_interpolate(const float* src, int width, int height,
                                  const BayerInfo& info,
                                  float* output_r, float* output_g, float* output_b);
};

} // namespace alcedo
