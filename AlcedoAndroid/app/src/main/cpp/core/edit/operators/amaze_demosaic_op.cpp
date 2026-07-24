#include "amaze_demosaic_op.h"
#include <cmath>
#include <algorithm>
#include <vector>
#include <android/log.h>

#define LOG_TAG "AlcedoAMAZE"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace alcedo {

AMAZEDemosaicOperator::BayerInfo AMAZEDemosaicOperator::get_bayer_info(int pattern) {
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

int AMAZEDemosaicOperator::color_at(int y, int x, const BayerInfo& info) {
    int row_mod = y & 1;
    int col_mod = x & 1;
    if (row_mod == info.red_row && col_mod == info.red_col) return 0; // Red
    if (row_mod == info.blue_row && col_mod == info.blue_col) return 2; // Blue
    return 1; // Green
}

void AMAZEDemosaicOperator::amaze_interpolate(const float* src, int width, int height,
                                               const BayerInfo& info,
                                               float* output_r, float* output_g, float* output_b) {
    size_t total = static_cast<size_t>(width) * height;

    // ── Step 1: Green channel interpolation using edge-directed method ──
    // AMAZE uses a multi-direction gradient approach with adaptive weighting
    std::vector<float> g(total, 0.0f);

    // Copy known green values
    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            if (color_at(y, x, info) == 1) {
                g[y * width + x] = src[y * width + x];
            }
        }
    }

    // Interpolate green at non-green positions using AMAZE-style multi-direction gradients
    for (int y = 3; y < height - 3; ++y) {
        for (int x = 3; x < width - 3; ++x) {
            int c = color_at(y, x, info);
            if (c == 1) continue; // already have green

            int idx = y * width + x;
            float val = src[idx];

            // Compute 8 directional gradients (N, NE, E, SE, S, SW, W, NW)
            struct Direction {
                float grad;
                float green_est;
            };

            Direction dirs[8];

            // North
            dirs[0].grad = std::abs(src[(y-1)*width+x] - src[(y+1)*width+x]) +
                           std::abs(2.0f*val - src[(y-2)*width+x] - src[(y+2)*width+x]);
            dirs[0].green_est = (src[(y-1)*width+x] + src[(y+1)*width+x]) * 0.5f +
                                val * 0.5f - (src[(y-2)*width+x] + src[(y+2)*width+x]) * 0.25f;

            // South (same gradient, same estimate)
            dirs[4].grad = dirs[0].grad;
            dirs[4].green_est = dirs[0].green_est;

            // East
            dirs[2].grad = std::abs(src[y*width+(x+1)] - src[y*width+(x-1)]) +
                           std::abs(2.0f*val - src[y*width+(x+2)] - src[y*width+(x-2)]);
            dirs[2].green_est = (src[y*width+(x-1)] + src[y*width+(x+1)]) * 0.5f +
                                val * 0.5f - (src[y*width+(x-2)] + src[y*width+(x+2)]) * 0.25f;

            // West
            dirs[6].grad = dirs[2].grad;
            dirs[6].green_est = dirs[2].green_est;

            // Northeast
            dirs[1].grad = std::abs(src[(y-1)*width+(x+1)] - src[(y+1)*width+(x-1)]) +
                           2.0f * std::abs(val - src[(y-2)*width+(x+2)]);
            dirs[1].green_est = (g[(y-1)*width+x] + g[y*width+(x+1)]) * 0.5f +
                                val - (src[(y-2)*width+(x+2)] + src[(y+2)*width+(x-2)]) * 0.25f;

            // Southeast
            dirs[3].grad = std::abs(src[(y+1)*width+(x+1)] - src[(y-1)*width+(x-1)]) +
                           2.0f * std::abs(val - src[(y+2)*width+(x+2)]);
            dirs[3].green_est = (g[(y+1)*width+x] + g[y*width+(x+1)]) * 0.5f +
                                val - (src[(y+2)*width+(x+2)] + src[(y-2)*width+(x-2)]) * 0.25f;

            // Southwest
            dirs[5].grad = std::abs(src[(y+1)*width+(x-1)] - src[(y-1)*width+(x+1)]) +
                           2.0f * std::abs(val - src[(y+2)*width+(x-2)]);
            dirs[5].green_est = (g[(y+1)*width+x] + g[y*width+(x-1)]) * 0.5f +
                                val - (src[(y+2)*width+(x-2)] + src[(y-2)*width+(x+2)]) * 0.25f;

            // Northwest
            dirs[7].grad = std::abs(src[(y-1)*width+(x-1)] - src[(y+1)*width+(x+1)]) +
                           2.0f * std::abs(val - src[(y-2)*width+(x-2)]);
            dirs[7].green_est = (g[(y-1)*width+x] + g[y*width+(x-1)]) * 0.5f +
                                val - (src[(y-2)*width+(x-2)] + src[(y+2)*width+(x+2)]) * 0.25f;

            // Find minimum gradient (most reliable direction)
            float min_grad = dirs[0].grad;
            float best_green = dirs[0].green_est;
            for (int d = 1; d < 8; ++d) {
                if (dirs[d].grad < min_grad) {
                    min_grad = dirs[d].grad;
                    best_green = dirs[d].green_est;
                }
            }

            // Weighted average: low gradient = high weight
            float weight_sum = 0.0f;
            float green_sum = 0.0f;
            for (int d = 0; d < 8; ++d) {
                float w = 1.0f / (1.0f + dirs[d].grad * dirs[d].grad);
                weight_sum += w;
                green_sum += w * dirs[d].green_est;
            }

            if (weight_sum > 0.0f) {
                g[idx] = green_sum / weight_sum;
            } else {
                g[idx] = best_green;
            }

            // Clamp to [0, 1]
            g[idx] = std::clamp(g[idx], 0.0f, 1.0f);
        }
    }

    // Handle borders with simple bilinear interpolation
    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            if (g[y * width + x] > 0.0f) continue;
            int c = color_at(y, x, info);
            if (c == 1) continue;

            float sum = 0.0f;
            int count = 0;
            if (y > 0 && g[(y-1)*width+x] > 0.0f) { sum += g[(y-1)*width+x]; count++; }
            if (y < height-1 && g[(y+1)*width+x] > 0.0f) { sum += g[(y+1)*width+x]; count++; }
            if (x > 0 && g[y*width+(x-1)] > 0.0f) { sum += g[y*width+(x-1)]; count++; }
            if (x < width-1 && g[y*width+(x+1)] > 0.0f) { sum += g[y*width+(x+1)]; count++; }
            if (count > 0) {
                g[y * width + x] = sum / count;
            }
        }
    }

    // ── Step 2: Red/Blue interpolation using color difference ratios ──
    std::vector<float> diff_r(total, 0.0f);
    std::vector<float> diff_b(total, 0.0f);

    // Compute color differences at known positions
    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            int c = color_at(y, x, info);
            int idx = y * width + x;
            if (c == 0) {
                diff_r[idx] = src[idx] - g[idx];
            } else if (c == 2) {
                diff_b[idx] = src[idx] - g[idx];
            }
        }
    }

    // Interpolate color differences at missing positions using adaptive weighting
    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            int c = color_at(y, x, info);
            int idx = y * width + x;

            if (c != 0) {
                // Need to interpolate R-G difference
                float sum = 0.0f;
                float wsum = 0.0f;

                // Check 8-connected neighbors for known R values
                int offsets[8][2] = {{-1,-1},{-1,0},{-1,1},{0,-1},{0,1},{1,-1},{1,0},{1,1}};
                for (int k = 0; k < 8; ++k) {
                    int ny = y + offsets[k][0];
                    int nx = x + offsets[k][1];
                    if (ny >= 0 && ny < height && nx >= 0 && nx < width) {
                        if (color_at(ny, nx, info) == 0) {
                            float w = 1.0f;
                            // Weight by distance: cardinal directions get more weight
                            if (offsets[k][0] == 0 || offsets[k][1] == 0) w = 2.0f;
                            sum += w * diff_r[ny * width + nx];
                            wsum += w;
                        }
                    }
                }

                if (wsum > 0.0f) {
                    diff_r[idx] = sum / wsum;
                }
            }

            if (c != 2) {
                // Need to interpolate B-G difference
                float sum = 0.0f;
                float wsum = 0.0f;

                int offsets[8][2] = {{-1,-1},{-1,0},{-1,1},{0,-1},{0,1},{1,-1},{1,0},{1,1}};
                for (int k = 0; k < 8; ++k) {
                    int ny = y + offsets[k][0];
                    int nx = x + offsets[k][1];
                    if (ny >= 0 && ny < height && nx >= 0 && nx < width) {
                        if (color_at(ny, nx, info) == 2) {
                            float w = 1.0f;
                            if (offsets[k][0] == 0 || offsets[k][1] == 0) w = 2.0f;
                            sum += w * diff_b[ny * width + nx];
                            wsum += w;
                        }
                    }
                }

                if (wsum > 0.0f) {
                    diff_b[idx] = sum / wsum;
                }
            }
        }
    }

    // Reconstruct R, G, B from differences
    for (int i = 0; i < total; ++i) {
        output_r[i] = g[i] + diff_r[i];
        output_g[i] = g[i];
        output_b[i] = g[i] + diff_b[i];

        // Clamp to [0, 1]
        output_r[i] = std::clamp(output_r[i], 0.0f, 1.0f);
        output_g[i] = std::clamp(output_g[i], 0.0f, 1.0f);
        output_b[i] = std::clamp(output_b[i], 0.0f, 1.0f);
    }

    // ── Step 3: Median filter for zipper artifact removal ──
    // Apply a 3x3 median filter on color differences to reduce zipper artifacts
    std::vector<float> filtered_r(total);
    std::vector<float> filtered_b(total);

    for (int y = 1; y < height - 1; ++y) {
        for (int x = 1; x < width - 1; ++x) {
            int idx = y * width + x;
            int c = color_at(y, x, info);

            // Collect color difference values in 3x3 neighborhood
            float r_diffs[9];
            float b_diffs[9];
            int count = 0;

            for (int dy = -1; dy <= 1; ++dy) {
                for (int dx = -1; dx <= 1; ++dx) {
                    int nidx = (y + dy) * width + (x + dx);
                    r_diffs[count] = output_r[nidx] - output_g[nidx];
                    b_diffs[count] = output_b[nidx] - output_g[nidx];
                    count++;
                }
            }

            // Find median
            std::sort(r_diffs, r_diffs + 9);
            std::sort(b_diffs, b_diffs + 9);
            float median_r = r_diffs[4];
            float median_b = b_diffs[4];

            // Blend median with original (only apply to non-green pixels to avoid over-smoothing)
            if (c != 1) {
                filtered_r[idx] = output_g[idx] + median_r;
                filtered_b[idx] = output_g[idx] + median_b;
            } else {
                filtered_r[idx] = output_r[idx];
                filtered_b[idx] = output_b[idx];
            }
        }
    }

    // Copy border pixels and filtered results
    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            int idx = y * width + x;
            if (y >= 1 && y < height - 1 && x >= 1 && x < width - 1) {
                output_r[idx] = std::clamp(filtered_r[idx], 0.0f, 1.0f);
                output_b[idx] = std::clamp(filtered_b[idx], 0.0f, 1.0f);
            }
        }
    }
}

void AMAZEDemosaicOperator::demosaic_uint16(const uint16_t* raw_data, int width, int height,
                                              int bayer_pattern,
                                              float* output_r, float* output_g, float* output_b,
                                              uint16_t white_level, uint16_t black_level) {
    if (!raw_data || !output_r || !output_g || !output_b || width <= 0 || height <= 0) {
        LOGE("AMAZE demosaic: invalid parameters (w=%d, h=%d)", width, height);
        return;
    }
    size_t total = static_cast<size_t>(width) * height;
    BayerInfo info = get_bayer_info(bayer_pattern);

    std::vector<float> src(total);
    float range = static_cast<float>(white_level - black_level);
    if (range < 1.0f) range = 65535.0f;
    for (size_t i = 0; i < total; ++i) {
        float val = static_cast<float>(static_cast<int32_t>(raw_data[i]) - static_cast<int32_t>(black_level));
        src[i] = std::clamp(val / range, 0.0f, 1.0f);
    }

    amaze_interpolate(src.data(), width, height, info, output_r, output_g, output_b);

    LOGI("AMAZE demosaic completed: %dx%d, pattern=%d", width, height, bayer_pattern);
}

void AMAZEDemosaicOperator::demosaic_float(const float* raw_data, int width, int height,
                                             int bayer_pattern,
                                             float* output_r, float* output_g, float* output_b,
                                             float white_level, float black_level) {
    if (!raw_data || !output_r || !output_g || !output_b || width <= 0 || height <= 0) {
        LOGE("AMAZE demosaic (float): invalid parameters (w=%d, h=%d)", width, height);
        return;
    }
    size_t total = static_cast<size_t>(width) * height;
    BayerInfo info = get_bayer_info(bayer_pattern);

    std::vector<float> src(total);
    float range = white_level - black_level;
    if (range < 1e-6f) range = 1.0f;
    for (size_t i = 0; i < total; ++i) {
        src[i] = (raw_data[i] - black_level) / range;
        src[i] = std::max(0.0f, src[i]);
    }

    amaze_interpolate(src.data(), width, height, info, output_r, output_g, output_b);

    LOGI("AMAZE demosaic (float) completed: %dx%d", width, height);
}

} // namespace alcedo
