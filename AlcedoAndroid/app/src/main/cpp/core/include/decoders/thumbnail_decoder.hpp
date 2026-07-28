// AlcedoAndroid - Thumbnail decoder.
// Extracts an embedded JPEG/preview thumbnail from a RAW file (TIFF-based) or
// decodes a regular JPEG/PNG thumbnail. Falls back to a downsampled decode of
// the full image when no embedded preview is available.
// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <cstdint>
#include <vector>

#include "decoders/data_decoder.hpp"
#include "image/image.hpp"

namespace alcedo {

class ThumbnailDecoder : public DataDecoder {
 public:
  explicit ThumbnailDecoder(int max_long_edge = 512) : max_long_edge_(max_long_edge) {}

  auto Decode(const image_path_t& path, image_id_t id, DecodeType type) -> DecodeResult override;
  auto Decode(const std::vector<uint8_t>& buffer, image_id_t id, DecodeType type)
      -> DecodeResult override;

  // Downsample an existing float image to fit within max_long_edge_.
  auto Downsample(const ImageBuffer& src) const -> std::shared_ptr<ImageBuffer>;

 private:
  int max_long_edge_;
};

}  // namespace alcedo
