#pragma once

#include "operator_base.h"
#include <cstddef>

namespace alcedo {

class BlackOperator : public OperatorBase<BlackOperator> {
public:
    BlackOperator() = default;
    explicit BlackOperator(float black_point) : black_point_(black_point) {}

    void SetBlackPoint(float black_point) { black_point_ = black_point; }

    void ApplyImpl(float* pixels, int width, int height, int channels) {
        apply(pixels, width * height, channels, black_point_);
    }

    OperatorType GetTypeImpl() const { return OperatorType::BLACK; }

    static void apply(float* pixels, int count, int channels, float black_point);

private:
    float black_point_ = 0.0f;
};

} // namespace alcedo
