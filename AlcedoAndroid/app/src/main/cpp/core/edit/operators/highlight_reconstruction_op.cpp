#include "highlight_reconstruction_op.h"
#include <cmath>
#include <algorithm>
#include <vector>
#include <android/log.h>

#define LOG_TAG "AlcedoHlRec"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace alcedo {

HighlightReconstructionOperator::BayerInfo
HighlightReconstructionOperator::get_bayer_info(int pattern) {
    BayerInfo info;
    switch (pattern) {
        case RGGB:
            info.red_row = 0; info.red_col = 0;
            info.green1_row = 0; info.green1_col = 1;
            info.green2_row = 1; info.green2_col = 0;
            info.blue_row = 1; info.blue_col = 1;
            break;
        case BGGR:
            info.red_row = 1; info.red_col = 1;
            info.green1_row = 0; info.green1_col = 1;
            info.green2_row = 1; info.green2_col = 0;
            info.blue_row = 0; info.blue_col = 0;
            break;
        case GRBG:
            info.red_row = 0; info.red_col = 1;
            info.green1_row = 0; info.green1_col = 0;
            info.green2_row = 1; info.green2_col = 1;
            info.blue_row = 1; info.blue_col = 0;
            break;
        case GBRG:
            info.red_row = 1; info.red_col = 0;
            info.green1_row = 0; info.green1_col = 0;
            info.green2_row = 1; info.green2_col = 1;
            info.blue_row = 0; info.blue_col = 1;
            break;
        default:
            info.red_row = 0; info.red_col = 0;
            info.green1_row = 0; info.green1_col = 1;
            info.green2_row = 1; info.green2_col = 0;
            info.blue_row = 1; info.blue_col = 1;
            break;
    }
    return info;
}

bool HighlightReconstructionOperator::is_same_color(int y1, int x1, int y2, int x2, const BayerInfo& info) {
    return color_at(y1, x1, info) == color_at(y2, x2, info);
}

int HighlightReconstructionOperator::color_at(int y, int x, const BayerInfo& info) {
    int row_mod = y & 1;
    int col_mod = x & 1;
    if (row_mod == info.red_row && col_mod == info.red_col) return 0; // Red
    if (row_mod == info.blue_row && col_mod == info.blue_col) return 2; // Blue
    return 1; // Green
}

template<typename T>
static void hr_apply_impl(T* raw_data, int width, int height,
                          int bayer_pattern,
                          T clip_threshold,
                          T white_level,
                          T black_level) {
    if (!raw_data || width <= 0 || height <= 0) return;
    if (white_level <= black_level) return;

    auto info = HighlightReconstructionOperator::get_bayer_info(bayer_pattern);

    // The RawTherapee algorithm:
    // 1. For each clipped pixel, find the ratio of unclipped channels in a 3x3 neighborhood
    // 2. Use the average ratio to estimate the clipped channel value
    // 3. Blend the reconstructed value with the clipped value

    std::vector<T> result(static_cast<size_t>(width) * height);
    std::copy(raw_data, raw_data + static_cast<size_t>(width) * height, result.begin());

    for (int y = 2; y < height - 2; ++y) {
        for (int x = 2; x < width - 2; ++x) {
            T val = raw_data[y * width + x];

            if (val < clip_threshold) continue;

            int pixel_color = HighlightReconstructionOperator::color_at(y, x, info);

            // Collect unclipped neighbors of other colors to estimate ratios
            float sum_ratios = 0.0f;
            int count = 0;

            // Look at a 5x5 neighborhood
            for (int dy = -2; dy <= 2; ++dy) {
                for (int dx = -2; dx <= 2; ++dx) {
                    if (dy == 0 && dx == 0) continue;
                    int ny = y + dy;
                    int nx = x + dx;
                    if (ny < 0 || ny >= height || nx < 0 || nx >= width) continue;

                    T neighbor_val = raw_data[ny * width + nx];
                    int neighbor_color = HighlightReconstructionOperator::color_at(ny, nx, info);

                    if (neighbor_color == pixel_color) continue;
                    if (neighbor_val >= clip_threshold) continue;

                    // For each valid neighbor, we need the corresponding same-color neighbor
                    // to compute the ratio. Look for a pixel of the same color as the center
                    // in the symmetric position.
                    int sym_y = y - dy;
                    int sym_x = x - dx;
                    if (sym_y < 0 || sym_y >= height || sym_x < 0 || sym_x >= width) continue;

                    T sym_val = raw_data[sym_y * width + sym_x];
                    int sym_color = HighlightReconstructionOperator::color_at(sym_y, sym_x, info);

                    if (sym_color == pixel_color && sym_val < clip_threshold && sym_val > black_level) {
                        // Compute ratio: how much brighter is this channel relative to the center color
                        float sym_diff = static_cast<float>(sym_val - black_level);
                        if (sym_diff < 1.0f) continue; // avoid division by near-zero
                        float ratio = static_cast<float>(neighbor_val - black_level) / sym_diff;
                        sum_ratios += ratio;
                        count++;
                    }
                }
            }

            if (count > 0) {
                float avg_ratio = sum_ratios / count;
                // Clamp ratio to reasonable values
                avg_ratio = std::max(0.5f, std::min(2.0f, avg_ratio));

                // Find the brightest unclipped pixel of any color in the neighborhood
                T max_unclipped = black_level;
                for (int dy = -2; dy <= 2; ++dy) {
                    for (int dx = -2; dx <= 2; ++dx) {
                        int ny = y + dy;
                        int nx = x + dx;
                        if (ny < 0 || ny >= height || nx < 0 || nx >= width) continue;
                        T nv = raw_data[ny * width + nx];
                        if (nv < clip_threshold && nv > max_unclipped) {
                            max_unclipped = nv;
                        }
                    }
                }

                // Estimate the clipped value
                if (max_unclipped > black_level) {
                    T estimated = static_cast<T>(black_level +
                        (max_unclipped - black_level) * avg_ratio);
                    estimated = std::min(estimated, white_level);

                    // Blend with original: keep original if it's brighter, else use estimate
                    result[y * width + x] = std::max(val, estimated);
                }
            }
        }
    }

    std::copy(result.begin(), result.end(), raw_data);
    LOGI("Highlight reconstruction completed: pattern=%d", bayer_pattern);
}

void HighlightReconstructionOperator::apply(uint16_t* raw_data, int width, int height,
                                            int bayer_pattern,
                                            uint16_t clip_threshold,
                                            uint16_t white_level,
                                            uint16_t black_level) {
    hr_apply_impl<uint16_t>(raw_data, width, height, bayer_pattern,
                            clip_threshold, white_level, black_level);
}

void HighlightReconstructionOperator::apply_float(float* raw_data, int width, int height,
                                                  int bayer_pattern,
                                                  float clip_threshold,
                                                  float white_level,
                                                  float black_level) {
    hr_apply_impl<float>(raw_data, width, height, bayer_pattern,
                         clip_threshold, white_level, black_level);
}

} // namespace alcedo