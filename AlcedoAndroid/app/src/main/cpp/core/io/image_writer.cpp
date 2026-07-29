// AlcedoAndroid - ImageWriter implementation.
// Writes processed ImageBuffers to JPEG/PNG/TIFF. JPEG and PNG encoding use
// Android's AndroidBitmap_compress (jnigraphics, API 30+) resolved at runtime
// so older devices fall back to a PPM writer with a warning. TIFF is written as
// a baseline little-endian RGB IFD so the file is always valid.
// SPDX-License-Identifier: GPL-3.0-only
#include "io/io.hpp"

#include <cmath>
#include <cstddef>
#include <cstdint>
#include <cstring>
#include <fstream>
#include <utility>
#include <vector>

#include <android/bitmap.h>
#include <android/data_space.h>
#include <dlfcn.h>

#include "utils/app_logging.hpp"

namespace alcedo {

namespace {

// Convert a float ImageBuffer to 8-bit sRGB (packed RGB) for the PPM fallback.
auto FloatTo8Bit(const ImageBuffer& buffer) -> std::vector<uint8_t> {
  auto& mat = buffer.GetCPUData();
  std::vector<uint8_t> out(mat.Total());
  for (size_t i = 0; i < mat.Total(); ++i) {
    float v = mat.Data()[i];
    // Apply sRGB OETF and clamp to [0,255].
    v = v <= 0.0031308f ? (12.92f * v) : (1.055f * std::pow(v, 1.0f / 2.4f) - 0.055f);
    int iv = static_cast<int>(v * 255.0f + 0.5f);
    out[i] = static_cast<uint8_t>(iv < 0 ? 0 : (iv > 255 ? 255 : iv));
  }
  return out;
}

// Convert a float ImageBuffer to 8-bit sRGB packed as RGBA_8888 (alpha=255) for
// AndroidBitmap_compress, which expects ANDROID_BITMAP_FORMAT_RGBA_8888 pixels.
auto FloatToRGBA8888(const ImageBuffer& buffer) -> std::vector<uint8_t> {
  auto& mat = buffer.GetCPUData();
  const int ch = mat.Channels();
  const size_t n = static_cast<size_t>(mat.Width()) * static_cast<size_t>(mat.Height());
  std::vector<uint8_t> out(n * 4);
  auto to8 = [](float v) -> uint8_t {
    if (v < 0.0f) v = 0.0f;
    if (v > 1.0f) v = 1.0f;
    v = v <= 0.0031308f ? (12.92f * v) : (1.055f * std::pow(v, 1.0f / 2.4f) - 0.055f);
    int iv = static_cast<int>(v * 255.0f + 0.5f);
    return static_cast<uint8_t>(iv < 0 ? 0 : (iv > 255 ? 255 : iv));
  };
  for (size_t i = 0; i < n; ++i) {
    uint8_t r = to8(mat.Data()[i * ch + 0]);
    uint8_t g = ch > 1 ? to8(mat.Data()[i * ch + 1]) : r;
    uint8_t b = ch > 2 ? to8(mat.Data()[i * ch + 2]) : r;
    out[i * 4 + 0] = r;
    out[i * 4 + 1] = g;
    out[i * 4 + 2] = b;
    out[i * 4 + 3] = 255;
  }
  return out;
}

// AndroidBitmap_compress (API 30+) signature. Resolved at runtime via dlsym so
// the library still loads on older devices that do not export the symbol.
using CompressWriteFn = int (*)(void* userContext, const void* data, size_t size);
using AndroidBitmapCompressFn = int (*)(const AndroidBitmapInfo* info, int32_t dataspace,
                                        const void* pixels, int32_t format, int32_t quality,
                                        void* userContext, CompressWriteFn writer);

// Compress format values matching ANDROID_BITMAP_COMPRESS_FORMAT_*.
constexpr int32_t kCompressFormatJpeg = 0;
constexpr int32_t kCompressFormatPng = 1;

AndroidBitmapCompressFn LoadAndroidBitmapCompress() {
  static auto fn = []() -> AndroidBitmapCompressFn {
    // libjnigraphics.so is a stable system library (present since API 8).
    void* handle = dlopen("libjnigraphics.so", RTLD_NOW | RTLD_LOCAL);
    if (!handle) {
      ALOGW("ImageWriter: dlopen libjnigraphics.so failed: %s", dlerror());
      return nullptr;
    }
    auto sym = reinterpret_cast<AndroidBitmapCompressFn>(dlsym(handle, "AndroidBitmap_compress"));
    if (!sym) {
      ALOGW("ImageWriter: AndroidBitmap_compress symbol unavailable (pre-API 30?)");
    }
    // Keep the handle alive for the process lifetime; the symbol remains valid
    // because libjnigraphics is also a build-time dependency.
    return sym;
  }();
  return fn;
}

// Writer callback: appends compressed bytes to the std::ostream passed via ctx.
// Returns non-zero on success (per AndroidBitmap_CompressWriteFunc contract).
int WriteToStream(void* ctx, const void* data, size_t size) {
  auto* stream = static_cast<std::ofstream*>(ctx);
  stream->write(static_cast<const char*>(data), static_cast<std::streamsize>(size));
  return stream->good() ? 1 : 0;
}

// Drive AndroidBitmap_compress over an RGBA_8888 pixel buffer into `path`.
// Returns true on success.
bool CompressViaAndroid(const ImageBuffer& buffer, const std::filesystem::path& path,
                        int32_t format, int quality) {
  auto compressFn = LoadAndroidBitmapCompress();
  if (!compressFn) return false;

  int w = buffer.Width();
  int h = buffer.Height();
  auto pixels = FloatToRGBA8888(buffer);

  AndroidBitmapInfo info{};
  info.width = static_cast<uint32_t>(w);
  info.height = static_cast<uint32_t>(h);
  info.stride = static_cast<uint32_t>(w) * 4u;
  info.format = ANDROID_BITMAP_FORMAT_RGBA_8888;
  info.flags = 0;

  std::ofstream out(path, std::ios::binary);
  if (!out.is_open()) return false;
  int rc = compressFn(&info, ADATASPACE_SRGB, pixels.data(), format, quality, &out,
                      &WriteToStream);
  out.flush();
  return rc == ANDROID_BITMAP_RESULT_SUCCESS && out.good();
}

}  // namespace

auto ImageWriter::WriteJPEG(const ImageBuffer& buffer, const std::filesystem::path& path,
                            int quality) -> bool {
  if (buffer.Empty()) return false;
  int w = buffer.Width();
  int h = buffer.Height();

  if (CompressViaAndroid(buffer, path, kCompressFormatJpeg, quality)) {
    ALOGI("ImageWriter: wrote JPEG to %s, q=%d (%dx%d)", path.c_str(), quality, w, h);
    return true;
  }
  ALOGW("ImageWriter: AndroidBitmap_compress JPEG unavailable/failed; writing PPM fallback to %s",
        path.c_str());

  // PPM (P6) fallback so the file is at least valid when jnigraphics compress
  // is not available (e.g. on pre-API 30 devices).
  auto data = FloatTo8Bit(buffer);
  std::ofstream f(path, std::ios::binary);
  if (!f.is_open()) return false;
  f << "P6\n" << w << " " << h << "\n255\n";
  f.write(reinterpret_cast<const char*>(data.data()),
          static_cast<std::streamsize>(data.size()));
  ALOGI("ImageWriter: wrote JPEG fallback (PPM) to %s, q=%d", path.c_str(), quality);
  return true;
}

auto ImageWriter::WritePNG(const ImageBuffer& buffer, const std::filesystem::path& path) -> bool {
  if (buffer.Empty()) return false;
  int w = buffer.Width();
  int h = buffer.Height();

  if (CompressViaAndroid(buffer, path, kCompressFormatPng, /*quality=*/100)) {
    ALOGI("ImageWriter: wrote PNG to %s (%dx%d)", path.c_str(), w, h);
    return true;
  }
  ALOGW("ImageWriter: AndroidBitmap_compress PNG unavailable/failed; writing PPM fallback to %s",
        path.c_str());

  auto data = FloatTo8Bit(buffer);
  std::ofstream f(path, std::ios::binary);
  if (!f.is_open()) return false;
  f << "P6\n" << w << " " << h << "\n255\n";
  f.write(reinterpret_cast<const char*>(data.data()),
          static_cast<std::streamsize>(data.size()));
  ALOGI("ImageWriter: wrote PNG fallback (PPM) to %s", path.c_str());
  return true;
}

auto ImageWriter::WriteTIFF(const ImageBuffer& buffer, const std::filesystem::path& path) -> bool {
  if (buffer.Empty()) return false;
  auto& mat = buffer.GetCPUData();
  int w = mat.Width();
  int h = mat.Height();
  // 8-bit packed RGB strip.
  auto data = FloatTo8Bit(buffer);
  const uint32_t strip_bytes = static_cast<uint32_t>(data.size());  // w*h*3

  std::ofstream f(path, std::ios::binary);
  if (!f.is_open()) return false;

  // Always emit little-endian bytes so the file is portable regardless of host.
  auto put16 = [&](uint16_t v) {
    char b[2] = {static_cast<char>(v & 0xFF), static_cast<char>((v >> 8) & 0xFF)};
    f.write(b, 2);
  };
  auto put32 = [&](uint32_t v) {
    char b[4] = {static_cast<char>(v & 0xFF), static_cast<char>((v >> 8) & 0xFF),
                 static_cast<char>((v >> 16) & 0xFF), static_cast<char>((v >> 24) & 0xFF)};
    f.write(b, 4);
  };
  auto entry = [&](uint16_t tag, uint16_t type, uint32_t count, uint32_t value) {
    put16(tag);
    put16(type);
    put32(count);
    put32(value);
  };

  // Header: "II" (little-endian), magic 42, offset to first (only) IFD = 8.
  put16(0x4949);
  put16(42);
  put32(8);

  // IFD layout: 2-byte count + N*12-byte entries + 4-byte next-IFD offset.
  constexpr uint16_t kNumEntries = 9;
  const uint32_t kBitsPerSampleOff =
      8u + 2u + 12u * static_cast<uint32_t>(kNumEntries) + 4u;  // right after the IFD
  const uint32_t kStripOffset = kBitsPerSampleOff + 6u;          // after 3 x uint16 (8,8,8)

  put16(kNumEntries);
  entry(256, 4 /*LONG*/, 1, static_cast<uint32_t>(w));   // ImageWidth
  entry(257, 4 /*LONG*/, 1, static_cast<uint32_t>(h));   // ImageLength
  entry(258, 3 /*SHORT*/, 3, kBitsPerSampleOff);         // BitsPerSample -> offset
  entry(259, 3 /*SHORT*/, 1, 1);                         // Compression = none
  entry(262, 3 /*SHORT*/, 1, 2);                         // PhotometricInterpretation = RGB
  entry(273, 4 /*LONG*/, 1, kStripOffset);               // StripOffsets
  entry(277, 3 /*SHORT*/, 1, 3);                         // SamplesPerPixel
  entry(278, 4 /*LONG*/, 1, static_cast<uint32_t>(h));   // RowsPerStrip
  entry(279, 4 /*LONG*/, 1, strip_bytes);                // StripByteCounts
  put32(0);  // next IFD offset = 0 (no more IFDs)

  // BitsPerSample values: 8, 8, 8 (referenced by tag 258).
  put16(8);
  put16(8);
  put16(8);

  // Pixel data strip (single strip covers the whole image).
  f.write(reinterpret_cast<const char*>(data.data()),
          static_cast<std::streamsize>(data.size()));

  ALOGI("ImageWriter: wrote 8-bit RGB TIFF to %s (%dx%dx3, %u bytes)", path.c_str(), w, h,
        strip_bytes);
  return true;
}

}  // namespace alcedo
