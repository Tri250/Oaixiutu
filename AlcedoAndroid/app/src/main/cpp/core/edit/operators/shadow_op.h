#pragma once

#include "operator_base.h"
#include <cstddef>

namespace alcedo {

class ShadowOperator : public OperatorBase<ShadowOperator> {
public:
    ShadowOperator() = default;
    explicit ShadowOperator(float shadow_amount) : shadow_amount_(shadow_amount) {}

    void SetShadowAmount(float shadow_amount) { shadow_amount_ = shadow_amount; }

    void ApplyImpl(float* pixels, int width, int height, int channels) {
        apply(pixels, width * height, channels, shadow_amount_);
    }

    OperatorType GetTypeImpl() const { return OperatorType::SHADOWS; }

    static void apply(float* pixels, int count, int channels, float shadow_amount);

private:
    float shadow_amount_ = 0.0f;
};

} // namespace alcedo
