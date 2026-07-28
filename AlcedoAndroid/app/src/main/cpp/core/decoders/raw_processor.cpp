// AlcedoAndroid - RawProcessor CPU implementation.
// Implements linearization, bilinear debayer, white balance and a best-effort
// camera->working-space (AP1) conversion using the matrices in the runtime
// color context. With the placeholder RAW decoder the matrices are identity,
// so the output is the debayered + white-balanced buffer.
// SPDX-License-Identifier: GPL-3.0-only
#include "decoders/raw_processor.hpp"

#include <algorithm>
#include <cmath>
#include <cstring>

#include "utils/app_logging.hpp"

namespace alcedo {

RawProcessor::RawProcessor(RawParams params, RawRuntimeColorContext context,
                           RawCfaPattern cfa, RawInputKind input_kind)
    : params_(std::move(params)),
      runtime_color_context_(std::move(context)),
      cfa_pattern_(cfa),
      input_kind_(input_kind) {}

void RawProcessor::ApplyLinearization(ImageBuffer& buf) const {
  // The placeholder RAW decoder already yields normalized [0,1] values; a real
  // build would apply the black-level + white-level + per-pixel linearization
  // table here. Kept as a clamp for safety.
  FloatMat& m = buf.GetCPUData();
  for (size_t i = 0; i < m.Total(); ++i) {
    m.Data()[i] = std::max(0.0f, m.Data()[i]);
  }
}

void RawProcessor::ApplyHighlightReconstruct(ImageBuffer& buf) const {
  if (!params_.highlights_reconstruct_) return;
  // Simple clip-based highlight reconstruction: any value >= 1.0 is desaturated
  // toward the channel average to avoid magenta highlights.
  FloatMat& m = buf.GetCPUData();
  if (m.Channels() < 3) return;
  for (int y = 0; y < m.Height(); ++y) {
    for (int x = 0; x < m.Width(); ++x) {
      float* p = m.Ptr(y, x);
      if (p[0] >= 1.0f || p[1] >= 1.0f || p[2] >= 1.0f) {
        const float avg = (p[0] + p[1] + p[2]) / 3.0f;
        for (int c = 0; c < 3; ++c) p[c] = std::min(1.0f, 0.5f * p[c] + 0.5f * avg);
      }
    }
  }
}

// Bilinear demosaic for a 2x2 Bayer pattern. Produces a 3-channel RGB buffer.
void RawProcessor::ApplyDebayer(const ImageBuffer& mosaic, ImageBuffer& out) const {
  if (input_kind_ == RawInputKind::LinearRGB) {
    out = mosaic.Clone();
    return;
  }
  const FloatMat& in = mosaic.GetCPUData();
  if (in.Empty()) return;
  const int w = in.Width();
  const int h = in.Height();
  out = ImageBuffer(w, h, 3);
  FloatMat& dst = out.GetCPUData();

  // Determine the (row, col) -> channel mapping for the Bayer pattern.
  // Pattern phase is RGGB by default; adjust for the other orderings.
  auto channel_at = [](RawCfaPattern cfa, int y, int x) -> int {
    const int yr = y & 1;
    const int xr = x & 1;
    switch (cfa) {
      case RawCfaPattern::RGGB: return (yr == 0 && xr == 0) ? 0 : (yr == 0) ? 1 : (xr == 0) ? 1 : 2;
      case RawCfaPattern::BGGR: return (yr == 0 && xr == 0) ? 2 : (yr == 0) ? 1 : (xr == 0) ? 1 : 0;
      case RawCfaPattern::GBRG: return (yr == 0 && xr == 0) ? 1 : (yr == 0) ? 2 : (xr == 0) ? 0 : 1;
      case RawCfaPattern::GRBG: return (yr == 0 && xr == 0) ? 1 : (yr == 0) ? 0 : (xr == 0) ? 2 : 1;
      case RawCfaPattern::XTrans:
      case RawCfaPattern::Unsupported:
      default:                  return (yr == 0 && xr == 0) ? 0 : (yr == 0) ? 1 : (xr == 0) ? 1 : 2;
    }
  };

  for (int y = 0; y < h; ++y) {
    for (int x = 0; x < w; ++x) {
      const int yc0 = std::max(0, y - 1), yc1 = std::min(h - 1, y + 1);
      const int xc0 = std::max(0, x - 1), xc1 = std::min(w - 1, x + 1);
      const float v00 = in.Ptr(y, x)[0];
      const float vN  = in.Ptr(yc0, x)[0];
      const float vS  = in.Ptr(yc1, x)[0];
      const float vW  = in.Ptr(y, xc0)[0];
      const float vE  = in.Ptr(y, xc1)[0];
      const float vNW = in.Ptr(yc0, xc0)[0];
      const float vNE = in.Ptr(yc0, xc1)[0];
      const float vSW = in.Ptr(yc1, xc0)[0];
      const float vSE = in.Ptr(yc1, xc1)[0];
      float* d = dst.Ptr(y, x);
      const int c = channel_at(cfa_pattern_, y, x);
      if (c == 0) {
        d[0] = v00;
        d[1] = 0.25f * (vN + vS + vW + vE);
        d[2] = 0.25f * (vNW + vNE + vSW + vSE);
      } else if (c == 2) {
        d[0] = 0.25f * (vNW + vNE + vSW + vSE);
        d[1] = 0.25f * (vN + vS + vW + vE);
        d[2] = v00;
      } else {
        d[0] = 0.5f * (vW + vE);
        d[1] = v00;
        d[2] = 0.5f * (vN + vS);
      }
    }
  }
  out.cpu_data_valid_ = true;
}

void RawProcessor::ApplyWhiteBalance(ImageBuffer& buf) const {
  FloatMat& m = buf.GetCPUData();
  if (m.Channels() < 3) return;
  float wb[3] = {1.0f, 1.0f, 1.0f};
  if (params_.use_camera_wb_) {
    for (int c = 0; c < 3; ++c) wb[c] = runtime_color_context_.cam_mul_[c];
  }
  // Normalize so the green multiplier is 1.
  const float g = wb[1] > 0.0f ? wb[1] : 1.0f;
  for (int c = 0; c < 3; ++c) wb[c] /= g;
  for (int y = 0; y < m.Height(); ++y) {
    for (int x = 0; x < m.Width(); ++x) {
      float* p = m.Ptr(y, x);
      for (int c = 0; c < 3; ++c) p[c] *= wb[c];
    }
  }
}

void RawProcessor::ConvertToWorkingSpace(ImageBuffer& buf) const {
  // Apply cam_xyz_ (3x3) to map camera RGB -> XYZ, then a fixed XYZ->AP1 matrix.
  // When cam_xyz_ is identity (placeholder), this reduces to XYZ->AP1.
  const float* cam_xyz = runtime_color_context_.cam_xyz_;
  // XYZ D65 -> ACES AP1 (approximate, from the ACES working space spec).
  static const float k_xyz_to_ap1[9] = {
      0.6624541811f, 0.1340042065f, 0.1561876870f,
      0.2722287168f, 0.6740817658f, 0.0536895174f,
     -0.0055746495f, 0.0040607335f, 1.0103391003f};
  float m[9];
  for (int r = 0; r < 3; ++r) {
    for (int c = 0; c < 3; ++c) {
      m[r * 3 + c] = 0.0f;
      for (int k = 0; k < 3; ++k) {
        m[r * 3 + c] += k_xyz_to_ap1[r * 3 + k] * cam_xyz[k * 3 + c];
      }
    }
  }
  FloatMat& mat = buf.GetCPUData();
  if (mat.Channels() < 3) return;
  for (int y = 0; y < mat.Height(); ++y) {
    for (int x = 0; x < mat.Width(); ++x) {
      float* p = mat.Ptr(y, x);
      const float r = p[0], g = p[1], b = p[2];
      p[0] = m[0] * r + m[1] * g + m[2] * b;
      p[1] = m[3] * r + m[4] * g + m[5] * b;
      p[2] = m[6] * r + m[7] * g + m[8] * b;
    }
  }
}

auto RawProcessor::Process(const ImageBuffer& mosaic) -> ImageBuffer {
  ImageBuffer work = mosaic.Clone();
  ApplyLinearization(work);
  ApplyHighlightReconstruct(work);

  ImageBuffer debayered;
  ApplyDebayer(work, debayered);
  ApplyWhiteBalance(debayered);
  ConvertToWorkingSpace(debayered);

  // Honor decode-res downsample for preview-quality decodes.
  if (params_.decode_res_ != DecodeRes::FULL) {
    int factor = 1;
    switch (params_.decode_res_) {
      case DecodeRes::HALF:    factor = 2; break;
      case DecodeRes::QUARTER: factor = 4; break;
      case DecodeRes::EIGHTH:  factor = 8; break;
      case DecodeRes::FULL:    factor = 1; break;
    }
    if (factor > 1) {
      const FloatMat& src = debayered.GetCPUData();
      int sw = src.Width(), sh = src.Height();
      int tw = std::max(1, sw / factor), th = std::max(1, sh / factor);
      ImageBuffer dst(tw, th, 3);
      FloatMat& dm = dst.GetCPUData();
      for (int y = 0; y < th; ++y) {
        for (int x = 0; x < tw; ++x) {
          const float* sp = src.Ptr(y * factor, x * factor);
          float* dp = dm.Ptr(y, x);
          for (int c = 0; c < 3; ++c) dp[c] = sp[c];
        }
      }
      dst.cpu_data_valid_ = true;
      return dst;
    }
  }
  return debayered;
}

}  // namespace alcedo
