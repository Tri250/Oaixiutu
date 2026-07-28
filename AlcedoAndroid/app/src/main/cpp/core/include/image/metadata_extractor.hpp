// AlcedoAndroid - EXIF / metadata extraction.
// On Android the heavy desktop exiv2 dependency is replaced with a lightweight
// extractor that parses a small subset of TIFF/EXIF tags directly from the RAW
// file bytes. Camera-vendor RAW headers are largely TIFF-based, so the same
// parser covers DNG/CR2/NEF/ARW thumbnails and EXIF blocks.
// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <cstdint>
#include <string>

#include "image/metadata.hpp"
#include "json.hpp"
#include "type/type.hpp"

namespace alcedo {

class MetadataExtractor {
 public:
  // Parse the EXIF display metadata from a file on disk.
  static ExifDisplayMetaData ExtractFromFile(const image_path_t& path);

  // Parse EXIF from an in-memory buffer (e.g. an embedded JPEG thumbnail or a
  // TIFF header). Returns an empty struct on failure.
  static ExifDisplayMetaData ExtractFromBuffer(const uint8_t* data, size_t len);

  // Serialize / deserialize EXIF display metadata to JSON.
  static nlohmann::json  ToJson(const ExifDisplayMetaData& meta);
  static ExifDisplayMetaData FromJson(const nlohmann::json& j);

  // Guess the image type from a file extension.
  static ImageType GuessType(const image_path_t& path);
};

}  // namespace alcedo
