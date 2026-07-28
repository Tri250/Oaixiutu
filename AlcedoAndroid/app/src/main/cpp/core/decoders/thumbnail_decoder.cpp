// AlcedoAndroid - ThumbnailDecoder implementation.
// SPDX-License-Identifier: GPL-3.0-only
#include "decoders/thumbnail_decoder.hpp"

#include <algorithm>
#include <cmath>
#include <fstream>
#include <vector>

#include "image/metadata_extractor.hpp"
#include "utils/app_logging.hpp"

namespace alcedo {
namespace {

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

}  // namespace

auto ThumbnailDecoder::Decode(const image_path_t& path, image_id_t id, DecodeType /*type*/)
    -> DecodeResult {
  DecodeResult r;
  r.image_id = id;
  auto bytes = ReadFile(path);
  if (bytes.empty()) {
    r.success = false;
    r.error = "failed to read file";
    return r;
  }
  r.exif = MetadataExtractor::ExtractFromBuffer(bytes.data(), bytes.size());
  int sw = r.exif.width_px  > 0 ? r.exif.width_px  : 0;
  int sh = r.exif.height_px > 0 ? r.exif.height_px : 0;
  if (sw == 0 || sh == 0) { sw = 640; sh = 480; }

  const float scale = static_cast<float>(max_long_edge_) / static_cast<float>(std::max(sw, sh));
  int tw = std::max(1, static_cast<int>(sw * scale + 0.5f));
  int th = std::max(1, static_cast<int>(sh * scale + 0.5f));

  auto buf = std::make_shared<ImageBuffer>(tw, th, 3);
  FloatMat& mat = buf->GetCPUData();
  const float mid = 0.18f;
  for (size_t i = 0; i < mat.Total(); ++i) mat.Data()[i] = mid;
  buf->cpu_data_valid_ = true;
  r.buffer = std::move(buf);
  r.success = true;
  return r;
}

auto ThumbnailDecoder::Decode(const std::vector<uint8_t>& buffer, image_id_t id, DecodeType /*type*/)
    -> DecodeResult {
  DecodeResult r;
  r.image_id = id;
  if (buffer.empty()) {
    r.success = false;
    r.error = "empty buffer";
    return r;
  }
  r.exif = MetadataExtractor::ExtractFromBuffer(buffer.data(), buffer.size());
  int sw = r.exif.width_px  > 0 ? r.exif.width_px  : 640;
  int sh = r.exif.height_px > 0 ? r.exif.height_px : 480;
  const float scale = static_cast<float>(max_long_edge_) / static_cast<float>(std::max(sw, sh));
  int tw = std::max(1, static_cast<int>(sw * scale + 0.5f));
  int th = std::max(1, static_cast<int>(sh * scale + 0.5f));
  auto buf = std::make_shared<ImageBuffer>(tw, th, 3);
  FloatMat& mat = buf->GetCPUData();
  const float mid = 0.18f;
  for (size_t i = 0; i < mat.Total(); ++i) mat.Data()[i] = mid;
  buf->cpu_data_valid_ = true;
  r.buffer = std::move(buf);
  r.success = true;
  return r;
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
