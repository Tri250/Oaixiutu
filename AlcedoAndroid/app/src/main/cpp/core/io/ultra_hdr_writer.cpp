// AlcedoAndroid - UltraHDRWriter implementation.
// Writes gain-mapped UltraHDR JPEG images for Android 14+ HDR display.
// SPDX-License-Identifier: GPL-3.0-only
#include "io/io.hpp"

#include <algorithm>
#include <cmath>
#include <filesystem>
#include <fstream>
#include <utility>
#include <vector>

#include "utils/app_logging.hpp"

namespace alcedo {

namespace {

// Log2 gain-map range encoded into the gain map JPEG. A ratio of 2^k maps to
// (k + kGainRange) / (2 * kGainRange) in [0,1]. Chosen wide enough to cover
// typical HDR display headroom without clipping common scenes.
constexpr float kGainRange = 4.0f;

}  // namespace

auto UltraHDRWriter::Write(const ImageBuffer& sdr_buffer, const ImageBuffer& hdr_buffer,
                           const std::filesystem::path& path, int quality) -> bool {
  if (sdr_buffer.Empty() || hdr_buffer.Empty()) return false;
  // The actual UltraHDR encoding follows the ISO 21496-1 gain-map specification.
  // The primary SDR JPEG is encoded via Android's Bitmap.compress; the gain map
  // is a secondary JPEG embedded in the MPF (Multi-Picture Format) container.
  // Here we compute a simple linear gain map and write the SDR JPEG plus a
  // separate gain-map JPEG alongside it so the export is verifiable on disk
  // even before the full MPF muxer (jni_export.cpp) is wired up.
  auto& sdr = sdr_buffer.GetCPUData();
  auto& hdr = hdr_buffer.GetCPUData();
  if (sdr.Width() != hdr.Width() || sdr.Height() != hdr.Height()) {
    ALOGE("UltraHDRWriter: SDR and HDR dimensions must match");
    return false;
  }

  int w = sdr.Width();
  int h = sdr.Height();
  int ch = sdr.Channels();

  // Compute per-pixel gain map ratio (log2 space per ISO 21496-1).
  std::vector<float> gain_map(sdr.Total(), 0.0f);
  for (size_t i = 0; i < sdr.Total(); ++i) {
    float s = sdr.Data()[i];
    float hv = hdr.Data()[i];
    if (s > 1e-6f && hv > 1e-6f) {
      gain_map[i] = std::log2(hv / s);
    }
  }

  // Build a displayable gain-map ImageBuffer (same dimensions as SDR) by
  // normalizing the log2 ratios into [0,1] for JPEG encoding.
  ImageBuffer gain_buf(w, h, ch);
  FloatMat& gmat = gain_buf.GetCPUData();
  for (size_t i = 0; i < gmat.Total(); ++i) {
    float v = (gain_map[i] + kGainRange) / (2.0f * kGainRange);
    gmat.Data()[i] = std::clamp(v, 0.0f, 1.0f);
  }
  gain_buf.cpu_data_valid_ = true;

  // SDR image -> primary JPEG at the requested path.
  ImageWriter writer;
  if (!writer.WriteJPEG(sdr_buffer, path, quality)) {
    ALOGE("UltraHDRWriter: failed to write SDR JPEG to %s", path.c_str());
    return false;
  }
  ALOGI("UltraHDRWriter: wrote SDR JPEG to %s (q=%d, %dx%d)", path.c_str(), quality, w, h);

  // Gain map -> secondary JPEG alongside the primary image.
  std::filesystem::path gain_path = path;
  gain_path += ".gainmap.jpg";
  if (!writer.WriteJPEG(gain_buf, gain_path, quality)) {
    ALOGE("UltraHDRWriter: failed to write gain-map JPEG to %s", gain_path.c_str());
    return false;
  }
  ALOGI("UltraHDRWriter: wrote gain-map JPEG to %s (q=%d, %dx%d)", gain_path.c_str(), quality, w, h);

  return true;
}

}  // namespace alcedo
