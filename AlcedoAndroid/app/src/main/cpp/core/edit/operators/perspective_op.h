#pragma once
#include <cstddef>

namespace alcedo {

// Perspective correction modes (matching UI PerspectiveMode)
enum class PerspectiveMode : int {
    MANUAL = 0,     // Free 4-corner drag
    VERTICAL = 1,   // Vertical lines correction only
    HORIZONTAL = 2, // Horizontal lines correction only
    VH = 3,         // Vertical + Horizontal
    FULL = 4         // Full free-form 4-point
};

struct PerspectiveCorrectionParams {
    // 4 source corner points in normalized [0,1] coordinates
    // Order: TL, TR, BR, BL
    float src_x[4] = {0.0f, 1.0f, 1.0f, 0.0f};
    float src_y[4] = {0.0f, 0.0f, 1.0f, 1.0f};

    // Correction amount: 0.0 = no correction, 1.0 = full correction
    float amount = 1.0f;

    // Active mode
    PerspectiveMode mode = PerspectiveMode::FULL;

    // Grid overlay toggle (for preview, not used in processing)
    bool show_grid = true;
};

class PerspectiveOperator {
public:
    // Apply perspective correction with 4-corner points.
    // Uses inverse mapping + bilinear interpolation.
    //
    // src_corners: 8 floats [x0,y0, x1,y1, x2,y2, x3,y3] normalized [0,1]
    //   Order: TL, TR, BR, BL
    // amount: 0.0..1.0, blend between identity and full correction
    // mode: which correction preset to apply
    static void apply(float* dst, int dst_width, int dst_height,
                      const float* src, int src_width, int src_height,
                      int channels,
                      const float src_corners[8],
                      float amount,
                      int mode);

    // Compute the homography matrix H (3x3 row-major) that maps
    // from src quadrilateral corners to dst rectangle corners.
    // Returns true on success, false if the system is degenerate.
    static bool compute_homography(const float src_x[4], const float src_y[4],
                                   int dst_width, int dst_height,
                                   float H[9]);

    // Compute the inverse of a 3x3 homography matrix.
    // Returns true on success, false if matrix is singular.
    static bool invert_homography(const float H[9], float H_inv[9]);

    // Apply a 3x3 homography to a point (forward mapping).
    static void transform_point_forward(const float H[9], float x, float y,
                                         float& ox, float& oy);

    // Apply a 3x3 homography to a point (inverse mapping: maps dst to src).
    static void transform_point_inverse(const float H_inv[9], float x, float y,
                                          float& ox, float& oy);

    // Compute the bounding box of the warped image (auto-crop).
    // Returns the output dimensions and offset via out_ parameters.
    static void compute_auto_crop(int src_width, int src_height,
                                  const float H[9],
                                  int& out_x, int& out_y,
                                  int& out_width, int& out_height);

    // Compute effective source corners for a given mode.
    // For VERTICAL mode: only top corners move inward/outward symmetrically.
    // For HORIZONTAL mode: only left corners move up/down symmetrically.
    // For VH mode: both constraints apply.
    // For FULL/MANUAL: corners used as-is.
    static void apply_mode_to_corners(float src_corners[8],
                                      int mode,
                                      int src_width, int src_height);

private:
    // Bilinear interpolation sampling
    static void bilinear_sample(const float* src, int width, int height, int channels,
                                float x, float y, float* out);

    // 3x3 matrix multiply: C = A * B
    static void mat3_multiply(const float A[9], const float B[9], float C[9]);

    // 3x3 matrix determinant
    static float mat3_determinant(const float M[9]);
};

} // namespace alcedo
