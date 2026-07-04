#include <vector>
#include <algorithm>

namespace alcedo {

class WhiteBalanceOperator {
public:
    static void apply(std::vector<float>& pixels, int width, int height,
                      float temperature, float tint) {
        float temp_scale = temperature / 6500.0f;
        float r_mult = std::max(0.5f, std::min(2.0f, temp_scale));
        float b_mult = std::max(0.5f, std::min(2.0f, 2.0f - temp_scale));
        float g_offset = tint * 0.01f;

        for (size_t i = 0; i < pixels.size(); i += 3) {
            pixels[i]     *= r_mult;
            pixels[i + 1] += g_offset;
            pixels[i + 2] *= b_mult;
        }
    }
};

} // namespace alcedo
