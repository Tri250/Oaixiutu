#include "ahd_demosaic_op.h"
#include <cmath>
#include <algorithm>
#include <vector>
#include <cstring>
#include <android/log.h>

#define LOG_TAG "AlcedoAHD"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace alcedo {

AHDDemosaicOperator::BayerInfo AHDDemosaicOperator::get_bayer_info(int pattern) {
    BayerInfo info;
    switch (pattern) {
        case RGGB:
            info.red_row = 0; info.red_col = 0;
            info.blue_row = 1; info.blue_col = 1;
            break;
        case BGGR:
            info.red_row = 1; info.red_col = 1;
            info.blue_row = 0; info.blue_col = 0;
            break;
        case GRBG:
            info.red_row = 0; info.red_col = 1;
            info.blue_row = 1; info.blue_col = 0;
            break;
        case GBRG:
            info.red_row = 1; info.red_col = 0;
            info.blue_row = 0; info.blue_col = 1;
            break;
        default:
            info.red_row = 0; info.red_col = 0;
            info.blue_row = 1; info.blue_col = 1;
            break;
    }
    return info;
}

int AHDDemosaicOperator::color_at(int y, int x, const BayerInfo& info) {
    int row_mod = y & 1;
    int col_mod = x & 1;
    if (row_mod == info.red_row && col_mod == info.red_col) return 0; // Red
    if (row_mod == info.blue_row && col_mod == info.blue_col) return 2; // Blue
    return 1; // Green
}

void AHDDemosaicOperator::ahd_interpolate(const float* src, int width, int height,
                                           const BayerInfo& info,
                                           float* output_r, float* output_g, float* output_b) {
    int total = width * height;
    int stride = width;

    // Allocate buffers for horizontal and vertical interpolations
    // Each stores interleaved RGB
    std::vector<float> rgb_h(total * 3, 0.0f);  // horizontal direction
    std::vector<float> rgb_v(total * 3, 0.0f);  // vertical direction

    // ── Step 1: Fill known values into both H and V buffers ──
    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            int idx = y * width + x;
            int c = color_at(y, x, info);
            float val = src[idx];

            // H buffer
            rgb_h[idx * 3 + c] = val;
            // V buffer
            rgb_v[idx * 3 + c] = val;
        }
    }

    // ── Step 2: Green channel interpolation ──
    // Horizontal direction
    for (int y = 0; y < height; ++y) {
        for (int x = 2; x < width - 2; ++x) {
            int c = color_at(y, x, info);
            if (c == 1) continue; // already have green

            int idx = y * width + x;
            float val = src[idx];

            // Horizontal gradients
            float grad = std::abs(src[y * width + (x + 1)] - src[y * width + (x - 1)]) +
                         std::abs(2.0f * val - src[y * width + (x - 2)] - src[y * width + (x + 2)]);

            float g_h;
            if (grad < 1e6f) {
                // Low gradient: interpolate horizontally
                g_h = (src[y * width + (x - 1)] + src[y * width + (x + 1)]) * 0.5f +
                      val - (src[y * width + (x - 2)] + src[y * width + (x + 2)]) * 0.25f;
            } else {
                g_h = (src[y * width + (x - 1)] + src[y * width + (x + 1)]) * 0.5f;
            }
            rgb_h[idx * 3 + 1] = g_h;
        }
    }

    // Vertical direction
    for (int y = 2; y < height - 2; ++y) {
        for (int x = 0; x < width; ++x) {
            int c = color_at(y, x, info);
            if (c == 1) continue;

            int idx = y * width + x;
            float val = src[idx];

            float grad = std::abs(src[(y + 1) * width + x] - src[(y - 1) * width + x]) +
                         std::abs(2.0f * val - src[(y - 2) * width + x] - src[(y + 2) * width + x]);

            float g_v;
            if (grad < 1e6f) {
                g_v = (src[(y - 1) * width + x] + src[(y + 1) * width + x]) * 0.5f +
                      val - (src[(y - 2) * width + x] + src[(y + 2) * width + x]) * 0.25f;
            } else {
                g_v = (src[(y - 1) * width + x] + src[(y + 1) * width + x]) * 0.5f;
            }
            rgb_v[idx * 3 + 1] = g_v;
        }
    }

    // ── Step 3: Red/Blue interpolation using color differences ──
    // Horizontal direction
    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            int c = color_at(y, x, info);
            int idx = y * width + x;
            float g = rgb_h[idx * 3 + 1];

            if (c == 0) {
                // Red pixel: interpolate blue horizontally
                if (x > 0 && x < width - 1) {
                    float diff_l = rgb_h[(y * width + x - 1) * 3 + 2] - rgb_h[(y * width + x - 1) * 3 + 1];
                    float diff_r = rgb_h[(y * width + x + 1) * 3 + 2] - rgb_h[(y * width + x + 1) * 3 + 1];
                    rgb_h[idx * 3 + 2] = g + (diff_l + diff_r) * 0.5f;
                }
            } else if (c == 2) {
                // Blue pixel: interpolate red horizontally
                if (x > 0 && x < width - 1) {
                    float diff_l = rgb_h[(y * width + x - 1) * 3 + 0] - rgb_h[(y * width + x - 1) * 3 + 1];
                    float diff_r = rgb_h[(y * width + x + 1) * 3 + 0] - rgb_h[(y * width + x + 1) * 3 + 1];
                    rgb_h[idx * 3 + 0] = g + (diff_l + diff_r) * 0.5f;
                }
            } else {
                // Green pixel
                if ((y & 1) == info.red_row) {
                    // Green in red row: interpolate red horizontally, blue vertically
                    if (x > 0 && x < width - 1) {
                        float diff_l = rgb_h[(y * width + x - 1) * 3 + 0] - rgb_h[(y * width + x - 1) * 3 + 1];
                        float diff_r = rgb_h[(y * width + x + 1) * 3 + 0] - rgb_h[(y * width + x + 1) * 3 + 1];
                        rgb_h[idx * 3 + 0] = g + (diff_l + diff_r) * 0.5f;
                    }
                    // Blue: same as green row below
                    if (y > 0 && y < height - 1) {
                        float diff_t = rgb_h[((y - 1) * width + x) * 3 + 2] - rgb_h[((y - 1) * width + x) * 3 + 1];
                        float diff_b = rgb_h[((y + 1) * width + x) * 3 + 2] - rgb_h[((y + 1) * width + x) * 3 + 1];
                        rgb_h[idx * 3 + 2] = g + (diff_t + diff_b) * 0.5f;
                    }
                } else {
                    // Green in blue row: interpolate blue horizontally, red vertically
                    if (x > 0 && x < width - 1) {
                        float diff_l = rgb_h[(y * width + x - 1) * 3 + 2] - rgb_h[(y * width + x - 1) * 3 + 1];
                        float diff_r = rgb_h[(y * width + x + 1) * 3 + 2] - rgb_h[(y * width + x + 1) * 3 + 1];
                        rgb_h[idx * 3 + 2] = g + (diff_l + diff_r) * 0.5f;
                    }
                    if (y > 0 && y < height - 1) {
                        float diff_t = rgb_h[((y - 1) * width + x) * 3 + 0] - rgb_h[((y - 1) * width + x) * 3 + 1];
                        float diff_b = rgb_h[((y + 1) * width + x) * 3 + 0] - rgb_h[((y + 1) * width + x) * 3 + 1];
                        rgb_h[idx * 3 + 0] = g + (diff_t + diff_b) * 0.5f;
                    }
                }
            }
        }
    }

    // Vertical direction (similar to horizontal but with vertical neighbors)
    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            int c = color_at(y, x, info);
            int idx = y * width + x;
            float g = rgb_v[idx * 3 + 1];

            if (c == 0) {
                if (y > 0 && y < height - 1) {
                    float diff_t = rgb_v[((y - 1) * width + x) * 3 + 2] - rgb_v[((y - 1) * width + x) * 3 + 1];
                    float diff_b = rgb_v[((y + 1) * width + x) * 3 + 2] - rgb_v[((y + 1) * width + x) * 3 + 1];
                    rgb_v[idx * 3 + 2] = g + (diff_t + diff_b) * 0.5f;
                }
            } else if (c == 2) {
                if (y > 0 && y < height - 1) {
                    float diff_t = rgb_v[((y - 1) * width + x) * 3 + 0] - rgb_v[((y - 1) * width + x) * 3 + 1];
                    float diff_b = rgb_v[((y + 1) * width + x) * 3 + 0] - rgb_v[((y + 1) * width + x) * 3 + 1];
                    rgb_v[idx * 3 + 0] = g + (diff_t + diff_b) * 0.5f;
                }
            } else {
                if ((y & 1) == info.red_row) {
                    if (x > 0 && x < width - 1) {
                        float diff_l = rgb_v[(y * width + x - 1) * 3 + 0] - rgb_v[(y * width + x - 1) * 3 + 1];
                        float diff_r = rgb_v[(y * width + x + 1) * 3 + 0] - rgb_v[(y * width + x + 1) * 3 + 1];
                        rgb_v[idx * 3 + 0] = g + (diff_l + diff_r) * 0.5f;
                    }
                    if (y > 0 && y < height - 1) {
                        float diff_t = rgb_v[((y - 1) * width + x) * 3 + 2] - rgb_v[((y - 1) * width + x) * 3 + 1];
                        float diff_b = rgb_v[((y + 1) * width + x) * 3 + 2] - rgb_v[((y + 1) * width + x) * 3 + 1];
                        rgb_v[idx * 3 + 2] = g + (diff_t + diff_b) * 0.5f;
                    }
                } else {
                    if (x > 0 && x < width - 1) {
                        float diff_l = rgb_v[(y * width + x - 1) * 3 + 2] - rgb_v[(y * width + x - 1) * 3 + 1];
                        float diff_r = rgb_v[(y * width + x + 1) * 3 + 2] - rgb_v[(y * width + x + 1) * 3 + 1];
                        rgb_v[idx * 3 + 2] = g + (diff_l + diff_r) * 0.5f;
                    }
                    if (y > 0 && y < height - 1) {
                        float diff_t = rgb_v[((y - 1) * width + x) * 3 + 0] - rgb_v[((y - 1) * width + x) * 3 + 1];
                        float diff_b = rgb_v[((y + 1) * width + x) * 3 + 0] - rgb_v[((y + 1) * width + x) * 3 + 1];
                        rgb_v[idx * 3 + 0] = g + (diff_t + diff_b) * 0.5f;
                    }
                }
            }
        }
    }

    // ── Step 4: Homogeneity-directed selection ──
    // For each pixel, choose the direction (H or V) that produces
    // more homogeneous (smooth) results in a local neighborhood.

    // Homogeneity: count of similar neighbors within a threshold
    auto homogeneity = [](const float* rgb, int y, int x, int w, int h, int radius, float threshold) -> int {
        int count = 0;
        for (int dy = -radius; dy <= radius; ++dy) {
            for (int dx = -radius; dx <= radius; ++dx) {
                if (dy == 0 && dx == 0) continue;
                int ny = y + dy;
                int nx = x + dx;
                if (ny < 0 || ny >= h || nx < 0 || nx >= w) continue;

                int idx1 = (y * w + x) * 3;
                int idx2 = (ny * w + nx) * 3;

                bool similar = true;
                for (int c = 0; c < 3; ++c) {
                    if (std::abs(rgb[idx1 + c] - rgb[idx2 + c]) > threshold) {
                        similar = false;
                        break;
                    }
                }
                if (similar) count++;
            }
        }
        return count;
    };

    // Choose direction for each pixel
    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            int idx = y * width + x;

            // Compute local variance for adaptive threshold
            float local_range = 0.0f;
            for (int dy = -1; dy <= 1; ++dy) {
                for (int dx = -1; dx <= 1; ++dx) {
                    int ny = y + dy;
                    int nx = x + dx;
                    if (ny >= 0 && ny < height && nx >= 0 && nx < width) {
                        int nidx = (ny * width + nx) * 3;
                        for (int c = 0; c < 3; ++c) {
                            local_range = std::max(local_range, rgb_h[nidx + c]);
                            local_range = std::max(local_range, rgb_v[nidx + c]);
                        }
                    }
                }
            }
            float threshold = local_range * 0.03f + 0.001f;

            int h_homo = homogeneity(rgb_h.data(), y, x, width, height, 1, threshold);
            int v_homo = homogeneity(rgb_v.data(), y, x, width, height, 1, threshold);

            if (h_homo >= v_homo) {
                // Horizontal direction is more homogeneous
                output_r[idx] = rgb_h[idx * 3 + 0];
                output_g[idx] = rgb_h[idx * 3 + 1];
                output_b[idx] = rgb_h[idx * 3 + 2];
            } else {
                // Vertical direction is more homogeneous
                output_r[idx] = rgb_v[idx * 3 + 0];
                output_g[idx] = rgb_v[idx * 3 + 1];
                output_b[idx] = rgb_v[idx * 3 + 2];
            }
        }
    }

    // Clamp output to [0, 1]
    for (int i = 0; i < total; ++i) {
        output_r[i] = std::clamp(output_r[i], 0.0f, 1.0f);
        output_g[i] = std::clamp(output_g[i], 0.0f, 1.0f);
        output_b[i] = std::clamp(output_b[i], 0.0f, 1.0f);
    }
}

void AHDDemosaicOperator::demosaic_uint16(const uint16_t* raw_data, int width, int height,
                                            int bayer_pattern,
                                            float* output_r, float* output_g, float* output_b,
                                            uint16_t white_level, uint16_t black_level) {
    int total = width * height;
    BayerInfo info = get_bayer_info(bayer_pattern);

    // Convert to float and normalize
    std::vector<float> src(total);
    float range = static_cast<float>(white_level - black_level);
    if (range < 1.0f) range = 65535.0f;
    for (int i = 0; i < total; ++i) {
        src[i] = static_cast<float>(raw_data[i] - black_level) / range;
        src[i] = std::max(0.0f, src[i]);
    }

    ahd_interpolate(src.data(), width, height, info, output_r, output_g, output_b);

    LOGI("AHD demosaic completed: %dx%d, pattern=%d", width, height, bayer_pattern);
}

void AHDDemosaicOperator::demosaic_float(const float* raw_data, int width, int height,
                                           int bayer_pattern,
                                           float* output_r, float* output_g, float* output_b,
                                           float white_level, float black_level) {
    int total = width * height;
    BayerInfo info = get_bayer_info(bayer_pattern);

    std::vector<float> src(total);
    float range = white_level - black_level;
    if (range < 1e-6f) range = 1.0f;
    for (int i = 0; i < total; ++i) {
        src[i] = (raw_data[i] - black_level) / range;
        src[i] = std::max(0.0f, src[i]);
    }

    ahd_interpolate(src.data(), width, height, info, output_r, output_g, output_b);

    LOGI("AHD demosaic (float) completed: %dx%d", width, height);
}

} // namespace alcedo
