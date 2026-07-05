#include "raw_decoder.h"
#include <fstream>
#include <cstring>
#include <cmath>
#include <algorithm>
#include <thread>
#include <android/log.h>

#if __ANDROID_API__ >= 21
#include <media/NdkMediaCodec.h>
#include <media/NdkMediaFormat.h>
#define ALCEDO_HAS_MEDIACODEC 1
#else
#define ALCEDO_HAS_MEDIACODEC 0
#endif

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

bool RawDecoder::is_nikon_he_format(const uint8_t* data, size_t size) {
    if (size < 8) return false;
    // Check for TIFF header with Nikon HE compression marker (comp tag 65000)
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
    // Nikon HE (High Efficiency) uses HEVC-based compression (comp tag 65000).
    // Attempt hardware HEVC decoding via NDK MediaCodec, with fallback to
    // uncompressed read if MediaCodec is unavailable or decoding fails.

#if ALCEDO_HAS_MEDIACODEC
    LOGI("Nikon HE: attempting NDK MediaCodec HEVC decode (%dx%d, %d bps)",
         width, height, bits_per_sample);

    // Create HEVC decoder
    AMediaCodec* codec = AMediaCodec_createDecoderByType("video/hevc");
    if (codec) {
        AMediaFormat* format = AMediaFormat_new();
        AMediaFormat_setString(format, AMEDIAFORMAT_KEY_MIME, "video/hevc");
        AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_WIDTH, width);
        AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_HEIGHT, height);

        media_status_t status = AMediaCodec_configure(codec, format, nullptr, nullptr, 0);
        AMediaFormat_delete(format);

        if (status == AMEDIA_OK) {
            status = AMediaCodec_start(codec);
            if (status == AMEDIA_OK) {
                // Feed compressed data as input buffer
                ssize_t inputIdx = AMediaCodec_dequeueInputBuffer(codec, 5000000); // 5s timeout
                if (inputIdx >= 0) {
                    size_t bufSize = 0;
                    uint8_t* inputBuf = AMediaCodec_getInputBuffer(codec, inputIdx, &bufSize);
                    if (inputBuf && bufSize >= compressed_size) {
                        memcpy(inputBuf, compressed_data, compressed_size);
                        AMediaCodec_queueInputBuffer(codec, inputIdx, 0, compressed_size,
                                                     0, 0); // pts=0, flags=0
                    } else {
                        AMediaCodec_queueInputBuffer(codec, inputIdx, 0, 0, 0, 0);
                    }
                }

                // Signal end of stream
                ssize_t eosIdx = AMediaCodec_dequeueInputBuffer(codec, 5000000);
                if (eosIdx >= 0) {
                    AMediaCodec_queueInputBuffer(codec, eosIdx, 0, 0, 0,
                                                 AMEDIACODEC_BUFFER_FLAG_EOS);
                }

                // Dequeue decoded output
                AMediaCodecBufferInfo info;
                ssize_t outputIdx = AMediaCodec_dequeueOutputBuffer(codec, &info, 5000000);
                if (outputIdx >= 0) {
                    size_t outSize = 0;
                    uint8_t* outBuf = AMediaCodec_getOutputBuffer(codec, outputIdx, &outSize);
                    if (outBuf && outSize >= static_cast<size_t>(width * height * 2)) {
                        // Convert decoded bytes to uint16_t RAW data
                        // HEVC output is typically YUV420; for Nikon HE the TTH
                        // (Tone Transfer Heuristic) maps pixel values back to linear RAW.
                        // Here we read the decoded output as 16-bit samples.
                        for (int i = 0; i < width * height; ++i) {
                            output[i] = (outBuf[i * 2] << 8) | outBuf[i * 2 + 1];
                        }
                        AMediaCodec_releaseOutputBuffer(codec, outputIdx, false);
                        AMediaCodec_stop(codec);
                        AMediaCodec_delete(codec);
                        LOGI("Nikon HE: MediaCodec decode succeeded");
                        return true;
                    }
                    AMediaCodec_releaseOutputBuffer(codec, outputIdx, false);
                }

                AMediaCodec_stop(codec);
            } else {
                LOGW("Nikon HE: AMediaCodec_start failed (%d)", status);
            }
        } else {
            LOGW("Nikon HE: AMediaCodec_configure failed (%d)", status);
        }
        AMediaCodec_delete(codec);
    } else {
        LOGW("Nikon HE: HEVC decoder not available on this device");
    }

    LOGW("Nikon HE: MediaCodec decode failed, falling back to uncompressed read");
#else
    LOGW("Nikon HE: NDK MediaCodec unavailable (API < 21), falling back to uncompressed read");
#endif

    // Fallback: treat as uncompressed if possible
    if (compressed_size >= static_cast<size_t>(width * height * 2)) {
        for (int i = 0; i < width * height && i * 2 < static_cast<int>(compressed_size); ++i) {
            output[i] = (compressed_data[i * 2] << 8) | compressed_data[i * 2 + 1];
        }
        return true;
    }

    // Cannot decode without MediaCodec and data is too small for uncompressed
    std::fill(output, output + width * height, 0);
    return false;
}

// ============================================================
// Lossless JPEG Huffman decoder (ITU-T T.81, SOF3/SOF7)
// ============================================================

namespace {

// Huffman table for Lossless JPEG (only DC/class-0 tables are used).
struct LosslessHuffmanTable {
    uint8_t lengths[16] = {0};        // number of codes of each length (1..16)
    std::vector<uint8_t> symbols;     // symbol values, in canonical order
    std::vector<uint16_t> codes;      // canonical code per symbol
    std::vector<int> codeLens;        // length per symbol (parallel to codes)
    bool valid = false;
};

// Build canonical Huffman codes from the (lengths, symbols) arrays per the
// JPEG spec (ITU-T T.81 Annex C): codes are assigned in increasing order of
// length, with the first code of each length being (prev_last_code + 1) << 1.
void buildHuffmanCodes(LosslessHuffmanTable& t) {
    const size_t n = t.symbols.size();
    t.codes.assign(n, 0);
    t.codeLens.assign(n, 0);
    uint32_t code = 0;
    size_t idx = 0;
    for (int len = 1; len <= 16; ++len) {
        int count = t.lengths[len - 1];
        for (int i = 0; i < count; ++i) {
            if (idx >= n) break;
            t.codes[idx] = static_cast<uint16_t>(code);
            t.codeLens[idx] = len;
            ++code;
            ++idx;
        }
        code <<= 1;
    }
    t.valid = (n > 0);
}

// Read a single bit (MSB-first within each byte) from a bitstream.
inline int readBit(const uint8_t* data, size_t size, size_t& bitOffset) {
    size_t bytePos = bitOffset >> 3;
    if (bytePos >= size) return 0;
    int bit = (data[bytePos] >> (7 - (bitOffset & 7))) & 1;
    ++bitOffset;
    return bit;
}

// Decode one Huffman symbol. Returns the symbol (SSSS for lossless JPEG) or
// -1 if no matching code was found.
int decodeSymbol(const uint8_t* data, size_t size, size_t& bitOffset,
                 const LosslessHuffmanTable& t) {
    if (!t.valid) return -1;
    uint32_t code = 0;
    size_t idx = 0;
    for (int len = 1; len <= 16; ++len) {
        code = (code << 1) | readBit(data, size, bitOffset);
        int count = t.lengths[len - 1];
        for (int i = 0; i < count; ++i) {
            if (idx >= t.codes.size()) return -1;
            if (code == t.codes[idx]) {
                return t.symbols[idx];
            }
            ++idx;
        }
    }
    return -1;
}

// Decode the magnitude bits for a Lossless JPEG difference value.
// SSSS = number of additional bits. If the high bit is 0, the value is
// negative; otherwise the value is the positive magnitude directly.
int decodeDiff(const uint8_t* data, size_t size, size_t& bitOffset, int ssss) {
    if (ssss == 0) return 0;
    int value = 0;
    for (int i = 0; i < ssss; ++i) {
        value = (value << 1) | readBit(data, size, bitOffset);
    }
    if (value < (1 << (ssss - 1))) {
        value -= (1 << ssss) - 1;
    }
    return value;
}

// Compute the predictor per ITU-T T.81 Annex H:
//   1 = Ra (left),          2 = Rb (above),
//   3 = Rc (upper-left),    4 = Ra + Rb - Rc,
//   5 = Ra + ((Rb - Rc) >> 1),
//   6 = Rb + ((Ra - Rc) >> 1),
//   7 = (Ra + Rb) >> 1
inline int computePredictor(int pred, int ra, int rb, int rc) {
    switch (pred) {
        case 1: return ra;
        case 2: return rb;
        case 3: return rc;
        case 4: return ra + rb - rc;
        case 5: return ra + ((rb - rc) >> 1);
        case 6: return rb + ((ra - rc) >> 1);
        case 7: return (ra + rb) >> 1;
        default: return ra;
    }
}

} // namespace

bool RawDecoder::decompress_lossless_jpeg(const uint8_t* data, size_t size,
                                           uint16_t* output, int width, int height,
                                           int bits_per_sample, int predictor) {
    // Full Lossless JPEG decoder (ITU-T T.81). Parses the DHT (Define Huffman
    // Table) and SOS (Start of Scan) markers, then decodes the prediction
    // residuals for each pixel using the selected predictor.
    const size_t outCount = static_cast<size_t>(width) * height;
    if (size < 2 || data[0] != 0xFF || data[1] != 0xD8) {
        LOGW("Lossless JPEG: invalid SOI");
        std::fill(output, output + outCount, 0);
        return false;
    }

    LosslessHuffmanTable dcTables[4];      // up to 4 components
    int componentDcTable[4] = {0, 0, 0, 0};
    int precision = bits_per_sample;
    int scanPredictor = predictor;          // Ss field in SOS
    int pointTransform = 0;                  // Al field in SOS (low 4 bits)

    size_t pos = 2;
    size_t scanStart = 0;
    bool foundSos = false;

    while (pos + 1 < size) {
        if (data[pos] != 0xFF) { ++pos; continue; }
        // Collapse runs of 0xFF fill bytes.
        while (pos + 1 < size && data[pos + 1] == 0xFF) ++pos;
        if (pos + 1 >= size) break;
        uint8_t marker = data[pos + 1];

        if (marker == 0xD9) break;                       // EOI
        if (marker == 0xDA) {                             // SOS
            if (pos + 4 > size) break;
            uint16_t segLen = (data[pos + 2] << 8) | data[pos + 3];
            size_t sosEnd = pos + 2 + segLen;
            if (sosEnd > size) break;
            uint8_t ns = data[pos + 4];                    // number of components
            for (int i = 0; i < ns && i < 4; ++i) {
                size_t base = pos + 5 + i * 2;
                if (base + 1 >= size) break;
                componentDcTable[i] = (data[base + 1] >> 4) & 0x0F;
            }
            if (pos + 5 + ns * 2 + 2 < size) {
                scanPredictor = data[pos + 5 + ns * 2];    // Ss
                pointTransform = data[pos + 5 + ns * 2 + 2] & 0x0F;  // Al
            }
            scanStart = sosEnd;
            foundSos = true;
            break;
        }
        if (marker == 0xC4) {                             // DHT
            if (pos + 4 > size) break;
            uint16_t segLen = (data[pos + 2] << 8) | data[pos + 3];
            size_t dhtEnd = pos + 2 + segLen;
            if (dhtEnd > size) dhtEnd = size;
            size_t p = pos + 4;
            while (p < dhtEnd) {
                uint8_t tableInfo = data[p++];
                int tableClass = (tableInfo >> 4) & 0x0F;
                int tableId = tableInfo & 0x0F;
                if (p + 16 > dhtEnd) break;
                int totalSymbols = 0;
                for (int i = 0; i < 16; ++i) totalSymbols += data[p + i];
                if (p + 16 + totalSymbols > dhtEnd) break;
                // Lossless JPEG only uses DC (class 0) tables.
                if (tableClass == 0 && tableId >= 0 && tableId <= 3) {
                    LosslessHuffmanTable& t = dcTables[tableId];
                    for (int i = 0; i < 16; ++i) t.lengths[i] = data[p + i];
                    t.symbols.assign(data + p + 16, data + p + 16 + totalSymbols);
                    buildHuffmanCodes(t);
                }
                p += 16 + totalSymbols;
            }
            pos = dhtEnd;
            continue;
        }
        if (marker == 0xC3 || marker == 0xC7 || marker == 0xCB || marker == 0xCF) {
            // SOF3 (lossless Huffman), SOF7 (lossless, extended),
            // SOF11/SOF15 (lossless arithmetic).
            if (pos + 8 > size) break;
            uint16_t segLen = (data[pos + 2] << 8) | data[pos + 3];
            size_t sofEnd = pos + 2 + segLen;
            if (sofEnd > size) sofEnd = size;
            precision = data[pos + 4];
            pos = sofEnd;
            continue;
        }
        if (marker == 0x00 || marker == 0xFF || (marker >= 0xD0 && marker <= 0xD7)) {
            pos += 2;
            continue;
        }
        // All other markers: skip the segment.
        if (pos + 4 > size) break;
        uint16_t segLen = (data[pos + 2] << 8) | data[pos + 3];
        pos += 2 + segLen;
    }

    if (!foundSos || scanStart >= size) {
        LOGW("Lossless JPEG: SOS not found");
        std::fill(output, output + outCount, 0);
        return false;
    }
    if (!dcTables[componentDcTable[0]].valid) {
        LOGW("Lossless JPEG: required Huffman table (id=%d) not present",
             componentDcTable[0]);
        std::fill(output, output + outCount, 0);
        return false;
    }

    // De-stuff the scan data: remove 0xFF00 stuffing and skip RST/EOI markers
    // so the bit reader can index bytes linearly.
    std::vector<uint8_t> scanData;
    scanData.reserve(size - scanStart);
    for (size_t i = scanStart; i < size; ++i) {
        uint8_t b = data[i];
        if (b == 0xFF && i + 1 < size) {
            uint8_t next = data[i + 1];
            if (next == 0x00) { ++i; continue; }            // stuffed 0xFF
            if (next >= 0xD0 && next <= 0xD7) { ++i; continue; }  // RST marker
            if (next == 0xD9) break;                       // EOI
            break;                                          // any other marker ends scan
        }
        scanData.push_back(b);
    }

    const int maxVal = (1 << precision) - 1;
    const int shift = pointTransform;
    size_t bitOffset = 0;
    bool ok = true;

    for (int row = 0; row < height; ++row) {
        for (int col = 0; col < width; ++col) {
            const LosslessHuffmanTable& tbl = dcTables[componentDcTable[0]];
            int ssss = decodeSymbol(scanData.data(), scanData.size(), bitOffset, tbl);
            if (ssss < 0) {
                output[row * width + col] = 0;
                ok = false;
                continue;
            }
            int diff = decodeDiff(scanData.data(), scanData.size(), bitOffset, ssss);

            int predReduced;
            if (row == 0 && col == 0) {
                // First sample uses 2^(P-Pt-1) as the prediction.
                predReduced = 1 << (precision - shift - 1);
            } else {
                int ra = (col > 0) ? output[row * width + col - 1] : 0;
                int rb = (row > 0) ? output[(row - 1) * width + col] : 0;
                int rc = (row > 0 && col > 0) ? output[(row - 1) * width + col - 1] : 0;
                // First column uses Rb (above); first row uses Ra (left).
                int predFull;
                if (col == 0)       predFull = rb;
                else if (row == 0)  predFull = ra;
                else                predFull = computePredictor(scanPredictor, ra, rb, rc);
                predReduced = (shift > 0) ? (predFull >> shift) : predFull;
            }

            int value = (predReduced + diff) << shift;
            if (value < 0) value = 0;
            if (value > maxVal) value = maxVal;
            output[row * width + col] = static_cast<uint16_t>(value);
        }
    }

    if (!ok) {
        LOGW("Lossless JPEG: partial decode (some pixels defaulted to 0)");
    } else {
        LOGI("Lossless JPEG decoded: %dx%d precision=%d predictor=%d Pt=%d",
             width, height, precision, scanPredictor, shift);
    }
    return true;
}

} // namespace alcedo