#pragma once

#include <vector>

namespace alcedo {

class FilmGrainOperator {
public:
    static void apply(std::vector<float>& pixels, int width, int height, float intensity);
};

} // namespace alcedo
