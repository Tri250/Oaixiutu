// Ported from AlcedoStudio desktop: image/image.hpp
// SPDX-License-Identifier: GPL-3.0-only
//
// Core Image model for Android. Uses std::string (UTF-8) throughout.
// No OpenCV, no OIIO, no Exiv2, no nlohmann/json.

#pragma once

#include <atomic>
#include <cstdint>
#include <cstring>
#include <string>
#include <utility>

#include "image/image_buffer.h"

namespace alcedo {

// ============================================================
// Type aliases
// ============================================================
using image_id_t  = uint64_t;
using image_path_t = std::string;

// ============================================================
// Image type enumeration
// ============================================================
enum class ImageType {
    UNKNOWN = 0,
    RAW,
    JPEG,
    TIFF,
    PNG,
    WEBP,
    DNG,
    HEIF
};

// ============================================================
// Image sync state
// ============================================================
enum class ImageSyncState {
    SYNCED  = 0,
    MODIFIED,
    NEW,
    DELETED
};

// ============================================================
// RAW runtime color context
// ============================================================
struct RawRuntimeColorContext {
    bool  valid                       = false;
    bool  output_in_camera_space      = false;

    float cam_mul[3]                  = {1.0f, 1.0f, 1.0f};
    float pre_mul[3]                  = {1.0f, 1.0f, 1.0f};
    float cam_xyz[9]                  = {1,0,0, 0,1,0, 0,0,1};
    float rgb_cam[9]                  = {1,0,0, 0,1,0, 0,0,1};

    // Camera identity
    std::string camera_make;
    std::string camera_model;

    // Lens metadata
    bool   lens_metadata_valid        = false;
    std::string lens_make;
    std::string lens_model;
    float  focal_length_mm            = 0.0f;
    float  aperture_f_number          = 0.0f;
    float  focus_distance_m           = 0.0f;
    float  focal_35mm_mm              = 0.0f;
    float  crop_factor_hint           = 1.0f;

    // Color matrices
    bool   color_matrices_valid       = false;
    float  color_matrix_1[9]          = {1,0,0, 0,1,0, 0,0,1};
    float  color_matrix_2[9]          = {1,0,0, 0,1,0, 0,0,1};

    // Forward matrices
    bool   forward_matrices_valid     = false;
    float  forward_matrix_1[9]        = {1,0,0, 0,1,0, 0,0,1};
    float  forward_matrix_2[9]        = {1,0,0, 0,1,0, 0,0,1};

    // As-shot neutral
    bool   as_shot_neutral_valid      = false;
    float  as_shot_neutral[3]         = {1.0f, 1.0f, 1.0f};

    // Calibration illuminants
    bool   calibration_illuminants_valid = false;
    int    color_matrix_1_cct         = 0;   // Correlated color temperature
    int    color_matrix_2_cct         = 0;

    void reset() {
        valid = false;
        output_in_camera_space = false;
        std::fill(cam_mul, cam_mul + 3, 1.0f);
        std::fill(pre_mul, pre_mul + 3, 1.0f);
        float identity[9] = {1,0,0, 0,1,0, 0,0,1};
        std::memcpy(cam_xyz, identity, sizeof(cam_xyz));
        std::memcpy(rgb_cam, identity, sizeof(rgb_cam));
        camera_make.clear();
        camera_model.clear();
        lens_metadata_valid = false;
        lens_make.clear();
        lens_model.clear();
        focal_length_mm = 0.0f;
        aperture_f_number = 0.0f;
        focus_distance_m = 0.0f;
        focal_35mm_mm = 0.0f;
        crop_factor_hint = 1.0f;
        color_matrices_valid = false;
        std::memcpy(color_matrix_1, identity, sizeof(color_matrix_1));
        std::memcpy(color_matrix_2, identity, sizeof(color_matrix_2));
        forward_matrices_valid = false;
        std::memcpy(forward_matrix_1, identity, sizeof(forward_matrix_1));
        std::memcpy(forward_matrix_2, identity, sizeof(forward_matrix_2));
        as_shot_neutral_valid = false;
        std::fill(as_shot_neutral, as_shot_neutral + 3, 1.0f);
        calibration_illuminants_valid = false;
        color_matrix_1_cct = 0;
        color_matrix_2_cct = 0;
    }
};

// ============================================================
// EXIF display metadata
// ============================================================
struct ExifDisplayMetaData {
    std::string make;
    std::string model;
    std::string lens;
    std::string lens_make;
    std::string date_time_str;
    float       aperture          = 0.0f;
    float       focal             = 0.0f;
    float       focal_35mm        = 0.0f;
    float       focus_distance_m  = 0.0f;
    float       iso               = 0.0f;
    std::pair<int, int> shutter_speed = {0, 1};  // numerator / denominator
    int         rating            = 0;            // -1..5
    bool        is_hdr            = false;
    int         width             = 0;
    int         height            = 0;

    // Clamp rating to [-1, 5].
    static int NormalizeRating(int r) {
        if (r < -1) return -1;
        if (r > 5)  return 5;
        return r;
    }

    // JSON serialization — self-contained (no nlohmann dependency).
    std::string ToJson() const;
    static ExifDisplayMetaData FromJson(const std::string& json);
};

// ============================================================
// Image class
// ============================================================
class Image {
public:
    // ── Construction ──
    Image();
    explicit Image(image_id_t id);
    Image(image_id_t id, const image_path_t& path);
    ~Image() = default;

    // Non-copyable, movable.
    Image(const Image&) = delete;
    Image& operator=(const Image&) = delete;
    Image(Image&&) noexcept = default;
    Image& operator=(Image&&) noexcept = default;

    // ── Data loading ──
    void LoadOriginalData(ImageBuffer&& buf);
    void LoadThumbnailData(ImageBuffer&& buf);
    void ClearData();
    void ClearThumbnail();

    // ── Checksum ──
    // Computes FNV-1a over the current pixel data (if present).
    void ComputeChecksum();
    uint64_t GetChecksum() const { return checksum_; }

    // ── EXIF JSON bridge ──
    std::string ExifToJson() const;
    static ExifDisplayMetaData JsonToExif(const std::string& json);

    // ── Metadata setters ──
    void SetExifDisplayMetaData(const ExifDisplayMetaData& data);
    void SetHdrDisplayMetadata(bool is_hdr);
    void SetRawColorContext(const RawRuntimeColorContext& ctx);

    // ── Metadata getters ──
    const ExifDisplayMetaData& GetExifDisplayMetaData() const { return exif_data_; }
    bool HasExifDisplay() const { return has_exif_display_; }
    const RawRuntimeColorContext& GetRawColorContext() const { return raw_color_context_; }
    bool HasRawColorContext() const { return has_raw_color_context_; }

    // ── Sync state ──
    void MarkSyncState(ImageSyncState state);
    ImageSyncState GetSyncState() const;

    // ── Getters / Setters ──
    image_id_t   GetImageId()   const { return image_id_; }
    const image_path_t& GetImagePath() const { return image_path_; }
    const std::string&   GetImageName() const { return image_name_; }
    ImageType     GetImageType() const { return image_type_; }
    bool          HasFullImg()   const { return has_full_img_; }
    bool          HasThumbnail() const { return has_thumbnail_; }
    bool          IsHdr()        const { return is_hdr_; }

    void SetImageId(image_id_t id)   { image_id_ = id; }
    void SetImagePath(const image_path_t& path);
    void SetImageName(const std::string& name) { image_name_ = name; }
    void SetImageType(ImageType type) { image_type_ = type; }

    ImageBuffer&       OriginalBuffer()       { return original_buffer_; }
    const ImageBuffer& OriginalBuffer() const { return original_buffer_; }
    ImageBuffer&       ThumbnailBuffer()       { return thumbnail_buffer_; }
    const ImageBuffer& ThumbnailBuffer() const { return thumbnail_buffer_; }

private:
    image_id_t       image_id_   = 0;
    image_path_t     image_path_;
    std::string      image_name_;
    ImageType        image_type_ = ImageType::UNKNOWN;

    // Pixel data
    ImageBuffer      original_buffer_;
    ImageBuffer      thumbnail_buffer_;

    // Metadata
    ExifDisplayMetaData    exif_data_;
    bool                   has_exif_display_     = false;
    RawRuntimeColorContext raw_color_context_;
    bool                   has_raw_color_context_ = false;

    // Sync
    std::atomic<ImageSyncState> sync_state_{ImageSyncState::NEW};

    // Checksum (FNV-1a)
    uint64_t checksum_ = 0;

    // Flags
    bool has_full_img_   = false;
    bool has_thumbnail_  = false;
    bool is_hdr_         = false;
};

} // namespace alcedo
