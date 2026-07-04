#include <vector>
#include <random>
#include <cmath>

namespace alcedo {

class FilmGrainOperator {
public:
    static void apply(std::vector<float>& pixels, int width, int height, float intensity) {
        std::mt19937 gen(42);
        std::normal_distribution<float> dist(0.0f, intensity * 0.05f);

        for (float& p : pixels) {
            p += dist(gen);
            p = std::max(0.0f, std::min(1.0f, p));
        }
    }
};

} // namespace alcedo
