#pragma once

#include <cstdint>
#include <string>
#include <vector>
#include <map>
#include <functional>

namespace alcedo {

// ============================================================
// Structured metadata for a decoded image
// ============================================================

struct CameraMetadata {
    std::string make;
    std::string model;
    std::string serial_number;
    std::string firmware_version;
    std::string unique_camera_model;
};

struct LensMetadata {
    std::string make;
    std::string model;
    std::string serial_number;
    float focal_length = 0.0f;           // mm
    float focal_length_35mm = 0.0f;      // 35mm equivalent
    float max_aperture = 0.0f;           // f-number
    float min_aperture = 0.0f;
    float focus_distance = 0.0f;         // meters
    std::string lens_id;
};

struct ExposureMetadata {
    float aperture = 0.0f;               // f-number
    float shutter_speed = 0.0f;          // seconds
    float iso = 0.0f;
    float exposure_bias = 0.0f;          // EV
    std::string exposure_program;        // "Manual", "Aperture Priority", etc.
    std::string metering_mode;           // "Matrix", "Center-weighted", "Spot"
    float brightness_value = 0.0f;
};

struct FlashMetadata {
    bool fired = false;
    bool return_detected = false;
    std::string mode;                    // "Auto", "On", "Off", "Red-eye", etc.
    float flash_compensation = 0.0f;
};

struct WhiteBalanceMetadata {
    std::string mode;                    // "Auto", "Manual", "Daylight", etc.
    float color_temperature = 0.0f;      // Kelvin
    float wb_rggb_levels[4] = {1.0f, 1.0f, 1.0f, 1.0f};
};

struct ImageMetadata {
    int width = 0;
    int height = 0;
    int orientation = 1;                  // 1=Normal, 6=90CW, 8=90CCW, 3=180
    std::string date_time_original;
    std::string date_time_digitized;
    std::string date_time_modified;
    std::string software;
    std::string artist;
    std::string copyright;
    std::string color_space;             // "sRGB", "Adobe RGB", "Uncalibrated"
    std::string compression;
    int bits_per_sample = 8;
    int samples_per_pixel = 3;
    float x_resolution = 72.0f;
    float y_resolution = 72.0f;
    int resolution_unit = 2;             // 1=none, 2=inch, 3=cm
};

struct GpsMetadata {
    bool has_gps = false;
    double latitude = 0.0;
    double longitude = 0.0;
    double altitude = 0.0;
    std::string latitude_ref;            // "N" or "S"
    std::string longitude_ref;           // "E" or "W"
    std::string altitude_ref;            // "0"=above sea level
    std::string gps_time_stamp;
    std::string gps_date_stamp;
    double speed = 0.0;                  // km/h
    double direction = 0.0;              // degrees
    std::string direction_ref;           // "T"=true, "M"=magnetic
    int gps_satellites = 0;
    std::string gps_measure_mode;        // "2"=2D, "3"=3D
};

struct DngColorMetadata {
    float color_matrix1[9] = {1,0,0, 0,1,0, 0,0,1};   // Camera RGB → XYZ (D50)
    float color_matrix2[9] = {1,0,0, 0,1,0, 0,0,1};   // Camera RGB → XYZ (StdA)
    float forward_matrix1[9] = {1,0,0, 0,1,0, 0,0,1}; // XYZ → Camera RGB
    float forward_matrix2[9] = {1,0,0, 0,1,0, 0,0,1};
    float calibration1[9] = {1,0,0, 0,1,0, 0,0,1};
    float calibration2[9] = {1,0,0, 0,1,0, 0,0,1};
    std::string calibration_illuminant1; // "Standard Light A", "D65", "D50", etc.
    std::string calibration_illuminant2;
    int calibration_illuminant1_value = 17;
    int calibration_illuminant2_value = 21;
    float analog_balance[4] = {1,1,1,1};
    float as_shot_neutral[4] = {1,1,1,1};
    float baseline_exposure = 0.0f;
    int cfa_pattern[4] = {0, 1, 1, 2};  // RGGB
    int cfa_repeat_rows = 2;
    int cfa_repeat_cols = 2;
    uint16_t white_level = 65535;
    uint16_t black_level = 0;
    int black_levels[4] = {0, 0, 0, 0};
    int bits_per_sample = 16;
    bool has_dng_data = false;
};

struct MakerNoteMetadata {
    std::string camera_maker;
    std::string raw_data_hex;            // Hex dump of MakerNote
    std::map<std::string, std::string> parsed_fields;
    // Nikon specific
    std::string nikon_iso_info;
    std::string nikon_color_mode;
    std::string nikon_quality;
    std::string nikon_white_balance;
    std::string nikon_sharpening;
    std::string nikon_focus_mode;
    std::string nikon_flash_setting;
    std::string nikon_iso_selection;
    float nikon_flash_compensation = 0.0f;
    int nikon_iso = 0;
    int nikon_iso2 = 0;
    // Canon specific
    std::string canon_owner_name;
    int canon_iso = 0;
    std::string canon_serial_number;
    int canon_sharpness = 0;
    int canon_contrast = 0;
    int canon_saturation = 0;
    std::string canon_color_tone;
    // Sony specific
    int sony_iso = 0;
    std::string sony_lens_id;
    std::string sony_creative_style;
    int sony_dynamic_range_optimizer = 0;
    // Fuji specific
    std::string fuji_film_simulation;
    int fuji_dynamic_range = 0;
    std::string fuji_grain_effect;
};

struct XmpMetadata {
    std::string raw_xml;
    std::map<std::string, std::string> dc_fields;       // Dublin Core
    std::map<std::string, std::string> xmp_fields;      // XMP basic
    std::map<std::string, std::string> crs_fields;      // Camera Raw Settings
    std::map<std::string, std::string> photoshop_fields;
    std::map<std::string, std::string> exif_fields;
    std::map<std::string, std::string> tiff_fields;
    std::string rating;
    std::string label;
    std::string creator;
    std::string description;
    std::string creator_tool;
    std::string keywords;
};

struct IccProfileMetadata {
    std::vector<uint8_t> raw_data;
    std::string description;
    std::string manufacturer;
    std::string model;
    std::string copyright;
    std::string device_class;            // "scnr", "mntr", "prtr", "spac"
    std::string color_space;             // "RGB", "CMYK", "GRAY", "LAB"
    std::string pcs;                     // "XYZ", "LAB"
    std::string rendering_intent;
    int version_major = 0;
    int version_minor = 0;
    bool has_profile = false;
};

// ============================================================
// Complete metadata container
// ============================================================

struct DecodedMetadata {
    CameraMetadata camera;
    LensMetadata lens;
    ExposureMetadata exposure;
    FlashMetadata flash;
    WhiteBalanceMetadata white_balance;
    ImageMetadata image;
    GpsMetadata gps;
    DngColorMetadata dng_color;
    MakerNoteMetadata maker_note;
    XmpMetadata xmp;
    IccProfileMetadata icc_profile;

    // Raw key-value pairs for all EXIF tags
    std::map<uint16_t, std::string> exif_tags;
    std::map<std::string, std::string> extra_fields;

    // File info
    std::string file_path;
    std::string file_type;
    uint64_t file_size = 0;

    bool has_metadata = false;
    bool has_exif = false;
    bool has_xmp = false;
    bool has_icc = false;
    bool has_maker_note = false;
    bool has_dng_color = false;
};

// ============================================================
// Metadata decoder
// ============================================================

using MetadataProgressCallback = std::function<void(float progress, const std::string& stage)>;

class MetadataDecoder {
public:
    MetadataDecoder();
    ~MetadataDecoder();

    // ── Full metadata extraction ──
    bool decode(const std::string& file_path, DecodedMetadata& metadata);
    bool decode_from_memory(const uint8_t* data, size_t size, DecodedMetadata& metadata);

    // ── With progress ──
    bool decode_with_progress(const std::string& file_path, DecodedMetadata& metadata,
                               MetadataProgressCallback progress_cb);

    // ── Individual parsers ──
    static bool parse_exif(const std::string& file_path, DecodedMetadata& metadata);
    static bool parse_exif_from_memory(const uint8_t* data, size_t size, DecodedMetadata& metadata);

    static bool parse_xmp(const std::string& file_path, DecodedMetadata& metadata);
    static bool parse_xmp_from_memory(const uint8_t* data, size_t size, DecodedMetadata& metadata);

    static bool parse_icc_profile(const std::string& file_path, DecodedMetadata& metadata);
    static bool parse_icc_profile_from_memory(const uint8_t* data, size_t size, DecodedMetadata& metadata);

    static bool parse_maker_note(const std::string& file_path, DecodedMetadata& metadata);
    static bool parse_maker_note_from_memory(const uint8_t* data, size_t size, DecodedMetadata& metadata);

    static bool parse_dng_color(const std::string& file_path, DecodedMetadata& metadata);
    static bool parse_dng_color_from_memory(const uint8_t* data, size_t size, DecodedMetadata& metadata);

    // ── Serialization ──
    static std::string to_json(const DecodedMetadata& metadata);
    static std::string to_json_compact(const DecodedMetadata& metadata);
    static bool from_json(const std::string& json, DecodedMetadata& metadata);

    // ── File type detection ──
    static std::string detect_file_type(const uint8_t* data, size_t size);
    static std::string detect_file_type(const std::string& path);

    // ── Utility ──
    static std::string get_exposure_program_name(int code);
    static std::string get_metering_mode_name(int code);
    static std::string get_flash_mode_name(int code);
    static std::string get_orientation_name(int orientation);
    static std::string get_color_space_name(int code);
    static std::string get_white_balance_name(int code);
    static std::string get_illuminant_name(int code);

private:
    // TIFF/EXIF parsing
    struct TiffCtx {
        bool little_endian = true;
        const uint8_t* data = nullptr;
        size_t size = 0;
    };

    static bool parse_tiff_header(const uint8_t* data, size_t size, TiffCtx& ctx);
    static bool read_ifd_entry(const TiffCtx& ctx, uint32_t ifd_offset, uint16_t idx,
                               uint16_t& tag, uint16_t& type, uint32_t& count,
                               uint32_t& value_offset, const uint8_t*& value_ptr);
    static uint16_t read_u16(const uint8_t* d, bool le);
    static uint32_t read_u32(const uint8_t* d, bool le);
    static float read_rational(const uint8_t* d, bool le);
    static float read_srational(const uint8_t* d, bool le);
    static std::string read_string(const uint8_t* d, size_t max_len);

    static void parse_ifd0(const TiffCtx& ctx, uint32_t ifd_offset, DecodedMetadata& meta);
    static void parse_exif_ifd(const TiffCtx& ctx, uint32_t ifd_offset, DecodedMetadata& meta);
    static void parse_gps_ifd(const TiffCtx& ctx, uint32_t ifd_offset, DecodedMetadata& meta);
    static void parse_maker_note_internal(const TiffCtx& ctx, const uint8_t* maker_data,
                                          size_t maker_size, DecodedMetadata& meta);
    static void parse_dng_ifd(const TiffCtx& ctx, uint32_t ifd_offset, DecodedMetadata& meta);

    // XMP parsing
    static void parse_xmp_packet(const std::string& xmp_xml, DecodedMetadata& meta);
    static std::string extract_xmp_field(const std::string& xml, const std::string& ns,
                                          const std::string& name);
    static std::string extract_xmp_tag(const std::string& xml, const std::string& tag);

    // ICC parsing
    static bool parse_icc_header(const uint8_t* data, size_t size, DecodedMetadata& meta);

    // MakerNote sub-parsers
    static void parse_nikon_maker_note(const TiffCtx& ctx, const uint8_t* data, size_t size,
                                        DecodedMetadata& meta);
    static void parse_canon_maker_note(const TiffCtx& ctx, const uint8_t* data, size_t size,
                                        DecodedMetadata& meta);
    static void parse_sony_maker_note(const TiffCtx& ctx, const uint8_t* data, size_t size,
                                       DecodedMetadata& meta);
    static void parse_fuji_maker_note(const TiffCtx& ctx, const uint8_t* data, size_t size,
                                       DecodedMetadata& meta);

    // JSON helpers
    static std::string json_escape(const std::string& s);
    static std::string json_float_array(const float* data, int count);
    static std::string json_int_array(const int* data, int count);

    MetadataProgressCallback progress_cb_;
    void report_progress(float p, const std::string& stage);
};

} // namespace alcedo