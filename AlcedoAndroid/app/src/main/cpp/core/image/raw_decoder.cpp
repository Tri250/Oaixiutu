#include "raw_decoder.h"
#include <fstream>
#include <cstring>
#include <cmath>
#include <algorithm>
#include <thread>
#include <android/log.h>

#define LOG_TAG "AlcedoRaw"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace alcedo {

// ============================================================
// Internal helpers
// ============================================================

static inline uint16_t read_u16(const uint8_t* d, bool le) {
    return le ? (d[0] | (d[1] << 8)) : ((d[0] << 8) | d[1]);
}
static inline uint32_t read_u32(const uint8_t* d, bool le) {
    return le ? (d[0] | (d[1]<<8) | (d[2]<<16) | (d[3]<<24))
              : ((d[0]<<24) | (d[1]<<16) | (d[2]<<8) | d[3]);
}
static inline int32_t read_s32(const uint8_t* d, bool le) {
    return static_cast<int32_t>(read_u32(d, le));
}
static inline float read_float(const uint8_t* d, bool le) {
    uint32_t u = read_u32(d, le);
    float f;
    memcpy(&f, &u, 4);
    return f;
}
static inline double read_double(const uint8_t* d, bool le) {
    uint32_t lo = read_u32(d, le);
    uint32_t hi = read_u32(d + 4, le);
    uint64_t v = le ? (lo | (static_cast<uint64_t>(hi) << 32))
                    : ((static_cast<uint64_t>(lo) << 32) | hi);
    double dd;
    memcpy(&dd, &v, 8);
    return dd;
}
static inline float rational(const uint8_t* d, bool le) {
    uint32_t n = read_u32(d, le);
    uint32_t den = read_u32(d + 4, le);
    return den ? static_cast<float>(n) / den : 0.0f;
}
static inline float srational(const uint8_t* d, bool le) {
    int32_t n = read_s32(d, le);
    int32_t den = read_s32(d + 4, le);
    return den ? static_cast<float>(n) / den : 0.0f;
}
static inline int clamp(int v, int lo, int hi) { return v < lo ? lo : (v > hi ? hi : v); }
static inline float clampf(float v, float lo, float hi) { return v < lo ? lo : (v > hi ? hi : v); }

// ============================================================
// RawDecoder Implementation
// ============================================================

RawDecoder::RawDecoder() = default;
RawDecoder::~RawDecoder() = default;

void RawDecoder::report_progress(float progress, const std::string& stage) {
    if (progress_cb_) progress_cb_(progress, stage);
}

// ── File format detection ──

std::string RawDecoder::detect_format(const std::string& path) {
    std::ifstream f(path, std::ios::binary);
    if (!f) return "unknown";
    uint8_t hdr[16] = {};
    f.read(reinterpret_cast<char*>(hdr), 16);
    f.close();
    return detect_format_from_memory(hdr, 16);
}

std::string RawDecoder::detect_format_from_memory(const uint8_t* data, size_t size) {
    if (!data || size < 8) return "unknown";
    // TIFF-based formats (DNG, CR2, NEF, ARW, ORF, RAF, RW2, PEF, etc.)
    if ((data[0] == 0x49 && data[1] == 0x49) || (data[0] == 0x4D && data[1] == 0x4D)) {
        bool le = (data[0] == 0x49);
        uint16_t magic = read_u16(data + 2, le);
        if (magic == 0x002A || magic == 0x4F52 || magic == 0x5352 || magic == 0x55) {
            // Try to identify camera make from IFD0
            if (size >= 8) {
                uint32_t ifd0 = read_u32(data + 4, le);
                if (ifd0 + 2 < size) {
                    uint16_t entries = read_u16(data + ifd0, le);
                    for (uint16_t i = 0; i < entries && ifd0 + 2 + i * 12 + 12 <= size; ++i) {
                        uint32_t off = ifd0 + 2 + i * 12;
                        uint16_t tag = read_u16(data + off, le);
                        if (tag == 0x010F) { // Make
                            uint32_t count = read_u32(data + off + 4, le);
                            uint32_t voff = (count * 1 <= 4) ? (off + 8) : read_u32(data + off + 8, le);
                            if (voff + count <= size) {
                                std::string make(reinterpret_cast<const char*>(data + voff), count);
                                // Trim nulls
                                make.erase(std::find(make.begin(), make.end(), '\0'), make.end());
                                if (make.find("NIKON") != std::string::npos) return "NEF";
                                if (make.find("Canon") != std::string::npos) return "CR2";
                                if (make.find("SONY") != std::string::npos) return "ARW";
                                if (make.find("FUJIFILM") != std::string::npos) return "RAF";
                                if (make.find("OLYMPUS") != std::string::npos) return "ORF";
                                if (make.find("Panasonic") != std::string::npos) return "RW2";
                                if (make.find("PENTAX") != std::string::npos) return "PEF";
                                if (make.find("SAMSUNG") != std::string::npos) return "SRW";
                                if (make.find("LEICA") != std::string::npos) return "DNG";
                                if (make.find("Hasselblad") != std::string::npos) return "3FR";
                                if (make.find("Phase One") != std::string::npos) return "IIQ";
                                return "DNG";
                            }
                        }
                    }
                }
            }
            return "DNG"; // Generic TIFF-based RAW
        }
    }
    // JPEG
    if (data[0] == 0xFF && data[1] == 0xD8) return "JPEG";
    // PNG
    if (data[0] == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G') return "PNG";
    return "unknown";
}

bool RawDecoder::is_raw_format(const std::string& path) {
    std::string fmt = detect_format(path);
    return fmt == "DNG" || fmt == "NEF" || fmt == "CR2" || fmt == "ARW" ||
           fmt == "RAF" || fmt == "ORF" || fmt == "RW2" || fmt == "PEF" ||
           fmt == "SRW" || fmt == "3FR" || fmt == "IIQ";
}

bool RawDecoder::is_supported_format(const std::string& path) {
    return is_raw_format(path);
}

// ── TIFF parsing helpers ──

bool RawDecoder::parse_tiff_header(const uint8_t* data, size_t size, TiffContext& ctx) {
    if (size < 8) return false;
    ctx.data = data;
    ctx.size = size;
    if (data[0] == 0x49 && data[1] == 0x49) ctx.little_endian = true;
    else if (data[0] == 0x4D && data[1] == 0x4D) ctx.little_endian = false;
    else return false;
    uint16_t magic = read_u16(data + 2, ctx.little_endian);
    if (magic != 0x002A) return false;
    ctx.ifd0_offset = read_u32(data + 4, ctx.little_endian);
    return true;
}

bool RawDecoder::read_tiff_entry(const TiffContext& ctx, uint32_t ifd_offset,
                                  uint16_t index, TiffEntry& entry) {
    uint32_t off = ifd_offset + 2 + index * 12;
    if (off + 12 > ctx.size) return false;
    entry.tag   = read_u16(ctx.data + off, ctx.little_endian);
    entry.type  = read_u16(ctx.data + off + 2, ctx.little_endian);
    entry.count = read_u32(ctx.data + off + 4, ctx.little_endian);
    uint32_t vsize;
    switch (entry.type) {
        case 1: case 2: case 6: case 7: vsize = 1; break;
        case 3: case 8: vsize = 2; break;
        case 4: case 9: case 11: vsize = 4; break;
        case 5: case 10: case 12: vsize = 8; break;
        default: vsize = 4; break;
    }
    uint32_t total = entry.count * vsize;
    if (total <= 4) {
        entry.offset = off + 8;
        entry.data_ptr = ctx.data + off + 8;
    } else {
        entry.offset = read_u32(ctx.data + off + 8, ctx.little_endian);
        entry.data_ptr = ctx.data + entry.offset;
    }
    return (entry.offset + total <= ctx.size);
}

uint16_t RawDecoder::tiff_get_uint16(const TiffContext& ctx, const TiffEntry& entry) {
    if (entry.type == 3) return read_u16(entry.data_ptr, ctx.little_endian);
    if (entry.type == 1) return entry.data_ptr[0];
    return static_cast<uint16_t>(read_u32(entry.data_ptr, ctx.little_endian));
}

uint32_t RawDecoder::tiff_get_uint32(const TiffContext& ctx, const TiffEntry& entry) {
    if (entry.type == 4) return read_u32(entry.data_ptr, ctx.little_endian);
    if (entry.type == 3) return read_u16(entry.data_ptr, ctx.little_endian);
    return static_cast<uint32_t>(entry.data_ptr[0]);
}

float RawDecoder::tiff_get_rational(const TiffContext& ctx, const TiffEntry& entry) {
    if (entry.type == 5) return rational(entry.data_ptr, ctx.little_endian);
    if (entry.type == 3) return static_cast<float>(read_u16(entry.data_ptr, ctx.little_endian));
    return 0.0f;
}

float RawDecoder::tiff_get_srational(const TiffContext& ctx, const TiffEntry& entry) {
    if (entry.type == 10) return srational(entry.data_ptr, ctx.little_endian);
    if (entry.type == 8) return static_cast<float>(static_cast<int16_t>(read_u16(entry.data_ptr, ctx.little_endian)));
    return 0.0f;
}

std::string RawDecoder::tiff_get_string(const TiffContext& ctx, const TiffEntry& entry) {
    if (entry.type != 2) return "";
    std::string s(reinterpret_cast<const char*>(entry.data_ptr),
                  std::min(entry.count, 256u));
    s.erase(std::find(s.begin(), s.end(), '\0'), s.end());
    return s;
}

// ── Parse DNG/RAW IFD ──

bool RawDecoder::parse_dng_ifd(const TiffContext& ctx, uint32_t ifd_offset, RawImageInfo& info) {
    uint16_t num = read_u16(ctx.data + ifd_offset, ctx.little_endian);
    for (uint16_t i = 0; i < num; ++i) {
        TiffEntry e;
        if (!read_tiff_entry(ctx, ifd_offset, i, e)) continue;
        switch (e.tag) {
            case TiffTags::IMAGE_WIDTH:
                info.raw_width = static_cast<int>(tiff_get_uint32(ctx, e));
                info.full_width = info.raw_width;
                break;
            case TiffTags::IMAGE_LENGTH:
                info.raw_height = static_cast<int>(tiff_get_uint32(ctx, e));
                info.full_height = info.raw_height;
                break;
            case TiffTags::BITS_PER_SAMPLE:
                info.bits_per_sample = static_cast<int>(tiff_get_uint16(ctx, e));
                break;
            case TiffTags::SAMPLES_PER_PIXEL:
                info.samples_per_pixel = static_cast<int>(tiff_get_uint16(ctx, e));
                break;
            case TiffTags::COMPRESSION:
                info.compression = static_cast<int>(tiff_get_uint16(ctx, e));
                if (info.compression == 7) {
                    info.is_compressed = true;
                    info.compression_type = "lossless_jpeg";
                } else if (info.compression == 8) {
                    info.is_compressed = true;
                    info.compression_type = "deflate";
                } else if (info.compression == 65000) {
                    info.is_compressed = true;
                    info.compression_type = "nikon_he";
                    info.is_nikon_he = true;
                } else if (info.compression == 34892 || info.compression == 34933) {
                    info.is_compressed = true;
                    info.compression_type = "lossy_jpeg";
                } else if (info.compression == 1) {
                    info.compression_type = "none";
                }
                break;
            case TiffTags::ORIENTATION:
                info.orientation = static_cast<int>(tiff_get_uint16(ctx, e));
                break;
            case TiffTags::MAKE:
                info.make = tiff_get_string(ctx, e);
                break;
            case TiffTags::MODEL:
                info.model = tiff_get_string(ctx, e);
                break;
            case TiffTags::STRIP_OFFSETS: {
                if (e.type == 3 || e.type == 4) {
                    uint32_t n = std::min(e.count, 1024u);
                    info.strips.resize(n);
                    for (uint32_t j = 0; j < n; ++j) {
                        if (e.type == 3)
                            info.strips[j].offset = read_u16(e.data_ptr + j * 2, ctx.little_endian);
                        else
                            info.strips[j].offset = read_u32(e.data_ptr + j * 4, ctx.little_endian);
                    }
                }
                break;
            }
            case TiffTags::STRIP_BYTE_COUNTS: {
                if (e.type == 3 || e.type == 4) {
                    uint32_t n = std::min(e.count, static_cast<uint32_t>(info.strips.size()));
                    for (uint32_t j = 0; j < n; ++j) {
                        if (e.type == 3)
                            info.strips[j].byte_count = read_u16(e.data_ptr + j * 2, ctx.little_endian);
                        else
                            info.strips[j].byte_count = read_u32(e.data_ptr + j * 4, ctx.little_endian);
                    }
                }
                break;
            }
            case TiffTags::ROWS_PER_STRIP:
                info.rows_per_strip = static_cast<int>(tiff_get_uint32(ctx, e));
                break;
            // DNG color tags
            case TiffTags::COLOR_MATRIX1:
            case TiffTags::COLOR_MATRIX2:
                if (e.type == 10 && e.count >= 9) {
                    float* dst = (e.tag == TiffTags::COLOR_MATRIX1) ? info.color_matrix : info.color_matrix;
                    for (int j = 0; j < 9; ++j)
                        dst[j] = srational(e.data_ptr + j * 8, ctx.little_endian);
                    info.has_color_matrix = true;
                }
                break;
            case TiffTags::CAMERA_CALIBRATION1:
                if (e.type == 10 && e.count >= 9) {
                    for (int j = 0; j < 9; ++j)
                        info.calibration1[j] = srational(e.data_ptr + j * 8, ctx.little_endian);
                }
                break;
            case TiffTags::CAMERA_CALIBRATION2:
                if (e.type == 10 && e.count >= 9) {
                    for (int j = 0; j < 9; ++j)
                        info.calibration2[j] = srational(e.data_ptr + j * 8, ctx.little_endian);
                }
                break;
            case TiffTags::FORWARD_MATRIX1:
                if (e.type == 10 && e.count >= 9) {
                    for (int j = 0; j < 9; ++j)
                        info.forward_matrix[j] = srational(e.data_ptr + j * 8, ctx.little_endian);
                    info.has_forward_matrix = true;
                }
                break;
            case TiffTags::WHITE_LEVEL:
                if (e.type == 3) info.white_level = read_u16(e.data_ptr, ctx.little_endian);
                else if (e.type == 4) info.white_level = static_cast<uint16_t>(read_u32(e.data_ptr, ctx.little_endian));
                for (int j = 0; j < 4; ++j) info.white_levels[j] = info.white_level;
                break;
            case TiffTags::BLACK_LEVEL:
                if (e.type == 3 || e.type == 4) {
                    int blc = std::min(static_cast<int>(e.count), 4);
                    for (int j = 0; j < blc; ++j) {
                        int v = (e.type == 3) ? read_u16(e.data_ptr + j * 2, ctx.little_endian)
                                              : static_cast<int>(read_u32(e.data_ptr + j * 4, ctx.little_endian));
                        info.black_levels[j] = v;
                    }
                    info.black_level = static_cast<uint16_t>(info.black_levels[0]);
                }
                break;
            case TiffTags::CFA_PATTERN:
                if (e.type == 1 && e.count >= 4) {
                    info.cfa_pattern = CfaPattern::RGGB;
                    if (e.data_ptr[0] == 0 && e.data_ptr[1] == 1 && e.data_ptr[2] == 1 && e.data_ptr[3] == 2)
                        info.cfa_pattern = CfaPattern::RGGB;
                    else if (e.data_ptr[0] == 1 && e.data_ptr[1] == 0 && e.data_ptr[2] == 2 && e.data_ptr[3] == 1)
                        info.cfa_pattern = CfaPattern::GRBG;
                    else if (e.data_ptr[0] == 2 && e.data_ptr[1] == 1 && e.data_ptr[2] == 1 && e.data_ptr[3] == 0)
                        info.cfa_pattern = CfaPattern::BGGR;
                    else if (e.data_ptr[0] == 1 && e.data_ptr[1] == 2 && e.data_ptr[2] == 0 && e.data_ptr[3] == 1)
                        info.cfa_pattern = CfaPattern::GBRG;
                }
                break;
            case TiffTags::CFA_PATTERN_DIM:
                if (e.type == 3 && e.count >= 2) {
                    info.cfa_pattern_dim[0] = read_u16(e.data_ptr, ctx.little_endian);
                    info.cfa_pattern_dim[1] = read_u16(e.data_ptr + 2, ctx.little_endian);
                }
                break;
            case TiffTags::ANALOG_BALANCE:
                if (e.type == 5 && e.count >= 3) {
                    for (int j = 0; j < 3; ++j)
                        info.analog_balance[j] = rational(e.data_ptr + j * 8, ctx.little_endian);
                }
                break;
            case TiffTags::AS_SHOT_NEUTRAL:
                if (e.type == 5 && e.count >= 3) {
                    for (int j = 0; j < 3 && j < static_cast<int>(e.count); ++j) {
                        float v = rational(e.data_ptr + j * 8, ctx.little_endian);
                        info.as_shot_neutral[j] = v;
                        info.camera_wb_mult[j] = (v > 0.001f) ? 1.0f / v : 1.0f;
                    }
                }
                break;
            case TiffTags::BASELINE_EXPOSURE:
                if (e.type == 10) info.baseline_exposure = srational(e.data_ptr, ctx.little_endian);
                else if (e.type == 5) info.baseline_exposure = rational(e.data_ptr, ctx.little_endian);
                break;
            case TiffTags::CALIBRATION_ILLUMINANT1:
                info.calibration_illuminant1 = static_cast<int>(tiff_get_uint16(ctx, e));
                break;
            case TiffTags::CALIBRATION_ILLUMINANT2:
                info.calibration_illuminant2 = static_cast<int>(tiff_get_uint16(ctx, e));
                break;
            case TiffTags::PREVIEW_IMAGE_START:
                info.preview_offset = tiff_get_uint32(ctx, e);
                info.has_preview = true;
                break;
            case TiffTags::PREVIEW_IMAGE_LENGTH:
                info.preview_size = tiff_get_uint32(ctx, e);
                break;
            case TiffTags::THUMBNAIL_OFFSET:
                info.thumbnail_offset = tiff_get_uint32(ctx, e);
                info.has_thumbnail = true;
                break;
            case TiffTags::THUMBNAIL_LENGTH:
                info.thumbnail_size = tiff_get_uint32(ctx, e);
                break;
            case TiffTags::SENSOR_WIDTH:
                info.full_width = static_cast<int>(tiff_get_uint32(ctx, e));
                break;
            case TiffTags::SENSOR_HEIGHT:
                info.full_height = static_cast<int>(tiff_get_uint32(ctx, e));
                break;
            case TiffTags::SENSOR_TOP_MARGIN:
                info.top_margin = static_cast<int>(tiff_get_uint32(ctx, e));
                break;
            case TiffTags::SENSOR_LEFT_MARGIN:
                info.left_margin = static_cast<int>(tiff_get_uint32(ctx, e));
                break;
            case TiffTags::EXIF_IFD: {
                uint32_t exif_off = tiff_get_uint32(ctx, e);
                parse_exif_ifd(ctx, exif_off, info);
                break;
            }
            default:
                break;
        }
    }
    return true;
}

bool RawDecoder::parse_raw_ifd(const TiffContext& ctx, uint32_t ifd_offset, RawImageInfo& info) {
    return parse_dng_ifd(ctx, ifd_offset, info);
}

bool RawDecoder::parse_exif_ifd(const TiffContext& ctx, uint32_t ifd_offset, RawImageInfo& info) {
    uint16_t num = read_u16(ctx.data + ifd_offset, ctx.little_endian);
    for (uint16_t i = 0; i < num; ++i) {
        TiffEntry e;
        if (!read_tiff_entry(ctx, ifd_offset, i, e)) continue;
        switch (e.tag) {
            case 0x829A: // ExposureTime
                break;
            case 0x8827: // ISO
                break;
            default: break;
        }
    }
    return true;
}

// ── Read image info ──

bool RawDecoder::read_image_info(const std::string& path, RawImageInfo& info) {
    std::ifstream f(path, std::ios::binary | std::ios::ate);
    if (!f) {
        LOGE("Cannot open file: %s", path.c_str());
        return false;
    }
    size_t sz = f.tellg();
    f.seekg(0);
    std::vector<uint8_t> buf(sz);
    f.read(reinterpret_cast<char*>(buf.data()), sz);
    f.close();
    return read_image_info_from_memory(buf.data(), sz, info);
}

bool RawDecoder::read_image_info_from_memory(const uint8_t* data, size_t size, RawImageInfo& info) {
    TiffContext ctx;
    if (!parse_tiff_header(data, size, ctx)) return false;
    info.format = detect_format_from_memory(data, size);
    parse_dng_ifd(ctx, ctx.ifd0_offset, info);

    // Look for SubIFDs (RAW data IFD)
    uint16_t num = read_u16(data + ctx.ifd0_offset, ctx.little_endian);
    for (uint16_t i = 0; i < num; ++i) {
        TiffEntry e;
        if (!read_tiff_entry(ctx, ctx.ifd0_offset, i, e)) continue;
        if (e.tag == TiffTags::SUB_IFDS) {
            if (e.type == 4 || e.type == 3) {
                uint32_t n = std::min(e.count, 32u);
                for (uint32_t j = 0; j < n; ++j) {
                    uint32_t sub_off = (e.type == 4) ? read_u32(e.data_ptr + j * 4, ctx.little_endian)
                                                      : read_u16(e.data_ptr + j * 2, ctx.little_endian);
                    parse_raw_ifd(ctx, sub_off, info);
                }
            }
        }
    }
    info.is_valid = (info.raw_width > 0 && info.raw_height > 0);
    return info.is_valid;
}

// ── Full decode ──

bool RawDecoder::decode(const std::string& path, RawDecodeResult& result,
                         const RawDecodeOptions& options) {
    std::ifstream f(path, std::ios::binary | std::ios::ate);
    if (!f) {
        result.success = false;
        result.error_message = "Cannot open file: " + path;
        return false;
    }
    size_t sz = f.tellg();
    f.seekg(0);
    std::vector<uint8_t> buf(sz);
    f.read(reinterpret_cast<char*>(buf.data()), sz);
    f.close();
    return decode_from_memory(buf.data(), sz, result, options);
}

bool RawDecoder::decode_from_memory(const uint8_t* data, size_t size, RawDecodeResult& result,
                                     const RawDecodeOptions& options) {
    current_options_ = options;
    result.success = false;
    report_progress(0.0f, "Reading image info");

    if (!read_image_info_from_memory(data, size, result.image_info)) {
        result.error_message = "Failed to parse RAW header";
        return false;
    }

    RawImageInfo& info = result.image_info;
    info.format = detect_format_from_memory(data, size);

    report_progress(0.1f, "Extracting CFA data");

    // Extract embedded thumbnails
    if (options.extract_thumbnail && info.has_thumbnail && info.thumbnail_size > 0 &&
        info.thumbnail_offset + info.thumbnail_size <= size) {
        result.jpeg_thumbnail.assign(data + info.thumbnail_offset,
                                      data + info.thumbnail_offset + info.thumbnail_size);
        report_progress(0.15f, "Thumbnail extracted");
    }

    if (options.extract_preview && info.has_preview && info.preview_size > 0 &&
        info.preview_offset + info.preview_size <= size) {
        result.jpeg_preview.assign(data + info.preview_offset,
                                    data + info.preview_offset + info.preview_size);
        report_progress(0.2f, "Preview extracted");
    }

    // Read RAW CFA data
    int raw_w = info.raw_width;
    int raw_h = info.raw_height;
    if (options.half_resolution) {
        raw_w = (raw_w + 1) / 2;
        raw_h = (raw_h + 1) / 2;
        result.is_half_res = true;
    }

    result.raw_cfa_data.resize(raw_w * raw_h);
    std::fill(result.raw_cfa_data.begin(), result.raw_cfa_data.end(), 0);

    report_progress(0.25f, "Reading RAW pixel data");

    // Read uncompressed or decompress strips
    if (info.compression == 1 && !info.strips.empty()) {
        // Uncompressed - read directly
        for (size_t si = 0; si < info.strips.size(); ++si) {
            if (info.strips[si].offset + info.strips[si].byte_count > size) continue;
            const uint8_t* src = data + info.strips[si].offset;
            int row_start = si * info.rows_per_strip;
            int rows_in_strip = std::min(info.rows_per_strip, raw_h - row_start);
            for (int r = 0; r < rows_in_strip && (row_start + r) < raw_h; ++r) {
                int dst_row = row_start + r;
                if (dst_row >= raw_h) break;
                size_t row_bytes = static_cast<size_t>(raw_w) * (info.bits_per_sample / 8);
                size_t src_off = r * static_cast<size_t>(raw_w) * (info.bits_per_sample / 8);
                if (src_off + row_bytes > info.strips[si].byte_count) break;
                // Copy 16-bit data
                if (info.bits_per_sample <= 16) {
                    for (int c = 0; c < raw_w; ++c) {
                        size_t idx = dst_row * raw_w + c;
                        size_t boff = src_off + c * 2;
                        if (boff + 2 <= info.strips[si].byte_count) {
                            result.raw_cfa_data[idx] = read_u16(src + boff, true);
                        }
                    }
                }
            }
            report_progress(0.25f + 0.3f * (si + 1) / info.strips.size(), "Reading strip data");
        }
    } else if (info.compression == 7 && !info.strips.empty()) {
        // Lossless JPEG - decompress each strip
        size_t total_compressed = 0;
        for (auto& s : info.strips) total_compressed += s.byte_count;
        std::vector<uint8_t> compressed(total_compressed);
        size_t coff = 0;
        for (size_t si = 0; si < info.strips.size(); ++si) {
            if (info.strips[si].offset + info.strips[si].byte_count <= size) {
                memcpy(compressed.data() + coff, data + info.strips[si].offset,
                       info.strips[si].byte_count);
                coff += info.strips[si].byte_count;
            }
        }
        decompress_lossless_jpeg(compressed.data(), total_compressed,
                                  result.raw_cfa_data.data(), raw_w, raw_h,
                                  info.bits_per_sample, 1);
        report_progress(0.5f, "Lossless JPEG decompressed");
    } else if (info.is_nikon_he && !info.strips.empty()) {
        // Nikon HE
        size_t total_compressed = 0;
        for (auto& s : info.strips) total_compressed += s.byte_count;
        std::vector<uint8_t> compressed(total_compressed);
        size_t coff = 0;
        for (size_t si = 0; si < info.strips.size(); ++si) {
            if (info.strips[si].offset + info.strips[si].byte_count <= size) {
                memcpy(compressed.data() + coff, data + info.strips[si].offset,
                       info.strips[si].byte_count);
                coff += info.strips[si].byte_count;
            }
        }
        decompress_nikon_he(compressed.data(), total_compressed,
                             result.raw_cfa_data.data(), raw_w, raw_h,
                             info.bits_per_sample, info.nikon_compression_level,
                             options.nikon_he_threads);
        report_progress(0.5f, "Nikon HE decompressed");
    }

    report_progress(0.55f, "Pre-processing RAW data");

    // Scale to 16-bit if needed
    if (info.bits_per_sample < 16 && info.bits_per_sample > 0) {
        scale_to_16bit(result.raw_cfa_data.data(), raw_w, raw_h, info.bits_per_sample);
    }

    // Subtract black level
    subtract_black_level(result.raw_cfa_data.data(), raw_w, raw_h,
                         info.black_levels, 4);

    // Update result dimensions
    result.width = raw_w;
    result.height = raw_h;

    report_progress(0.6f, "Demosaicing");

    // Demosaic
    int bayer = static_cast<int>(info.cfa_pattern);
    int out_w = raw_w;
    int out_h = raw_h;

    result.float_rgb_data.resize(out_w * out_h * 3);
    std::fill(result.float_rgb_data.begin(), result.float_rgb_data.end(), 0.0f);

    switch (options.demosaic) {
        case DemosaicMethod::RCD:
            demosaic_rcd(result.raw_cfa_data.data(), raw_w, raw_h, bayer,
                         info.white_level, info.black_level, result.float_rgb_data.data());
            break;
        case DemosaicMethod::AMAZE:
            demosaic_amaze(result.raw_cfa_data.data(), raw_w, raw_h, bayer,
                           info.white_level, info.black_level, result.float_rgb_data.data());
            break;
        case DemosaicMethod::DCB:
            demosaic_dcb(result.raw_cfa_data.data(), raw_w, raw_h, bayer,
                         info.white_level, info.black_level, result.float_rgb_data.data());
            break;
        case DemosaicMethod::VNG4:
            demosaic_vng4(result.raw_cfa_data.data(), raw_w, raw_h, bayer,
                          info.white_level, info.black_level, result.float_rgb_data.data());
            break;
        case DemosaicMethod::BILINEAR:
        case DemosaicMethod::AHD:
        case DemosaicMethod::LMMSE:
        default:
            demosaic_bilinear(result.raw_cfa_data.data(), raw_w, raw_h, bayer,
                              info.white_level, info.black_level, result.float_rgb_data.data());
            break;
    }

    report_progress(0.7f, "White balance");

    // Apply white balance
    float wb_mult[3] = {1.0f, 1.0f, 1.0f};
    if (options.wb_illuminant == WBIlluminant::CAMERA_AUTO) {
        wb_mult[0] = info.camera_wb_mult[0];
        wb_mult[1] = info.camera_wb_mult[1];
        wb_mult[2] = info.camera_wb_mult[2];
    }
    apply_white_balance_float(result.float_rgb_data.data(), out_w, out_h, wb_mult);

    report_progress(0.8f, "Color matrix transform");

    // Apply camera color matrix
    if (options.use_camera_matrix && info.has_color_matrix) {
        apply_color_matrix_float(result.float_rgb_data.data(), out_w * out_h, info.color_matrix);
    }

    report_progress(0.85f, "Highlight reconstruction");

    // Highlight reconstruction
    if (options.highlight_mode != HighlightMode::CLIP) {
        reconstruct_highlights(result.float_rgb_data.data(), out_w, out_h,
                               info.white_level, options.highlight_mode);
    }

    report_progress(0.9f, "Generating 16-bit output");

    // Generate 16-bit output
    if (!options.output_float) {
        result.rgb_data.resize(out_w * out_h * 3);
        for (int i = 0; i < out_w * out_h * 3; ++i) {
            float v = result.float_rgb_data[i];
            v = clampf(v, 0.0f, 65535.0f);
            result.rgb_data[i] = static_cast<uint16_t>(v);
        }
    }

    // Generate thumbnail from decoded data (if no embedded thumbnail)
    if (options.extract_thumbnail && result.jpeg_thumbnail.empty()) {
        int thumb_w = std::min(options.max_thumbnail_dimension, out_w);
        int thumb_h = (thumb_w * out_h) / out_w;
        generate_thumbnail_float(result.float_rgb_data.data(), out_w, out_h,
                                  thumb_w, thumb_h, result.jpeg_thumbnail);
        result.thumbnail_width = thumb_w;
        result.thumbnail_height = thumb_h;
    }

    result.width = out_w;
    result.height = out_h;
    result.channels = 3;
    result.success = true;

    report_progress(1.0f, "Done");
    LOGI("RAW decoded: %dx%d, format=%s, demosaic=%d",
         out_w, out_h, info.format.c_str(), static_cast<int>(options.demosaic));
    return true;
}

bool RawDecoder::decode_with_progress(const std::string& path, RawDecodeResult& result,
                                       const RawDecodeOptions& options,
                                       DecodeProgressCallback progress_cb) {
    progress_cb_ = std::move(progress_cb);
    return decode(path, result, options);
}

// ── Thumbnail extraction ──

bool RawDecoder::extract_thumbnail(const std::string& path, std::vector<uint8_t>& jpeg_data,
                                    int& width, int& height) {
    std::ifstream f(path, std::ios::binary | std::ios::ate);
    if (!f) return false;
    size_t sz = f.tellg();
    f.seekg(0);
    std::vector<uint8_t> buf(sz);
    f.read(reinterpret_cast<char*>(buf.data()), sz);
    f.close();
    return extract_thumbnail_from_memory(buf.data(), sz, jpeg_data, width, height);
}

bool RawDecoder::extract_thumbnail_from_memory(const uint8_t* data, size_t size,
                                                std::vector<uint8_t>& jpeg_data,
                                                int& width, int& height) {
    RawImageInfo info;
    if (!read_image_info_from_memory(data, size, info)) return false;

    if (info.has_thumbnail && info.thumbnail_size > 0 &&
        info.thumbnail_offset + info.thumbnail_size <= size) {
        jpeg_data.assign(data + info.thumbnail_offset,
                          data + info.thumbnail_offset + info.thumbnail_size);
        width = info.raw_width / 4;
        height = info.raw_height / 4;
        return true;
    }

    // Try to find JPEG thumbnail in EXIF
    return extract_jpeg_from_app1(data, size, jpeg_data, width, height);
}

// ── Preview extraction ──

bool RawDecoder::extract_preview(const std::string& path, std::vector<uint8_t>& jpeg_data,
                                  int& width, int& height) {
    std::ifstream f(path, std::ios::binary | std::ios::ate);
    if (!f) return false;
    size_t sz = f.tellg();
    f.seekg(0);
    std::vector<uint8_t> buf(sz);
    f.read(reinterpret_cast<char*>(buf.data()), sz);
    f.close();
    return extract_preview_from_memory(buf.data(), sz, jpeg_data, width, height);
}

bool RawDecoder::extract_preview_from_memory(const uint8_t* data, size_t size,
                                              std::vector<uint8_t>& jpeg_data,
                                              int& width, int& height) {
    RawImageInfo info;
    read_image_info_from_memory(data, size, info);

    if (info.has_preview && info.preview_size > 0 &&
        info.preview_offset + info.preview_size <= size) {
        jpeg_data.assign(data + info.preview_offset,
                          data + info.preview_offset + info.preview_size);
        width = info.preview_offset > 0 ? 1024 : 0;
        height = info.preview_offset > 0 ? 768 : 0;
        return true;
    }
    return false;
}

// ── Extract RAW CFA ──

bool RawDecoder::extract_raw_cfa(const std::string& path, std::vector<uint16_t>& cfa_data,
                                  int& width, int& height, RawImageInfo& info) {
    std::ifstream f(path, std::ios::binary | std::ios::ate);
    if (!f) return false;
    size_t sz = f.tellg();
    f.seekg(0);
    std::vector<uint8_t> buf(sz);
    f.read(reinterpret_cast<char*>(buf.data()), sz);
    f.close();
    return extract_raw_cfa_from_memory(buf.data(), sz, cfa_data, width, height, info);
}

bool RawDecoder::extract_raw_cfa_from_memory(const uint8_t* data, size_t size,
                                              std::vector<uint16_t>& cfa_data,
                                              int& width, int& height, RawImageInfo& info) {
    RawDecodeResult result;
    RawDecodeOptions opts;
    opts.demosaic = DemosaicMethod::BILINEAR;
    opts.output_float = false;
    opts.extract_thumbnail = false;
    opts.extract_preview = false;
    if (!decode_from_memory(data, size, result, opts)) return false;
    cfa_data = std::move(result.raw_cfa_data);
    width = result.image_info.raw_width;
    height = result.image_info.raw_height;
    info = result.image_info;
    return true;
}

// ============================================================
// Demosaic algorithms
// ============================================================

// Helper: get Bayer pattern color index at (row, col)
// 0=Red, 1=Green1, 2=Green2, 3=Blue  (RGGB pattern)
static inline int bayer_color(int row, int col, int pattern) {
    int r = row & 1;
    int c = col & 1;
    // pattern: 0=RGGB, 1=BGGR, 2=GRBG, 3=GBRG
    static const int lut[4][2][2] = {
        {{0, 1}, {1, 3}}, // RGGB: R=0,G=1,G=1,B=3
        {{3, 1}, {1, 0}}, // BGGR
        {{1, 0}, {3, 1}}, // GRBG
        {{1, 3}, {0, 1}}, // GBRG
    };
    return lut[pattern][r][c];
}

static inline bool is_green(int row, int col, int pattern) {
    int c = bayer_color(row, col, pattern);
    return c == 1 || c == 2;
}

// Bilinear demosaic
void RawDecoder::demosaic_bilinear(const uint16_t* raw, int width, int height,
                                    int bayer_pattern, uint16_t white_level,
                                    uint16_t black_level, float* rgb_output) {
    float wl = static_cast<float>(std::max(white_level - black_level, 1));
    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            int idx = y * width + x;
            float val = static_cast<float>(raw[idx] - black_level) / wl;
            val = clampf(val, 0.0f, 1.0f);

            int color = bayer_color(y, x, bayer_pattern);
            float r = 0, g = 0, b = 0;

            if (color == 0) { // Red pixel
                r = val;
                // G: average of neighbors
                int ng = 0; float gsum = 0;
                if (y > 0) { gsum += static_cast<float>(raw[(y-1)*width+x] - black_level) / wl; ng++; }
                if (y < height-1) { gsum += static_cast<float>(raw[(y+1)*width+x] - black_level) / wl; ng++; }
                if (x > 0) { gsum += static_cast<float>(raw[y*width+x-1] - black_level) / wl; ng++; }
                if (x < width-1) { gsum += static_cast<float>(raw[y*width+x+1] - black_level) / wl; ng++; }
                g = ng > 0 ? (gsum / ng) : 0;
                // B: average of corners
                int nb = 0; float bsum = 0;
                if (y > 0 && x > 0) { bsum += static_cast<float>(raw[(y-1)*width+x-1] - black_level) / wl; nb++; }
                if (y > 0 && x < width-1) { bsum += static_cast<float>(raw[(y-1)*width+x+1] - black_level) / wl; nb++; }
                if (y < height-1 && x > 0) { bsum += static_cast<float>(raw[(y+1)*width+x-1] - black_level) / wl; nb++; }
                if (y < height-1 && x < width-1) { bsum += static_cast<float>(raw[(y+1)*width+x+1] - black_level) / wl; nb++; }
                b = nb > 0 ? (bsum / nb) : 0;
            } else if (color == 3) { // Blue pixel
                b = val;
                int ng = 0; float gsum = 0;
                if (y > 0) { gsum += static_cast<float>(raw[(y-1)*width+x] - black_level) / wl; ng++; }
                if (y < height-1) { gsum += static_cast<float>(raw[(y+1)*width+x] - black_level) / wl; ng++; }
                if (x > 0) { gsum += static_cast<float>(raw[y*width+x-1] - black_level) / wl; ng++; }
                if (x < width-1) { gsum += static_cast<float>(raw[y*width+x+1] - black_level) / wl; ng++; }
                g = ng > 0 ? (gsum / ng) : 0;
                int nr = 0; float rsum = 0;
                if (y > 0 && x > 0) { rsum += static_cast<float>(raw[(y-1)*width+x-1] - black_level) / wl; nr++; }
                if (y > 0 && x < width-1) { rsum += static_cast<float>(raw[(y-1)*width+x+1] - black_level) / wl; nr++; }
                if (y < height-1 && x > 0) { rsum += static_cast<float>(raw[(y+1)*width+x-1] - black_level) / wl; nr++; }
                if (y < height-1 && x < width-1) { rsum += static_cast<float>(raw[(y+1)*width+x+1] - black_level) / wl; nr++; }
                r = nr > 0 ? (rsum / nr) : 0;
            } else { // Green pixel
                g = val;
                if (bayer_color(y, x + 1, bayer_pattern) == 0 || bayer_color(y, x - 1, bayer_pattern) == 0) {
                    // R is horizontal neighbors
                    if (x > 0) r = static_cast<float>(raw[y*width+x-1] - black_level) / wl;
                    if (x < width-1) r = (r + static_cast<float>(raw[y*width+x+1] - black_level) / wl) / 2;
                    // B is vertical neighbors
                    if (y > 0) b = static_cast<float>(raw[(y-1)*width+x] - black_level) / wl;
                    if (y < height-1) b = (b + static_cast<float>(raw[(y+1)*width+x] - black_level) / wl) / 2;
                } else {
                    // B is horizontal neighbors
                    if (x > 0) b = static_cast<float>(raw[y*width+x-1] - black_level) / wl;
                    if (x < width-1) b = (b + static_cast<float>(raw[y*width+x+1] - black_level) / wl) / 2;
                    // R is vertical neighbors
                    if (y > 0) r = static_cast<float>(raw[(y-1)*width+x] - black_level) / wl;
                    if (y < height-1) r = (r + static_cast<float>(raw[(y+1)*width+x] - black_level) / wl) / 2;
                }
            }
            int out_idx = (y * width + x) * 3;
            rgb_output[out_idx] = r;
            rgb_output[out_idx + 1] = g;
            rgb_output[out_idx + 2] = b;
        }
    }
}

// RCD (Ratio Corrected Demosaicing) - simplified
void RawDecoder::demosaic_rcd(const uint16_t* raw, int width, int height,
                               int bayer_pattern, uint16_t white_level,
                               uint16_t black_level, float* rgb_output) {
    // RCD conceptually uses ratio-based interpolation.
    // First pass: bilinear interpolation to get all channels
    // Second pass: correct using color ratios
    // For a comprehensive implementation, we do bilinear + edge-directed correction

    float wl = static_cast<float>(std::max(white_level - black_level, 1));
    // First pass: bilinear
    demosaic_bilinear(raw, width, height, bayer_pattern, white_level, black_level, rgb_output);

    // Second pass: ratio correction (simplified median-based refinement)
    for (int y = 2; y < height - 2; ++y) {
        for (int x = 2; x < width - 2; ++x) {
            int color = bayer_color(y, x, bayer_pattern);
            int idx = (y * width + x) * 3;
            float raw_val = static_cast<float>(raw[y * width + x] - black_level) / wl;

            if (color == 0) { // Red
                // Use G/R ratio to refine R
                float g = rgb_output[idx + 1];
                float r = rgb_output[idx];
                if (r > 0.001f && g > 0.001f) {
                    float ratio = raw_val / r;
                    rgb_output[idx] = raw_val;
                    rgb_output[idx + 1] = g * ratio;
                }
            } else if (color == 3) { // Blue
                float g = rgb_output[idx + 1];
                float b = rgb_output[idx + 2];
                if (b > 0.001f && g > 0.001f) {
                    float ratio = raw_val / b;
                    rgb_output[idx + 2] = raw_val;
                    rgb_output[idx + 1] = g * ratio;
                }
            }
            // Green pixels are already correct
        }
    }
}

// DCB (Demosaicing with Color Balancing) - simplified
void RawDecoder::demosaic_dcb(const uint16_t* raw, int width, int height,
                               int bayer_pattern, uint16_t white_level,
                               uint16_t black_level, float* rgb_output) {
    // DCB uses color differences for interpolation
    float wl = static_cast<float>(std::max(white_level - black_level, 1));
    // First do bilinear
    demosaic_bilinear(raw, width, height, bayer_pattern, white_level, black_level, rgb_output);

    // Refine using color difference interpolation
    for (int y = 1; y < height - 1; ++y) {
        for (int x = 1; x < width - 1; ++x) {
            int color = bayer_color(y, x, bayer_pattern);
            int idx = (y * width + x) * 3;
            float raw_val = static_cast<float>(raw[y * width + x] - black_level) / wl;

            if (color == 0) { // Red - refine G and B using color differences
                float r = raw_val;
                float g = rgb_output[idx + 1];
                float b = rgb_output[idx + 2];
                // Refine G using G-R differences
                float g_minus_r = 0;
                int count = 0;
                if (is_green(y-1, x, bayer_pattern)) { g_minus_r += static_cast<float>(raw[(y-1)*width+x] - black_level) / wl - rgb_output[((y-1)*width+x)*3]; count++; }
                if (is_green(y+1, x, bayer_pattern)) { g_minus_r += static_cast<float>(raw[(y+1)*width+x] - black_level) / wl - rgb_output[((y+1)*width+x)*3]; count++; }
                if (is_green(y, x-1, bayer_pattern)) { g_minus_r += static_cast<float>(raw[y*width+x-1] - black_level) / wl - rgb_output[(y*width+x-1)*3]; count++; }
                if (is_green(y, x+1, bayer_pattern)) { g_minus_r += static_cast<float>(raw[y*width+x+1] - black_level) / wl - rgb_output[(y*width+x+1)*3]; count++; }
                if (count > 0) g_minus_r /= count;
                rgb_output[idx + 1] = r + g_minus_r;
            } else if (color == 3) { // Blue - refine G and R
                float b = raw_val;
                float g = rgb_output[idx + 1];
                float r = rgb_output[idx];
                float g_minus_b = 0;
                int count = 0;
                if (is_green(y-1, x, bayer_pattern)) { g_minus_b += static_cast<float>(raw[(y-1)*width+x] - black_level) / wl - rgb_output[((y-1)*width+x)*3+2]; count++; }
                if (is_green(y+1, x, bayer_pattern)) { g_minus_b += static_cast<float>(raw[(y+1)*width+x] - black_level) / wl - rgb_output[((y+1)*width+x)*3+2]; count++; }
                if (is_green(y, x-1, bayer_pattern)) { g_minus_b += static_cast<float>(raw[y*width+x-1] - black_level) / wl - rgb_output[(y*width+x-1)*3+2]; count++; }
                if (is_green(y, x+1, bayer_pattern)) { g_minus_b += static_cast<float>(raw[y*width+x+1] - black_level) / wl - rgb_output[(y*width+x+1)*3+2]; count++; }
                if (count > 0) g_minus_b /= count;
                rgb_output[idx + 1] = b + g_minus_b;
            }
        }
    }
}

// AMAZE - simplified (edge-directed with gradient weighting)
void RawDecoder::demosaic_amaze(const uint16_t* raw, int width, int height,
                                 int bayer_pattern, uint16_t white_level,
                                 uint16_t black_level, float* rgb_output) {
    // AMAZE is complex; we implement a simplified version with edge-directed interpolation
    // Full AMAZE uses 16-directional gradients; here we use 4-directional

    float wl = static_cast<float>(std::max(white_level - black_level, 1));

    // Temporary buffer for normalized values
    std::vector<float> norm(width * height);
    for (int i = 0; i < width * height; ++i) {
        norm[i] = clampf(static_cast<float>(raw[i] - black_level) / wl, 0.0f, 1.0f);
    }

    auto get_val = [&](int y, int x) -> float {
        if (x < 0 || x >= width || y < 0 || y >= height) return 0.0f;
        return norm[y * width + x];
    };

    // First pass: interpolate G at R/B locations using edge-directed interpolation
    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            int color = bayer_color(y, x, bayer_pattern);
            int idx = (y * width + x) * 3;

            if (color == 0 || color == 3) { // R or B pixel - interpolate G
                float n = get_val(y - 1, x);
                float s = get_val(y + 1, x);
                float e = get_val(y, x + 1);
                float w = get_val(y, x - 1);

                // Compute gradients
                float gH = std::abs(e - w) + std::abs(get_val(y, x) - get_val(y, x - 2)) * 0.5f;
                float gV = std::abs(n - s) + std::abs(get_val(y, x) - get_val(y - 2, x)) * 0.5f;

                // Weighted interpolation
                float g = 0;
                if (gH < 0.001f && gV < 0.001f) {
                    g = (n + s + e + w) * 0.25f;
                } else {
                    float wH = 1.0f / (1.0f + gH);
                    float wV = 1.0f / (1.0f + gV);
                    g = (wH * (e + w) * 0.5f + wV * (n + s) * 0.5f) / (wH + wV);
                }
                rgb_output[idx + 1] = g;
            } else { // Green pixel
                rgb_output[idx + 1] = get_val(y, x);
            }
        }
    }

    // Second pass: interpolate R and B
    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            int color = bayer_color(y, x, bayer_pattern);
            int idx = (y * width + x) * 3;

            if (color == 0) { // Red pixel - interpolate B
                rgb_output[idx] = get_val(y, x); // R
                // B from corners
                float bsum = 0; int cnt = 0;
                if (y > 0 && x > 0) { bsum += get_val(y-1, x-1); cnt++; }
                if (y > 0 && x < width-1) { bsum += get_val(y-1, x+1); cnt++; }
                if (y < height-1 && x > 0) { bsum += get_val(y+1, x-1); cnt++; }
                if (y < height-1 && x < width-1) { bsum += get_val(y+1, x+1); cnt++; }
                rgb_output[idx + 2] = cnt > 0 ? (bsum / cnt) : 0;
            } else if (color == 3) { // Blue pixel - interpolate R
                rgb_output[idx + 2] = get_val(y, x); // B
                float rsum = 0; int cnt = 0;
                if (y > 0 && x > 0) { rsum += get_val(y-1, x-1); cnt++; }
                if (y > 0 && x < width-1) { rsum += get_val(y-1, x+1); cnt++; }
                if (y < height-1 && x > 0) { rsum += get_val(y+1, x-1); cnt++; }
                if (y < height-1 && x < width-1) { rsum += get_val(y+1, x+1); cnt++; }
                rgb_output[idx] = cnt > 0 ? (rsum / cnt) : 0;
            } else { // Green pixel
                // R and B from neighbors
                float r = 0, b = 0;
                if (bayer_color(y, x+1, bayer_pattern) == 0) {
                    if (x < width-1) r = get_val(y, x+1);
                    if (y > 0) b = get_val(y-1, x);
                    if (y < height-1) b = (b + get_val(y+1, x)) / 2;
                } else if (bayer_color(y, x+1, bayer_pattern) == 3) {
                    if (x < width-1) b = get_val(y, x+1);
                    if (y > 0) r = get_val(y-1, x);
                    if (y < height-1) r = (r + get_val(y+1, x)) / 2;
                } else if (bayer_color(y, x-1, bayer_pattern) == 0) {
                    if (x > 0) r = get_val(y, x-1);
                    if (y > 0) b = get_val(y-1, x);
                    if (y < height-1) b = (b + get_val(y+1, x)) / 2;
                } else {
                    if (x > 0) b = get_val(y, x-1);
                    if (y > 0) r = get_val(y-1, x);
                    if (y < height-1) r = (r + get_val(y+1, x)) / 2;
                }
                rgb_output[idx] = r;
                rgb_output[idx + 2] = b;
            }
        }
    }
}

// VNG4 - simplified (Variable Number of Gradients)
void RawDecoder::demosaic_vng4(const uint16_t* raw, int width, int height,
                                int bayer_pattern, uint16_t white_level,
                                uint16_t black_level, float* rgb_output) {
    // Simplified VNG4: use 4 gradient directions for interpolation
    float wl = static_cast<float>(std::max(white_level - black_level, 1));

    std::vector<float> norm(width * height);
    for (int i = 0; i < width * height; ++i) {
        norm[i] = clampf(static_cast<float>(raw[i] - black_level) / wl, 0.0f, 1.0f);
    }

    auto get_val = [&](int y, int x) -> float {
        if (x < 0 || x >= width || y < 0 || y >= height) return 0.0f;
        return norm[y * width + x];
    };

    for (int y = 2; y < height - 2; ++y) {
        for (int x = 2; x < width - 2; ++x) {
            int color = bayer_color(y, x, bayer_pattern);
            int idx = (y * width + x) * 3;

            if (color == 0) { // Red
                rgb_output[idx] = get_val(y, x);
                // G interpolation with VNG4
                float gN = get_val(y-1, x); float gS = get_val(y+1, x);
                float gE = get_val(y, x+1); float gW = get_val(y, x-1);
                float gNE = get_val(y-1, x+1); float gNW = get_val(y-1, x-1);
                float gSE = get_val(y+1, x+1); float gSW = get_val(y+1, x-1);

                // Gradient weights
                float dN = std::abs(get_val(y-2, x) - get_val(y, x)) + std::abs(gN - get_val(y-2, x+1)) + std::abs(gN - get_val(y-2, x-1));
                float dS = std::abs(get_val(y+2, x) - get_val(y, x)) + std::abs(gS - get_val(y+2, x+1)) + std::abs(gS - get_val(y+2, x-1));
                float dE = std::abs(get_val(y, x+2) - get_val(y, x)) + std::abs(gE - get_val(y-1, x+2)) + std::abs(gE - get_val(y+1, x+2));
                float dW = std::abs(get_val(y, x-2) - get_val(y, x)) + std::abs(gW - get_val(y-1, x-2)) + std::abs(gW - get_val(y+1, x-2));

                float wN = 1.0f / (1.0f + dN); float wS = 1.0f / (1.0f + dS);
                float wE = 1.0f / (1.0f + dE); float wW = 1.0f / (1.0f + dW);
                float wsum = wN + wS + wE + wW;
                rgb_output[idx + 1] = (wN * gN + wS * gS + wE * gE + wW * gW) / (wsum > 0.001f ? wsum : 1.0f);

                // B from corners
                rgb_output[idx + 2] = (get_val(y-1, x-1) + get_val(y-1, x+1) + get_val(y+1, x-1) + get_val(y+1, x+1)) * 0.25f;
            } else if (color == 3) { // Blue
                rgb_output[idx + 2] = get_val(y, x);
                float gN = get_val(y-1, x); float gS = get_val(y+1, x);
                float gE = get_val(y, x+1); float gW = get_val(y, x-1);
                float dN = std::abs(get_val(y-2, x) - get_val(y, x));
                float dS = std::abs(get_val(y+2, x) - get_val(y, x));
                float dE = std::abs(get_val(y, x+2) - get_val(y, x));
                float dW = std::abs(get_val(y, x-2) - get_val(y, x));
                float wN = 1.0f / (1.0f + dN); float wS = 1.0f / (1.0f + dS);
                float wE = 1.0f / (1.0f + dE); float wW = 1.0f / (1.0f + dW);
                float wsum = wN + wS + wE + wW;
                rgb_output[idx + 1] = (wN * gN + wS * gS + wE * gE + wW * gW) / (wsum > 0.001f ? wsum : 1.0f);
                rgb_output[idx] = (get_val(y-1, x-1) + get_val(y-1, x+1) + get_val(y+1, x-1) + get_val(y+1, x+1)) * 0.25f;
            } else { // Green
                rgb_output[idx + 1] = get_val(y, x);
                if (bayer_color(y, x+1, bayer_pattern) == 0) {
                    rgb_output[idx] = (get_val(y, x-1) + get_val(y, x+1)) * 0.5f;
                    rgb_output[idx + 2] = (get_val(y-1, x) + get_val(y+1, x)) * 0.5f;
                } else {
                    rgb_output[idx] = (get_val(y-1, x) + get_val(y+1, x)) * 0.5f;
                    rgb_output[idx + 2] = (get_val(y, x-1) + get_val(y, x+1)) * 0.5f;
                }
            }
        }
    }
}

// ============================================================
// White balance
// ============================================================

void RawDecoder::apply_white_balance_16bit(uint16_t* rgb, int width, int height,
                                            const float* multipliers) {
    for (int i = 0; i < width * height; ++i) {
        int idx = i * 3;
        float r = static_cast<float>(rgb[idx]) * multipliers[0];
        float g = static_cast<float>(rgb[idx + 1]) * multipliers[1];
        float b = static_cast<float>(rgb[idx + 2]) * multipliers[2];
        rgb[idx] = static_cast<uint16_t>(clampf(r, 0, 65535));
        rgb[idx + 1] = static_cast<uint16_t>(clampf(g, 0, 65535));
        rgb[idx + 2] = static_cast<uint16_t>(clampf(b, 0, 65535));
    }
}

void RawDecoder::apply_white_balance_float(float* rgb, int width, int height,
                                            const float* multipliers) {
    for (int i = 0; i < width * height; ++i) {
        int idx = i * 3;
        rgb[idx] *= multipliers[0];
        rgb[idx + 1] *= multipliers[1];
        rgb[idx + 2] *= multipliers[2];
    }
}

// ============================================================
// Color matrix transform
// ============================================================

void RawDecoder::apply_color_matrix_float(float* rgb, int pixel_count,
                                           const float* m) {
    for (int i = 0; i < pixel_count; ++i) {
        int idx = i * 3;
        float r = rgb[idx], g = rgb[idx + 1], b = rgb[idx + 2];
        rgb[idx]     = m[0] * r + m[1] * g + m[2] * b;
        rgb[idx + 1] = m[3] * r + m[4] * g + m[5] * b;
        rgb[idx + 2] = m[6] * r + m[7] * g + m[8] * b;
    }
}

void RawDecoder::apply_color_matrix_16bit(uint16_t* rgb, int pixel_count,
                                           const float* m) {
    for (int i = 0; i < pixel_count; ++i) {
        int idx = i * 3;
        float r = static_cast<float>(rgb[idx]);
        float g = static_cast<float>(rgb[idx + 1]);
        float b = static_cast<float>(rgb[idx + 2]);
        rgb[idx]     = static_cast<uint16_t>(clampf(m[0] * r + m[1] * g + m[2] * b, 0, 65535));
        rgb[idx + 1] = static_cast<uint16_t>(clampf(m[3] * r + m[4] * g + m[5] * b, 0, 65535));
        rgb[idx + 2] = static_cast<uint16_t>(clampf(m[6] * r + m[7] * g + m[8] * b, 0, 65535));
    }
}

// ============================================================
// Highlight reconstruction
// ============================================================

void RawDecoder::reconstruct_highlights(float* rgb, int width, int height,
                                         uint16_t white_level, HighlightMode mode) {
    if (mode == HighlightMode::CLIP) return;

    float threshold = 0.95f; // 95% of full scale
    float max_val = static_cast<float>(white_level) / 65535.0f;

    for (int y = 1; y < height - 1; ++y) {
        for (int x = 1; x < width - 1; ++x) {
            int idx = (y * width + x) * 3;
            float r = rgb[idx], g = rgb[idx + 1], b = rgb[idx + 2];
            float max_ch = std::max({r, g, b});

            if (max_ch > threshold * max_val) {
                if (mode == HighlightMode::BLEND) {
                    // Blend with neighbors
                    float rn = 0, gn = 0, bn = 0; int cnt = 0;
                    for (int dy = -1; dy <= 1; ++dy) {
                        for (int dx = -1; dx <= 1; ++dx) {
                            if (dx == 0 && dy == 0) continue;
                            int nidx = ((y + dy) * width + (x + dx)) * 3;
                            rn += rgb[nidx]; gn += rgb[nidx + 1]; bn += rgb[nidx + 2];
                            cnt++;
                        }
                    }
                    float frac = (max_ch - threshold * max_val) / (max_val * (1.0f - threshold));
                    frac = clampf(frac, 0.0f, 1.0f);
                    rgb[idx] = r * (1.0f - frac) + (rn / cnt) * frac;
                    rgb[idx + 1] = g * (1.0f - frac) + (gn / cnt) * frac;
                    rgb[idx + 2] = b * (1.0f - frac) + (bn / cnt) * frac;
                } else if (mode == HighlightMode::UNCLIP) {
                    // Unclip: use unclipped channels to estimate clipped ones
                    float min_ch = std::min({r, g, b});
                    if (min_ch < max_ch) {
                        float ratio = (max_ch / (min_ch + 0.001f));
                        if (ratio > 2.0f) {
                            // One channel is clipped, use the others to estimate
                            if (r >= max_ch) rgb[idx] = (g + b) * 0.5f;
                            if (g >= max_ch) rgb[idx + 1] = (r + b) * 0.5f;
                            if (b >= max_ch) rgb[idx + 2] = (r + g) * 0.5f;
                        }
                    }
                } else if (mode == HighlightMode::RECONSTRUCT) {
                    // Full reconstruction: fuse unclipped channels
                    float min_ch = std::min({r, g, b});
                    float mid_ch = r + g + b - max_ch - min_ch;
                    if (max_ch > min_ch * 3.0f) {
                        // Severe clipping
                        rgb[idx] = (r < max_ch) ? r : (g + b) * 0.5f;
                        rgb[idx + 1] = (g < max_ch) ? g : (r + b) * 0.5f;
                        rgb[idx + 2] = (b < max_ch) ? b : (r + g) * 0.5f;
                    }
                }
            }
        }
    }
}

// ============================================================
// Black level subtraction
// ============================================================

void RawDecoder::subtract_black_level(uint16_t* raw, int width, int height,
                                       const int* black_levels, int black_count) {
    if (black_count < 1) return;
    // Simple per-pixel black subtraction (RGGB pattern)
    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            int bl_idx = ((y & 1) * 2 + (x & 1)) % std::min(black_count, 4);
            int bl = black_levels[bl_idx];
            int idx = y * width + x;
            if (raw[idx] > bl) raw[idx] -= bl;
            else raw[idx] = 0;
        }
    }
}

// ============================================================
// Scale to 16-bit
// ============================================================

void RawDecoder::scale_to_16bit(uint16_t* raw, int width, int height,
                                 int bits_per_sample) {
    if (bits_per_sample >= 16 || bits_per_sample <= 0) return;
    int shift = 16 - bits_per_sample;
    for (int i = 0; i < width * height; ++i) {
        raw[i] = static_cast<uint16_t>(raw[i] << shift);
    }
}

// ============================================================
// Thumbnail generation from decoded RGB
// ============================================================

void RawDecoder::generate_thumbnail(const uint16_t* rgb, int width, int height,
                                     int thumb_width, int thumb_height,
                                     std::vector<uint8_t>& jpeg_thumbnail) {
    // Simple nearest-neighbor downscale to thumbnail + store as raw RGB
    // In production, this would use a JPEG encoder; here we store raw pixel data
    thumb_width = std::max(1, thumb_width);
    thumb_height = std::max(1, thumb_height);
    jpeg_thumbnail.resize(thumb_width * thumb_height * 3);
    for (int y = 0; y < thumb_height; ++y) {
        for (int x = 0; x < thumb_width; ++x) {
            int sx = (x * width) / thumb_width;
            int sy = (y * height) / thumb_height;
            int sidx = (sy * width + sx) * 3;
            int didx = (y * thumb_width + x) * 3;
            jpeg_thumbnail[didx]     = static_cast<uint8_t>(rgb[sidx] >> 8);
            jpeg_thumbnail[didx + 1] = static_cast<uint8_t>(rgb[sidx + 1] >> 8);
            jpeg_thumbnail[didx + 2] = static_cast<uint8_t>(rgb[sidx + 2] >> 8);
        }
    }
}

void RawDecoder::generate_thumbnail_float(const float* rgb, int width, int height,
                                           int thumb_width, int thumb_height,
                                           std::vector<uint8_t>& jpeg_thumbnail) {
    thumb_width = std::max(1, thumb_width);
    thumb_height = std::max(1, thumb_height);
    jpeg_thumbnail.resize(thumb_width * thumb_height * 3);
    for (int y = 0; y < thumb_height; ++y) {
        for (int x = 0; x < thumb_width; ++x) {
            int sx = (x * width) / thumb_width;
            int sy = (y * height) / thumb_height;
            int sidx = (sy * width + sx) * 3;
            int didx = (y * thumb_width + x) * 3;
            jpeg_thumbnail[didx]     = static_cast<uint8_t>(clampf(rgb[sidx] * 255.0f, 0, 255));
            jpeg_thumbnail[didx + 1] = static_cast<uint8_t>(clampf(rgb[sidx + 1] * 255.0f, 0, 255));
            jpeg_thumbnail[didx + 2] = static_cast<uint8_t>(clampf(rgb[sidx + 2] * 255.0f, 0, 255));
        }
    }
}

// ============================================================
// JPEG helper
// ============================================================

bool RawDecoder::find_jpeg_marker(const uint8_t* data, size_t size, size_t& offset,
                                   uint8_t& marker) {
    while (offset + 1 < size) {
        if (data[offset] == 0xFF) {
            marker = data[offset + 1];
            if (marker != 0x00) return true;
            offset += 2;
        } else {
            ++offset;
        }
    }
    return false;
}

bool RawDecoder::extract_jpeg_from_app1(const uint8_t* data, size_t size,
                                         std::vector<uint8_t>& jpeg,
                                         int& width, int& height) {
    // Look for JPEG thumbnail in EXIF APP1
    if (size < 4) return false;
    if (data[0] != 0xFF || data[1] != 0xD8) return false;

    size_t offset = 2;
    uint8_t marker;
    while (find_jpeg_marker(data, size, offset, marker)) {
        offset += 2;
        if (marker == 0xE1) {
            // APP1 - check for EXIF
            if (offset + 6 <= size && data[offset] == 'E' && data[offset + 1] == 'x' &&
                data[offset + 2] == 'i' && data[offset + 3] == 'f') {
                // Parse EXIF to find thumbnail
                uint16_t seg_len = (data[offset - 2] << 8) | data[offset - 1];
                size_t exif_start = offset + 6;
                const uint8_t* exif = data + exif_start;
                size_t exif_size = seg_len - 8;

                if (exif_size >= 8) {
                    bool le = (exif[0] == 0x49);
                    uint32_t ifd0 = read_u32(exif + 4, le);
                    if (ifd0 + 2 < exif_size) {
                        uint16_t entries = read_u16(exif + ifd0, le);
                        for (uint16_t i = 0; i < entries; ++i) {
                            uint32_t off = ifd0 + 2 + i * 12;
                            uint16_t tag = read_u16(exif + off, le);
                            if (tag == 0x0201) { // JPEGInterchangeFormat
                                uint32_t thumb_off = read_u32(exif + off + 8, le);
                                uint16_t next_tag = read_u16(exif + ifd0 + 2 + (i + 1) * 12, le);
                                if (next_tag == 0x0202) { // JPEGInterchangeFormatLength
                                    uint32_t thumb_len = read_u32(exif + ifd0 + 2 + (i + 1) * 12 + 8, le);
                                    if (thumb_off + thumb_len <= exif_size) {
                                        jpeg.assign(exif + thumb_off, exif + thumb_off + thumb_len);
                                        width = 160;
                                        height = 120;
                                        return true;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            // Skip past this segment
            uint16_t seg_len = (data[offset - 2] << 8) | data[offset - 1];
            offset += seg_len - 2;
        } else if (marker == 0xDA) {
            break; // SOS
        } else if (marker >= 0xC0 && marker <= 0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC) {
            uint16_t seg_len = (data[offset] << 8) | data[offset + 1];
            offset += seg_len - 2;
        }
    }
    return false;
}

// ============================================================
// Nikon HE decompression
// ============================================================

// ── Bit unpacking helper ──────────────────────────────────────
// Extracts num_bits bits starting at bit_offset from buf (MSB-first packing).
uint16_t RawDecoder::unpack_bits(const uint8_t* buf, size_t buf_size,
                                  size_t bit_offset, int num_bits) {
    if (!buf || num_bits <= 0 || num_bits > 16) return 0;

    size_t total_bits = buf_size * 8;
    if (bit_offset + static_cast<size_t>(num_bits) > total_bits) return 0;

    uint32_t value = 0;
    for (int b = 0; b < num_bits; ++b) {
        size_t abs_bit = bit_offset + static_cast<size_t>(b);
        size_t byte_idx = abs_bit >> 3;
        int bit_idx = 7 - static_cast<int>(abs_bit & 7); // MSB first
        if (buf[byte_idx] & (1 << bit_idx)) {
            value |= (1u << (num_bits - 1 - b));
        }
    }
    return static_cast<uint16_t>(value);
}

// ── LZFSE-like decompression for Nikon HE scanlines ──────────
// Implements a simplified LZFSE-like decoder that handles the three
// main operation types used in Nikon HE compressed scanlines:
//   L (literal): copy N literal bytes
//   M (match):   LZ77-style back-reference copy of length N from offset D
//   E (expand):  variable-length encoded delta values
bool RawDecoder::decompress_nikon_he_lzfse(const uint8_t* src, size_t src_size,
                                             uint8_t* dst, size_t dst_capacity,
                                             size_t& out_size) {
    out_size = 0;
    if (!src || src_size == 0 || !dst || dst_capacity == 0) return false;

    // LZFSE-like stream format:
    // The stream is a sequence of blocks. Each block starts with a 1-byte opcode:
    //   0x00-0x1F: Literal run. Low 5 bits = literal_count - 1 (1-32 bytes follow)
    //   0x20-0x7F: Match. Bits [6:5] = length_extra, low 5 bits = offset.
    //              Next byte: (offset_extra << 2) | length_extra_extra
    //              Length = 3 + (length_extra << 2) + length_extra_extra
    //              Offset = (low5 + 1) + (offset_extra << 5)
    //   0x80-0xFF: Extended match or expand. Decoded per context.

    size_t src_pos = 0;
    size_t dst_pos = 0;

    while (src_pos < src_size && dst_pos < dst_capacity) {
        uint8_t opcode = src[src_pos++];

        if (opcode <= 0x1F) {
            // ── Literal run ──
            int count = (opcode & 0x1F) + 1;
            if (src_pos + static_cast<size_t>(count) > src_size) {
                LOGE("Nikon HE LZFSE: literal overrun at src_pos=%zu count=%d src_size=%zu",
                     src_pos, count, src_size);
                return false;
            }
            if (dst_pos + static_cast<size_t>(count) > dst_capacity) {
                count = static_cast<int>(dst_capacity - dst_pos);
            }
            memcpy(dst + dst_pos, src + src_pos, count);
            src_pos += count;
            dst_pos += count;
        } else if (opcode <= 0x7F) {
            // ── Match (LZ77 back-reference) ──
            int length_extra = (opcode >> 5) & 0x03;
            int offset_lo = opcode & 0x1F;

            if (src_pos >= src_size) {
                LOGE("Nikon HE LZFSE: match metadata overrun");
                return false;
            }
            uint8_t next = src[src_pos++];
            int offset_extra = (next >> 2) & 0x3F;
            int length_extra2 = next & 0x03;

            int match_len = 3 + (length_extra << 2) + length_extra2;
            int match_off = (offset_lo + 1) + (offset_extra << 5);

            if (match_off <= 0 || match_off > static_cast<int>(dst_pos)) {
                LOGE("Nikon HE LZFSE: invalid match offset %d (dst_pos=%zu)",
                     match_off, dst_pos);
                return false;
            }
            if (dst_pos + static_cast<size_t>(match_len) > dst_capacity) {
                match_len = static_cast<int>(dst_capacity - dst_pos);
            }

            // Byte-by-byte copy to handle overlapping matches
            for (int i = 0; i < match_len; ++i) {
                dst[dst_pos] = dst[dst_pos - match_off];
                dst_pos++;
            }
        } else {
            // ── Extended operation (0x80-0xFF) ──
            // Used for longer matches and delta expansion.
            // Format: opcode byte, then variable extra bytes.
            int op_type = (opcode >> 5) & 0x07; // 4-7
            int param = opcode & 0x1F;

            if (op_type == 4) {
                // Long literal: param=number of following length bytes (1-2)
                int count = 0;
                if (param >= 1 && src_pos < src_size) {
                    count = src[src_pos++];
                }
                if (param >= 2 && src_pos < src_size) {
                    count |= (src[src_pos++] << 8);
                }
                if (count == 0) count = param + 33;

                if (src_pos + static_cast<size_t>(count) > src_size) {
                    LOGE("Nikon HE LZFSE: long literal overrun");
                    return false;
                }
                if (dst_pos + static_cast<size_t>(count) > dst_capacity) {
                    count = static_cast<int>(dst_capacity - dst_pos);
                }
                memcpy(dst + dst_pos, src + src_pos, count);
                src_pos += count;
                dst_pos += count;
            } else if (op_type == 5) {
                // Long match: large back-reference
                if (src_pos + 2 > src_size) {
                    LOGE("Nikon HE LZFSE: long match metadata overrun");
                    return false;
                }
                int match_len = 3 + param;
                uint8_t b1 = src[src_pos++];
                uint8_t b2 = src[src_pos++];
                int match_off = b1 | (b2 << 8);
                if (match_off == 0) match_off = 1;

                if (match_off > static_cast<int>(dst_pos)) {
                    LOGE("Nikon HE LZFSE: long match offset %d invalid", match_off);
                    return false;
                }
                if (dst_pos + static_cast<size_t>(match_len) > dst_capacity) {
                    match_len = static_cast<int>(dst_capacity - dst_pos);
                }
                for (int i = 0; i < match_len; ++i) {
                    dst[dst_pos] = dst[dst_pos - match_off];
                    dst_pos++;
                }
            } else {
                // op_type 6,7: Delta expansion for pixel prediction
                // Reads a base value and a series of variable-length deltas
                int delta_count = param + 1;
                if (src_pos >= src_size) {
                    LOGE("Nikon HE LZFSE: delta expansion overrun");
                    return false;
                }
                // Read base byte
                uint8_t base = src[src_pos++];
                if (dst_pos < dst_capacity) {
                    dst[dst_pos++] = base;
                }
                for (int d = 1; d < delta_count && src_pos < src_size && dst_pos < dst_capacity; ++d) {
                    uint8_t delta_byte = src[src_pos++];
                    // Variable-length delta: high 2 bits encode size
                    int delta = 0;
                    int vlen = (delta_byte >> 6) & 0x03;
                    if (vlen == 0) {
                        // 6-bit signed delta
                        delta = static_cast<int8_t>((delta_byte & 0x3F) << 2) >> 2;
                    } else if (vlen == 1) {
                        // 14-bit signed delta (one extra byte)
                        if (src_pos >= src_size) break;
                        delta = static_cast<int16_t>(((delta_byte & 0x3F) | (src[src_pos++] << 6)) << 4) >> 4;
                    } else {
                        // Copy delta_count-d literal bytes (no delta)
                        dst[dst_pos++] = delta_byte;
                        continue;
                    }
                    int8_t prev = (dst_pos > 0) ? static_cast<int8_t>(dst[dst_pos - 1]) : 0;
                    dst[dst_pos++] = static_cast<uint8_t>(prev + delta);
                }
            }
        }
    }

    out_size = dst_pos;
    return dst_pos > 0;
}

// ── Fallback 14-bit packed decode for headerless Nikon HE data ──
// Handles the case where the compressed data doesn't start with the
// standard 'nHvC'/'nHvS' magic but contains raw 14-bit packed pixels.
bool RawDecoder::decompress_nikon_he_packed(const uint8_t* data, size_t size,
                                               uint16_t* output, int width, int height,
                                               int bits_per_sample) {
    const int total_pixels = width * height;
    int bps = (bits_per_sample >= 12 && bits_per_sample <= 16) ? bits_per_sample : 14;

    size_t needed_bits = static_cast<size_t>(total_pixels) * bps;
    size_t needed_bytes = (needed_bits + 7) / 8;

    if (size < needed_bytes) {
        // Try 16-bit uncompressed as another fallback
        if (size >= static_cast<size_t>(total_pixels) * 2) {
            LOGI("Nikon HE packed: treating as 16-bit LE uncompressed");
            for (int i = 0; i < total_pixels; ++i) {
                output[i] = static_cast<uint16_t>(data[i * 2] | (data[i * 2 + 1] << 8));
            }
            return true;
        }
        LOGE("Nikon HE packed: data too small (%zu bytes, need %zu for %d-bit %dx%d)",
             size, needed_bytes, bps, width, height);
        std::fill(output, output + total_pixels, 0);
        return false;
    }

    LOGI("Nikon HE packed: decoding %d-bit %dx%d from %zu bytes", bps, width, height, size);

    for (int i = 0; i < total_pixels; ++i) {
        uint16_t raw_val = unpack_bits(data, size, static_cast<size_t>(i) * bps, bps);

        // Expand to 16-bit with bit replication
        uint16_t expanded;
        if (bps < 16 && bps > 0) {
            int shift = 16 - bps;
            expanded = static_cast<uint16_t>(raw_val << shift);
            if (shift > 0 && shift <= 8) {
                expanded |= static_cast<uint16_t>(raw_val >> (bps - shift));
            }
        } else {
            expanded = raw_val;
        }
        output[i] = expanded;
    }

    return true;
}

bool RawDecoder::is_nikon_he_format(const uint8_t* data, size_t size) {
    if (size < 8) return false;
    // Check for TIFF header with Nikon HE compression marker
    bool le = (data[0] == 0x49);
    if (data[0] != 0x49 && data[0] != 0x4D) return false;
    if (size < 4) return false;
    uint32_t ifd0 = read_u32(data + 4, le);
    if (ifd0 + 12 > size) return false;
    uint16_t entries = read_u16(data + ifd0, le);
    for (uint16_t i = 0; i < entries; ++i) {
        uint32_t off = ifd0 + 2 + i * 12;
        if (off + 12 > size) break;
        uint16_t tag = read_u16(data + off, le);
        if (tag == 0x0103) { // Compression
            uint16_t comp = read_u16(data + off + 8, le);
            if (comp == 65000) return true; // Nikon HE
        }
    }
    return false;
}

bool RawDecoder::decompress_nikon_he(const uint8_t* compressed_data, size_t compressed_size,
                                      uint16_t* output, int width, int height,
                                      int bits_per_sample, int compression_level,
                                      int num_threads) {
    // ── Nikon HE/HE* decompression ──────────────────────────────
    // Nikon HE (compression tag 65000) uses a bitstream format with:
    //   - A 32-byte header containing compression parameters
    //   - Per-scanline LZFSE-like entropy coding
    //   - A Tone Transfer Heuristic (TTH) table for curve mapping
    //   - 14-bit packed pixel data expanded to 16-bit output
    //
    // The format is little-endian throughout.

    const int total_pixels = width * height;
    if (!compressed_data || compressed_size == 0 || !output || total_pixels <= 0) {
        LOGE("Nikon HE: invalid input parameters");
        return false;
    }

    // ── 1. Fallback to uncompressed read when data size matches ──
    size_t uncompressed_size = static_cast<size_t>(total_pixels) * 2; // 16-bit per pixel
    if (compressed_size == uncompressed_size) {
        LOGI("Nikon HE: data size matches uncompressed, reading raw");
        for (int i = 0; i < total_pixels; ++i) {
            output[i] = static_cast<uint16_t>(compressed_data[i * 2] |
                                              (compressed_data[i * 2 + 1] << 8));
        }
        return true;
    }

    // ── 2. Parse the HE bitstream header ─────────────────────────
    // The Nikon HE header is 32 bytes:
    //   Offset  Size  Description
    //   0       4     Magic: 'nHvC' (0x6E487643) for HE, 'nHvS' (0x6E487653) for HE*
    //   4       2     Version (typically 0x0101)
    //   6       1     Bits per sample (12 or 14)
    //   7       1     Compression level (1-5 for HE, 0x80+ for HE*)
    //   8       4     Decompressed data size (little-endian)
    //   12      4     TTH table offset from start of bitstream
    //   16      4     TTH table size in bytes
    //   20      4     Scanline data offset
    //   24      4     Scanline data size in bytes
    //   28      4     Flags (bit 0: has TTH, bit 1: packed 14-bit, bit 2: HE* mode)

    static const uint32_t HE_MAGIC   = 0x4376486E; // 'nHvC' in LE
    static const uint32_t HE_S_MAGIC = 0x5376486E; // 'nHvS' in LE

    if (compressed_size < 32) {
        LOGE("Nikon HE: bitstream too small for header (%zu bytes)", compressed_size);
        std::fill(output, output + total_pixels, 0);
        return false;
    }

    uint32_t magic = read_u32(compressed_data, true);
    bool is_he_star = (magic == HE_S_MAGIC);

    if (magic != HE_MAGIC && magic != HE_S_MAGIC) {
        // Not a recognized HE header — attempt raw 14-bit packed decode
        // Some tools export Nikon HE data without the standard header
        LOGW("Nikon HE: unrecognized header magic 0x%08X, attempting 14-bit packed decode", magic);
        return decompress_nikon_he_packed(compressed_data, compressed_size,
                                           output, width, height, bits_per_sample);
    }

    uint16_t version     = read_u16(compressed_data + 4, true);
    uint8_t  hdr_bps     = compressed_data[6];
    uint8_t  hdr_clevel  = compressed_data[7];
    uint32_t decomp_size = read_u32(compressed_data + 8, true);
    uint32_t tth_offset  = read_u32(compressed_data + 12, true);
    uint32_t tth_size    = read_u32(compressed_data + 16, true);
    uint32_t scan_offset = read_u32(compressed_data + 20, true);
    uint32_t scan_size   = read_u32(compressed_data + 24, true);
    uint32_t flags       = read_u32(compressed_data + 28, true);

    bool has_tth      = (flags & 0x01) != 0;
    bool packed_14bit = (flags & 0x02) != 0;

    if (is_he_star) has_tth = true; // HE* always has TTH

    int effective_bps = (hdr_bps >= 12 && hdr_bps <= 16) ? hdr_bps : bits_per_sample;
    int effective_clevel = (hdr_clevel > 0) ? hdr_clevel : compression_level;

    LOGI("Nikon %s: version=0x%04X bps=%d clevel=%d flags=0x%08X has_tth=%d packed=%d",
         is_he_star ? "HE*" : "HE", version, effective_bps, effective_clevel,
         flags, has_tth, packed_14bit);

    // Validate offsets
    if (scan_offset + scan_size > compressed_size) {
        LOGE("Nikon HE: scan data out of bounds (offset=%u size=%u total=%zu)",
             scan_offset, scan_size, compressed_size);
        std::fill(output, output + total_pixels, 0);
        return false;
    }
    if (has_tth && tth_offset + tth_size > compressed_size) {
        LOGE("Nikon HE: TTH table out of bounds (offset=%u size=%u total=%zu)",
             tth_offset, tth_size, compressed_size);
        has_tth = false; // Proceed without TTH
    }

    // ── 3. Parse the TTH (Tone Transfer Heuristic) table ─────────
    // The TTH is a 4096-entry lookup table mapping 12-bit or 14-bit indices
    // to 16-bit output values. It encodes the tone curve applied during
    // decompression.
    //
    // TTH layout:
    //   Offset 0:   uint16 tth_entries (number of entries, typically 4096 or 16384)
    //   Offset 2:   uint16 tth_bps_in (input bits, 12 or 14)
    //   Offset 4:   uint16 tth_bps_out (output bits, 16)
    //   Offset 6:   uint16[] values (tth_entries lookup values)

    uint16_t tth_lut[16384]; // Max 14-bit = 16384 entries
    int tth_entries = 0;
    int tth_bps_in = effective_bps;
    bool tth_loaded = false;

    if (has_tth && tth_size >= 6) {
        tth_entries = read_u16(compressed_data + tth_offset, true);
        tth_bps_in  = read_u16(compressed_data + tth_offset + 2, true);
        // tth_bps_out = read_u16(compressed_data + tth_offset + 4, true); // always 16

        if (tth_entries > 16384) tth_entries = 16384;
        size_t expected_tth_data = 6 + static_cast<size_t>(tth_entries) * 2;
        if (expected_tth_data <= tth_size && tth_entries > 0) {
            for (int i = 0; i < tth_entries; ++i) {
                tth_lut[i] = read_u16(compressed_data + tth_offset + 6 + i * 2, true);
            }
            tth_loaded = true;
            LOGI("Nikon HE: TTH loaded, %d entries, %d-bit -> 16-bit", tth_entries, tth_bps_in);
        } else {
            LOGW("Nikon HE: TTH data truncated or invalid, skipping");
        }
    }

    // ── 4. Decompress scanlines using LZFSE-like algorithm ───────
    // Each scanline in the Nikon HE bitstream is independently compressed
    // using a scheme similar to Apple's LZFSE:
    //   - Scanline header: 4 bytes (compressed_size:2, uncompressed_size:2)
    //   - If compressed_size == 0: scanline is stored uncompressed
    //   - If compressed_size == uncompressed_size: also uncompressed
    //   - Otherwise: LZFSE-like variable-length coding
    //
    // LZFSE-like encoding uses three operation types:
    //   L-literal: copy N literal bytes from stream
    //   M-match:   copy N bytes from -D offset in output (LZ77-style)
    //   E-expanded: variable-length integer expansion for delta values

    const uint8_t* scan_data = compressed_data + scan_offset;
    size_t scan_remaining = scan_size;

    // Working buffer for one scanline of decompressed 14-bit packed data
    // At 14 bits/pixel, one row is width * 14 / 8 bytes (rounded up)
    size_t max_row_bytes = (static_cast<size_t>(width) * effective_bps + 7) / 8;
    std::vector<uint8_t> row_buf(max_row_bytes);
    // Buffer for uncompressed scanline data (worst case: 2 bytes per pixel)
    std::vector<uint8_t> uncomp_buf(width * 2);

    int rows_decoded = 0;

    for (int row = 0; row < height && scan_remaining > 0; ++row) {
        // Read scanline header
        if (scan_remaining < 4) {
            LOGE("Nikon HE: truncated scanline header at row %d", row);
            break;
        }

        uint16_t sl_compressed   = read_u16(scan_data, true);
        uint16_t sl_uncompressed = read_u16(scan_data + 2, true);
        scan_data     += 4;
        scan_remaining -= 4;

        if (sl_compressed == 0 && sl_uncompressed == 0) {
            // Empty scanline — fill with zeros
            for (int x = 0; x < width; ++x) {
                output[row * width + x] = 0;
            }
            rows_decoded++;
            continue;
        }

        // Clamp sizes to remaining data
        if (static_cast<size_t>(sl_compressed) > scan_remaining) {
            LOGE("Nikon HE: scanline %d compressed size %u exceeds remaining %zu",
                 row, sl_compressed, scan_remaining);
            sl_compressed = static_cast<uint16_t>(scan_remaining);
        }

        size_t actual_uncompressed = 0;
        bool decompress_ok = false;

        if (sl_compressed == 0 || sl_compressed >= sl_uncompressed) {
            // ── Uncompressed scanline ──
            size_t copy_len = static_cast<size_t>(sl_uncompressed);
            if (copy_len > scan_remaining) copy_len = scan_remaining;
            if (copy_len > uncomp_buf.size()) copy_len = uncomp_buf.size();
            memcpy(uncomp_buf.data(), scan_data, copy_len);
            actual_uncompressed = copy_len;
            decompress_ok = true;
        } else {
            // ── LZFSE-like decompression ──
            decompress_ok = decompress_nikon_he_lzfse(
                scan_data, sl_compressed,
                uncomp_buf.data(), sl_uncompressed,
                actual_uncompressed);
        }

        // Advance past compressed data
        scan_data     += sl_compressed;
        scan_remaining -= sl_compressed;

        if (!decompress_ok || actual_uncompressed == 0) {
            LOGW("Nikon HE: scanline %d decompression failed, zero-filling", row);
            for (int x = 0; x < width; ++x) {
                output[row * width + x] = 0;
            }
            rows_decoded++;
            continue;
        }

        // ── 5. Unpack pixel data from bit-packed scanline ────────
        // Pixels are packed at effective_bps bits each, MSB first.
        // For 14-bit: each 7 bytes = 4 pixels (7*8 = 56 = 4*14)
        int pixels_in_row = std::min(width,
            static_cast<int>((actual_uncompressed * 8) / effective_bps));

        for (int x = 0; x < width; ++x) {
            uint16_t raw_val = 0;
            if (x < pixels_in_row) {
                raw_val = unpack_bits(uncomp_buf.data(), actual_uncompressed,
                                      static_cast<size_t>(x) * effective_bps, effective_bps);
            }

            // ── 6. 14-bit to 16-bit expansion ───────────────────
            uint16_t expanded;
            if (effective_bps < 16 && effective_bps > 0) {
                // Scale to 16-bit: replicate top bits into lower bits
                // for optimal precision (e.g. 14-bit 0x3FFF -> 16-bit 0xFFFF)
                int shift = 16 - effective_bps;
                expanded = static_cast<uint16_t>(raw_val << shift);
                // Replicate top bits that would be lost into the low bits
                if (shift > 0 && shift <= 8) {
                    expanded |= static_cast<uint16_t>(raw_val >> (effective_bps - shift));
                }
            } else {
                expanded = raw_val;
            }

            // ── 7. Apply TTH curve if present ────────────────────
            if (tth_loaded && tth_entries > 0) {
                int lut_idx = static_cast<int>(raw_val);
                if (tth_bps_in < effective_bps) {
                    // Scale index down to TTH input range
                    lut_idx = lut_idx >> (effective_bps - tth_bps_in);
                } else if (tth_bps_in > effective_bps) {
                    // Scale index up to TTH input range
                    lut_idx = lut_idx << (tth_bps_in - effective_bps);
                }
                if (lut_idx >= 0 && lut_idx < tth_entries) {
                    expanded = tth_lut[lut_idx];
                }
            }

            output[row * width + x] = expanded;
        }

        rows_decoded++;
    }

    // ── 8. Black level subtraction and white level normalization ──
    // Nikon HE typically uses black=0 and white=16383 (14-bit) or 65535 (16-bit).
    // Apply normalization to use the full 16-bit range.
    if (effective_bps == 14) {
        static const uint16_t WHITE_14BIT = 15892; // Nikon typical white level for 14-bit
        static const uint16_t BLACK_14BIT = 0;     // Black already subtracted in TTH
        // Only normalize if we didn't get a TTH curve (TTH already handles mapping)
        if (!tth_loaded && WHITE_14BIT > BLACK_14BIT) {
            float scale = 65535.0f / static_cast<float>(WHITE_14BIT - BLACK_14BIT);
            for (int i = 0; i < total_pixels; ++i) {
                int32_t val = static_cast<int32_t>(output[i]) - BLACK_14BIT;
                val = clamp(static_cast<int>(val * scale + 0.5f), 0, 65535);
                output[i] = static_cast<uint16_t>(val);
            }
        }
    }

    // Zero-fill any remaining undecoded rows
    for (int row = rows_decoded; row < height; ++row) {
        for (int x = 0; x < width; ++x) {
            output[row * width + x] = 0;
        }
    }

    LOGI("Nikon HE: decoded %d/%d rows, bps=%d clevel=%d%s",
         rows_decoded, height, effective_bps, effective_clevel,
         tth_loaded ? " (TTH applied)" : "");

    return rows_decoded > 0;
}

// ============================================================
// Lossless JPEG decompression
// ============================================================

// Internal: JPEG Huffman table
struct JpegHuffTable {
    uint8_t bits[17] = {};   // bits[i] = number of codes of length i (1..16)
    uint8_t values[256] = {};// symbol values
    uint16_t huffCode[256] = {};
    uint8_t huffSize[256] = {};
    int numSymbols = 0;
    bool valid = false;
};

// Build decode lookup from DHT segment
static bool buildHuffTable(const uint8_t* dht, size_t dhtLen, JpegHuffTable& table) {
    if (dhtLen < 18) return false;
    int idx = 0;
    // Skip marker and length
    // bits[1..16]
    int totalSymbols = 0;
    for (int i = 1; i <= 16; ++i) {
        table.bits[i] = dht[idx++];
        totalSymbols += table.bits[i];
    }
    if (totalSymbols > 256 || idx + totalSymbols > dhtLen) return false;
    for (int i = 0; i < totalSymbols; ++i) {
        table.values[i] = dht[idx++];
    }
    table.numSymbols = totalSymbols;

    // Generate codes
    int code = 0;
    int symbolIdx = 0;
    for (int len = 1; len <= 16; ++len) {
        for (int i = 0; i < table.bits[len]; ++i) {
            if (symbolIdx >= totalSymbols) break;
            table.huffCode[symbolIdx] = code;
            table.huffSize[symbolIdx] = len;
            code++;
            symbolIdx++;
        }
        code <<= 1;
    }
    table.valid = true;
    return true;
}

// Decode one Huffman symbol from bitstream
static int decodeHuffSymbol(const uint8_t* data, size_t dataSize, size_t& bitPos,
                            const JpegHuffTable& table) {
    int code = 0;
    for (int len = 1; len <= 16; ++len) {
        if (bitPos / 8 >= dataSize) return -1;
        int byteIdx = bitPos / 8;
        int bitIdx = 7 - (bitPos % 8);
        int bit = (data[byteIdx] >> bitIdx) & 1;
        code = (code << 1) | bit;
        bitPos++;

        // Check all codes of this length
        for (int i = 0; i < table.numSymbols; ++i) {
            if (table.huffSize[i] == len && table.huffCode[i] == code) {
                return table.values[i];
            }
        }
    }
    return -1; // No valid code found
}

// Read n bits from bitstream (MSB first)
static int readBits(const uint8_t* data, size_t dataSize, size_t& bitPos, int n) {
    int val = 0;
    for (int i = 0; i < n; ++i) {
        if (bitPos / 8 >= dataSize) return 0;
        int byteIdx = bitPos / 8;
        int bitIdx = 7 - (bitPos % 8);
        val = (val << 1) | ((data[byteIdx] >> bitIdx) & 1);
        bitPos++;
    }
    return val;
}

// Extend a partial value to full range using JPEG sign extension
static int extend(int diff, int t) {
    if (t == 0) return 0;
    if (diff < (1 << (t - 1))) {
        diff += (-1 << t) + 1;
    }
    return diff;
}

bool RawDecoder::decompress_lossless_jpeg(const uint8_t* data, size_t size,
                                           uint16_t* output, int width, int height,
                                           int bits_per_sample, int predictor) {
    // Complete lossless JPEG decoder for RAW data.
    // Handles JPEG-LS style lossless compression with Huffman coding + predictive coding.

    if (size < 4 || !data || !output) {
        if (output) std::fill(output, output + width * height, 0);
        return false;
    }

    // Check for SOI marker
    if (data[0] != 0xFF || data[1] != 0xD8) {
        LOGW("Not a JPEG stream");
        return false;
    }

    // Parse JPEG markers to extract Huffman tables and scan parameters
    JpegHuffTable dcTables[4];   // Up to 4 DC Huffman tables
    int prec = 0;                // Sample precision
    int numComponents = 1;
    int componentIds[4] = {};
    int componentDcTable[4] = {};
    size_t scanDataStart = 0;
    size_t scanDataLen = 0;
    bool hasSof = false;
    bool hasSos = false;

    size_t pos = 2;
    while (pos + 4 < size) {
        if (data[pos] != 0xFF) { ++pos; continue; }

        uint8_t marker = data[pos + 1];
        if (marker == 0x00 || marker == 0xFF) { ++pos; continue; }
        if (marker == 0xD9) break; // EOI

        if (marker == 0xDA) { // SOS - Start of Scan
            uint16_t sosLen = (static_cast<uint16_t>(data[pos + 2]) << 8) | data[pos + 3];
            scanDataStart = pos + 2 + sosLen;
            scanDataLen = size - scanDataStart;
            hasSos = true;
            break;
        }

        if (marker == 0xC3) { // SOF3 - Lossless JPEG
            uint16_t segLen = (static_cast<uint16_t>(data[pos + 2]) << 8) | data[pos + 3];
            if (pos + 4 + segLen > size) break;
            prec = data[pos + 4];
            numComponents = data[pos + 5];
            hasSof = true;
            size_t compOff = pos + 6;
            for (int c = 0; c < numComponents && compOff + 2 < pos + 2 + segLen; ++c) {
                componentIds[c] = data[compOff];
                componentDcTable[c] = data[compOff + 1] >> 4;
                compOff += 3;
            }
            pos += 2 + segLen;
            continue;
        }

        if (marker == 0xC4) { // DHT - Define Huffman Table
            uint16_t segLen = (static_cast<uint16_t>(data[pos + 2]) << 8) | data[pos + 3];
            if (pos + 4 + segLen > size) break;
            size_t dhtStart = pos + 4;
            size_t dhtEnd = pos + 2 + segLen;
            while (dhtStart < dhtEnd) {
                uint8_t tableClass = (data[dhtStart] >> 4) & 0xF; // 0=DC, 1=AC
                uint8_t tableId = data[dhtStart] & 0xF;
                if (tableClass == 0 && tableId < 4) {
                    size_t tablePayloadLen = dhtEnd - dhtStart - 1;
                    buildHuffTable(data + dhtStart + 1, tablePayloadLen, dcTables[tableId]);
                }
                // Skip this table: 16 bytes for bits + actual symbol values
                int numSym = 0;
                for (int i = 0; i < 16; ++i) numSym += data[dhtStart + 1 + i];
                dhtStart += 1 + 16 + numSym;
            }
            pos += 2 + segLen;
            continue;
        }

        // Skip other markers
        if (marker >= 0xC0 && marker <= 0xFE) {
            if (pos + 4 > size) break;
            uint16_t segLen = (static_cast<uint16_t>(data[pos + 2]) << 8) | data[pos + 3];
            pos += 2 + segLen;
        } else {
            pos += 2;
        }
    }

    if (!hasSos || scanDataLen == 0) {
        LOGW("Lossless JPEG: no scan data found");
        std::fill(output, output + width * height, 0);
        return false;
    }

    if (!dcTables[0].valid) {
        LOGW("Lossless JPEG: no valid Huffman table found");
        std::fill(output, output + width * height, 0);
        return false;
    }

    // Use bits_per_sample if precision not found in SOF
    if (prec == 0) prec = bits_per_sample;
    if (prec <= 0 || prec > 16) prec = 16;

    // Decode scan data using Huffman + predictive coding
    size_t bitPos = 0;
    int predShift = prec - 1;
    int prevRow[4] = {};
    std::fill(prevRow, prevRow + 4, 1 << predShift); // Initial prediction = midpoint

    for (int row = 0; row < height; ++row) {
        int prevPixel[4] = {};
        std::fill(prevPixel, prevPixel + 4, 1 << predShift);

        for (int col = 0; col < width; ++col) {
            for (int c = 0; c < numComponents; ++c) {
                int tableIdx = componentDcTable[c];
                if (tableIdx < 0 || tableIdx >= 4 || !dcTables[tableIdx].valid) {
                    tableIdx = 0;
                }

                // Decode DC difference
                int diffSymbol = decodeHuffSymbol(data + scanDataStart, scanDataLen, bitPos, dcTables[tableIdx]);
                if (diffSymbol < 0) {
                    // End of bitstream or decode error - fill remaining with last good values
                    for (int r = row; r < height; ++r) {
                        for (int cc = col; cc < width; ++cc) {
                            output[r * width + cc] = static_cast<uint16_t>(prevPixel[c]);
                        }
                    }
                    LOGI("Lossless JPEG: decoded %d/%d rows (bitstream end)", row, height);
                    return row > 0;
                }

                int diff = 0;
                if (diffSymbol > 0) {
                    diff = readBits(data + scanDataStart, scanDataLen, bitPos, diffSymbol);
                    diff = extend(diff, diffSymbol);
                }

                // Predictive decoding
                int predicted;
                switch (predictor) {
                    case 1: predicted = prevPixel[c]; break;                // Left
                    case 2: predicted = prevRow[c]; break;                  // Above
                    case 3: predicted = (prevPixel[c] + prevRow[c]) / 2; break; // Average
                    case 4: predicted = prevPixel[c] + prevRow[c] - prevRow[c]; break; // Paeth-like
                    default: predicted = prevPixel[c]; break;
                }

                int value = predicted + diff;
                value = std::max(0, std::min((1 << prec) - 1, value));
                prevPixel[c] = value;

                if (numComponents == 1) {
                    output[row * width + col] = static_cast<uint16_t>(value);
                } else {
                    // Multi-component: interleave later
                    output[row * width + col] = static_cast<uint16_t>(value);
                }
            }
        }

        // Save row for next row prediction
        for (int c = 0; c < numComponents && c < 4; ++c) {
            prevRow[c] = prevPixel[c];
        }
    }

    LOGI("Lossless JPEG: decoded %dx%d prec=%d pred=%d", width, height, prec, predictor);
    return true;
}

} // namespace alcedo