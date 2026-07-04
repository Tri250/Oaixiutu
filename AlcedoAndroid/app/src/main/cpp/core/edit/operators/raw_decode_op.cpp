#include "raw_decode_op.h"
#include <algorithm>

namespace alcedo {

RawDecodeOp::RawDecodeOp() = default;

RawDecodeOp::RawDecodeOp(DemosaicMethod method, RawInputSpace space)
    : method_(method), input_space_(space) {}

void RawDecodeOp::ApplyImpl(float* pixels, int width, int height, int channels) {
    // This is a parameter-holder operator. The actual RAW decoding
    // (demosaicing, white balance, highlight recovery) happens in the
    // decoder layer (raw_decoder.h/cpp) before the pipeline runs.
    //
    // This operator serves as a signal to the pipeline about what decode
    // preferences to use. When the pipeline encounters this op, it reads
    // the parameters and uses them to configure the decoder.
    //
    // In the ApplyImpl pass, we validate the parameters and optionally
    // apply a simple input space conversion if the data is in camera space
    // and needs to be converted to AP0.

    if (input_space_ == RawInputSpace::CAMERA) {
        // When in CAMERA space, the pixel data is in the camera's native
        // color space. We leave it as-is since the CSTOp will handle
        // the conversion to working space later in the pipeline.
        // This is just a marker that the data is not yet in AP0.
    }

    // Validate parameters
    method_ = std::clamp(method_, DemosaicMethod::AHD, DemosaicMethod::RCD);
    input_space_ = std::clamp(input_space_, RawInputSpace::AP0, RawInputSpace::CAMERA);
}

} // namespace alcedo
