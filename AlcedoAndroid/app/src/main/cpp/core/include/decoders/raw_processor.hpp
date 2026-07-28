// AlcedoAndroid - RAW processor.
// Self-contained Android port of the desktop RawProcessor. The libraw coupling
// is removed: the processor operates on a mosaiced (single-channel) ImageBuffer
// plus a RawRuntimeColorContext produced by the RawDecoder, and produces a
// debayered, white-balanced, working-space (AP0/AP1) float RGB buffer. The GPU
// path uses Vulkan compute (raw_processor_vulkan.cpp); CPU fallback lives here.
// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <cstdint>
#include <optional>

#include "decoders/data_decoder.hpp"
#include "image/image_buffer.hpp"
#include "image/metadata.hpp"
#include "type/type.hpp"

namespace alcedo {

enum class RawGpuBackend { CPU, Vulkan };

inline auto IsRawGpuBackend(RawGpuBackend backend) -> bool { return backend != RawGpuBackend::CPU; }

// Bayer / X-Trans CFA pattern descriptor.
enum class RawCfaPattern : int {
  Unsupported = 0,
  RGGB        = 1,
  BGGR        = 2,
  GBRG        = 3,
  GRBG        = 4,
  XTrans      = 5,
};

enum class RawDemosaicMethod : int {
  Default     = 0,
  Bilinear    = 1,
  Ahb         = 2,   // AHD
  Rcd         = 3,
  Amaze       = 4,
  Neural      = 5,
};

enum class RawInputKind : int {
  Unsupported = 0,
  BayerCFA    = 1,
  XTransCFA   = 2,
  LinearRGB   = 3,
};

struct RawParams {
  RawGpuBackend      gpu_backend_            = RawGpuBackend::CPU;
  RawDemosaicMethod  demosaic_method_        = RawDemosaicMethod::Bilinear;
  bool               highlights_reconstruct_ = false;
  bool               use_camera_wb_          = true;
  uint32_t           user_wb_cct_            = 6500;   // custom white balance temp
  DecodeRes          decode_res_             = DecodeRes::FULL;
  // Default crop rectangle [x, y, w, h] in raw pixels (0 = full sensor).
  uint32_t           default_crop_[4]        = {0, 0, 0, 0};
};

// Processes a mosaiced RAW buffer into a linear working-space RGB ImageBuffer.
class RawProcessor {
 public:
  RawProcessor(RawParams params, RawRuntimeColorContext context,
               RawCfaPattern cfa = RawCfaPattern::RGGB,
               RawInputKind input_kind = RawInputKind::BayerCFA);

  // Run the full CPU processing pipeline. Returns the debayered RGB buffer.
  auto Process(const ImageBuffer& mosaic) -> ImageBuffer;

  auto GetRuntimeColorContext() const -> const RawRuntimeColorContext& {
    return runtime_color_context_;
  }

 private:
  void ApplyLinearization(ImageBuffer& buf) const;
  void ApplyHighlightReconstruct(ImageBuffer& buf) const;
  void ApplyDebayer(const ImageBuffer& mosaic, ImageBuffer& out) const;
  void ApplyWhiteBalance(ImageBuffer& buf) const;
  void ConvertToWorkingSpace(ImageBuffer& buf) const;

  RawParams                params_;
  RawRuntimeColorContext   runtime_color_context_;
  RawCfaPattern            cfa_pattern_;
  RawInputKind             input_kind_;
};

// Vulkan-backed RAW processing (implemented in raw_processor_vulkan.cpp).
auto ProcessRawVulkan(const ImageBuffer& mosaic, const RawParams& params,
                      const RawRuntimeColorContext& context, RawCfaPattern cfa,
                      RawInputKind input_kind) -> ImageBuffer;

}  // namespace alcedo
