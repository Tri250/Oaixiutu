#pragma once

#include "operator_base.h"
#include <cstddef>

namespace alcedo {

class WhiteOperator : public OperatorBase<WhiteOperator> {
public:
    WhiteOperator() = default;
    explicit WhiteOperator(float white_point) : white_point_(white_point) {}

    void SetWhitePoint(float white_point) { white_point_ = white_point; }

    void ApplyImpl(float* pixels, int width, int height, int channels) {
        apply(pixels, width * height, channels, white_point_);
    }

    OperatorType GetTypeImpl() const { return OperatorType::WHITE; }

    static void apply(float* pixels, int count, int channels, float white_point);

private:
    float white_point_ = 1.0f;
};

} // namespace alcedo
