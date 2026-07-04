#pragma once
#include <cstdint>
#include <string>
#include <map>
#include <vector>

namespace alcedo {

// ============================================================
// EXIF / Metadata structures
// ============================================================

struct ExifData {
    // Camera
    std::string make;
    std::string model;
    std::string serial_number;

    // Lens
    std::string lens_make;
    std::string lens_model;
    float focal_length = 0.0f;
    float focal_length_35mm = 0.0f;
    float max_aperture = 0.0f;

    // Exposure
    float aperture = 0.0f;       // f-number
    float shutter_speed = 0.0f;  // seconds
    float iso = 0.0f;
    float exposure_bias = 0.0f;
    std::string exposure_program;
    std::string metering_mode;

    // Flash
    bool flash_fired = false;
    std::string flash_mode;

    // White balance
    std::string white_balance;
    float color_temperature = 0.0f;

    // Image
    int image_width = 0;
    int image_height = 0;
    int orientation = 1; // 1=normal, 6=90CW, 8=90CCW, 3=180
    std::string date_time_original;
    std::string date_time_digitized;
    std::string software;

    // GPS
    bool has_gps = false;
    double gps_latitude = 0.0;
    double gps_longitude = 0.0;
    double gps_altitude = 0.0;
    std::string gps_latitude_ref;
    std::string gps_longitude_ref;

    // Color
    std::string color_space;
    std::string profile_name;

    // RAW specific
    std::string raw_format;
    int raw_bayer_pattern = 0;
    uint16_t raw_white_level = 65535;
    uint16_t raw_black_level = 0;
    int raw_black_levels[4] = {0, 0, 0, 0}; // Per-channel black levels
    uint16_t raw_white_levels[4] = {65535, 65535, 65535, 65535};
    float raw_color_matrix[9] = {1,0,0, 0,1,0, 0,0,1}; // Camera color matrix
    float raw_camera_to_xyz[9] = {1,0,0, 0,1,0, 0,0,1};
    float raw_calibration_illuminant1[9] = {1,0,0, 0,1,0, 0,0,1};
    float raw_calibration_illuminant2[9] = {1,0,0, 0,1,0, 0,0,1};
    std::string raw_calibration_illuminant1_name;
    std::string raw_calibration_illuminant2_name;
    float raw_forward_matrix[9] = {1,0,0, 0,1,0, 0,0,1};
    int raw_cfa_pattern[4] = {0, 1, 1, 2}; // RGGB

    // XMP
    std::string xmp_raw_data;
    std::map<std::string, std::string> xmp_fields;

    // ICC profile
    std::vector<uint8_t> icc_profile_data;
    std::string icc_profile_description;
};

// ============================================================
// Metadata Extractor
// ============================================================

class MetadataExtractor {
public:
    // Extract EXIF from JPEG/TIFF file
    static bool extract_exif(const std::string& file_path, ExifData& data);

    // Extract EXIF from memory buffer
    static bool extract_exif_from_memory(const uint8_t* buffer, size_t size, ExifData& data);

    // Extract XMP metadata
    static bool extract_xmp(const std::string& file_path, ExifData& data);
    static bool extract_xmp_from_memory(const uint8_t* buffer, size_t size, ExifData& data);

    // Extract ICC profile
    static bool extract_icc_profile(const std::string& file_path, ExifData& data);
    static bool extract_icc_profile_from_memory(const uint8_t* buffer, size_t size, ExifData& data);

    // Extract RAW color matrix from DNG metadata
    static bool extract_raw_color_matrix(const std::string& dng_path, ExifData& data);

    // Extract all metadata from a file
    static bool extract_all(const std::string& file_path, ExifData& data);

    // Helper: convert EXIF GPS coordinates to decimal degrees
    static double exif_gps_to_decimal(double degrees, double minutes, double seconds, const std::string& ref);

    // Helper: parse EXIF rational (numerator/denominator)
    static float parse_rational(const uint8_t* data, bool little_endian);

    // Helper: detect file type from magic bytes
    static std::string detect_file_type(const uint8_t* buffer, size_t size);

private:
    // TIFF/EXIF internal parsing
    static bool parse_tiff_header(const uint8_t* data, size_t size, bool& little_endian, uint32_t& ifd0_offset);
    static bool parse_ifd_entry(const uint8_t* data, size_t size, bool little_endian,
                                uint32_t ifd_offset, uint16_t entry_index,
                                uint16_t& tag, uint16_t& type, uint32_t& count, uint32_t& value_offset);
    static bool parse_ifd(const uint8_t* data, size_t size, bool little_endian,
                          uint32_t ifd_offset, ExifData& out_data);

    // XMP parsing
    static bool parse_xmp_packet(const std::string& xmp_data, ExifData& out_data);

    // ICC parsing
    static bool parse_icc_header(const uint8_t* data, size_t size, ExifData& out_data);

    // DNG specific
    static bool parse_dng_tags(const uint8_t* data, size_t size, bool little_endian,
                               uint32_t ifd_offset, ExifData& out_data);
};

} // namespace alcedo