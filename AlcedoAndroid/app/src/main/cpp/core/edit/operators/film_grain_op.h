#pragma once

#include <vector>

namespace alcedo {

class FilmGrainOperator {
public:
    static void apply(std::vector<float>& pixels, int width, int height, int channels, float intensity);
};

} // namespace alcedo
