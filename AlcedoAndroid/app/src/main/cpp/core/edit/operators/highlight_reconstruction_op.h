#pragma once
#include <cstddef>
#include <cstdint>

namespace alcedo {

class HighlightReconstructionOperator {
public:
    // Highlight reconstruction based on the RawTherapee algorithm.
    // Reconstructs clipped highlights in RAW sensor data by using
    // information from unclipped channels.
    //
    // The algorithm works on Bayer-pattern RAW data (single channel per pixel).
    // For each clipped pixel, it estimates the missing value based on the
    // ratio of the unclipped channels in neighboring pixels.
    //
    // bayer_pattern: CFAPattern describing the Bayer layout
    //   RGGB = 0, BGGR = 1, GRBG = 2, GBRG = 3
    // clip_threshold: value above which a pixel is considered clipped
    //                    (typically 0.95 * white_level)
    // white_level: the white level of the sensor
    // black_level: the black level of the sensor
    static void apply(uint16_t* raw_data, int width, int height,
                      int bayer_pattern,
                      uint16_t clip_threshold,
                      uint16_t white_level,
                      uint16_t black_level);
    static void apply_float(float* raw_data, int width, int height,
                           int bayer_pattern,
                           float clip_threshold,
                           float white_level,
                           float black_level);

    enum CFAPattern {
        RGGB = 0,
        BGGR = 1,
        GRBG = 2,
        GBRG = 3
    };

    struct BayerInfo {
        int red_row, red_col;
        int green1_row, green1_col;
        int green2_row, green2_col;
        int blue_row, blue_col;
    };
    static BayerInfo get_bayer_info(int pattern);
    static bool is_same_color(int y1, int x1, int y2, int x2, const BayerInfo& info);
    static int color_at(int y, int x, const BayerInfo& info);
};

} // namespace alcedo