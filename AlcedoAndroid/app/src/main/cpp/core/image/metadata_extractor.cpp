#include "metadata_extractor.h"
#include <fstream>
#include <cstring>
#include <algorithm>
#include <sstream>
#include <android/log.h>

#define LOG_TAG "AlcedoMetadata"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace alcedo {

// TIFF tag constants
enum TiffTag : uint16_t {
    TAG_MAKE = 0x010F,
    TAG_MODEL = 0x0110,
    TAG_ORIENTATION = 0x0112,
    TAG_SOFTWARE = 0x0131,
    TAG_DATE_TIME = 0x0132,
    TAG_EXIF_IFD = 0x8769,
    TAG_GPS_IFD = 0x8825,
    TAG_IMAGE_WIDTH = 0x0100,
    TAG_IMAGE_HEIGHT = 0x0101,
    TAG_BITS_PER_SAMPLE = 0x0102,
    TAG_DNG_CFA_PATTERN_DIM = 0x828D,
    TAG_DNG_CFA_PATTERN = 0x828E,
    TAG_DNG_COLOR_MATRIX1 = 0xC621,
    TAG_DNG_COLOR_MATRIX2 = 0xC622,
    TAG_DNG_CAMERA_CALIBRATION1 = 0xC623,
    TAG_DNG_CAMERA_CALIBRATION2 = 0xC624,
    TAG_DNG_FORWARD_MATRIX1 = 0xC714,
    TAG_DNG_FORWARD_MATRIX2 = 0xC715,
    TAG_DNG_WHITE_LEVEL = 0xC61D,
    TAG_DNG_BLACK_LEVEL = 0xC61A,
    TAG_DNG_CALIBRATION_ILLUMINANT1 = 0xC65A,
    TAG_DNG_CALIBRATION_ILLUMINANT2 = 0xC65B,
};

enum ExifTag : uint16_t {
    EXIF_EXPOSURE_TIME = 0x829A,
    EXIF_FNUMBER = 0x829D,
    EXIF_EXPOSURE_PROGRAM = 0x8822,
    EXIF_ISO = 0x8827,
    EXIF_DATE_TIME_ORIGINAL = 0x9003,
    EXIF_DATE_TIME_DIGITIZED = 0x9004,
    EXIF_SHUTTER_SPEED = 0x9201,
    EXIF_APERTURE = 0x9202,
    EXIF_EXPOSURE_BIAS = 0x9204,
    EXIF_MAX_APERTURE = 0x9205,
    EXIF_METERING_MODE = 0x9207,
    EXIF_FLASH = 0x9209,
    EXIF_FOCAL_LENGTH = 0x920A,
    EXIF_FOCAL_LENGTH_35MM = 0xA405,
    EXIF_LENS_MAKE = 0xA433,
    EXIF_LENS_MODEL = 0xA434,
    EXIF_SERIAL_NUMBER = 0xA431,
    EXIF_COLOR_SPACE = 0xA001,
    EXIF_WHITE_BALANCE = 0xA403,
};

enum GpsTag : uint16_t {
    GPS_LATITUDE_REF = 0x0001,
    GPS_LATITUDE = 0x0002,
    GPS_LONGITUDE_REF = 0x0003,
    GPS_LONGITUDE = 0x0004,
    GPS_ALTITUDE_REF = 0x0005,
    GPS_ALTITUDE = 0x0006,
};

// ============================================================
// Helper functions
// ============================================================

static uint16_t read_uint16(const uint8_t* data, bool little_endian) {
    if (little_endian) return data[0] | (data[1] << 8);
    return (data[0] << 8) | data[1];
}

static uint32_t read_uint32(const uint8_t* data, bool little_endian) {
    if (little_endian) return data[0] | (data[1] << 8) | (data[2] << 16) | (data[3] << 24);
    return (data[0] << 24) | (data[1] << 16) | (data[2] << 8) | data[3];
}

static float read_rational(const uint8_t* data, bool little_endian) {
    uint32_t num = read_uint32(data, little_endian);
    uint32_t den = read_uint32(data + 4, little_endian);
    if (den == 0) return 0.0f;
    return static_cast<float>(num) / static_cast<float>(den);
}

static std::string read_string(const uint8_t* data, size_t max_len) {
    std::string result;
    for (size_t i = 0; i < max_len && data[i] != 0; ++i) {
        result += static_cast<char>(data[i]);
    }
    return result;
}

float MetadataExtractor::parse_rational(const uint8_t* data, bool little_endian) {
    return read_rational(data, little_endian);
}

double MetadataExtractor::exif_gps_to_decimal(double degrees, double minutes, double seconds, const std::string& ref) {
    double decimal = degrees + minutes / 60.0 + seconds / 3600.0;
    if (ref == "S" || ref == "W") decimal = -decimal;
    return decimal;
}

std::string MetadataExtractor::detect_file_type(const uint8_t* buffer, size_t size) {
    if (size < 4) return "unknown";
    if (buffer[0] == 0xFF && buffer[1] == 0xD8) return "JPEG";
    if (buffer[0] == 0x89 && buffer[1] == 0x50 && buffer[2] == 0x4E && buffer[3] == 0x47) return "PNG";
    if (buffer[0] == 0x49 && buffer[1] == 0x49) return "TIFF";
    if (buffer[0] == 0x4D && buffer[1] == 0x4D) return "TIFF";
    if (buffer[0] == 'R' && buffer[1] == 'I' && buffer[2] == 'F' && buffer[3] == 'F') return "RIFF";
    return "unknown";
}

// ============================================================
// TIFF/IFD parsing
// ============================================================

bool MetadataExtractor::parse_tiff_header(const uint8_t* data, size_t size, bool& little_endian, uint32_t& ifd0_offset) {
    if (size < 8) return false;
    if (data[0] == 0x49 && data[1] == 0x49) little_endian = true;
    else if (data[0] == 0x4D && data[1] == 0x4D) little_endian = false;
    else return false;

    uint16_t magic = read_uint16(data + 2, little_endian);
    if (magic != 0x002A) return false;

    ifd0_offset = read_uint32(data + 4, little_endian);
    return true;
}

bool MetadataExtractor::parse_ifd_entry(const uint8_t* data, size_t size, bool little_endian,
                                         uint32_t ifd_offset, uint16_t entry_index,
                                         uint16_t& tag, uint16_t& type, uint32_t& count, uint32_t& value_offset) {
    uint64_t entry_offset64 = static_cast<uint64_t>(ifd_offset) + 2 + static_cast<uint64_t>(entry_index) * 12;
    if (entry_offset64 > UINT32_MAX || entry_offset64 + 12 > size) return false;
    uint32_t entry_offset = static_cast<uint32_t>(entry_offset64);

    tag = read_uint16(data + entry_offset, little_endian);
    type = read_uint16(data + entry_offset + 2, little_endian);
    count = read_uint32(data + entry_offset + 4, little_endian);

    uint32_t value_size;
    switch (type) {
        case 1: case 6: value_size = 1; break;   // BYTE
        case 2: case 7: value_size = 1; break;    // ASCII
        case 3: value_size = 2; break;             // SHORT
        case 4: case 9: value_size = 4; break;    // LONG
        case 5: case 10: value_size = 8; break;   // RATIONAL
        case 8: value_size = 2; break;             // SSHORT
        case 11: value_size = 4; break;            // FLOAT
        case 12: value_size = 8; break;            // DOUBLE
        default: value_size = 4; break;
    }

    uint64_t total_size64 = static_cast<uint64_t>(count) * value_size;
    if (total_size64 > UINT32_MAX) return false;
    uint32_t total_size = static_cast<uint32_t>(total_size64);
    if (total_size <= 4) {
        value_offset = entry_offset + 8;
    } else {
        value_offset = read_uint32(data + entry_offset + 8, little_endian);
    }

    return (value_offset + total_size <= size);
}

static void parse_ifd_values(const uint8_t* data, size_t size, bool little_endian,
                              uint32_t ifd_offset, ExifData& out_data) {
    uint16_t num_entries = read_uint16(data + ifd_offset, little_endian);

    for (uint16_t i = 0; i < num_entries; ++i) {
        uint16_t tag, type;
        uint32_t count, value_offset;
        if (!MetadataExtractor::parse_ifd_entry(data, size, little_endian, ifd_offset, i, tag, type, count, value_offset))
            continue;

        switch (tag) {
            case TAG_MAKE:
                if (type == 2 && count > 0)
                    out_data.make = read_string(data + value_offset, std::min(count, 64u));
                break;
            case TAG_MODEL:
                if (type == 2 && count > 0)
                    out_data.model = read_string(data + value_offset, std::min(count, 64u));
                break;
            case TAG_ORIENTATION:
                if (type == 3 && count >= 1)
                    out_data.orientation = read_uint16(data + value_offset, little_endian);
                break;
            case TAG_SOFTWARE:
                if (type == 2 && count > 0)
                    out_data.software = read_string(data + value_offset, std::min(count, 64u));
                break;
            case TAG_DATE_TIME:
                if (type == 2 && count > 0)
                    out_data.date_time_original = read_string(data + value_offset, std::min(count, 20u));
                break;
            case TAG_IMAGE_WIDTH:
                if (type == 3 || type == 4)
                    out_data.image_width = (type == 3) ? read_uint16(data + value_offset, little_endian)
                                                        : read_uint32(data + value_offset, little_endian);
                break;
            case TAG_IMAGE_HEIGHT:
                if (type == 3 || type == 4)
                    out_data.image_height = (type == 3) ? read_uint16(data + value_offset, little_endian)
                                                         : read_uint32(data + value_offset, little_endian);
                break;
            case TAG_EXIF_IFD:
                if (type == 4) {
                    uint32_t exif_offset = read_uint32(data + value_offset, little_endian);
                    MetadataExtractor::parse_ifd(data, size, little_endian, exif_offset, out_data);
                }
                break;
            case TAG_GPS_IFD:
                if (type == 4) {
                    uint32_t gps_offset = read_uint32(data + value_offset, little_endian);
                    MetadataExtractor::parse_ifd(data, size, little_endian, gps_offset, out_data);
                }
                break;
            default:
                break;
        }
    }
}

bool MetadataExtractor::parse_ifd(const uint8_t* data, size_t size, bool little_endian,
                                   uint32_t ifd_offset, ExifData& out_data) {
    if (ifd_offset + 2 > size) return false;
    parse_ifd_values(data, size, little_endian, ifd_offset, out_data);
    return true;
}

// ============================================================
// EXIF extraction
// ============================================================

// EXIF IFD tag handling
static void parse_exif_ifd(const uint8_t* data, size_t size, bool little_endian,
                            uint32_t ifd_offset, ExifData& out_data) {
    uint16_t num_entries = read_uint16(data + ifd_offset, little_endian);

    for (uint16_t i = 0; i < num_entries; ++i) {
        uint16_t tag, type;
        uint32_t count, value_offset;
        if (!MetadataExtractor::parse_ifd_entry(data, size, little_endian, ifd_offset, i, tag, type, count, value_offset))
            continue;

        switch (tag) {
            case EXIF_EXPOSURE_TIME:
                if (type == 5) out_data.shutter_speed = read_rational(data + value_offset, little_endian);
                break;
            case EXIF_FNUMBER:
                if (type == 5) out_data.aperture = read_rational(data + value_offset, little_endian);
                break;
            case EXIF_ISO:
                if (type == 3) out_data.iso = read_uint16(data + value_offset, little_endian);
                else if (type == 4) out_data.iso = static_cast<float>(read_uint32(data + value_offset, little_endian));
                break;
            case EXIF_DATE_TIME_ORIGINAL:
                if (type == 2) out_data.date_time_original = read_string(data + value_offset, std::min(count, 20u));
                break;
            case EXIF_DATE_TIME_DIGITIZED:
                if (type == 2) out_data.date_time_digitized = read_string(data + value_offset, std::min(count, 20u));
                break;
            case EXIF_FOCAL_LENGTH:
                if (type == 5) out_data.focal_length = read_rational(data + value_offset, little_endian);
                break;
            case EXIF_FOCAL_LENGTH_35MM:
                if (type == 3) out_data.focal_length_35mm = read_uint16(data + value_offset, little_endian);
                break;
            case EXIF_LENS_MAKE:
                if (type == 2) out_data.lens_make = read_string(data + value_offset, std::min(count, 64u));
                break;
            case EXIF_LENS_MODEL:
                if (type == 2) out_data.lens_model = read_string(data + value_offset, std::min(count, 64u));
                break;
            case EXIF_SERIAL_NUMBER:
                if (type == 2) out_data.serial_number = read_string(data + value_offset, std::min(count, 32u));
                break;
            case EXIF_EXPOSURE_BIAS:
                if (type == 10) {
                    int32_t num = static_cast<int32_t>(read_uint32(data + value_offset, little_endian));
                    int32_t den = static_cast<int32_t>(read_uint32(data + value_offset + 4, little_endian));
                    if (den != 0) out_data.exposure_bias = static_cast<float>(num) / den;
                }
                break;
            case EXIF_MAX_APERTURE:
                if (type == 5) out_data.max_aperture = read_rational(data + value_offset, little_endian);
                break;
            case EXIF_FLASH:
                if (type == 3) {
                    uint16_t flash_val = read_uint16(data + value_offset, little_endian);
                    out_data.flash_fired = (flash_val & 0x01) != 0;
                }
                break;
            case EXIF_COLOR_SPACE:
                if (type == 3) {
                    uint16_t cs = read_uint16(data + value_offset, little_endian);
                    out_data.color_space = (cs == 1) ? "sRGB" : (cs == 0xFFFF) ? "Uncalibrated" : "Adobe RGB";
                }
                break;
            case EXIF_WHITE_BALANCE:
                if (type == 3) {
                    out_data.white_balance = (read_uint16(data + value_offset, little_endian) == 0) ? "Auto" : "Manual";
                }
                break;
            default:
                break;
        }
    }
}

// GPS IFD parsing
static void parse_gps_ifd(const uint8_t* data, size_t size, bool little_endian,
                           uint32_t ifd_offset, ExifData& out_data) {
    uint16_t num_entries = read_uint16(data + ifd_offset, little_endian);
    double lat_deg = 0, lat_min = 0, lat_sec = 0;
    double lon_deg = 0, lon_min = 0, lon_sec = 0;

    for (uint16_t i = 0; i < num_entries; ++i) {
        uint16_t tag, type;
        uint32_t count, value_offset;
        if (!MetadataExtractor::parse_ifd_entry(data, size, little_endian, ifd_offset, i, tag, type, count, value_offset))
            continue;

        switch (tag) {
            case GPS_LATITUDE_REF:
                if (type == 2) out_data.gps_latitude_ref = read_string(data + value_offset, 2);
                break;
            case GPS_LATITUDE:
                if (type == 5 && count >= 3) {
                    lat_deg = read_rational(data + value_offset, little_endian);
                    lat_min = read_rational(data + value_offset + 8, little_endian);
                    lat_sec = read_rational(data + value_offset + 16, little_endian);
                }
                break;
            case GPS_LONGITUDE_REF:
                if (type == 2) out_data.gps_longitude_ref = read_string(data + value_offset, 2);
                break;
            case GPS_LONGITUDE:
                if (type == 5 && count >= 3) {
                    lon_deg = read_rational(data + value_offset, little_endian);
                    lon_min = read_rational(data + value_offset + 8, little_endian);
                    lon_sec = read_rational(data + value_offset + 16, little_endian);
                }
                break;
            case GPS_ALTITUDE:
                if (type == 5) out_data.gps_altitude = read_rational(data + value_offset, little_endian);
                break;
            default:
                break;
        }
    }

    if (lat_deg != 0 || lon_deg != 0) {
        out_data.has_gps = true;
        out_data.gps_latitude = MetadataExtractor::exif_gps_to_decimal(lat_deg, lat_min, lat_sec, out_data.gps_latitude_ref);
        out_data.gps_longitude = MetadataExtractor::exif_gps_to_decimal(lon_deg, lon_min, lon_sec, out_data.gps_longitude_ref);
    }
}

// ============================================================
// Public API
// ============================================================

bool MetadataExtractor::extract_exif(const std::string& file_path, ExifData& data) {
    std::ifstream file(file_path, std::ios::binary | std::ios::ate);
    if (!file.is_open()) {
        LOGE("Failed to open file: %s", file_path.c_str());
        return false;
    }

    size_t file_size = file.tellg();
    file.seekg(0, std::ios::beg);

    std::vector<uint8_t> buffer(file_size);
    file.read(reinterpret_cast<char*>(buffer.data()), file_size);
    file.close();

    return extract_exif_from_memory(buffer.data(), buffer.size(), data);
}

bool MetadataExtractor::extract_exif_from_memory(const uint8_t* buffer, size_t size, ExifData& data) {
    if (!buffer || size < 4) return false;

    std::string file_type = detect_file_type(buffer, size);

    if (file_type == "JPEG") {
        // Find EXIF APP1 marker (0xFFE1)
        size_t offset = 2; // Skip SOI marker
        while (offset + 4 < size) {
            if (buffer[offset] == 0xFF) {
                uint8_t marker = buffer[offset + 1];
                if (marker == 0xE1) {
                    // APP1 - EXIF
                    uint16_t segment_size = (buffer[offset + 2] << 8) | buffer[offset + 3];
                    if (offset + 2 + segment_size <= size) {
                        const uint8_t* exif_data = buffer + offset + 4;
                        size_t exif_size = segment_size - 2;
                        // Check "Exif\0\0" header
                        if (exif_size >= 6 && exif_data[0] == 'E' && exif_data[1] == 'x' &&
                            exif_data[2] == 'i' && exif_data[3] == 'f' && exif_data[4] == 0 && exif_data[5] == 0) {
                            exif_data += 6;
                            exif_size -= 6;
                            bool little_endian;
                            uint32_t ifd0_offset;
                            if (parse_tiff_header(exif_data, exif_size, little_endian, ifd0_offset)) {
                                parse_ifd(exif_data, exif_size, little_endian, ifd0_offset, data);
                                return true;
                            }
                        }
                    }
                    break;
                } else if (marker == 0xDA) {
                    break; // SOS - start of scan, stop searching
                }
                // Skip markers with length
                if (marker >= 0xC0 && marker != 0xC4 && marker != 0xC8 && marker != 0xCC) {
                    offset += 2 + ((buffer[offset + 2] << 8) | buffer[offset + 3]);
                } else {
                    offset += 2;
                }
            } else {
                ++offset;
            }
        }
    } else if (file_type == "TIFF") {
        bool little_endian;
        uint32_t ifd0_offset;
        if (parse_tiff_header(buffer, size, little_endian, ifd0_offset)) {
            parse_ifd(buffer, size, little_endian, ifd0_offset, data);
            return true;
        }
    }

    return false;
}

bool MetadataExtractor::extract_xmp(const std::string& file_path, ExifData& data) {
    std::ifstream file(file_path, std::ios::binary | std::ios::ate);
    if (!file.is_open()) return false;
    size_t size = file.tellg();
    file.seekg(0);
    std::vector<uint8_t> buffer(size);
    file.read(reinterpret_cast<char*>(buffer.data()), size);
    return extract_xmp_from_memory(buffer.data(), buffer.size(), data);
}

bool MetadataExtractor::extract_xmp_from_memory(const uint8_t* buffer, size_t size, ExifData& data) {
    // Search for XMP packet in JPEG: APP1 marker with "http://ns.adobe.com/xap/1.0/"
    if (!buffer || size < 4) return false;

    std::string xmp_header = "http://ns.adobe.com/xap/1.0/";
    std::string content(reinterpret_cast<const char*>(buffer),
                        std::min(size, static_cast<size_t>(65536)));
    size_t pos = content.find(xmp_header);
    if (pos != std::string::npos) {
        // The XMP data follows the null-terminated header
        size_t data_start = content.find('\0', pos + xmp_header.length());
        if (data_start != std::string::npos) {
            data_start++;
            // Find the closing </x:xmpmeta> or just take a reasonable chunk
            size_t data_end = content.find("<?xpacket end", data_start);
            if (data_end == std::string::npos) {
                data_end = std::min(data_start + 65536, content.length());
            }
            data.xmp_raw_data = content.substr(data_start, data_end - data_start);
            parse_xmp_packet(data.xmp_raw_data, data);
            return true;
        }
    }
    return false;
}

bool MetadataExtractor::parse_xmp_packet(const std::string& xmp_data, ExifData& out_data) {
    // Simple XMP field extraction
    auto extract_field = [&](const std::string& name) -> std::string {
        std::string search = name + "=\"";
        size_t pos = xmp_data.find(search);
        if (pos != std::string::npos) {
            pos += search.length();
            size_t end = xmp_data.find('"', pos);
            if (end != std::string::npos) {
                return xmp_data.substr(pos, end - pos);
            }
        }
        return "";
    };

    auto extract_tag = [&](const std::string& tag) -> std::string {
        std::string search = "<" + tag + ">";
        size_t pos = xmp_data.find(search);
        if (pos != std::string::npos) {
            pos += search.length();
            std::string close = "</" + tag + ">";
            size_t end = xmp_data.find(close, pos);
            if (end != std::string::npos) {
                return xmp_data.substr(pos, end - pos);
            }
        }
        return "";
    };

    out_data.xmp_fields["CreatorTool"] = extract_tag("xmp:CreatorTool");
    out_data.xmp_fields["Rating"] = extract_tag("xmp:Rating");
    out_data.xmp_fields["Label"] = extract_tag("xmp:Label");
    out_data.xmp_fields["Description"] = extract_tag("dc:description");
    out_data.xmp_fields["Creator"] = extract_tag("dc:creator");

    return !out_data.xmp_fields.empty();
}

bool MetadataExtractor::extract_icc_profile(const std::string& file_path, ExifData& data) {
    std::ifstream file(file_path, std::ios::binary | std::ios::ate);
    if (!file.is_open()) return false;
    size_t size = file.tellg();
    file.seekg(0);
    std::vector<uint8_t> buffer(size);
    file.read(reinterpret_cast<char*>(buffer.data()), size);
    return extract_icc_profile_from_memory(buffer.data(), buffer.size(), data);
}

bool MetadataExtractor::extract_icc_profile_from_memory(const uint8_t* buffer, size_t size, ExifData& data) {
    if (!buffer || size < 4) return false;

    // JPEG: Look for APP2 marker (0xFFE2) containing ICC profile
    if (buffer[0] == 0xFF && buffer[1] == 0xD8) {
        size_t offset = 2;
        while (offset + 4 < size) {
            if (buffer[offset] == 0xFF) {
                uint8_t marker = buffer[offset + 1];
                if (marker == 0xE2) {
                    uint16_t seg_size = (buffer[offset + 2] << 8) | buffer[offset + 3];
                    if (offset + 2 + seg_size <= size && seg_size > 14) {
                        // ICC profile starts after "ICC_PROFILE\0" + chunk info
                        const uint8_t* icc_start = buffer + offset + 4;
                        // Check for "ICC_PROFILE"
                        if (icc_start[0] == 'I' && icc_start[1] == 'C' && icc_start[2] == 'C') {
                            size_t data_offset = 14; // Skip "ICC_PROFILE\0\1\1"
                            size_t icc_size = seg_size - 2 - data_offset;
                            data.icc_profile_data.assign(icc_start + data_offset,
                                                          icc_start + data_offset + icc_size);
                            parse_icc_header(data.icc_profile_data.data(), data.icc_profile_data.size(), data);
                            return true;
                        }
                    }
                    offset += seg_size;
                } else if (marker == 0xDA) {
                    break;
                } else {
                    offset += 2 + ((buffer[offset + 2] << 8) | buffer[offset + 3]);
                }
            } else {
                ++offset;
            }
        }
    }

    // PNG: Look for iCCP chunk
    if (buffer[0] == 0x89 && buffer[1] == 0x50 && buffer[2] == 0x4E) {
        size_t offset = 8; // Skip PNG signature
        while (offset + 12 < size) {
            uint32_t chunk_size = (buffer[offset] << 24) | (buffer[offset+1] << 16) |
                                  (buffer[offset+2] << 8) | buffer[offset+3];
            if (buffer[offset+4] == 'i' && buffer[offset+5] == 'C' &&
                buffer[offset+6] == 'C' && buffer[offset+7] == 'P') {
                const uint8_t* chunk_data = buffer + offset + 8;
                // Skip profile name (null-terminated) and compression byte
                size_t name_end = 0;
                while (name_end < chunk_size && chunk_data[name_end] != 0) name_end++;
                if (name_end + 2 < chunk_size) {
                    size_t profile_start = name_end + 2;
                    size_t profile_size = chunk_size - profile_start;
                    data.icc_profile_data.assign(chunk_data + profile_start,
                                                  chunk_data + profile_start + profile_size);
                    parse_icc_header(data.icc_profile_data.data(), data.icc_profile_data.size(), data);
                    return true;
                }
            }
            offset += 12 + chunk_size;
        }
    }

    return false;
}

bool MetadataExtractor::parse_icc_header(const uint8_t* data, size_t size, ExifData& out_data) {
    if (size < 128) return false;

    // ICC profile description starts at offset 84 (tag count at 128, tags follow)
    // Get the profile description tag (tag signature 'desc')
    if (size >= 132) {
        uint32_t tag_count = (data[128] << 24) | (data[129] << 16) | (data[130] << 8) | data[131];
        for (uint32_t i = 0; i < tag_count && (132 + i * 12 + 12) <= size; ++i) {
            uint32_t tag_offset = 132 + i * 12;
            uint32_t tag_sig = (data[tag_offset] << 24) | (data[tag_offset+1] << 16) |
                               (data[tag_offset+2] << 8) | data[tag_offset+3];
            if (tag_sig == 0x64657363) { // 'desc'
                uint32_t desc_offset = (data[tag_offset+4] << 24) | (data[tag_offset+5] << 16) |
                                       (data[tag_offset+6] << 8) | data[tag_offset+7];
                uint32_t desc_size = (data[tag_offset+8] << 24) | (data[tag_offset+9] << 16) |
                                     (data[tag_offset+10] << 8) | data[tag_offset+11];
                if (desc_offset + desc_size <= size && desc_size > 4) {
                    out_data.icc_profile_description = read_string(data + desc_offset + 4, desc_size - 4);
                    out_data.profile_name = out_data.icc_profile_description;
                    break;
                }
            }
        }
    }

    return true;
}

bool MetadataExtractor::extract_raw_color_matrix(const std::string& dng_path, ExifData& data) {
    std::ifstream file(dng_path, std::ios::binary | std::ios::ate);
    if (!file.is_open()) return false;
    size_t size = file.tellg();
    file.seekg(0);
    std::vector<uint8_t> buffer(size);
    file.read(reinterpret_cast<char*>(buffer.data()), size);
    file.close();

    bool little_endian;
    uint32_t ifd0_offset;
    if (!parse_tiff_header(buffer.data(), buffer.size(), little_endian, ifd0_offset)) return false;

    parse_dng_tags(buffer.data(), buffer.size(), little_endian, ifd0_offset, data);
    return true;
}

bool MetadataExtractor::parse_dng_tags(const uint8_t* data, size_t size, bool little_endian,
                                        uint32_t ifd_offset, ExifData& out_data) {
    uint16_t num_entries = read_uint16(data + ifd_offset, little_endian);

    for (uint16_t i = 0; i < num_entries; ++i) {
        uint16_t tag, type;
        uint32_t count, value_offset;
        if (!parse_ifd_entry(data, size, little_endian, ifd_offset, i, tag, type, count, value_offset))
            continue;

        switch (tag) {
            case TAG_DNG_COLOR_MATRIX1:
            case TAG_DNG_COLOR_MATRIX2:
                if (type == 10 && count >= 9) { // SRATIONAL
                    float* dst = (tag == TAG_DNG_COLOR_MATRIX1) ? out_data.raw_color_matrix : out_data.raw_color_matrix;
                    for (int j = 0; j < 9 && j < static_cast<int>(count); ++j) {
                        int32_t num = static_cast<int32_t>(read_uint32(data + value_offset + j * 8, little_endian));
                        int32_t den = static_cast<int32_t>(read_uint32(data + value_offset + j * 8 + 4, little_endian));
                        dst[j] = (den != 0) ? static_cast<float>(num) / den : 0.0f;
                    }
                }
                break;
            case TAG_DNG_CAMERA_CALIBRATION1:
                if (type == 10 && count >= 9) {
                    for (int j = 0; j < 9; ++j) {
                        int32_t num = static_cast<int32_t>(read_uint32(data + value_offset + j * 8, little_endian));
                        int32_t den = static_cast<int32_t>(read_uint32(data + value_offset + j * 8 + 4, little_endian));
                        out_data.raw_calibration_illuminant1[j] = (den != 0) ? static_cast<float>(num) / den : 0.0f;
                    }
                }
                break;
            case TAG_DNG_CAMERA_CALIBRATION2:
                if (type == 10 && count >= 9) {
                    for (int j = 0; j < 9; ++j) {
                        int32_t num = static_cast<int32_t>(read_uint32(data + value_offset + j * 8, little_endian));
                        int32_t den = static_cast<int32_t>(read_uint32(data + value_offset + j * 8 + 4, little_endian));
                        out_data.raw_calibration_illuminant2[j] = (den != 0) ? static_cast<float>(num) / den : 0.0f;
                    }
                }
                break;
            case TAG_DNG_FORWARD_MATRIX1:
                if (type == 10 && count >= 9) {
                    for (int j = 0; j < 9; ++j) {
                        int32_t num = static_cast<int32_t>(read_uint32(data + value_offset + j * 8, little_endian));
                        int32_t den = static_cast<int32_t>(read_uint32(data + value_offset + j * 8 + 4, little_endian));
                        out_data.raw_forward_matrix[j] = (den != 0) ? static_cast<float>(num) / den : 0.0f;
                    }
                }
                break;
            case TAG_DNG_WHITE_LEVEL:
                if (type == 3) out_data.raw_white_level = read_uint16(data + value_offset, little_endian);
                else if (type == 4) out_data.raw_white_level = static_cast<uint16_t>(read_uint32(data + value_offset, little_endian));
                break;
            case TAG_DNG_BLACK_LEVEL:
                if (type == 3 || type == 4) {
                    int bl_count = std::min(static_cast<int>(count), 4);
                    for (int j = 0; j < bl_count; ++j) {
                        uint32_t val = (type == 3) ? read_uint16(data + value_offset + j * 2, little_endian)
                                                    : read_uint32(data + value_offset + j * 4, little_endian);
                        out_data.raw_black_levels[j] = val;
                        if (j == 0) out_data.raw_black_level = static_cast<uint16_t>(val);
                    }
                }
                break;
            case TAG_DNG_CFA_PATTERN:
                if (type == 1 && count >= 4) {
                    for (int j = 0; j < 4; ++j) {
                        out_data.raw_cfa_pattern[j] = data[value_offset + j];
                    }
                    // Determine Bayer pattern from CFA
                    if (out_data.raw_cfa_pattern[0] == 0 && out_data.raw_cfa_pattern[1] == 1 &&
                        out_data.raw_cfa_pattern[2] == 1 && out_data.raw_cfa_pattern[3] == 2) {
                        out_data.raw_bayer_pattern = 0; // RGGB
                    }
                }
                break;
            case TAG_DNG_CALIBRATION_ILLUMINANT1:
                if (type == 3) {
                    out_data.raw_calibration_illuminant1_name = "D65";
                }
                break;
            default:
                break;
        }
    }

    return true;
}

bool MetadataExtractor::extract_all(const std::string& file_path, ExifData& data) {
    bool ok = extract_exif(file_path, data);
    extract_xmp(file_path, data);
    extract_icc_profile(file_path, data);
    LOGI("Metadata extracted from %s: %s %s, ISO=%.0f, f/%.1f, %.3fs",
         file_path.c_str(), data.make.c_str(), data.model.c_str(),
         data.iso, data.aperture, data.shutter_speed);
    return ok;
}

} // namespace alcedo