#pragma once
#include "operator_base.h"

namespace alcedo {

// Output Device Transform (ODT)
// Supports ACES ODT and OpenDRT methods
// Ported from desktop odt_op.cpp + aces_odt_cpu + open_drt_cpu
enum class ODTMethod : int { ACES = 0, OPEN_DRT = 1 };

class ODTOp : public OperatorBase<ODTOp> {
public:
    ODTOp();
    ODTOp(ODTMethod method, float peak_luminance);

    void ApplyImpl(float* pixels, int width, int height, int channels);
    OperatorType GetTypeImpl() const { return OperatorType::ODT; }

    void SetMethod(ODTMethod method) { method_ = method; }
    void SetPeakLuminance(float nits) { peak_luminance_ = nits; }
    void SetOutputSRGB();
    void SetOutputP3();
    void SetOutputRec2020();

private:
    ODTMethod method_ = ODTMethod::OPEN_DRT;
    float peak_luminance_ = 100.0f;
    int output_space_ = 0; // 0=sRGB, 1=P3, 2=Rec2020
};

} // namespace alcedo
