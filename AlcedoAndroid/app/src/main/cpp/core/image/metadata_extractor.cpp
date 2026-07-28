// AlcedoAndroid - MetadataExtractor implementation (lightweight TIFF/EXIF parser).
// SPDX-License-Identifier: GPL-3.0-only
#include "image/metadata_extractor.hpp"

#include <algorithm>
#include <cstring>
#include <fstream>
#include <vector>

#include "utils/app_logging.hpp"
#include "utils/converter.hpp"

namespace alcedo {

namespace {

// TIFF tag IDs we care about.
enum TiffTag : uint16_t {
  kMake              = 0x010F,
  kModel             = 0x0110,
  kOrientation       = 0x0112,
  kImageWidth        = 0x0100,
  kImageHeight       = 0x0101,
  kBitsPerSample     = 0x0102,
  kDateTime          = 0x0132,
  kFocalLength       = 0x920A,
  kFocalLength35mm   = 0xA405,
  kExposureTime      = 0x829A,
  kFNumber           = 0x829D,
  kISOSpeedRatings   = 0x8827,
  kLensModel         = 0xA434,
  kLensMake          = 0xA433,
  kColorSpace        = 0xA001,
  kGPSInfo           = 0x8825,
  kExifIFD           = 0x8769,
};

uint16_t ReadU16(const uint8_t* p, bool little) {
  return little ? (uint16_t(p[0]) | (uint16_t(p[1]) << 8))
                : (uint16_t(p[1]) | (uint16_t(p[0]) << 8));
}
uint32_t ReadU32(const uint8_t* p, bool little) {
  return little ? (uint32_t(p[0]) | (uint32_t(p[1]) << 8) | (uint32_t(p[2]) << 16) | (uint32_t(p[3]) << 24))
                : (uint32_t(p[3]) | (uint32_t(p[2]) << 8) | (uint32_t(p[1]) << 16) | (uint32_t(p[0]) << 24));
}
float ReadRational(const uint8_t* base, uint32_t offset, bool little) {
  const uint8_t* p = base + offset;
  if (offset + 8 > 0xFFFFFFFF) return 0.0f;
  uint32_t num = ReadU32(p, little);
  uint32_t den = ReadU32(p + 4, little);
  return den ? float(num) / float(den) : 0.0f;
}

std::string ReadAscii(const uint8_t* base, uint32_t value_off, uint32_t count, bool little,
                      size_t buf_len) {
  if (count <= 4) {
    return std::string(reinterpret_cast<const char*>(&value_off),
                       std::min<size_t>(count, 4));
  }
  if (value_off + count > buf_len) return {};
  std::string s(reinterpret_cast<const char*>(base + value_off), count);
  // Trim trailing NULs.
  while (!s.empty() && s.back() == '\0') s.pop_back();
  return s;
}

void ParseIFD(const uint8_t* base, size_t buf_len, uint32_t ifd_off, bool little,
              ExifDisplayMetaData& meta) {
  if (ifd_off + 2 > buf_len) return;
  uint16_t count = ReadU16(base + ifd_off, little);
  const uint8_t* entry = base + ifd_off + 2;
  for (uint16_t i = 0; i < count; ++i) {
    if (entry + 12 > base + buf_len) break;
    uint16_t tag    = ReadU16(entry, little);
    uint16_t type   = ReadU16(entry + 2, little);
    uint32_t count2 = ReadU32(entry + 4, little);
    uint32_t value_off = ReadU32(entry + 8, little);
    (void)type;
    switch (tag) {
      case kMake:            meta.camera_make = ReadAscii(base, value_off, count2, little, buf_len); break;
      case kModel:           meta.camera_model = ReadAscii(base, value_off, count2, little, buf_len); break;
      case kLensMake:        meta.lens_make = ReadAscii(base, value_off, count2, little, buf_len); break;
      case kLensModel:       meta.lens_model = ReadAscii(base, value_off, count2, little, buf_len); break;
      case kImageWidth:      meta.width_px = (count2 == 1) ? value_off : (int)ReadU16(base + value_off, little); break;
      case kImageHeight:     meta.height_px = (count2 == 1) ? value_off : (int)ReadU16(base + value_off, little); break;
      case kBitsPerSample:   meta.bit_depth = (count2 == 1) ? value_off : (int)ReadU16(base + value_off, little); break;
      case kDateTime:        meta.capture_time = ReadAscii(base, value_off, count2, little, buf_len); break;
      case kFocalLength:     meta.focal_length_mm = ReadRational(base, value_off, little); break;
      case kFocalLength35mm: meta.focal_length_35mm = (float)(short)value_off; break;
      case kExposureTime:    meta.exposure_time_s = ReadRational(base, value_off, little); break;
      case kFNumber:         meta.aperture_f = ReadRational(base, value_off, little); break;
      case kISOSpeedRatings: meta.iso = (float)(short)value_off; break;
      case kColorSpace:      meta.color_space = (value_off == 1) ? "sRGB" : "Untagged"; break;
      case kOrientation:     meta.orientation = std::to_string(value_off & 0xFFFF); break;
      case kExifIFD:         ParseIFD(base, buf_len, value_off, little, meta); break;
      default: break;
    }
    entry += 12;
  }
}

ExifDisplayMetaData ParseTiff(const uint8_t* data, size_t len) {
  ExifDisplayMetaData meta;
  if (len < 8) return meta;
  bool little;
  if (data[0] == 'I' && data[1] == 'I') little = true;
  else if (data[0] == 'M' && data[1] == 'M') little = false;
  else return meta;
  if (ReadU16(data + 2, little) != 42) return meta;
  uint32_t ifd_off = ReadU32(data + 4, little);
  ParseIFD(data, len, ifd_off, little, meta);
  return meta;
}

}  // namespace

ExifDisplayMetaData MetadataExtractor::ExtractFromFile(const image_path_t& path) {
  std::ifstream f(path, std::ios::binary);
  if (!f) {
    ALOGW("MetadataExtractor: cannot open %s", path.string().c_str());
    return {};
  }
  std::vector<uint8_t> buf((std::istreambuf_iterator<char>(f)),
                           std::istreambuf_iterator<char>());
  return ExtractFromBuffer(buf.data(), buf.size());
}

ExifDisplayMetaData MetadataExtractor::ExtractFromBuffer(const uint8_t* data, size_t len) {
  if (!data || len < 4) return {};
  // JPEG: scan for APP1 EXIF segment.
  if (data[0] == 0xFF && data[1] == 0xD8) {
    size_t i = 2;
    while (i + 4 <= len) {
      if (data[i] != 0xFF) break;
      uint8_t marker = data[i + 1];
      uint16_t seg_len = uint16_t(data[i + 2] << 8) | data[i + 3];
      if (marker == 0xE1 && i + 10 < len &&
          std::memcmp(data + i + 4, "Exif\0\0", 6) == 0) {
        return ParseTiff(data + i + 10, len - i - 10);
      }
      i += 2 + seg_len;
      if (marker == 0xDA) break;  // SOS
    }
    return {};
  }
  // TIFF-based RAW (DNG/CR2/NEF/ARW).
  return ParseTiff(data, len);
}

nlohmann::json MetadataExtractor::ToJson(const ExifDisplayMetaData& m) {
  nlohmann::json j;
  j["camera_make"]   = m.camera_make;
  j["camera_model"]  = m.camera_model;
  j["lens_make"]     = m.lens_make;
  j["lens_model"]    = m.lens_model;
  j["focal_length"]  = m.focal_length_mm;
  j["focal_length_35mm"] = m.focal_length_35mm;
  j["aperture"]      = m.aperture_f;
  j["exposure_time"] = m.exposure_time_s;
  j["iso"]           = m.iso;
  j["capture_time"]  = m.capture_time;
  j["orientation"]   = m.orientation;
  j["width"]         = m.width_px;
  j["height"]        = m.height_px;
  j["bit_depth"]     = m.bit_depth;
  j["color_space"]   = m.color_space;
  j["gps_lat"]       = m.gps_lat;
  j["gps_lon"]       = m.gps_lon;
  j["has_gps"]       = m.has_gps;
  return j;
}

ExifDisplayMetaData MetadataExtractor::FromJson(const nlohmann::json& j) {
  ExifDisplayMetaData m;
  m.camera_make        = j.value("camera_make", "");
  m.camera_model       = j.value("camera_model", "");
  m.lens_make          = j.value("lens_make", "");
  m.lens_model         = j.value("lens_model", "");
  m.focal_length_mm    = j.value("focal_length", 0.0f);
  m.focal_length_35mm  = j.value("focal_length_35mm", 0.0f);
  m.aperture_f         = j.value("aperture", 0.0f);
  m.exposure_time_s    = j.value("exposure_time", 0.0f);
  m.iso                = j.value("iso", 0.0f);
  m.capture_time       = j.value("capture_time", "");
  m.orientation        = j.value("orientation", "");
  m.width_px           = j.value("width", 0);
  m.height_px          = j.value("height", 0);
  m.bit_depth          = j.value("bit_depth", 0);
  m.color_space        = j.value("color_space", "");
  m.gps_lat            = j.value("gps_lat", 0.0f);
  m.gps_lon            = j.value("gps_lon", 0.0f);
  m.has_gps            = j.value("has_gps", false);
  return m;
}

ImageType MetadataExtractor::GuessType(const image_path_t& path) {
  std::string ext = converter::ToLower(path.extension().string());
  if (ext == ".jpg" || ext == ".jpeg") return ImageType::JPEG;
  if (ext == ".png")  return ImageType::PNG;
  if (ext == ".tif" || ext == ".tiff") return ImageType::TIFF;
  if (ext == ".arw")  return ImageType::ARW;
  if (ext == ".cr2")  return ImageType::CR2;
  if (ext == ".cr3")  return ImageType::CR3;
  if (ext == ".nef")  return ImageType::NEF;
  if (ext == ".dng")  return ImageType::DNG;
  return ImageType::DEFAULT;
}

}  // namespace alcedo
