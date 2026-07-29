// AlcedoAndroid - Image class (tracked image file with embedded metadata).
// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <atomic>
#include <cstdint>
#include <memory>
#include <string>

#include "image/image_buffer.hpp"
#include "image/metadata.hpp"
#include "json.hpp"
#include "type/type.hpp"

namespace alcedo {

enum class ImageType { DEFAULT, JPEG, PNG, TIFF, ARW, CR2, CR3, NEF, DNG };
enum class ThumbState : uint8_t { NOT_PRESENT = 0, PENDING, READY, FAILED };
enum class ImageSyncState : uint8_t { SYNCED, UNSYNCED, MODIFIED, DELETED };

// Represents a tracked image file. Holds metadata + image/thumbnail buffers.
class Image {
 public:
  image_id_t            image_id_   = 0;
  image_path_t          image_path_;
  file_name_t           image_name_;

  nlohmann::json        exif_json_;
  ExifDisplayMetaData   exif_display_;
  RawRuntimeColorContext raw_color_context_;
  std::atomic<bool>     has_raw_color_context_{false};

  ImageBuffer           image_data_;
  ImageBuffer           processed_data_;
  ImageBuffer           thumbnail_;
  ImageType             image_type_ = ImageType::DEFAULT;

  std::atomic<bool>     has_thumbnail_{false};
  std::atomic<ThumbState>     thumb_state_{ThumbState::NOT_PRESENT};
  std::atomic<ImageSyncState> sync_state_{ImageSyncState::SYNCED};

  p_hash_t              checksum_ = 0;

  std::atomic<bool>     has_full_img_{false};
  std::atomic<bool>     has_thumb_{false};
  std::atomic<bool>     has_exif_{false};
  std::atomic<bool>     has_exif_json_{false};
  std::atomic<bool>     has_exif_display_{false};

  std::atomic<bool>     thumb_pinned_{false};
  std::atomic<bool>     full_pinned_{false};

  Image() = default;
  explicit Image(image_id_t image_id);
  explicit Image(image_id_t image_id, image_path_t image_path, ImageType image_type);
  explicit Image(image_id_t image_id, image_path_t image_path, file_name_t image_name,
                 ImageType image_type);
  explicit Image(image_path_t image_path, ImageType image_type);
  Image(Image&& other) noexcept;

  Image& operator=(Image&& other) noexcept;

  void LoadOriginalData(ImageBuffer&& load_image);
  void LoadThumbnailData(ImageBuffer&& thumbnail);

  ImageBuffer&       GetImageData() { return image_data_; }
  ImageBuffer&       GetThumbnailBuffer() { return thumbnail_; }
  FloatMat&          GetThumbnailMat() { return thumbnail_.GetCPUData(); }

  // Processed (rendered) result buffer, kept separate from the original image
  // data so re-rendering always starts from the untouched source pixels.
  void               SetImageData(ImageBuffer buf) { processed_data_ = std::move(buf); }
  ImageBuffer&       GetProcessedData() { return processed_data_; }

  void               SetId(image_id_t image_id);
  void               SetExifDisplayMetaData(ExifDisplayMetaData&& exif_display);
  void               SetHdrDisplayMetadata(bool is_hdr);
  void               SetRawColorContext(RawRuntimeColorContext&& ctx);
  const RawRuntimeColorContext& GetRawColorContext() const { return raw_color_context_; }
  bool               HasRawColorContext() const { return has_raw_color_context_.load(); }

  void               ClearData();
  void               ClearThumbnail();
  void               ComputeChecksum();
  std::string        ExifToJson();
  void               JsonToExif(std::string json_str);

  void               MarkSyncState(ImageSyncState state);
  ImageSyncState     GetSyncState() { return sync_state_.load(); }
};

}  // namespace alcedo
