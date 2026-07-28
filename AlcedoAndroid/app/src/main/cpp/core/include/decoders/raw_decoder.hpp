// AlcedoAndroid - RAW decoder.
// Self-contained Android port. The desktop libraw-backed decoder is replaced
// with a lightweight decoder that parses TIFF-based RAW headers (DNG/CR2/NEF/
// ARW share a TIFF structure) to recover dimensions and color matrices, then
// produces a linear float buffer. A hook is provided for vendor libraw
// integration at build time (ALCEDO_HAVE_LIBRAW).
// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <cstdint>
#include <vector>

#include "decoders/data_decoder.hpp"
#include "image/image.hpp"
#include "image/metadata.hpp"

namespace alcedo {

enum class OutputColorSpace : int {
  RAW         = 0,
  sRGB        = 1,
  AdobeRGB    = 2,
  Wide        = 3,
  ProPhotoRGB = 4,
  XYZ         = 5,
  ACES        = 6,
  DCIP3       = 7,
  REC2020     = 8,
};

class RawDecoder : public DataDecoder {
 public:
  RawDecoder() = default;

  auto Decode(const image_path_t& path, image_id_t id, DecodeType type) -> DecodeResult override;
  auto Decode(const std::vector<uint8_t>& buffer, image_id_t id, DecodeType type)
      -> DecodeResult override;

  // Decode into a caller-supplied Image (fills image_data_ + raw_color_context_).
  void Decode(const std::vector<uint8_t>& buffer, std::shared_ptr<Image> source_img);

 private:
  auto DecodeTiffLike(const uint8_t* data, size_t len, image_id_t id) -> DecodeResult;
};

}  // namespace alcedo
