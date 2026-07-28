// AlcedoAndroid - DataDecoder base interface.
// Self-contained Android port: the desktop exiv2/opencv/libraw-backed decoder
// hierarchy is collapsed to a single interface that yields an ImageBuffer +
// metadata from a file path or in-memory buffer.
// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <cstdint>
#include <memory>
#include <string>
#include <vector>

#include "image/image.hpp"
#include "image/image_buffer.hpp"
#include "type/type.hpp"

namespace alcedo {

enum class DecodeType { SLEEVE_LOADING, THUMB, RAW, REGULAR };

// Result of a decode operation: the decoded image buffer (if any) and metadata.
struct DecodeResult {
  image_id_t                image_id = 0;
  bool                      success  = false;
  std::shared_ptr<ImageBuffer> buffer;
  ExifDisplayMetaData       exif{};
  RawRuntimeColorContext    raw_context{};
  bool                      has_raw_context = false;
  std::string               error;
};

// Base class for all decoders.
class DataDecoder {
 public:
  virtual ~DataDecoder() = default;

  // Decode from a file path.
  virtual auto Decode(const image_path_t& path, image_id_t id, DecodeType type) -> DecodeResult = 0;

  // Decode from an in-memory buffer (e.g. a thumbnail extracted elsewhere).
  virtual auto Decode(const std::vector<uint8_t>& buffer, image_id_t id, DecodeType type)
      -> DecodeResult = 0;
};

}  // namespace alcedo
