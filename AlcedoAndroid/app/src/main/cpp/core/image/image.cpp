// AlcedoAndroid - Image implementation.
// SPDX-License-Identifier: GPL-3.0-only
#include "image/image.hpp"

#include <fstream>
#include <utility>

#include "image/metadata_extractor.hpp"
#include "utils/app_logging.hpp"
#include "utils/time_provider.hpp"

namespace alcedo {

Image::Image(image_id_t image_id) : image_id_(image_id) {}

Image::Image(image_id_t image_id, image_path_t image_path, ImageType image_type)
    : image_id_(image_id), image_path_(std::move(image_path)),
      image_name_(image_path_.filename().string()), image_type_(image_type) {}

Image::Image(image_id_t image_id, image_path_t image_path, file_name_t image_name,
             ImageType image_type)
    : image_id_(image_id), image_path_(std::move(image_path)),
      image_name_(std::move(image_name)), image_type_(image_type) {}

Image::Image(image_path_t image_path, ImageType image_type)
    : image_path_(std::move(image_path)),
      image_name_(image_path_.filename().string()), image_type_(image_type) {}

Image::Image(Image&& other) noexcept : Image() { *this = std::move(other); }

Image& Image::operator=(Image&& other) noexcept {
  if (this == &other) return *this;
  image_id_              = other.image_id_;
  image_path_            = std::move(other.image_path_);
  image_name_            = std::move(other.image_name_);
  exif_json_             = std::move(other.exif_json_);
  exif_display_         = std::move(other.exif_display_);
  raw_color_context_    = std::move(other.raw_color_context_);
  has_raw_color_context_.store(other.has_raw_color_context_.load());
  image_data_           = std::move(other.image_data_);
  thumbnail_            = std::move(other.thumbnail_);
  image_type_           = other.image_type_;
  has_thumbnail_.store(other.has_thumbnail_.load());
  thumb_state_.store(other.thumb_state_.load());
  sync_state_.store(other.sync_state_.load());
  checksum_             = other.checksum_;
  has_full_img_.store(other.has_full_img_.load());
  has_thumb_.store(other.has_thumb_.load());
  has_exif_.store(other.has_exif_.load());
  has_exif_json_.store(other.has_exif_json_.load());
  has_exif_display_.store(other.has_exif_display_.load());
  thumb_pinned_.store(other.thumb_pinned_.load());
  full_pinned_.store(other.full_pinned_.load());
  return *this;
}

void Image::LoadOriginalData(ImageBuffer&& load_image) {
  image_data_ = std::move(load_image);
  has_full_img_.store(true);
  sync_state_.store(ImageSyncState::MODIFIED);
}

void Image::LoadThumbnailData(ImageBuffer&& thumbnail) {
  thumbnail_ = std::move(thumbnail);
  has_thumbnail_.store(true);
  has_thumb_.store(true);
  thumb_state_.store(ThumbState::READY);
}

void Image::SetId(image_id_t image_id) { image_id_ = image_id; }

void Image::SetExifDisplayMetaData(ExifDisplayMetaData&& exif_display) {
  exif_display_ = std::move(exif_display);
  has_exif_display_.store(true);
  has_exif_.store(true);
}

void Image::SetHdrDisplayMetadata(bool /*is_hdr*/) {
  // Reserved for HDR display metadata (PQ/HLG). Stored in exif_json_.
}

void Image::SetRawColorContext(RawRuntimeColorContext&& ctx) {
  raw_color_context_ = std::move(ctx);
  has_raw_color_context_.store(true);
}

void Image::ClearData() {
  image_data_.ReleaseCPUData();
  image_data_.ReleaseGPUData();
  has_full_img_.store(false);
}

void Image::ClearThumbnail() {
  thumbnail_.ReleaseCPUData();
  thumbnail_.ReleaseGPUData();
  has_thumbnail_.store(false);
  has_thumb_.store(false);
  thumb_state_.store(ThumbState::NOT_PRESENT);
}

void Image::ComputeChecksum() {
  const FloatMat& mat = image_data_.GetCPUData();
  if (mat.Empty()) {
    checksum_ = 0;
    return;
  }
  // Simple FNV-1a 64-bit over the float bytes.
  uint64_t h = 0xcbf29ce484222325ULL;
  const uint8_t* bytes = reinterpret_cast<const uint8_t*>(mat.Data());
  size_t len = mat.Total() * sizeof(float);
  for (size_t i = 0; i < len; ++i) {
    h ^= bytes[i];
    h *= 0x100000001b3ULL;
  }
  checksum_ = h;
}

std::string Image::ExifToJson() {
  exif_json_ = MetadataExtractor::ToJson(exif_display_);
  has_exif_json_.store(true);
  return exif_json_.dump();
}

void Image::JsonToExif(std::string json_str) {
  try {
    exif_json_ = nlohmann::json::parse(json_str);
    exif_display_ = MetadataExtractor::FromJson(exif_json_);
    has_exif_display_.store(true);
    has_exif_.store(true);
  } catch (const std::exception& e) {
    ALOGW("Image::JsonToExif failed: %s", e.what());
  }
}

void Image::MarkSyncState(ImageSyncState state) { sync_state_.store(state); }

}  // namespace alcedo
