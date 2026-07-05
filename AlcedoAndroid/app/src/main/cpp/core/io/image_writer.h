// Ported from AlcedoStudio desktop: io/image_writer.hpp
// SPDX-License-Identifier: GPL-3.0-only
//
// Image writer for Android. The C++ layer prepares float data and does
// format conversion; actual file writing is delegated to the Kotlin layer
// via Android Bitmap API. No OIIO, no OpenCV.

#pragma once

#include <cstdint>
#include <string>

#include "image/image_buffer.h"

namespace alcedo {

// ============================================================
// Image format type
// ============================================================
enum class ImageFormatType {
    JPEG = 0,
    PNG,
    WEBP,
    TIFF,
    BMP,
    DNG,
    HEIF,
    EXR
};

// ============================================================
// Export format options
// ============================================================
struct ExportFormatOptions {
    ImageFormatType format_       = ImageFormatType::JPEG;
    int             quality_      = 95;        // JPEG/WEBP quality (1–100)
    int             compression_level_ = 6;    // PNG compression (0–9)

    std::string     export_path_;              // Destination file path

    // Resize
    bool            resize_enabled_ = false;
    int             max_length_side_ = 4096;   // Max dimension after resize

    // Bit depth
    enum class BIT_DEPTH {
        BIT_8  = 0,
        BIT_16 = 1,
        BIT_32 = 2
    };
    BIT_DEPTH       bit_depth_ = BIT_DEPTH::BIT_8;

    // TIFF compression
    enum class TIFF_COMPRESS {
        NONE = 0,
        LZW  = 1,
        ZIP  = 2
    };
    TIFF_COMPRESS   tiff_compress_ = TIFF_COMPRESS::NONE;

    // HDR export mode
    enum class HDR_EXPORT_MODE {
        SDR          = 0,
        HDR_TONEMAP  = 1,
        ULTRA_HDR    = 2
    };
    HDR_EXPORT_MODE hdr_export_mode_ = HDR_EXPORT_MODE::SDR;

    // Ultra HDR specific
    int             ultra_hdr_quality_       = 95;
    bool            ultra_hdr_dither_enabled_ = true;
};

// ============================================================
// Export color profile config
// ============================================================
struct ExportColorProfileConfig {
    int   encoding_space  = 0;   // 0=sRGB, 1=DisplayP3, 2=Rec2020
    int   encoding_eotf   = 0;   // 0=sRGB, 1=PQ, 2=HLG, 3=Linear
    float peak_luminance  = 100.0f;  // cd/m²
};

// ============================================================
// Image Writer
// ============================================================
class ImageWriter {
public:
    // Write an image to the given path with the specified options.
    // On Android, this prepares the pixel data (format conversion, resize)
    // and writes it as a raw ALCD blob. The Kotlin layer reads the blob and
    // performs actual encoding (JPEG/PNG/WEBP etc.) via Android Bitmap API.
    //
    // Returns true if the pixel data was written successfully.
    static bool WriteImageToPath(const ImageBuffer& buffer,
                                 const ExportFormatOptions& options,
                                 const ExportColorProfileConfig& color_config);

    // Determine whether Ultra HDR export should be used for the given buffer.
    static bool ShouldWriteUltraHdr(const ImageBuffer& buffer,
                                    const ExportFormatOptions& options);

private:
    // ── Format conversion ──
    static bool ConvertFloat32ToUint8(const float* src, uint8_t* dst,
                                      int pixel_count, int channels);
    static bool ConvertFloat32ToUint16(const float* src, uint16_t* dst,
                                       int pixel_count, int channels);

    // ── Channel conversion ──
    static bool RgbaToRgb(const uint8_t* src, uint8_t* dst, int pixel_count);
    static bool RgbaF32ToRgbF32(const float* src, float* dst, int pixel_count);

    // ── Resize (bilinear) ──
    static bool ResizeBilinearUint8(const uint8_t* src, int src_w, int src_h,
                                    uint8_t* dst, int dst_w, int dst_h, int channels);
    static bool ResizeBilinearF32(const float* src, int src_w, int src_h,
                                  float* dst, int dst_w, int dst_h, int channels);

    // ── Clamp ──
    static float ClampF32(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
};

} // namespace alcedo
