// AlcedoAndroid - ThumbnailDecoder implementation.
// Decodes a thumbnail by preferring the embedded EXIF JPEG thumbnail (tags
// 0x0201 / 0x0202) and falling back to a full decode of the image buffer.
// Decoding uses Android's AImageDecoder (libandroid, API 30+) resolved at
// runtime via dlsym; on older devices, or when no decodable thumbnail data is
// present, a mid-gray placeholder is returned with a warning.
// SPDX-License-Identifier: GPL-3.0-only
#include "decoders/thumbnail_decoder.hpp"

#include <algorithm>
#include <cmath>
#include <cstring>
#include <cstddef>
#include <cstdint>
#include <fstream>
#include <optional>
#include <utility>
#include <vector>

#include <android/bitmap.h>
#include <dlfcn.h>

#include "image/metadata_extractor.hpp"
#include "utils/app_logging.hpp"

namespace alcedo {
namespace {

// 500 MiB guard for tellg() bounds checking (also used by image_loader /
// raw_decoder ReadFile). Prevents absurd allocations from a bogus file size.
constexpr int64_t kMaxReadBytes = 500LL * 1024 * 1024;

std::vector<uint8_t> ReadFile(const image_path_t& path) {
  std::ifstream ifs(path, std::ios::binary | std::ios::ate);
  if (!ifs) return {};
  const auto size = ifs.tellg();
  if (size < 0 || static_cast<int64_t>(size) > kMaxReadBytes) return {};
  ifs.seekg(0, std::ios::beg);
  std::vector<uint8_t> buf(static_cast<size_t>(size));
  ifs.read(reinterpret_cast<char*>(buf.data()), size);
  return buf;
}

// ---- Embedded EXIF JPEG thumbnail extraction (tags 0x0201 / 0x0202) ----

struct ExifThumb { size_t offset; size_t length; };

// Locate the TIFF header inside a JPEG (APP1 "Exif\0\0"). Returns 0 if absent.
size_t FindExifTiffInJpeg(const uint8_t* data, size_t len) {
  if (len < 4 || data[0] != 0xFF || data[1] != 0xD8) return 0;  // not a JPEG
  size_t pos = 2;
  while (pos + 4 <= len) {
    if (data[pos] != 0xFF) break;
    uint8_t marker = data[pos + 1];
    if (marker == 0xDA) break;                       // SOS: image data follows
    if (marker == 0xFF) { ++pos; continue; }         // fill byte
    uint16_t seg_len = (static_cast<uint16_t>(data[pos + 2]) << 8) | data[pos + 3];
    if (seg_len < 2 || pos + 2 + seg_len > len) break;
    if (marker == 0xE1 && seg_len >= 8 &&
        std::memcmp(data + pos + 4, "Exif\0\0", 6) == 0) {
      return pos + 4 + 6;  // start of the TIFF header
    }
    pos += 2 + seg_len;
  }
  return 0;
}

// Find an embedded JPEG thumbnail referenced by the EXIF tags
// JPEGInterchangeFormat (0x0201) and JPEGInterchangeFormatLength (0x0202).
// Walks up to 4 IFDs in the TIFF chain (covers IFD0/IFD1 used by JPEG EXIF and
// the single IFD used by many TIFF-based RAW files).
std::optional<ExifThumb> FindExifJpegThumbnail(const uint8_t* data, size_t len) {
  if (!data || len < 8) return std::nullopt;

  size_t tiff_start = 0;
  if ((data[0] == 'I' && data[1] == 'I') || (data[0] == 'M' && data[1] == 'M')) {
    tiff_start = 0;  // TIFF-based RAW (DNG/CR2/NEF/ARW)
  } else {
    tiff_start = FindExifTiffInJpeg(data, len);
    if (tiff_start == 0 || tiff_start + 8 > len) return std::nullopt;
  }

  const bool be = (data[tiff_start] == 'M');
  auto r16 = [&](size_t off) -> uint16_t {
    if (off + 2 > len) return 0;
    return be ? (static_cast<uint16_t>(data[off]) << 8 | data[off + 1])
              : (data[off] | static_cast<uint16_t>(data[off + 1]) << 8);
  };
  auto r32 = [&](size_t off) -> uint32_t {
    if (off + 4 > len) return 0;
    if (be) {
      return (static_cast<uint32_t>(data[off]) << 24) |
             (static_cast<uint32_t>(data[off + 1]) << 16) |
             (static_cast<uint32_t>(data[off + 2]) << 8) | data[off + 3];
    }
    return data[off] | (static_cast<uint32_t>(data[off + 1]) << 8) |
           (static_cast<uint32_t>(data[off + 2]) << 16) |
           (static_cast<uint32_t>(data[off + 3]) << 24);
  };

  if (r16(tiff_start + 2) != 42) return std::nullopt;
  uint32_t ifd_off = r32(tiff_start + 4);
  if (ifd_off == 0) return std::nullopt;
  size_t ifd_pos = tiff_start + ifd_off;

  for (int i = 0; i < 4 && ifd_pos + 2 <= len; ++i) {
    uint16_t count = r16(ifd_pos);
    uint32_t jif_off = 0, jif_len = 0;
    bool has_off = false, has_len = false;
    for (int e = 0; e < count; ++e) {
      size_t ep = ifd_pos + 2 + static_cast<size_t>(e) * 12;
      if (ep + 12 > len) break;
      uint16_t tag = r16(ep);
      uint32_t val = r32(ep + 8);
      if (tag == 0x0201) { jif_off = val; has_off = true; }
      else if (tag == 0x0202) { jif_len = val; has_len = true; }
    }
    if (has_off && has_len && jif_len > 0 && jif_off < len) {
      size_t abs_off = tiff_start + jif_off;
      if (abs_off + 2 <= len && data[abs_off] == 0xFF && data[abs_off + 1] == 0xD8 &&
          abs_off + jif_len <= len) {
        return ExifThumb{abs_off, jif_len};
      }
    }
    size_t next_pos = ifd_pos + 2 + static_cast<size_t>(count) * 12;
    if (next_pos + 4 > len) break;
    uint32_t next = r32(next_pos);
    if (next == 0) break;
    ifd_pos = tiff_start + next;
  }
  return std::nullopt;
}

// ---- Native image decode via AImageDecoder (API 30+, libandroid) ----

struct AImageDecoder;
struct AImageDecoderApi {
  int (*create)(const void*, size_t, AImageDecoder**);
  void (*destroy)(AImageDecoder*);
  int32_t (*width)(const AImageDecoder*);
  int32_t (*height)(const AImageDecoder*);
  int (*set_format)(AImageDecoder*, int);
  int (*set_target_size)(AImageDecoder*, int32_t, int32_t);
  size_t (*min_stride)(const AImageDecoder*);
  int (*decode)(AImageDecoder*, void*, size_t, size_t);
};

const AImageDecoderApi* LoadAImageDecoderApi() {
  static const AImageDecoderApi* api = []() -> const AImageDecoderApi* {
    void* h = dlopen("libandroid.so", RTLD_NOW | RTLD_LOCAL);
    if (!h) return nullptr;
    static AImageDecoderApi a{};
    a.create = reinterpret_cast<decltype(a.create)>(dlsym(h, "AImageDecoder_createFromBuffer"));
    a.destroy = reinterpret_cast<decltype(a.destroy)>(dlsym(h, "AImageDecoder_delete"));
    a.width = reinterpret_cast<decltype(a.width)>(dlsym(h, "AImageDecoder_getWidth"));
    a.height = reinterpret_cast<decltype(a.height)>(dlsym(h, "AImageDecoder_getHeight"));
    a.set_format = reinterpret_cast<decltype(a.set_format)>(dlsym(h, "AImageDecoder_setAndroidBitmapFormat"));
    a.set_target_size = reinterpret_cast<decltype(a.set_target_size)>(dlsym(h, "AImageDecoder_setTargetSize"));
    a.min_stride = reinterpret_cast<decltype(a.min_stride)>(dlsym(h, "AImageDecoder_getMinimumStride"));
    a.decode = reinterpret_cast<decltype(a.decode)>(dlsym(h, "AImageDecoder_decodeImage"));
    // Core functions must all be present; set_target_size/set_format optional.
    if (!a.create || !a.destroy || !a.width || !a.height || !a.min_stride || !a.decode ||
        !a.set_format) {
      return nullptr;
    }
    return &a;
  }();
  return api;
}

constexpr int kImageDecoderSuccess = 0;
constexpr int kFormatRgba8888 = ANDROID_BITMAP_FORMAT_RGBA_8888;  // == 1

// Decode `bytes` (JPEG/PNG/baseline-TIFF) into RGBA_8888 pixels. When
// target_w/target_h > 0 the decoder is asked to decode at that resolution
// (efficient thumbnailing); otherwise the native resolution is used.
bool DecodeToRGBA(const uint8_t* bytes, size_t len, int target_w, int target_h,
                  std::vector<uint8_t>& out_rgba, int& out_w, int& out_h, size_t& out_stride) {
  const AImageDecoderApi* api = LoadAImageDecoderApi();
  if (!api) return false;

  AImageDecoder* dec = nullptr;
  if (api->create(bytes, len, &dec) != kImageDecoderSuccess || !dec) return false;

  // Require RGBA_8888 so the pixel layout below is well defined.
  if (api->set_format(dec, kFormatRgba8888) != kImageDecoderSuccess) {
    api->destroy(dec);
    return false;
  }
  int dw = api->width(dec);
  int dh = api->height(dec);
  if (api->set_target_size && target_w > 0 && target_h > 0) {
    if (api->set_target_size(dec, target_w, target_h) == kImageDecoderSuccess) {
      dw = api->width(dec);
      dh = api->height(dec);
    }
  }
  if (dw <= 0 || dh <= 0) {
    api->destroy(dec);
    return false;
  }
  size_t stride = api->min_stride(dec);
  if (stride < static_cast<size_t>(dw) * 4) stride = static_cast<size_t>(dw) * 4;
  std::vector<uint8_t> pixels(stride * static_cast<size_t>(dh));
  if (api->decode(dec, pixels.data(), stride, pixels.size()) != kImageDecoderSuccess) {
    api->destroy(dec);
    return false;
  }
  api->destroy(dec);

  out_rgba = std::move(pixels);
  out_w = dw;
  out_h = dh;
  out_stride = stride;
  return true;
}

// Convert decoded sRGB RGBA_8888 pixels (with row stride) to a linear float RGB
// ImageBuffer. Alpha is dropped (working space is RGB).
std::shared_ptr<ImageBuffer> RGBAToFloatBuffer(const uint8_t* rgba, size_t stride, int w, int h) {
  auto buf = std::make_shared<ImageBuffer>(w, h, 3);
  FloatMat& mat = buf->GetCPUData();
  auto to_lin = [](uint8_t v) -> float {
    float s = static_cast<float>(v) / 255.0f;
    return s <= 0.04045f ? (s / 12.92f)
                         : std::pow((s + 0.055f) / 1.055f, 2.4f);
  };
  for (int y = 0; y < h; ++y) {
    const uint8_t* row = rgba + y * stride;
    for (int x = 0; x < w; ++x) {
      const uint8_t* px = row + x * 4;
      float* dst = mat.Ptr(y, x);
      dst[0] = to_lin(px[0]);
      dst[1] = to_lin(px[1]);
      dst[2] = to_lin(px[2]);
    }
  }
  buf->cpu_data_valid_ = true;
  return buf;
}

}  // namespace

auto ThumbnailDecoder::DecodeFromBytes(std::vector<uint8_t> bytes, image_id_t id) -> DecodeResult {
  DecodeResult r;
  r.image_id = id;
  if (bytes.empty()) {
    r.success = false;
    r.error = "empty buffer";
    return r;
  }

  r.exif = MetadataExtractor::ExtractFromBuffer(bytes.data(), bytes.size());
  int sw = r.exif.width_px > 0 ? r.exif.width_px : 0;
  int sh = r.exif.height_px > 0 ? r.exif.height_px : 0;
  if (sw == 0 || sh == 0) { sw = 640; sh = 480; }

  const float scale =
      static_cast<float>(max_long_edge_) / static_cast<float>(std::max(sw, sh));
  int tw = std::max(1, static_cast<int>(sw * scale + 0.5f));
  int th = std::max(1, static_cast<int>(sh * scale + 0.5f));

  std::vector<uint8_t> rgba;
  int pw = 0, ph = 0;
  size_t pstride = 0;
  bool decoded = false;

  // 1) Prefer the embedded EXIF JPEG thumbnail (fast, avoids a full decode).
  //    Decode it at its native resolution; the embedded thumb is already small.
  auto exif_thumb = FindExifJpegThumbnail(bytes.data(), bytes.size());
  if (exif_thumb) {
    const uint8_t* tdata = bytes.data() + exif_thumb->offset;
    if (DecodeToRGBA(tdata, exif_thumb->length, /*target_w=*/0, /*target_h=*/0, rgba, pw, ph,
                     pstride)) {
      decoded = true;
      ALOGI("ThumbnailDecoder: decoded embedded EXIF thumbnail (src %dx%d -> %dx%d)", sw, sh, pw, ph);
    } else {
      ALOGW("ThumbnailDecoder: EXIF thumbnail found but could not be decoded");
    }
  }

  // 2) Otherwise decode the full buffer, asking the decoder for thumbnail res.
  if (!decoded) {
    if (DecodeToRGBA(bytes.data(), bytes.size(), tw, th, rgba, pw, ph, pstride)) {
      decoded = true;
      ALOGI("ThumbnailDecoder: decoded full image to thumbnail (src %dx%d -> %dx%d)", sw, sh, pw, ph);
    }
  }

  if (decoded) {
    auto raw = RGBAToFloatBuffer(rgba.data(), pstride, pw, ph);
    if (raw) {
      // Normalize to max_long_edge_ (downsamples full-res decodes; no-op when
      // the decoder already produced thumbnail-sized output).
      auto buf = Downsample(*raw);
      if (buf) {
        r.buffer = std::move(buf);
        r.success = true;
        return r;
      }
    }
  }

  // 3) Last resort: gray placeholder. Reached when no decodable thumbnail data
  //    was found (e.g. pre-API 30 device, or unsupported RAW without an
  //    embedded JPEG preview).
  ALOGW("ThumbnailDecoder: no decodable thumbnail data found; returning gray placeholder (%dx%d)",
        tw, th);
  auto buf = std::make_shared<ImageBuffer>(tw, th, 3);
  FloatMat& mat = buf->GetCPUData();
  const float mid = 0.18f;
  for (size_t i = 0; i < mat.Total(); ++i) mat.Data()[i] = mid;
  buf->cpu_data_valid_ = true;
  r.buffer = std::move(buf);
  r.success = true;
  return r;
}

auto ThumbnailDecoder::Decode(const image_path_t& path, image_id_t id, DecodeType /*type*/)
    -> DecodeResult {
  auto bytes = ReadFile(path);
  if (bytes.empty()) {
    DecodeResult r;
    r.image_id = id;
    r.success = false;
    r.error = "failed to read file";
    return r;
  }
  return DecodeFromBytes(std::move(bytes), id);
}

auto ThumbnailDecoder::Decode(const std::vector<uint8_t>& buffer, image_id_t id, DecodeType /*type*/)
    -> DecodeResult {
  return DecodeFromBytes(buffer, id);
}

auto ThumbnailDecoder::Downsample(const ImageBuffer& src) const -> std::shared_ptr<ImageBuffer> {
  const FloatMat& in = src.GetCPUData();
  if (in.Empty()) return nullptr;
  int sw = in.Width();
  int sh = in.Height();
  int ch = in.Channels();
  const float scale = static_cast<float>(max_long_edge_) / static_cast<float>(std::max(sw, sh));
  if (scale >= 1.0f) {
    // No downsample needed; return a clone.
    return std::make_shared<ImageBuffer>(src.Clone());
  }
  int tw = std::max(1, static_cast<int>(sw * scale + 0.5f));
  int th = std::max(1, static_cast<int>(sh * scale + 0.5f));
  auto out = std::make_shared<ImageBuffer>(tw, th, ch);
  FloatMat& dst = out->GetCPUData();
  // Bilinear downsample.
  for (int y = 0; y < th; ++y) {
    const float fy = (static_cast<float>(y) + 0.5f) / scale - 0.5f;
    const int y0 = std::clamp(static_cast<int>(std::floor(fy)), 0, sh - 1);
    const int y1 = std::clamp(y0 + 1, 0, sh - 1);
    const float wy = std::clamp(fy - std::floor(fy), 0.0f, 1.0f);
    for (int x = 0; x < tw; ++x) {
      const float fx = (static_cast<float>(x) + 0.5f) / scale - 0.5f;
      const int x0 = std::clamp(static_cast<int>(std::floor(fx)), 0, sw - 1);
      const int x1 = std::clamp(x0 + 1, 0, sw - 1);
      const float wx = std::clamp(fx - std::floor(fx), 0.0f, 1.0f);
      const float* p00 = in.Ptr(y0, x0);
      const float* p01 = in.Ptr(y0, x1);
      const float* p10 = in.Ptr(y1, x0);
      const float* p11 = in.Ptr(y1, x1);
      float* d = dst.Ptr(y, x);
      for (int c = 0; c < ch; ++c) {
        const float v0 = p00[c] * (1.0f - wx) + p01[c] * wx;
        const float v1 = p10[c] * (1.0f - wx) + p11[c] * wx;
        d[c] = v0 * (1.0f - wy) + v1 * wy;
      }
    }
  }
  out->cpu_data_valid_ = true;
  return out;
}

}  // namespace alcedo
