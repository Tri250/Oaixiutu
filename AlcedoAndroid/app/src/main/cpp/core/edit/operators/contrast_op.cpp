#include <vector>
#include <algorithm>

namespace alcedo {

class ContrastOperator {
public:
    static void apply(std::vector<float>& pixels, int width, int height, float contrast) {
        float scale = 1.0f + contrast;
        float offset = -0.5f * scale + 0.5f;
        for (float& p : pixels) {
            p = p * scale + offset;
            p = std::max(0.0f, std::min(1.0f, p));
        }
    }
};

} // namespace alcedo
