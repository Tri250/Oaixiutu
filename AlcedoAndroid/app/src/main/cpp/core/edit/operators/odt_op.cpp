#include "odt_op.h"
#include "../color_science.h"
#include <cmath>
#include <algorithm>

namespace alcedo {

ODTOp::ODTOp() = default;

ODTOp::ODTOp(ODTMethod method, float peak_luminance)
    : method_(method), peak_luminance_(peak_luminance) {}

void ODTOp::SetOutputSRGB() { output_space_ = 0; }
void ODTOp::SetOutputP3() { output_space_ = 1; }
void ODTOp::SetOutputRec2020() { output_space_ = 2; }

void ODTOp::ApplyImpl(float* pixels, int width, int height, int channels) {
    size_t total = static_cast<size_t>(width) * height;

    for (size_t i = 0; i < total; ++i) {
        size_t idx = i * channels;
        float r = pixels[idx];
        float g = pixels[idx + 1];
        float b = pixels[idx + 2];

        // Apply the selected ODT method
        if (method_ == ODTMethod::ACES) {
            // ACES Output Transform
            // The aces_output_transform_srgb includes RRT + ODT for sRGB output
            color_science::aces_output_transform_srgb(&r, &g, &b, peak_luminance_);
        } else {
            // OpenDRT Output Transform
            color_science::opendrt_output_transform(&r, &g, &b, peak_luminance_);
        }

        // If output is not sRGB, convert from sRGB to the target space
        if (output_space_ == 1) {
            // sRGB → Display P3
            color_science::convert_color_space(&r, &g, &b, 0, 1);
        } else if (output_space_ == 2) {
            // sRGB → Rec2020
            color_science::convert_color_space(&r, &g, &b, 0, 2);
        }

        pixels[idx]     = std::clamp(r, 0.0f, 1.0f);
        pixels[idx + 1] = std::clamp(g, 0.0f, 1.0f);
        pixels[idx + 2] = std::clamp(b, 0.0f, 1.0f);
    }
}

} // namespace alcedo
