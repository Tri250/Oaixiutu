#pragma once

#include "operator_base.h"
#include <cstddef>

namespace alcedo {

class HighlightOperator : public OperatorBase<HighlightOperator> {
public:
    HighlightOperator() = default;
    explicit HighlightOperator(float highlight_amount) : highlight_amount_(highlight_amount) {}

    void SetHighlightAmount(float highlight_amount) { highlight_amount_ = highlight_amount; }

    void ApplyImpl(float* pixels, int width, int height, int channels) {
        apply(pixels, width * height, channels, highlight_amount_);
    }

    OperatorType GetTypeImpl() const { return OperatorType::HIGHLIGHTS; }

    static void apply(float* pixels, int count, int channels, float highlight_amount);

private:
    float highlight_amount_ = 0.0f;
};

} // namespace alcedo
