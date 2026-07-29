// AlcedoAndroid - MetadataDecoder implementation.
// SPDX-License-Identifier: GPL-3.0-only
#include "decoders/metadata_decoder.hpp"

#include <fstream>
#include <vector>

#include "image/metadata_extractor.hpp"
#include "utils/app_logging.hpp"

namespace alcedo {
namespace {

// 500 MiB guard for tellg() bounds checking (matches image_loader /
// raw_decoder / thumbnail_decoder). Prevents absurd allocations from a bogus
// file size returned by a failed tellg() or a malformed stat.
constexpr int64_t kMaxReadBytes = 500LL * 1024 * 1024;

std::vector<uint8_t> ReadFile(const image_path_t& path) {
  std::ifstream ifs(path, std::ios::binary | std::ios::ate);
  if (!ifs) return {};
  const auto size = ifs.tellg();
  // Bounds-check tellg(): reject negative (-1 on failure) or absurdly large
  // sizes (> 500 MiB) to avoid a bogus allocation.
  if (size <= 0 || static_cast<int64_t>(size) > kMaxReadBytes) return {};
  ifs.seekg(0, std::ios::beg);
  std::vector<uint8_t> buf(static_cast<size_t>(size));
  ifs.read(reinterpret_cast<char*>(buf.data()), size);
  return buf;
}

}  // namespace

auto MetadataDecoder::Decode(const image_path_t& path, image_id_t id, DecodeType /*type*/)
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
  r.success = true;
  return r;
}

auto MetadataDecoder::Decode(const std::vector<uint8_t>& buffer, image_id_t id, DecodeType /*type*/)
    -> DecodeResult {
  DecodeResult r;
  r.image_id = id;
  if (buffer.empty()) {
    r.success = false;
    r.error = "empty buffer";
    return r;
  }
  r.exif = MetadataExtractor::ExtractFromBuffer(buffer.data(), buffer.size());
  r.success = true;
  return r;
}

}  // namespace alcedo
