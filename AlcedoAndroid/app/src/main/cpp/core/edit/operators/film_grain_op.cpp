#include "film_grain_op.h"
#include <random>
#include <algorithm>

namespace alcedo {

void FilmGrainOperator::apply(std::vector<float>& pixels, int width, int height, int channels, float intensity) {
    if (intensity <= 0.0f || width <= 0 || height <= 0 || channels <= 0) return;
    std::mt19937 gen(42);
    std::normal_distribution<float> dist(0.0f, intensity * 0.05f);

    int total = width * height;
    int color_channels = std::min(channels, 3); // Only apply grain to RGB, not alpha
    for (int i = 0; i < total; ++i) {
        for (int c = 0; c < color_channels; ++c) {
            float& p = pixels[i * channels + c];
            p += dist(gen);
            p = std::max(0.0f, std::min(1.0f, p));
        }
    }
}

} // namespace alcedo
