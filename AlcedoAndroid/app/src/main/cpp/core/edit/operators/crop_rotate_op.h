#pragma once

#include <cstddef>

namespace alcedo {

class CropRotateOperator {
public:
    // Crop: extracts region [left, right) x [top, bottom) from src to dst.
    // dst buffer must be pre-allocated with size (right-left)*(bottom-top)*channels.
    static void apply_crop(float* dst, const float* src, int src_width, int src_height,
                           int channels, int left, int top, int right, int bottom);

    // Rotate: supports 90, 180, 270 degrees clockwise.
    // dst buffer must be pre-allocated with appropriate size.
    // For 90/270: dst dimensions are src_height x src_width.
    static void apply_rotate(float* dst, const float* src, int src_width, int src_height,
                             int channels, int angle);
};

} // namespace alcedo
