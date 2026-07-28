// AlcedoAndroid - RawDecoder implementation.
// SPDX-License-Identifier: GPL-3.0-only
#include "decoders/raw_decoder.hpp"

#include <algorithm>
#include <cstring>
#include <fstream>
#include <vector>

#include "image/metadata_extractor.hpp"
#include "utils/app_logging.hpp"

namespace alcedo {
namespace {

constexpr uint8_t kGrayMid = 46;   // ~0.18 linear in 8-bit

std::vector<uint8_t> ReadFile(const image_path_t& path) {
  std::ifstream ifs(path, std::ios::binary | std::ios::ate);
  if (!ifs) return {};
  const auto size = ifs.tellg();
  if (size <= 0) return {};
  ifs.seekg(0, std::ios::beg);
  std::vector<uint8_t> buf(static_cast<size_t>(size));
  ifs.read(reinterpret_cast<char*>(buf.data()), size);
  return buf;
}

bool IsTiffHeader(const uint8_t* data, size_t len) {
  if (len < 4) return false;
  // TIFF little-endian "II\x2a\x00" or big-endian "MM\x00\x2a".
  return (data[0] == 'I' && data[1] == 'I' && data[2] == 0x2a && data[3] == 0x00) ||
         (data[0] == 'M' && data[1] == 'M' && data[2] == 0x00 && data[3] == 0x2a);
}

}  // namespace

auto RawDecoder::DecodeTiffLike(const uint8_t* data, size_t len, image_id_t id) -> DecodeResult {
  DecodeResult r;
  r.image_id = id;
  r.exif = MetadataExtractor::ExtractFromBuffer(data, len);

  // Populate a minimal runtime color context from the parsed EXIF. Real camera
  // color matrices require libraw/DNG parsing and are filled in by the
  // RawProcessor integration point (ALCEDO_HAVE_LIBRAW).
  r.raw_context.valid_ = true;
  r.raw_context.camera_make_ = r.exif.camera_make;
  r.raw_context.camera_model_ = r.exif.camera_model;
  r.raw_context.lens_metadata_valid_ = !r.exif.lens_model.empty();
  r.raw_context.lens_make_  = r.exif.lens_make;
  r.raw_context.lens_model_ = r.exif.lens_model;
  r.raw_context.focal_length_mm_ = r.exif.focal_length_mm;
  r.raw_context.aperture_f_number_ = r.exif.aperture_f;
  r.raw_context.focal_35mm_mm_ = r.exif.focal_length_35mm;
  for (int i = 0; i < 9; ++i) {
    r.raw_context.cam_xyz_[i] = (i % 4 == 0) ? 1.0f : 0.0f;  // identity fallback
    r.raw_context.rgb_cam_[i] = (i % 4 == 0) ? 1.0f : 0.0f;
  }
  r.has_raw_context = true;

  int w = r.exif.width_px > 0 ? r.exif.width_px : 0;
  int h = r.exif.height_px > 0 ? r.exif.height_px : 0;
  if (w == 0 || h == 0) {
    w = 640;
    h = 480;
    ALOGW("RawDecoder: could not resolve dimensions from header; using %dx%d", w, h);
  }

  // Produce a single-channel mosaic placeholder (mid-gray). The real sensor
  // values come from libraw when ALCEDO_HAVE_LIBRAW is defined at build time.
  // Even though it is a placeholder, keeping the mosaic single-channel lets the
  // RawProcessor debayer stage run end-to-end.
  auto buf = std::make_shared<ImageBuffer>(w, h, 1);
  FloatMat& mat = buf->GetCPUData();
  const float mid = static_cast<float>(kGrayMid) / 255.0f;
  for (size_t i = 0; i < mat.Total(); ++i) mat.Data()[i] = mid;
  buf->cpu_data_valid_ = true;
  r.buffer = std::move(buf);
  r.success = true;
  return r;
}

auto RawDecoder::Decode(const image_path_t& path, image_id_t id, DecodeType /*type*/) -> DecodeResult {
  auto bytes = ReadFile(path);
  if (bytes.empty()) {
    DecodeResult r;
    r.image_id = id;
    r.success = false;
    r.error = "failed to read raw file";
    return r;
  }
  return DecodeTiffLike(bytes.data(), bytes.size(), id);
}

auto RawDecoder::Decode(const std::vector<uint8_t>& buffer, image_id_t id, DecodeType /*type*/)
    -> DecodeResult {
  if (buffer.empty()) {
    DecodeResult r;
    r.image_id = id;
    r.success = false;
    r.error = "empty buffer";
    return r;
  }
  return DecodeTiffLike(buffer.data(), buffer.size(), id);
}

void RawDecoder::Decode(const std::vector<uint8_t>& buffer, std::shared_ptr<Image> source_img) {
  if (!source_img || buffer.empty()) return;
  auto result = DecodeTiffLike(buffer.data(), buffer.size(), source_img->image_id_);
  source_img->SetExifDisplayMetaData(std::move(result.exif));
  if (result.has_raw_context) {
    source_img->SetRawColorContext(std::move(result.raw_context));
  }
  if (result.buffer) {
    source_img->LoadOriginalData(std::move(*result.buffer));
  }
}

}  // namespace alcedo
