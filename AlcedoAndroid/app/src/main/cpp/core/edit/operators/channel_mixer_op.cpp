#include "channel_mixer_op.h"
#include <algorithm>

namespace alcedo {

void ChannelMixerOperator::apply_rgb(float* pixels, int width, int height,
                                      const float matrix[9], bool monochrome) {
    size_t total = static_cast<size_t>(width) * height;

    for (size_t i = 0; i < total; ++i) {
        size_t idx = i * 3;
        float r = pixels[idx];
        float g = pixels[idx + 1];
        float b = pixels[idx + 2];

        float r_out = r * matrix[0] + g * matrix[1] + b * matrix[2];
        float g_out = r * matrix[3] + g * matrix[4] + b * matrix[5];
        float b_out = r * matrix[6] + g * matrix[7] + b * matrix[8];

        if (monochrome) {
            // Average the three channels for grayscale output
            float gray = r_out * 0.299f + g_out * 0.587f + b_out * 0.114f;
            pixels[idx]     = gray;
            pixels[idx + 1] = gray;
            pixels[idx + 2] = gray;
        } else {
            pixels[idx]     = r_out;
            pixels[idx + 1] = g_out;
            pixels[idx + 2] = b_out;
        }
    }
}

void ChannelMixerOperator::apply_rgba(float* pixels, int width, int height,
                                       const float matrix[9], bool monochrome) {
    size_t total = static_cast<size_t>(width) * height;

    for (size_t i = 0; i < total; ++i) {
        size_t idx = i * 4;
        float r = pixels[idx];
        float g = pixels[idx + 1];
        float b = pixels[idx + 2];

        float r_out = r * matrix[0] + g * matrix[1] + b * matrix[2];
        float g_out = r * matrix[3] + g * matrix[4] + b * matrix[5];
        float b_out = r * matrix[6] + g * matrix[7] + b * matrix[8];

        if (monochrome) {
            float gray = r_out * 0.299f + g_out * 0.587f + b_out * 0.114f;
            pixels[idx]     = gray;
            pixels[idx + 1] = gray;
            pixels[idx + 2] = gray;
        } else {
            pixels[idx]     = r_out;
            pixels[idx + 1] = g_out;
            pixels[idx + 2] = b_out;
        }
    }
}

void ChannelMixerOperator::preset_identity(float matrix[9]) {
    float m[9] = {1,0,0, 0,1,0, 0,0,1};
    for (int i = 0; i < 9; ++i) matrix[i] = m[i];
}

void ChannelMixerOperator::preset_bw_red_filter(float matrix[9]) {
    float m[9] = {0.393f,0.769f,0.189f, 0.349f,0.686f,0.168f, 0.272f,0.534f,0.131f};
    for (int i = 0; i < 9; ++i) matrix[i] = m[i];
}

void ChannelMixerOperator::preset_bw_green_filter(float matrix[9]) {
    float m[9] = {0.17f,0.5f,0.3f, 0.17f,0.5f,0.3f, 0.17f,0.5f,0.3f};
    for (int i = 0; i < 9; ++i) matrix[i] = m[i];
}

void ChannelMixerOperator::preset_bw_blue_filter(float matrix[9]) {
    float m[9] = {0.131f,0.534f,0.272f, 0.168f,0.686f,0.349f, 0.189f,0.769f,0.393f};
    for (int i = 0; i < 9; ++i) matrix[i] = m[i];
}

void ChannelMixerOperator::preset_bw_average(float matrix[9]) {
    float m[9] = {0.333f,0.333f,0.333f, 0.333f,0.333f,0.333f, 0.333f,0.333f,0.333f};
    for (int i = 0; i < 9; ++i) matrix[i] = m[i];
}

void ChannelMixerOperator::preset_bw_luminance(float matrix[9]) {
    float m[9] = {0.2126f,0.7152f,0.0722f, 0.2126f,0.7152f,0.0722f, 0.2126f,0.7152f,0.0722f};
    for (int i = 0; i < 9; ++i) matrix[i] = m[i];
}

} // namespace alcedo