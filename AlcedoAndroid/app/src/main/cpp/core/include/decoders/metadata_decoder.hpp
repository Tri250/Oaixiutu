// AlcedoAndroid - Metadata decoder (metadata-only extraction pass).
// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <cstdint>
#include <vector>

#include "decoders/data_decoder.hpp"
#include "image/image.hpp"

namespace alcedo {

class MetadataDecoder : public DataDecoder {
 public:
  MetadataDecoder() = default;

  auto Decode(const image_path_t& path, image_id_t id, DecodeType type) -> DecodeResult override;
  auto Decode(const std::vector<uint8_t>& buffer, image_id_t id, DecodeType type)
      -> DecodeResult override;
};

}  // namespace alcedo
