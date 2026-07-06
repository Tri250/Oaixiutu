#include "denoise.h"
#include <cmath>
#include <algorithm>
#include <cstring>

namespace alcedo {

// ============================================================
// Helper: compute luminance from RGB using Rec.709 coefficients
// ============================================================
static inline float rgb_to_luma(float r, float g, float b) {
    return 0.2126f * r + 0.7152f * g + 0.0722f * b;
}

// ============================================================
// Helper: RGB to YCbCr
// ============================================================
static inline void rgb_to_ycbcr(float r, float g, float b,
                                 float& y, float& cb, float& cr) {
    y  =  0.299f * r + 0.587f * g + 0.114f * b;
    cb = -0.168736f * r - 0.331264f * g + 0.5f * b + 0.5f;
    cr =  0.5f * r - 0.418688f * g - 0.081312f * b + 0.5f;
}

// ============================================================
// Helper: YCbCr to RGB
// ============================================================
static inline void ycbcr_to_rgb(float y, float cb, float cr,
                                 float& r, float& g, float& b) {
    cb -= 0.5f;
    cr -= 0.5f;
    r = y + 1.402f * cr;
    g = y - 0.344136f * cb - 0.714136f * cr;
    b = y + 1.772f * cb;
}

// ============================================================
// Helper: clamp value to [0, 1]
// ============================================================
static inline float clamp01(float v) {
    return std::max(0.0f, std::min(1.0f, v));
}

// ============================================================
// Luminance Denoise — Non-Local Means (NLM)
//
// Search window: 21x21 (search_radius = 10)
// Patch window: 7x7 (patch_radius = 3)
// Gaussian-weighted patch distance
// detailPreserve controls blending between original and denoised
// ============================================================
std::vector<float> luminance_denoise_nlm(
    const std::vector<float>& input,
    int width, int height, int channels,
    float strength, float detailPreserve
) {
    if (strength <= 0.0f) return input;

    const int search_radius = 10;  // 21x21 search window
    const int patch_radius  = 3;   // 7x7 patch window
    const int patch_size    = (2 * patch_radius + 1);

    // Filtering parameter h: controls decay of weights.
    // Higher strength → lower h (more aggressive smoothing)
    // Typical h is proportional to noise standard deviation.
    // With strength in [0,1], we map so that strength=0.5 gives a moderate h.
    const float h = std::max(0.01f, 0.4f * (1.0f - strength * 0.8f));
    const float h2 = h * h;

    // Precompute Gaussian weights for the patch
    std::vector<float> patch_weights(patch_size * patch_size);
    const float sigma_patch = patch_radius / 2.0f;
    const float sigma2 = 2.0f * sigma_patch * sigma_patch;
    float weight_sum = 0.0f;
    for (int dy = -patch_radius; dy <= patch_radius; ++dy) {
        for (int dx = -patch_radius; dx <= patch_radius; ++dx) {
            float w = std::exp(-(dx * dx + dy * dy) / sigma2);
            patch_weights[(dy + patch_radius) * patch_size + (dx + patch_radius)] = w;
            weight_sum += w;
        }
    }
    // Normalize patch weights
    for (auto& w : patch_weights) w /= weight_sum;

    // Step 1: Build luminance image for distance computation
    const int n = width * height;
    std::vector<float> luma(n);
    for (int i = 0; i < n; ++i) {
        int idx = i * channels;
        luma[i] = rgb_to_luma(input[idx], input[idx + 1], input[idx + 2]);
    }

    // Step 2: NLM denoising
    // Search step can be >1 for performance; use step=2 for moderate speed
    const int search_step = strength > 0.7f ? 1 : 2;

    std::vector<float> output(input.size());

    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            const int center = y * width + x;
            float sum_val[3] = {0.0f, 0.0f, 0.0f};
            float weight_total = 0.0f;

            for (int sy = -search_radius; sy <= search_radius; sy += search_step) {
                for (int sx = -search_radius; sx <= search_radius; sx += search_step) {
                    int ny = y + sy;
                    int nx = x + sx;
                    if (ny < 0 || ny >= height || nx < 0 || nx >= width) continue;

                    // Compute Gaussian-weighted patch distance
                    float dist = 0.0f;
                    for (int dy = -patch_radius; dy <= patch_radius; ++dy) {
                        for (int dx = -patch_radius; dx <= patch_radius; ++dx) {
                            int py1 = y + dy, px1 = x + dx;
                            int py2 = ny + dy, px2 = nx + dx;
                            if (py1 < 0 || py1 >= height || px1 < 0 || px1 >= width) continue;
                            if (py2 < 0 || py2 >= height || px2 < 0 || px2 >= width) continue;

                            float diff = luma[py1 * width + px1] - luma[py2 * width + px2];
                            float pw = patch_weights[(dy + patch_radius) * patch_size + (dx + patch_radius)];
                            dist += pw * diff * diff;
                        }
                    }

                    float w = std::exp(-dist / h2);
                    int neighbor = ny * width + nx;
                    int nc = neighbor * channels;
                    sum_val[0] += w * input[nc];
                    sum_val[1] += w * input[nc + 1];
                    sum_val[2] += w * input[nc + 2];
                    weight_total += w;
                }
            }

            int oc = center * channels;
            if (weight_total > 0.0f) {
                float denoised[3] = {
                    sum_val[0] / weight_total,
                    sum_val[1] / weight_total,
                    sum_val[2] / weight_total
                };

                // Blend original and denoised based on detailPreserve
                // detailPreserve=0 → full denoise, detailPreserve=1 → keep original
                float blend = detailPreserve;
                output[oc]     = clamp01(input[oc]     * blend + denoised[0] * (1.0f - blend));
                output[oc + 1] = clamp01(input[oc + 1] * blend + denoised[1] * (1.0f - blend));
                output[oc + 2] = clamp01(input[oc + 2] * blend + denoised[2] * (1.0f - blend));
            } else {
                output[oc]     = input[oc];
                output[oc + 1] = input[oc + 1];
                output[oc + 2] = input[oc + 2];
            }

            // Copy alpha channel if present
            if (channels >= 4) {
                output[oc + 3] = input[oc + 3];
            }
        }
    }

    return output;
}

// ============================================================
// Chroma Denoise — Bilateral Filter in YCbCr Space
//
// Convert RGB to YCbCr, apply bilateral filter on Cb/Cr channels,
// then convert back. Y channel is preserved (luminance detail).
// colorThreshold controls the range sigma of the bilateral filter.
// ============================================================
std::vector<float> chroma_denoise_bilateral(
    const std::vector<float>& input,
    int width, int height, int channels,
    float strength, float colorThreshold
) {
    if (strength <= 0.0f) return input;

    const int n = width * height;

    // Spatial sigma for bilateral filter
    const float sigma_s = 3.0f + strength * 5.0f;  // spatial: 3..8 pixels
    // Range sigma: colorThreshold controls how similar colors need to be
    const float sigma_r = 0.02f + colorThreshold * 0.15f;  // range: 0.02..0.17

    const float sigma_s2 = 2.0f * sigma_s * sigma_s;
    const float sigma_r2 = 2.0f * sigma_r * sigma_r;

    // Kernel radius: truncate at 2*sigma_s
    const int radius = static_cast<int>(std::ceil(2.0f * sigma_s));

    // Step 1: Convert to YCbCr
    std::vector<float> ycbcr(n * 3);
    for (int i = 0; i < n; ++i) {
        int idx = i * channels;
        rgb_to_ycbcr(input[idx], input[idx + 1], input[idx + 2],
                     ycbcr[i * 3], ycbcr[i * 3 + 1], ycbcr[i * 3 + 2]);
    }

    // Step 2: Bilateral filter on Cb and Cr channels
    std::vector<float> filtered_cb(n);
    std::vector<float> filtered_cr(n);

    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            const int ci = y * width + x;
            const float cb_center = ycbcr[ci * 3 + 1];
            const float cr_center = ycbcr[ci * 3 + 2];

            float sum_cb = 0.0f, sum_cr = 0.0f;
            float w_total = 0.0f;

            for (int dy = -radius; dy <= radius; ++dy) {
                for (int dx = -radius; dx <= radius; ++dx) {
                    int ny = y + dy;
                    int nx = x + dx;
                    if (ny < 0 || ny >= height || nx < 0 || nx >= width) continue;

                    int ni = ny * width + nx;
                    float cb_n = ycbcr[ni * 3 + 1];
                    float cr_n = ycbcr[ni * 3 + 2];

                    // Spatial weight
                    float spatial_w = std::exp(-(dx * dx + dy * dy) / sigma_s2);

                    // Range weight: use distance in CbCr space
                    float dcb = cb_n - cb_center;
                    float dcr = cr_n - cr_center;
                    float range_dist = dcb * dcb + dcr * dcr;
                    float range_w = std::exp(-range_dist / sigma_r2);

                    float w = spatial_w * range_w;
                    sum_cb += w * cb_n;
                    sum_cr += w * cr_n;
                    w_total += w;
                }
            }

            if (w_total > 0.0f) {
                filtered_cb[ci] = sum_cb / w_total;
                filtered_cr[ci] = sum_cr / w_total;
            } else {
                filtered_cb[ci] = cb_center;
                filtered_cr[ci] = cr_center;
            }
        }
    }

    // Step 3: Blend original Cb/Cr with filtered Cb/Cr based on strength
    // and convert back to RGB
    std::vector<float> output(input.size());
    for (int i = 0; i < n; ++i) {
        float y_val  = ycbcr[i * 3];
        float cb_val = ycbcr[i * 3 + 1] * (1.0f - strength) + filtered_cb[i] * strength;
        float cr_val = ycbcr[i * 3 + 2] * (1.0f - strength) + filtered_cr[i] * strength;

        float r, g, b;
        ycbcr_to_rgb(y_val, cb_val, cr_val, r, g, b);

        int idx = i * channels;
        output[idx]     = clamp01(r);
        output[idx + 1] = clamp01(g);
        output[idx + 2] = clamp01(b);

        // Copy alpha channel if present
        if (channels >= 4) {
            output[idx + 3] = input[idx + 3];
        }
    }

    return output;
}

} // namespace alcedo
