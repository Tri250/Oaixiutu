// AlcedoAndroid - Image loader/writer/ultrahdr/icc headers (IO layer).
// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <unordered_map>
#include <vector>

#include "image/image.hpp"
#include "image/image_buffer.hpp"
#include "type/type.hpp"

namespace alcedo {

// Loads an image file (JPEG/PNG/TIFF/RAW) into an Image object.
class ImageLoader {
 public:
  auto Load(const std::filesystem::path& path) -> std::shared_ptr<Image>;
  auto LoadThumbnail(const std::filesystem::path& path, uint32_t max_size) -> std::shared_ptr<Image>;
  auto DetectType(const std::filesystem::path& path) -> ImageType;
};

// Writes a processed ImageBuffer to a file in JPEG/PNG/TIFF format.
class ImageWriter {
 public:
  auto WriteJPEG(const ImageBuffer& buffer, const std::filesystem::path& path, int quality) -> bool;
  auto WritePNG(const ImageBuffer& buffer, const std::filesystem::path& path) -> bool;
  auto WriteTIFF(const ImageBuffer& buffer, const std::filesystem::path& path) -> bool;
};

// Writes an UltraHDR (gain-mapped JPEG) image for Android 14+ display.
class UltraHDRWriter {
 public:
  auto Write(const ImageBuffer& sdr_buffer, const ImageBuffer& hdr_buffer,
             const std::filesystem::path& path, int quality) -> bool;
};

// Resolves the ICC profile to embed on export based on the output colour space.
class ExportICCProfileResolver {
 public:
  auto Resolve(const std::string& color_space) -> std::optional<std::vector<uint8_t>>;
  void RegisterProfile(const std::string& color_space, std::vector<uint8_t> profile);
 private:
  std::unordered_map<std::string, std::vector<uint8_t>> profiles_;
};

}  // namespace alcedo
