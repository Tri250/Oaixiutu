// Ported from AlcedoStudio desktop: io/ultra_hdr_writer.hpp
// SPDX-License-Identifier: GPL-3.0-only
//
// Ultra HDR (Gain Map) writer for Android. Uses Android's libultrahdr
// (available in AOSP). The actual encoding calls are behind
// ALCEDO_HAS_ULTRAHDR guard.

#pragma once

#include <cstdint>
#include <string>
#include <vector>

#include "image/image_buffer.h"
#include "io/image_writer.h"

namespace alcedo {

// ============================================================
// Ultra HDR Writer
// ============================================================
class UltraHdrWriter {
public:
    // Write an Ultra HDR JPEG (Gain Map) image.
    // The SDR base is an 8-bit JPEG; the gain map encodes the HDR intent.
    // On Android, actual encoding uses libultrahdr (guarded).
    //
    // Returns true if the output was written successfully.
    static bool WriteUltraHdr(const ImageBuffer& hdr_buffer,
                              const ExportFormatOptions& options,
                              const ExportColorProfileConfig& color_config);

    // Check if libultrahdr is available at compile time.
    static bool IsUltraHdrAvailable();

private:
    // ── PQ (SMPTE ST 2084) decode ──
    // Converts PQ-encoded signal value [0,1] to linear light.
    static float PqDecode(float signal);

    // ── HLG decode ──
    // Converts HLG signal value [0,1] to linear light.
    static float HlgDecode(float signal, float peak_luminance = 1000.0f);

    // ── HDR intent to linear ──
    // Converts the HDR buffer's pixel data to scene-linear float values
    // based on the EOTF specified in color_config.
    static bool ConvertHdrToLinear(const ImageBuffer& hdr_buffer,
                                   const ExportColorProfileConfig& color_config,
                                   std::vector<float>& linear_rgba);

    // ── SDR base generation ──
    // Tone-maps linear HDR data to SDR (sRGB) 8-bit.
    static bool GenerateSdrBase(const float* linear_rgba,
                                int width, int height,
                                std::vector<uint8_t>& sdr_rgb);

    // ── Gain map computation ──
    // Computes the ratio between HDR and SDR for the gain map.
    static bool ComputeGainMap(const float* linear_hdr,
                               const uint8_t* sdr_rgb,
                               int width, int height,
                               float hdr_headroom,
                               std::vector<float>& gain_map);

    // ── Ordered dithering ──
    // Applies Bayer ordered dithering to reduce banding in 8-bit output.
    static void ApplyOrderedDither(uint8_t* data, int width, int height,
                                   int channels, int strength = 1);

    // ── Encode with libultrahdr ──
    // The actual call to libultrahdr's GainMapMetadata and Jpeg encoding.
    // Only available when ALCEDO_HAS_ULTRAHDR is defined.
    static bool EncodeWithLibUltraHdr(const uint8_t* sdr_jpeg_data,
                                      size_t sdr_jpeg_size,
                                      const float* gain_map,
                                      int gain_map_width, int gain_map_height,
                                      float hdr_headroom,
                                      int quality,
                                      std::vector<uint8_t>& output_jpeg);
};

} // namespace alcedo
