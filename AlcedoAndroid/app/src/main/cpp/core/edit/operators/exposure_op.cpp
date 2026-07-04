#include <vector>
#include <cmath>

namespace alcedo {

class ExposureOperator {
public:
    static void apply(std::vector<float>& pixels, int width, int height, float exposure_stops) {
        float scale = std::pow(2.0f, exposure_stops);
        for (size_t i = 0; i < pixels.size(); ++i) {
            pixels[i] *= scale;
        }
    }
};

} // namespace alcedo
