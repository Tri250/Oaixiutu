#pragma once

#include <vector>

namespace alcedo {

/**
 * Luminance denoise using Non-Local Means (NLM) algorithm.
 * Operates on the luminance channel to reduce noise while preserving edges and detail.
 *
 * @param input    Interleaved float pixel array (RGB or RGBA).
 * @param width    Image width in pixels.
 * @param height   Image height in pixels.
 * @param channels Number of channels per pixel (3=RGB, 4=RGBA).
 * @param strength  Denoise strength in [0,1]. 0 = no denoise, 1 = maximum.
 * @param detailPreserve  Detail preservation factor in [0,1]. Higher values preserve more detail.
 * @return         Denoised pixel array (same size as input).
 */
std::vector<float> luminance_denoise_nlm(
    const std::vector<float>& input,
    int width, int height, int channels,
    float strength, float detailPreserve
);

/**
 * Chroma denoise using bilateral filtering in YCbCr color space.
 * Converts RGB to YCbCr, applies bilateral filter to Cb and Cr channels,
 * then converts back to RGB.
 *
 * @param input           Interleaved float pixel array (RGB or RGBA).
 * @param width           Image width in pixels.
 * @param height          Image height in pixels.
 * @param channels        Number of channels per pixel (3=RGB, 4=RGBA).
 * @param strength        Denoise strength in [0,1]. 0 = no denoise, 1 = maximum.
 * @param colorThreshold  Color similarity threshold in [0,1]. Higher = more aggressive blending.
 * @return                Denoised pixel array (same size as input).
 */
std::vector<float> chroma_denoise_bilateral(
    const std::vector<float>& input,
    int width, int height, int channels,
    float strength, float colorThreshold
);

} // namespace alcedo
