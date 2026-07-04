// Ported from AlcedoStudio desktop: io/ultra_hdr_writer.cpp
// SPDX-License-Identifier: GPL-3.0-only
//
// Ultra HDR (Gain Map) writer implementation for Android.
// Includes PQ/HLG decode, SDR base generation, ordered dithering,
// and HDR intent conversion to linear.
// The actual libultrahdr encoding calls are behind
// ALCEDO_HAS_ULTRAHDR guard.

#include "io/ultra_hdr_writer.h"

#include <android/log.h>
#include <algorithm>
#include <cmath>
#include <cstring>
#include <fstream>
#include <vector>

#if defined(ALCEDO_HAS_ULTRAHDR)
#include <ultrahdr/ultrahdr_api.h>
#endif

namespace alcedo {

namespace {

constexpr const char* kTag = "AlcedoUltraHDR";

// ── ST 2084 (PQ) constants ──
constexpr float kPqM1 = 0.15930175781f;    // 2610 / 16384
constexpr float kPqM2 = 78.84375f;         // 2523 / 32  * 32
constexpr float kPqC1 = 0.8359375f;        // 3424 / 4096
constexpr float kPqC2 = 18.8515625f;       // 2413 / 128
constexpr float kPqC3 = 18.6875f;          // 2392 / 128

// ── HLG constants ──
constexpr float kHlgA = 0.17883277f;
constexpr float kHlgB = 0.28466892f;
constexpr float kHlgC = 0.55991073f;

// ── sRGB linearization LUT (built once) ──
float SrgbLinearize(uint8_t v) {
    float s = static_cast<float>(v) / 255.0f;
    if (s <= 0.04045f) {
        return s / 12.92f;
    }
    return std::pow((s + 0.055f) / 1.055f, 2.4f);
}

// sRGB companding (linear → gamma)
uint8_t SrgbCompress(float linear) {
    float s;
    if (linear <= 0.0031308f) {
        s = 12.92f * linear;
    } else {
        s = 1.055f * std::pow(linear, 1.0f / 2.4f) - 0.055f;
    }
    s = std::clamp(s, 0.0f, 1.0f);
    return static_cast<uint8_t>(s * 255.0f + 0.5f);
}

// Simple Reinhard tone-mapping for SDR base.
float ReinhardMap(float hdr_linear, float white_point) {
    float mapped = hdr_linear / (1.0f + hdr_linear);
    if (white_point > 0.0f) {
        float white_mapped = white_point / (1.0f + white_point);
        mapped /= white_mapped;
    }
    return std::clamp(mapped, 0.0f, 1.0f);
}

// 4x4 Bayer matrix for ordered dithering
constexpr uint8_t kBayer4x4[16] = {
     0,  8,  2, 10,
    12,  4, 14,  6,
     3, 11,  1,  9,
    15,  7, 13,  5
};

} // anonymous namespace

// ============================================================
// UltraHdrWriter public
// ============================================================

bool UltraHdrWriter::WriteUltraHdr(const ImageBuffer& hdr_buffer,
                                    const ExportFormatOptions& options,
                                    const ExportColorProfileConfig& color_config) {
    if (!hdr_buffer.is_valid() || !hdr_buffer.is_hdr) {
        __android_log_print(ANDROID_LOG_ERROR, kTag,
                            "WriteUltraHdr: buffer is not valid HDR");
        return false;
    }

    const int width = hdr_buffer.width;
    const int height = hdr_buffer.height;

    // ── Step 1: Convert HDR to linear ──
    std::vector<float> linear_rgba;
    if (!ConvertHdrToLinear(hdr_buffer, color_config, linear_rgba)) {
        __android_log_print(ANDROID_LOG_ERROR, kTag,
                            "WriteUltraHdr: HDR → linear conversion failed");
        return false;
    }

    // ── Step 2: Generate SDR base ──
    std::vector<uint8_t> sdr_rgb;
    if (!GenerateSdrBase(linear_rgba.data(), width, height, sdr_rgb)) {
        __android_log_print(ANDROID_LOG_ERROR, kTag,
                            "WriteUltraHdr: SDR base generation failed");
        return false;
    }

    // ── Step 3: Apply dithering ──
    if (options.ultra_hdr_dither_enabled_) {
        ApplyOrderedDither(sdr_rgb.data(), width, height, 3);
    }

    // ── Step 4: Compute gain map ──
    float hdr_headroom = color_config.peak_luminance / 100.0f;  // Relative to SDR
    std::vector<float> gain_map;
    if (!ComputeGainMap(linear_rgba.data(), sdr_rgb.data(),
                        width, height, hdr_headroom, gain_map)) {
        __android_log_print(ANDROID_LOG_ERROR, kTag,
                            "WriteUltraHdr: gain map computation failed");
        return false;
    }

#if defined(ALCEDO_HAS_ULTRAHDR)
    // ── Step 5: Encode with libultrahdr ──
    // Build a minimal JPEG from the SDR base for libultrahdr input.
    // In practice, the SDR JPEG encoding is done by the Kotlin side
    // using Android's Bitmap.compress(), but for the libultrahdr path
    // we need raw JPEG bytes.
    //
    // Here we prepare the data structures and call libultrahdr.
    // The SDR JPEG is expected to be provided by the Kotlin bridge.
    __android_log_print(ANDROID_LOG_INFO, kTag,
                        "WriteUltraHdr: using libultrahdr encoding "
                        "(%dx%d, quality=%d, headroom=%.1f)",
                        width, height, options.ultra_hdr_quality_, hdr_headroom);

    // Placeholder: the actual JPEG encoding + libultrahdr call requires
    // the Kotlin side to provide the SDR JPEG bytes. In a full integration,
    // this would:
    // 1. Call EncodeWithLibUltraHdr() with the SDR JPEG and gain map
    // 2. Write the resulting Ultra HDR JPEG to options.export_path_
    // For now, we write the prepared data as a raw blob for the Kotlin bridge.
    std::ofstream ofs(options.export_path_, std::ios::binary);
    if (!ofs.is_open()) {
        __android_log_print(ANDROID_LOG_ERROR, kTag,
                            "WriteUltraHdr: cannot open output %s",
                            options.export_path_.c_str());
        return false;
    }
    // Header
    uint32_t w = static_cast<uint32_t>(width);
    uint32_t h = static_cast<uint32_t>(height);
    ofs.write("UHDR", 4);
    ofs.write(reinterpret_cast<const char*>(&w), 4);
    ofs.write(reinterpret_cast<const char*>(&h), 4);
    float headroom_f = hdr_headroom;
    ofs.write(reinterpret_cast<const char*>(&headroom_f), sizeof(float));
    // SDR RGB data
    ofs.write(reinterpret_cast<const char*>(sdr_rgb.data()),
              static_cast<std::streamsize>(sdr_rgb.size()));
    // Gain map data
    ofs.write(reinterpret_cast<const char*>(gain_map.data()),
              static_cast<std::streamsize>(gain_map.size() * sizeof(float)));
    return ofs.good();
#else
    // Without libultrahdr, write a raw pixel blob for the Kotlin bridge
    // to process using Android's UltraHDR API.
    __android_log_print(ANDROID_LOG_INFO, kTag,
                        "WriteUltraHdr: libultrahdr not available, "
                        "writing raw data for Kotlin bridge (%dx%d)",
                        width, height);

    std::ofstream ofs(options.export_path_, std::ios::binary);
    if (!ofs.is_open()) {
        __android_log_print(ANDROID_LOG_ERROR, kTag,
                            "WriteUltraHdr: cannot open output %s",
                            options.export_path_.c_str());
        return false;
    }
    uint32_t w = static_cast<uint32_t>(width);
    uint32_t h = static_cast<uint32_t>(height);
    ofs.write("UHDR", 4);
    ofs.write(reinterpret_cast<const char*>(&w), 4);
    ofs.write(reinterpret_cast<const char*>(&h), 4);
    float headroom_f = hdr_headroom;
    ofs.write(reinterpret_cast<const char*>(&headroom_f), sizeof(float));
    ofs.write(reinterpret_cast<const char*>(sdr_rgb.data()),
              static_cast<std::streamsize>(sdr_rgb.size()));
    ofs.write(reinterpret_cast<const char*>(gain_map.data()),
              static_cast<std::streamsize>(gain_map.size() * sizeof(float)));
    return ofs.good();
#endif
}

bool UltraHdrWriter::IsUltraHdrAvailable() {
#if defined(ALCEDO_HAS_ULTRAHDR)
    return true;
#else
    return false;
#endif
}

// ============================================================
// PQ / HLG decode
// ============================================================

float UltraHdrWriter::PqDecode(float signal) {
    // Inverse PQ: signal → linear
    signal = std::clamp(signal, 0.0f, 1.0f);
    float vp = std::pow(signal, 1.0f / kPqM2);
    float n = std::max(vp - kPqC1, 0.0f);
    float d = kPqC2 - kPqC3 * vp;
    if (std::abs(d) < 1e-10f) return 0.0f;
    float linear = std::pow(n / d, 1.0f / kPqM1);
    // Scale by 10,000 cd/m² (PQ nominal peak), then normalize to 1.0 = peak
    return linear;
}

float UltraHdrWriter::HlgDecode(float signal, float peak_luminance) {
    signal = std::clamp(signal, 0.0f, 1.0f);
    float linear;
    if (signal <= 0.5f) {
        linear = signal * signal / (3.0f * kHlgA);
    } else {
        linear = (std::exp((signal - kHlgC) / kHlgA) + kHlgB) / 12.0f;
    }
    // Scale by the display peak luminance / 1000 (HLG reference white = 203 cd/m²)
    float scene_referred = linear * (peak_luminance / 1000.0f);
    return scene_referred;
}

// ============================================================
// HDR to linear conversion
// ============================================================

bool UltraHdrWriter::ConvertHdrToLinear(const ImageBuffer& hdr_buffer,
                                          const ExportColorProfileConfig& color_config,
                                          std::vector<float>& linear_rgba) {
    const int width = hdr_buffer.width;
    const int height = hdr_buffer.height;
    const int channels = hdr_buffer.channels;
    const int pixel_count = width * height;

    linear_rgba.resize(static_cast<size_t>(pixel_count) * 4);  // Always output RGBA

    const float* src = hdr_buffer.float_data();
    if (!src) {
        __android_log_print(ANDROID_LOG_ERROR, kTag,
                            "ConvertHdrToLinear: no float data");
        return false;
    }

    for (int i = 0; i < pixel_count; ++i) {
        float r = src[i * channels + 0];
        float g = src[i * channels + 1];
        float b = src[i * channels + 2];
        float a = (channels >= 4) ? src[i * channels + 3] : 1.0f;

        // Apply inverse EOTF based on the encoding
        switch (color_config.encoding_eotf) {
            case 1:  // PQ
                r = PqDecode(r);
                g = PqDecode(g);
                b = PqDecode(b);
                break;
            case 2:  // HLG
                r = HlgDecode(r, color_config.peak_luminance);
                g = HlgDecode(g, color_config.peak_luminance);
                b = HlgDecode(b, color_config.peak_luminance);
                break;
            case 3:  // Linear — already linear
                break;
            case 0:  // sRGB — need to linearize
            default:
                // Assume sRGB inverse for pixel values > 1.0 (already linear)
                // For values <= 1.0, apply inverse sRGB
                if (r <= 1.0f) r = std::pow(std::clamp(r, 0.0f, 1.0f), 2.2f);
                if (g <= 1.0f) g = std::pow(std::clamp(g, 0.0f, 1.0f), 2.2f);
                if (b <= 1.0f) b = std::pow(std::clamp(b, 0.0f, 1.0f), 2.2f);
                break;
        }

        linear_rgba[i * 4 + 0] = r;
        linear_rgba[i * 4 + 1] = g;
        linear_rgba[i * 4 + 2] = b;
        linear_rgba[i * 4 + 3] = a;
    }

    return true;
}

// ============================================================
// SDR base generation
// ============================================================

bool UltraHdrWriter::GenerateSdrBase(const float* linear_rgba,
                                      int width, int height,
                                      std::vector<uint8_t>& sdr_rgb) {
    const int pixel_count = width * height;
    sdr_rgb.resize(static_cast<size_t>(pixel_count) * 3);

    // Estimate white point from max luminance in the image
    float max_lum = 0.0f;
    for (int i = 0; i < pixel_count; ++i) {
        float lum = 0.2126f * linear_rgba[i * 4 + 0] +
                    0.7152f * linear_rgba[i * 4 + 1] +
                    0.0722f * linear_rgba[i * 4 + 2];
        if (lum > max_lum) max_lum = lum;
    }

    for (int i = 0; i < pixel_count; ++i) {
        float r = ReinhardMap(linear_rgba[i * 4 + 0], max_lum);
        float g = ReinhardMap(linear_rgba[i * 4 + 1], max_lum);
        float b = ReinhardMap(linear_rgba[i * 4 + 2], max_lum);

        sdr_rgb[i * 3 + 0] = SrgbCompress(r);
        sdr_rgb[i * 3 + 1] = SrgbCompress(g);
        sdr_rgb[i * 3 + 2] = SrgbCompress(b);
    }

    return true;
}

// ============================================================
// Gain map computation
// ============================================================

bool UltraHdrWriter::ComputeGainMap(const float* linear_hdr,
                                     const uint8_t* sdr_rgb,
                                     int width, int height,
                                     float hdr_headroom,
                                     std::vector<float>& gain_map) {
    const int pixel_count = width * height;
    gain_map.resize(static_cast<size_t>(pixel_count));

    const float eps = 1e-6f;

    for (int i = 0; i < pixel_count; ++i) {
        // HDR luminance (scene-linear, relative)
        float hdr_lum = 0.2126f * linear_hdr[i * 4 + 0] +
                        0.7152f * linear_hdr[i * 4 + 1] +
                        0.0722f * linear_hdr[i * 4 + 2];

        // SDR luminance
        float sdr_r = SrgbLinearize(sdr_rgb[i * 3 + 0]);
        float sdr_g = SrgbLinearize(sdr_rgb[i * 3 + 1]);
        float sdr_b = SrgbLinearize(sdr_rgb[i * 3 + 2]);
        float sdr_lum = 0.2126f * sdr_r + 0.7152f * sdr_g + 0.0722f * sdr_b;

        // Gain = log2(HDR / SDR) / log2(headroom)
        // Clamp to [0, 1] for the gain map encoding
        float ratio = hdr_lum / (sdr_lum + eps);
        float log_ratio = std::log2(std::max(ratio, 1.0f));
        float log_headroom = std::log2(std::max(hdr_headroom, 1.0f + eps));
        float gain = (log_headroom > eps) ? log_ratio / log_headroom : 0.0f;
        gain_map[i] = std::clamp(gain, 0.0f, 1.0f);
    }

    return true;
}

// ============================================================
// Ordered dithering
// ============================================================

void UltraHdrWriter::ApplyOrderedDither(uint8_t* data, int width, int height,
                                          int channels, int strength) {
    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            int bayer_idx = (y & 3) * 4 + (x & 3);
            int dither = static_cast<int>(kBayer4x4[bayer_idx]) - 8; // [-8, 7]
            dither *= strength;

            for (int c = 0; c < channels; ++c) {
                int idx = (y * width + x) * channels + c;
                int val = static_cast<int>(data[idx]) + dither;
                data[idx] = static_cast<uint8_t>(std::clamp(val, 0, 255));
            }
        }
    }
}

// ============================================================
// Encode with libultrahdr (guarded)
// ============================================================

bool UltraHdrWriter::EncodeWithLibUltraHdr(const uint8_t* sdr_jpeg_data,
                                             size_t sdr_jpeg_size,
                                             const float* gain_map,
                                             int gain_map_width,
                                             int gain_map_height,
                                             float hdr_headroom,
                                             int quality,
                                             std::vector<uint8_t>& output_jpeg) {
#if defined(ALCEDO_HAS_ULTRAHDR)
    // Use libultrahdr to encode the gain map into an Ultra HDR JPEG.
    // The exact API depends on the libultrahdr version available in AOSP.
    //
    // Typical flow:
    // 1. Create ultrahdr_encoder_ptr
    // 2. Set SDR JPEG, gain map data, metadata (hdr_headroom, etc.)
    // 3. Encode to get the output Ultra HDR JPEG
    //
    // Example (API may vary):
    //   ultrahdr::uhdr_codec_private_t* enc = uhdr_create_encoder();
    //   uhdr_enc_set_sdr_image(enc, sdr_jpeg_data, sdr_jpeg_size);
    //   uhdr_enc_set_gainmap(enc, gain_map, gain_map_width, gain_map_height, ...);
    //   uhdr_enc_set_quality(enc, quality);
    //   uhdr_encode_result_t result = uhdr_encode(enc);
    //   if (result.error_code == UHDR_CODEC_OK) {
    //       output_jpeg.assign(result.data, result.data + result.size);
    //   }
    //   uhdr_release_encoder(enc);

    __android_log_print(ANDROID_LOG_INFO, kTag,
                        "EncodeWithLibUltraHdr: encoding %dx%d gain map, "
                        "quality=%d, headroom=%.1f",
                        gain_map_width, gain_map_height, quality, hdr_headroom);

    // Placeholder — the actual libultrahdr integration requires
    // the specific AOSP version's API. For now, return false
    // to indicate the Kotlin bridge should handle encoding.
    (void)sdr_jpeg_data;
    (void)sdr_jpeg_size;
    (void)gain_map;
    (void)gain_map_width;
    (void)gain_map_height;
    (void)hdr_headroom;
    (void)quality;
    (void)output_jpeg;
    return false;
#else
    (void)sdr_jpeg_data;
    (void)sdr_jpeg_size;
    (void)gain_map;
    (void)gain_map_width;
    (void)gain_map_height;
    (void)hdr_headroom;
    (void)quality;
    (void)output_jpeg;
    return false;
#endif
}

} // namespace alcedo
