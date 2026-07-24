#include "perspective_op.h"
#include <cmath>
#include <algorithm>
#include <vector>
#include <cstring>
#include <android/log.h>

#define LOG_TAG "AlcedoPerspective"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace alcedo {

// ============================================================
// Bilinear interpolation
// ============================================================

void PerspectiveOperator::bilinear_sample(const float* src, int width, int height, int channels,
                                           float x, float y, float* out) {
    int x0 = static_cast<int>(std::floor(x));
    int y0 = static_cast<int>(std::floor(y));
    int x1 = std::min(x0 + 1, width - 1);
    int y1 = std::min(y0 + 1, height - 1);

    x0 = std::max(0, std::min(width - 1, x0));
    y0 = std::max(0, std::min(height - 1, y0));

    float fx = x - static_cast<float>(x0);
    float fy = y - static_cast<float>(y0);

    for (int c = 0; c < channels; ++c) {
        float v00 = src[(y0 * width + x0) * channels + c];
        float v01 = src[(y1 * width + x0) * channels + c];
        float v10 = src[(y0 * width + x1) * channels + c];
        float v11 = src[(y1 * width + x1) * channels + c];

        out[c] = v00 * (1.0f - fx) * (1.0f - fy) +
                 v10 * fx * (1.0f - fy) +
                 v01 * (1.0f - fx) * fy +
                 v11 * fx * fy;
    }
}

// ============================================================
// 3x3 matrix utilities
// ============================================================

void PerspectiveOperator::mat3_multiply(const float A[9], const float B[9], float C[9]) {
    for (int i = 0; i < 3; ++i) {
        for (int j = 0; j < 3; ++j) {
            C[i * 3 + j] = 0.0f;
            for (int k = 0; k < 3; ++k) {
                C[i * 3 + j] += A[i * 3 + k] * B[k * 3 + j];
            }
        }
    }
}

float PerspectiveOperator::mat3_determinant(const float M[9]) {
    // M is row-major: [a b c; d e f; g h i]
    float a = M[0], b = M[1], c = M[2];
    float d = M[3], e = M[4], f = M[5];
    float g = M[6], h = M[7], i = M[8];

    return a * (e * i - f * h)
         - b * (d * i - f * g)
         + c * (d * h - e * g);
}

bool PerspectiveOperator::invert_homography(const float H[9], float H_inv[9]) {
    float det = mat3_determinant(H);
    if (std::abs(det) < 1e-10f) {
        LOGE("Homography matrix is singular, cannot invert");
        return false;
    }

    float inv_det = 1.0f / det;

    // Cofactor matrix transposed (adjugate)
    float a = H[0], b = H[1], c = H[2];
    float d = H[3], e = H[4], f = H[5];
    float g = H[6], h = H[7], i = H[8];

    H_inv[0] = (e * i - f * h) * inv_det;
    H_inv[1] = (c * h - b * i) * inv_det;
    H_inv[2] = (b * f - c * e) * inv_det;
    H_inv[3] = (f * g - d * i) * inv_det;
    H_inv[4] = (a * i - c * g) * inv_det;
    H_inv[5] = (c * d - a * f) * inv_det;
    H_inv[6] = (d * h - e * g) * inv_det;
    H_inv[7] = (b * g - a * h) * inv_det;
    H_inv[8] = (a * e - b * d) * inv_det;

    return true;
}

// ============================================================
// Homography computation via DLT
// ============================================================

bool PerspectiveOperator::compute_homography(const float src_x[4], const float src_y[4],
                                              int dst_width, int dst_height,
                                              float H[9]) {
    // Destination rectangle corners in pixel coordinates
    float dst_x[4] = {0.0f, static_cast<float>(dst_width),
                       static_cast<float>(dst_width), 0.0f};
    float dst_y[4] = {0.0f, 0.0f,
                       static_cast<float>(dst_height), static_cast<float>(dst_height)};

    // Convert src corners from normalized to pixel coordinates
    float sx[4], sy[4];
    for (int i = 0; i < 4; ++i) {
        sx[i] = src_x[i] * dst_width;
        sy[i] = src_y[i] * dst_height;
    }

    // Build 8x9 linear system for DLT
    // For each correspondence (sx, sy) -> (dx, dy):
    //   [-sx, -sy, -1, 0, 0, 0, dx*sx, dx*sy, dx]
    //   [0, 0, 0, -sx, -sy, -1, dy*sx, dy*sy, dy]
    float A[72] = {0}; // 8x9

    for (int i = 0; i < 4; ++i) {
        float px = sx[i], py = sy[i];
        float dx = dst_x[i], dy = dst_y[i];

        int row = i * 2;
        A[row * 9 + 0] = -px;
        A[row * 9 + 1] = -py;
        A[row * 9 + 2] = -1.0f;
        A[row * 9 + 3] = 0.0f;
        A[row * 9 + 4] = 0.0f;
        A[row * 9 + 5] = 0.0f;
        A[row * 9 + 6] = dx * px;
        A[row * 9 + 7] = dx * py;
        A[row * 9 + 8] = dx;

        row = i * 2 + 1;
        A[row * 9 + 0] = 0.0f;
        A[row * 9 + 1] = 0.0f;
        A[row * 9 + 2] = 0.0f;
        A[row * 9 + 3] = -px;
        A[row * 9 + 4] = -py;
        A[row * 9 + 5] = -1.0f;
        A[row * 9 + 6] = dy * px;
        A[row * 9 + 7] = dy * py;
        A[row * 9 + 8] = dy;
    }

    // Solve using SVD-like approach: Gaussian elimination on A^T*A
    // For simplicity, use Gaussian elimination with partial pivoting
    // on the 9x9 normal equations A^T*A*h = 0

    // Compute A^T*A (9x9 symmetric)
    float ATA[81] = {0};
    for (int i = 0; i < 9; ++i) {
        for (int j = 0; j < 9; ++j) {
            float sum = 0.0f;
            for (int k = 0; k < 8; ++k) {
                sum += A[k * 9 + i] * A[k * 9 + j];
            }
            ATA[i * 9 + j] = sum;
        }
    }

    // Find the null space of ATA by shifting to a solvable system:
    // We fix h8 = 1 and solve the 8x8 system ATA[:8,:8]*h[:8] = -ATA[:8,8]
    float M[64] = {0}; // 8x8
    float B[8] = {0};

    for (int i = 0; i < 8; ++i) {
        for (int j = 0; j < 8; ++j) {
            M[i * 8 + j] = ATA[i * 9 + j];
        }
        B[i] = -ATA[i * 9 + 8];
    }

    // Gaussian elimination with partial pivoting
    for (int col = 0; col < 8; ++col) {
        int max_row = col;
        float max_val = std::abs(M[col * 8 + col]);
        for (int row = col + 1; row < 8; ++row) {
            float val = std::abs(M[row * 8 + col]);
            if (val > max_val) {
                max_val = val;
                max_row = row;
            }
        }
        if (max_val < 1e-10f) {
            LOGE("Homography DLT: degenerate system");
            // Set identity
            H[0] = 1; H[1] = 0; H[2] = 0;
            H[3] = 0; H[4] = 1; H[5] = 0;
            H[6] = 0; H[7] = 0; H[8] = 1;
            return false;
        }

        if (max_row != col) {
            for (int j = 0; j < 8; ++j) {
                std::swap(M[col * 8 + j], M[max_row * 8 + j]);
            }
            std::swap(B[col], B[max_row]);
        }

        float pivot = M[col * 8 + col];
        for (int row = 0; row < 8; ++row) {
            if (row == col) continue;
            float factor = M[row * 8 + col] / pivot;
            for (int j = col; j < 8; ++j) {
                M[row * 8 + j] -= factor * M[col * 8 + j];
            }
            B[row] -= factor * B[col];
        }
    }

    // Back substitution
    for (int i = 0; i < 8; ++i) {
        H[i] = B[i] / M[i * 8 + i];
    }
    H[8] = 1.0f;

    return true;
}

// ============================================================
// Point transforms
// ============================================================

void PerspectiveOperator::transform_point_forward(const float H[9], float x, float y,
                                                    float& ox, float& oy) {
    float w = H[6] * x + H[7] * y + H[8];
    if (std::abs(w) < 1e-10f) {
        ox = x;
        oy = y;
        return;
    }
    ox = (H[0] * x + H[1] * y + H[2]) / w;
    oy = (H[3] * x + H[4] * y + H[5]) / w;
}

void PerspectiveOperator::transform_point_inverse(const float H_inv[9], float x, float y,
                                                    float& ox, float& oy) {
    // Same formula as forward but with the inverse matrix
    transform_point_forward(H_inv, x, y, ox, oy);
}

// ============================================================
// Auto-crop: compute bounding box of warped image
// ============================================================

void PerspectiveOperator::compute_auto_crop(int src_width, int src_height,
                                             const float H[9],
                                             int& out_x, int& out_y,
                                             int& out_width, int& out_height) {
    // Transform the 4 corners of the source image
    float corners_x[4] = {0.0f, static_cast<float>(src_width),
                           static_cast<float>(src_width), 0.0f};
    float corners_y[4] = {0.0f, 0.0f,
                           static_cast<float>(src_height), static_cast<float>(src_height)};

    float min_x = 1e30f, max_x = -1e30f;
    float min_y = 1e30f, max_y = -1e30f;

    for (int i = 0; i < 4; ++i) {
        float tx, ty;
        transform_point_forward(H, corners_x[i], corners_y[i], tx, ty);
        min_x = std::min(min_x, tx);
        max_x = std::max(max_x, tx);
        min_y = std::min(min_y, ty);
        max_y = std::max(max_y, ty);
    }

    out_x = static_cast<int>(std::floor(min_x));
    out_y = static_cast<int>(std::floor(min_y));
    out_width = static_cast<int>(std::ceil(max_x)) - out_x;
    out_height = static_cast<int>(std::ceil(max_y)) - out_y;

    // Safety clamp
    if (out_width <= 0) out_width = src_width;
    if (out_height <= 0) out_height = src_height;
}

// ============================================================
// Mode-based corner adjustment
// ============================================================

void PerspectiveOperator::apply_mode_to_corners(float src_corners[8],
                                                  int mode,
                                                  int src_width, int src_height) {
    // src_corners: [x0,y0, x1,y1, x2,y2, x3,y3] = TL, TR, BR, BL
    // Normalized coordinates

    auto effective_mode = static_cast<PerspectiveMode>(mode);

    switch (effective_mode) {
        case PerspectiveMode::VERTICAL: {
            // Only allow horizontal movement of top corners to converge
            // Vertical perspective: top two corners move inward symmetrically
            float top_center_x = (src_corners[0] + src_corners[2]) * 0.5f;
            // Shift TL and TR x towards center based on their current offset
            // Keep bottom corners at default (0,1,1,1 in y)
            src_corners[1] = 0.0f;  // TL y stays
            src_corners[3] = 0.0f;  // TR y stays
            src_corners[5] = 1.0f;  // BR y stays
            src_corners[7] = 1.0f;  // BL y stays
            src_corners[6] = 1.0f;  // BR x stays at 1
            src_corners[4] = 1.0f;  // BR x stays at 1
            // Actually for VERTICAL mode, we constrain:
            // Bottom-left and bottom-right stay at their default x positions
            // Only top-left and top-right x are free to move
            // This is handled by the UI; here we just constrain bottom corners
            src_corners[6] = 1.0f;  // BR x
            src_corners[4] = 1.0f;  // BL x (wait, BL is index 3)
            // Re-check indices: [TLx,TLy, TRx,TRy, BRx,BRy, BLx,BLy]
            // Wait, the order is TL,TR,BR,BL so:
            // [0]=TLx [1]=TLy [2]=TRx [3]=TRy [4]=BRx [5]=BRy [6]=BLx [7]=BLy
            // For vertical correction: fix bottom corners, allow top corners to shift x
            src_corners[4] = 1.0f;  // BR x fixed
            src_corners[6] = 0.0f;  // BL x fixed
            src_corners[5] = 1.0f;  // BR y fixed
            src_corners[7] = 1.0f;  // BL y fixed
            break;
        }
        case PerspectiveMode::HORIZONTAL: {
            // Only allow vertical movement of left corners
            // Fix right corners
            src_corners[2] = 1.0f;  // TR x fixed
            src_corners[3] = 0.0f;  // TR y fixed
            src_corners[4] = 1.0f;  // BR x fixed
            src_corners[5] = 1.0f;  // BR y fixed
            break;
        }
        case PerspectiveMode::VH: {
            // Both constraints: fix bottom and right corners
            src_corners[4] = 1.0f;  // BR x fixed
            src_corners[5] = 1.0f;  // BR y fixed
            break;
        }
        case PerspectiveMode::FULL:
        case PerspectiveMode::MANUAL:
        default:
            // All corners free - use as-is
            break;
    }
}

// ============================================================
// Main perspective correction apply
// ============================================================

void PerspectiveOperator::apply(float* dst, int dst_width, int dst_height,
                                 const float* src, int src_width, int src_height,
                                 int channels,
                                 const float src_corners[8],
                                 float amount,
                                 int mode) {
    if (!dst || !src || dst_width <= 0 || dst_height <= 0 ||
        src_width <= 0 || src_height <= 0) return;

    // Clamp amount
    amount = std::max(0.0f, std::min(1.0f, amount));

    // If amount is near zero, just copy source to destination
    if (amount < 1e-6f) {
        for (int y = 0; y < dst_height && y < src_height; ++y) {
            for (int x = 0; x < dst_width && x < src_width; ++x) {
                int src_idx = (y * src_width + x) * channels;
                int dst_idx = (y * dst_width + x) * channels;
                for (int c = 0; c < channels; ++c) {
                    dst[dst_idx + c] = src[src_idx + c];
                }
            }
        }
        return;
    }

    // Interpolate corners between identity (full rectangle) and user-specified
    // Identity corners: TL(0,0), TR(1,0), BR(1,1), BL(0,1)
    float identity[8] = {0.0f, 0.0f, 1.0f, 0.0f, 1.0f, 1.0f, 0.0f, 1.0f};
    float interp_corners[8];
    for (int i = 0; i < 8; ++i) {
        interp_corners[i] = identity[i] + (src_corners[i] - identity[i]) * amount;
    }

    // Apply mode constraints
    float effective_corners[8];
    std::memcpy(effective_corners, interp_corners, sizeof(effective_corners));
    apply_mode_to_corners(effective_corners, mode, src_width, src_height);

    // Extract corner coordinates
    float cx[4] = {effective_corners[0], effective_corners[2],
                    effective_corners[4], effective_corners[6]};
    float cy[4] = {effective_corners[1], effective_corners[3],
                    effective_corners[5], effective_corners[7]};

    // Compute homography: maps from the distorted quad (src corners in normalized)
    // to the destination rectangle
    float H[9];
    if (!compute_homography(cx, cy, dst_width, dst_height, H)) {
        // Fallback: copy source
        for (int y = 0; y < dst_height && y < src_height; ++y) {
            for (int x = 0; x < dst_width && x < src_width; ++x) {
                int src_idx = (y * src_width + x) * channels;
                int dst_idx = (y * dst_width + x) * channels;
                for (int c = 0; c < channels; ++c) {
                    dst[dst_idx + c] = src[src_idx + c];
                }
            }
        }
        return;
    }

    // Compute inverse homography for inverse mapping
    float H_inv[9];
    if (!invert_homography(H, H_inv)) {
        // Fallback: copy source
        for (int y = 0; y < dst_height && y < src_height; ++y) {
            for (int x = 0; x < dst_width && x < src_width; ++x) {
                int src_idx = (y * src_width + x) * channels;
                int dst_idx = (y * dst_width + x) * channels;
                for (int c = 0; c < channels; ++c) {
                    dst[dst_idx + c] = src[src_idx + c];
                }
            }
        }
        return;
    }

    // Inverse mapping: for each destination pixel, find source coordinate
    for (int y = 0; y < dst_height; ++y) {
        for (int x = 0; x < dst_width; ++x) {
            float sx, sy;
            transform_point_inverse(H_inv, static_cast<float>(x), static_cast<float>(y), sx, sy);

            float out[4] = {0.0f, 0.0f, 0.0f, 1.0f};
            if (sx >= 0.0f && sx < static_cast<float>(src_width) &&
                sy >= 0.0f && sy < static_cast<float>(src_height)) {
                bilinear_sample(src, src_width, src_height, channels, sx, sy, out);
            }
            // For 4-channel images, preserve alpha when inside bounds
            int idx = (y * dst_width + x) * channels;
            for (int c = 0; c < channels; ++c) {
                dst[idx + c] = out[c];
            }
        }
    }

    LOGI("Perspective applied: amount=%.2f mode=%d", amount, mode);
}

} // namespace alcedo
