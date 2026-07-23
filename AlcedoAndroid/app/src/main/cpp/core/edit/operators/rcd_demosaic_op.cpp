#include "rcd_demosaic_op.h"
#include <cmath>
#include <algorithm>
#include <vector>
#include <android/log.h>

#define LOG_TAG "AlcedoRCD"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace alcedo {

RCDDemosaicOperator::BayerInfo RCDDemosaicOperator::get_bayer_info(int pattern) {
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

int RCDDemosaicOperator::color_at(int y, int x, const BayerInfo& info) {
    int row_mod = y & 1;
    int col_mod = x & 1;
    if (row_mod == info.red_row && col_mod == info.red_col) return 0; // Red
    if (row_mod == info.blue_row && col_mod == info.blue_col) return 2; // Blue
    return 1; // Green
}

// Hamilton-Adams edge-directed green interpolation
void RCDDemosaicOperator::interpolate_green(const float* src, int width, int height,
                                             const BayerInfo& info, float* g) {
    int total = width * height;
    std::fill(g, g + total, 0.0f);

    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            int c = color_at(y, x, info);
            if (c == 1) {
                // Green pixel - copy directly
                g[y * width + x] = src[y * width + x];
            }
        }
    }

    // Interpolate green at red/blue pixels
    for (int y = 2; y < height - 2; ++y) {
        for (int x = 2; x < width - 2; ++x) {
            int c = color_at(y, x, info);
            if (c == 1) continue; // Already have green

            // Horizontal and vertical gradients
            float h_grad = std::abs(src[y * width + (x + 1)] - src[y * width + (x - 1)]) +
                          std::abs(2.0f * src[y * width + x] -
                                   src[y * width + (x + 2)] - src[y * width + (x - 2)]);
            float v_grad = std::abs(src[(y + 1) * width + x] - src[(y - 1) * width + x]) +
                          std::abs(2.0f * src[y * width + x] -
                                   src[(y + 2) * width + x] - src[(y - 2) * width + x]);

            if (h_grad < v_grad) {
                // Interpolate horizontally
                float gv = (src[y * width + (x - 1)] + src[y * width + (x + 1)]) * 0.5f;
                float cv = src[y * width + x];
                g[y * width + x] = gv + (cv - (src[y * width + (x - 2)] + src[y * width + (x + 2)]) * 0.5f) * 0.5f;
            } else if (v_grad < h_grad) {
                // Interpolate vertically
                float gv = (src[(y - 1) * width + x] + src[(y + 1) * width + x]) * 0.5f;
                float cv = src[y * width + x];
                g[y * width + x] = gv + (cv - (src[(y - 2) * width + x] + src[(y + 2) * width + x]) * 0.5f) * 0.5f;
            } else {
                // Average both directions
                float gh = (src[y * width + (x - 1)] + src[y * width + (x + 1)]) * 0.5f;
                float gv = (src[(y - 1) * width + x] + src[(y + 1) * width + x]) * 0.5f;
                g[y * width + x] = (gh + gv) * 0.5f;
            }
        }
    }

    // Handle borders with simple interpolation
    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            if (g[y * width + x] > 0.0f) continue;
            int c = color_at(y, x, info);
            if (c == 1) continue;

            float sum = 0.0f;
            int count = 0;
            if (y > 0) { sum += g[(y - 1) * width + x]; count++; }
            if (y < height - 1) { sum += g[(y + 1) * width + x]; count++; }
            if (x > 0) { sum += g[y * width + (x - 1)]; count++; }
            if (x < width - 1) { sum += g[y * width + (x + 1)]; count++; }
            if (count > 0) {
                g[y * width + x] = sum / count;
            }
        }
    }
}

// RCD-style red/blue interpolation using color difference ratios
void RCDDemosaicOperator::interpolate_red_blue(const float* src, const float* g,
                                                int width, int height,
                                                const BayerInfo& info,
                                                float* r, float* b) {
    int total = width * height;
    std::vector<float> diff_r(total, 0.0f);
    std::vector<float> diff_b(total, 0.0f);

    // At known R and B pixels, compute R-G and B-G differences
    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            int c = color_at(y, x, info);
            int idx = y * width + x;
            if (c == 0) {
                // Red pixel
                diff_r[idx] = src[idx] - g[idx];
                r[idx] = src[idx];
            } else if (c == 2) {
                // Blue pixel
                diff_b[idx] = src[idx] - g[idx];
                b[idx] = src[idx];
            }
        }
    }

    // Interpolate R-G differences at missing R positions
    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            int c = color_at(y, x, info);
            int idx = y * width + x;

            if (c != 0) {
                // Interpolate R-G difference
                float sum = 0.0f;
                int count = 0;

                // Check 4 diagonal neighbors
                int offsets[4][2] = {{-1,-1}, {-1,1}, {1,-1}, {1,1}};
                for (int k = 0; k < 4; ++k) {
                    int ny = y + offsets[k][0];
                    int nx = x + offsets[k][1];
                    if (ny >= 0 && ny < height && nx >= 0 && nx < width) {
                        int nidx = ny * width + nx;
                        if (color_at(ny, nx, info) == 0) {
                            sum += diff_r[nidx];
                            count++;
                        }
                    }
                }

                if (count > 0) {
                    diff_r[idx] = sum / count;
                } else {
                    // Fallback to horizontal/vertical
                    if (y > 0) { if (color_at(y-1, x, info) == 0) { diff_r[idx] = diff_r[(y-1)*width+x]; } }
                    else if (y < height-1) { if (color_at(y+1, x, info) == 0) { diff_r[idx] = diff_r[(y+1)*width+x]; } }
                    else if (x > 0) { if (color_at(y, x-1, info) == 0) { diff_r[idx] = diff_r[y*width+(x-1)]; } }
                    else if (x < width-1) { if (color_at(y, x+1, info) == 0) { diff_r[idx] = diff_r[y*width+(x+1)]; } }
                }

                r[idx] = g[idx] + diff_r[idx];
            }

            if (c != 2) {
                // Interpolate B-G difference
                float sum = 0.0f;
                int count = 0;

                int offsets[4][2] = {{-1,-1}, {-1,1}, {1,-1}, {1,1}};
                for (int k = 0; k < 4; ++k) {
                    int ny = y + offsets[k][0];
                    int nx = x + offsets[k][1];
                    if (ny >= 0 && ny < height && nx >= 0 && nx < width) {
                        int nidx = ny * width + nx;
                        if (color_at(ny, nx, info) == 2) {
                            sum += diff_b[nidx];
                            count++;
                        }
                    }
                }

                if (count > 0) {
                    diff_b[idx] = sum / count;
                } else {
                    if (y > 0) { if (color_at(y-1, x, info) == 2) { diff_b[idx] = diff_b[(y-1)*width+x]; } }
                    else if (y < height-1) { if (color_at(y+1, x, info) == 2) { diff_b[idx] = diff_b[(y+1)*width+x]; } }
                    else if (x > 0) { if (color_at(y, x-1, info) == 2) { diff_b[idx] = diff_b[y*width+(x-1)]; } }
                    else if (x < width-1) { if (color_at(y, x+1, info) == 2) { diff_b[idx] = diff_b[y*width+(x+1)]; } }
                }

                b[idx] = g[idx] + diff_b[idx];
            }
        }
    }

    // Clamp values to [0, 1]
    for (int i = 0; i < total; ++i) {
        r[i] = std::clamp(r[i], 0.0f, 1.0f);
        b[i] = std::clamp(b[i], 0.0f, 1.0f);
    }
}

void RCDDemosaicOperator::demosaic_uint16(const uint16_t* raw_data, int width, int height,
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

    // Interpolate green
    std::vector<float> g(total);
    interpolate_green(src.data(), width, height, info, g.data());

    // Interpolate red and blue
    interpolate_red_blue(src.data(), g.data(), width, height, info, output_r, output_b);

    // Copy green
    std::copy(g.begin(), g.end(), output_g);

    LOGI("RCD demosaic completed: %dx%d, pattern=%d", width, height, bayer_pattern);
}

void RCDDemosaicOperator::demosaic_float(const float* raw_data, int width, int height,
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

    std::vector<float> g(total);
    interpolate_green(src.data(), width, height, info, g.data());

    interpolate_red_blue(src.data(), g.data(), width, height, info, output_r, output_b);

    std::copy(g.begin(), g.end(), output_g);

    LOGI("RCD demosaic (float) completed: %dx%d", width, height);
}

} // namespace alcedo