#include <array>

namespace alcedo {

std::array<float, 9> identity_matrix() {
    return {1.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 1.0f};
}

std::array<float, 9> saturation_matrix(float sat) {
    float lumR = 0.299f, lumG = 0.587f, lumB = 0.114f;
    return {
        lumR * (1 - sat) + sat, lumG * (1 - sat),       lumB * (1 - sat),
        lumR * (1 - sat),       lumG * (1 - sat) + sat, lumB * (1 - sat),
        lumR * (1 - sat),       lumG * (1 - sat),       lumB * (1 - sat) + sat
    };
}

} // namespace alcedo
