#pragma once
#include "operator_base.h"

namespace alcedo {

// OpenCV-style color space conversion operator
// Supports conversion codes similar to cv::cvtColor
// Uses native color_science.h functions (no OpenCV dependency)
class CVCvtColorOp : public OperatorBase<CVCvtColorOp> {
public:
    enum Code {
        RGB2BGR = 0, BGR2RGB = 1,
        RGB2HSV = 2, HSV2RGB = 3,
        RGB2Lab = 4, Lab2RGB = 5,
        RGB2YCrCb = 6, YCrCb2RGB = 7,
        RGB2GRAY = 8
    };

    CVCvtColorOp();
    explicit CVCvtColorOp(Code code);

    void ApplyImpl(float* pixels, int width, int height, int channels);
    OperatorType GetTypeImpl() const { return OperatorType::CV_CVT; }

    void SetCode(Code code) { code_ = code; }

private:
    Code code_ = RGB2BGR;
};

} // namespace alcedo
