#pragma once
#include "operator_base.h"
#include "core/image/raw_decoder.h"

namespace alcedo {

// RAW decode control operator
// Ported from desktop raw_decode_op.cpp
// Controls RAW processing parameters: demosaic method, white balance,
// highlight recovery, color space, etc.
using alcedo::DemosaicMethod;
enum class RawInputSpace : int { AP0 = 0, CAMERA = 1 };

class RawDecodeOp : public OperatorBase<RawDecodeOp> {
public:
    RawDecodeOp();
    RawDecodeOp(DemosaicMethod method, RawInputSpace space);

    void ApplyImpl(float* pixels, int width, int height, int channels);
    OperatorType GetTypeImpl() const { return OperatorType::RAW_DECODE; }

    void SetDemosaicMethod(DemosaicMethod method) { method_ = method; }
    void SetInputSpace(RawInputSpace space) { input_space_ = space; }
    void SetUseCameraWhiteBalance(bool use) { use_camera_wb_ = use; }
    void SetHighlightRecovery(bool enable) { highlight_recovery_ = enable; }

private:
    DemosaicMethod method_ = DemosaicMethod::RCD;
    RawInputSpace input_space_ = RawInputSpace::AP0;
    bool use_camera_wb_ = true;
    bool highlight_recovery_ = true;
};

} // namespace alcedo
