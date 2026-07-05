#pragma once

#include <cstddef>
#include <cstdint>
#include <vector>

namespace alcedo {

class AutoExposureOperator {
public:
    // Analyzes the image histogram using a percentile-based approach to find
    // optimal exposure. Targets middle-gray (18%) at the 50th percentile.
    //
    // Returns the exposure adjustment value in stops (EV).
    // A positive value means the image is underexposed and needs brightening.
    // A negative value means the image is overexposed and needs darkening.
    // Zero means the image is already well-exposed.
    //
    // Parameters:
    //   pixels: float RGB/RGBA pixel data (0.0-1.0 range)
    //   width: image width
    //   height: image height
    //   channels: number of channels (3 or 4)
    //   target_percentile: percentile to target (default 50th = median)
    //   target_luminance: target luminance at the percentile (default 0.18 = 18% gray)
    static float compute_auto_exposure(const float* pixels, int width, int height,
                                       int channels,
                                       float target_percentile = 0.5f,
                                       float target_luminance = 0.18f);

    // Apply auto exposure to pixel data in-place
    static void apply(float* pixels, int width, int height, int channels,
                      float target_percentile = 0.5f,
                      float target_luminance = 0.18f);

    // Compute luminance histogram
    static void compute_histogram(const float* pixels, size_t pixel_count, int channels,
                                  int* histogram, int bins = 256);

    // Find percentile value from histogram
    static float find_percentile(const int* histogram, int bins, size_t pixel_count,
                                 float percentile);
};

} // namespace alcedo
