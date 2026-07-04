#include <vector>
#include <cmath>

namespace alcedo {

class SharpenOperator {
public:
    static void apply_rgb(std::vector<float>& pixels, int width, int height, float amount) {
        std::vector<float> copy = pixels;
        float kernel[9] = {
            0.0f, -1.0f, 0.0f,
            -1.0f, 5.0f, -1.0f,
            0.0f, -1.0f, 0.0f
        };
        float scale = amount * 0.5f;

        for (int y = 1; y < height - 1; ++y) {
            for (int x = 1; x < width - 1; ++x) {
                for (int c = 0; c < 3; ++c) {
                    float sum = 0.0f;
                    for (int ky = -1; ky <= 1; ++ky) {
                        for (int kx = -1; kx <= 1; ++kx) {
                            int idx = ((y + ky) * width + (x + kx)) * 3 + c;
                            sum += copy[idx] * kernel[(ky + 1) * 3 + (kx + 1)];
                        }
                    }
                    int center = (y * width + x) * 3 + c;
                    pixels[center] = copy[center] + (sum - copy[center]) * scale;
                }
            }
        }
    }
};

} // namespace alcedo
