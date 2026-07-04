#pragma once
#include <cstddef>

namespace alcedo {

class GeometryOperator {
public:
    // Comprehensive geometry transformations:
    // - Rotation (in degrees)
    // - Scale (uniform)
    // - Perspective correction (4-point keystone)
    // - DNG warp (polynomial distortion correction)
    // - Crop
    //
    // All operations output to a pre-allocated buffer.
    // The source image is read-only.

    // Rotate + scale + translate
    // angle: degrees, counter-clockwise
    // scale: 1.0 = no change
    // center_x, center_y: rotation center, normalized [0,1]
    static void rotate_scale(float* dst, int dst_width, int dst_height,
                             const float* src, int src_width, int src_height,
                             int channels, float angle, float scale,
                             float center_x = 0.5f, float center_y = 0.5f);

    // Perspective correction from 4 source points to 4 destination points
    // src_points: 8 floats [x0,y0, x1,y1, x2,y2, x3,y3] normalized [0,1]
    // dst_points: 8 floats, same format
    static void perspective_warp(float* dst, int dst_width, int dst_height,
                                 const float* src, int src_width, int src_height,
                                 int channels,
                                 const float src_points[8],
                                 const float dst_points[8]);

    // DNG warp (polynomial distortion) using the DNG OpcodeList format
    // WarpRectilinear: applies polynomial radial distortion correction
    // coefficients: 4 coefficients [k_r0, k_r1, k_r2, k_r3] for radial polynomial
    // cx, cy: optical center, normalized [0,1]
    static void dng_warp_rectilinear(float* dst, int dst_width, int dst_height,
                                     const float* src, int src_width, int src_height,
                                     int channels,
                                     const float coefficients[4],
                                     float cx = 0.5f, float cy = 0.5f);

    // Crop: extracts a region from source to destination
    static void crop(float* dst, int dst_width, int dst_height,
                     const float* src, int src_width, int src_height,
                     int channels,
                     int src_x, int src_y, int src_w, int src_h);

    // Flip horizontal/vertical
    static void flip_horizontal(float* data, int width, int height, int channels);
    static void flip_vertical(float* data, int width, int height, int channels);

private:
    static void bilinear_sample(const float* src, int width, int height, int channels,
                                float x, float y, float* out);
    static bool solve_perspective(const float src[8], const float dst[8], float matrix[9]);
    static void transform_point(const float matrix[9], float x, float y, float& ox, float& oy);
};

} // namespace alcedo