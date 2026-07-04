#include "metadata_decoder.h"
#include <fstream>
#include <cstring>
#include <sstream>
#include <cmath>
#include <algorithm>
#include <android/log.h>

#define LOG_TAG "AlcedoMetaDec"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace alcedo {

// ── Internal helpers ──

inline uint16_t MetadataDecoder::read_u16(const uint8_t* d, bool le) {
    return le ? (d[0] | (d[1] << 8)) : ((d[0] << 8) | d[1]);
}
inline uint32_t MetadataDecoder::read_u32(const uint8_t* d, bool le) {
    return le ? (d[0] | (d[1]<<8) | (d[2]<<16) | (d[3]<<24))
              : ((d[0]<<24) | (d[1]<<16) | (d[2]<<8) | d[3]);
}
inline float MetadataDecoder::read_rational(const uint8_t* d, bool le) {
    uint32_t n = read_u32(d, le), den = read_u32(d + 4, le);
    return den ? static_cast<float>(n) / den : 0.0f;
}
inline float MetadataDecoder::read_srational(const uint8_t* d, bool le) {
    int32_t n = static_cast<int32_t>(read_u32(d, le));
    int32_t den = static_cast<int32_t>(read_u32(d + 4, le));
    return den ? static_cast<float>(n) / den : 0.0f;
}
inline std::string MetadataDecoder::read_string(const uint8_t* d, size_t max_len) {
    std::string s;
    for (size_t i = 0; i < max_len && d[i] != 0; ++i) s += static_cast<char>(d[i]);
    return s;
}

MetadataDecoder::MetadataDecoder() = default;
MetadataDecoder::~MetadataDecoder() = default;

void MetadataDecoder::report_progress(float p, const std::string& stage) {
    if (progress_cb_) progress_cb_(p, stage);
}

// ── TIFF parsing ──

bool MetadataDecoder::parse_tiff_header(const uint8_t* data, size_t size, TiffCtx& ctx) {
    if (size < 8) return false;
    ctx.data = data; ctx.size = size;
    if (data[0] == 0x49 && data[1] == 0x49) ctx.little_endian = true;
    else if (data[0] == 0x4D && data[1] == 0x4D) ctx.little_endian = false;
    else return false;
    return read_u16(data + 2, ctx.little_endian) == 0x002A;
}

bool MetadataDecoder::read_ifd_entry(const TiffCtx& ctx, uint32_t ifd_offset, uint16_t idx,
                                      uint16_t& tag, uint16_t& type, uint32_t& count,
                                      uint32_t& value_offset, const uint8_t*& value_ptr) {
    uint32_t off = ifd_offset + 2 + idx * 12;
    if (off + 12 > ctx.size) return false;
    tag = read_u16(ctx.data + off, ctx.little_endian);
    type = read_u16(ctx.data + off + 2, ctx.little_endian);
    count = read_u32(ctx.data + off + 4, ctx.little_endian);
    uint32_t vsize;
    switch (type) {
        case 1: case 2: case 6: case 7: vsize = 1; break;
        case 3: case 8: vsize = 2; break;
        case 4: case 9: case 11: vsize = 4; break;
        case 5: case 10: case 12: vsize = 8; break;
        default: vsize = 4; break;
    }
    uint32_t total = count * vsize;
    if (total <= 4) {
        value_offset = off + 8;
        value_ptr = ctx.data + off + 8;
    } else {
        value_offset = read_u32(ctx.data + off + 8, ctx.little_endian);
        value_ptr = ctx.data + value_offset;
    }
    return (value_offset + total <= ctx.size);
}

// ── File type detection ──

std::string MetadataDecoder::detect_file_type(const uint8_t* data, size_t size) {
    if (!data || size < 4) return "unknown";
    if (data[0] == 0xFF && data[1] == 0xD8) return "JPEG";
    if (data[0] == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G') return "PNG";
    if (data[0] == 0x49 && data[1] == 0x49 && read_u16(data + 2, true) == 0x002A) return "TIFF";
    if (data[0] == 0x4D && data[1] == 0x4D && read_u16(data + 2, false) == 0x002A) return "TIFF";
    if (data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F') return "RIFF";
    if (data[0] == 'B' && data[1] == 'M') return "BMP";
    return "unknown";
}

std::string MetadataDecoder::detect_file_type(const std::string& path) {
    std::ifstream f(path, std::ios::binary);
    if (!f) return "unknown";
    uint8_t hdr[16] = {};
    f.read(reinterpret_cast<char*>(hdr), 16);
    return detect_file_type(hdr, 16);
}

// ── Top-level decode ──

bool MetadataDecoder::decode(const std::string& file_path, DecodedMetadata& metadata) {
    std::ifstream f(file_path, std::ios::binary | std::ios::ate);
    if (!f) {
        LOGE("Cannot open: %s", file_path.c_str());
        return false;
    }
    metadata.file_path = file_path;
    metadata.file_size = f.tellg();
    f.seekg(0);
    std::vector<uint8_t> buf(metadata.file_size);
    f.read(reinterpret_cast<char*>(buf.data()), metadata.file_size);
    f.close();
    return decode_from_memory(buf.data(), metadata.file_size, metadata);
}

bool MetadataDecoder::decode_from_memory(const uint8_t* data, size_t size, DecodedMetadata& metadata) {
    if (!data || size < 4) return false;
    metadata.file_type = detect_file_type(data, size);
    report_progress(0.05f, "Detecting file type");

    parse_exif_from_memory(data, size, metadata);
    report_progress(0.3f, "EXIF parsed");

    parse_xmp_from_memory(data, size, metadata);
    report_progress(0.5f, "XMP parsed");

    parse_icc_profile_from_memory(data, size, metadata);
    report_progress(0.6f, "ICC parsed");

    parse_maker_note_from_memory(data, size, metadata);
    report_progress(0.8f, "MakerNote parsed");

    parse_dng_color_from_memory(data, size, metadata);
    report_progress(0.95f, "DNG color parsed");

    metadata.has_metadata = metadata.has_exif || metadata.has_xmp || metadata.has_icc;
    report_progress(1.0f, "Done");
    return metadata.has_metadata;
}

bool MetadataDecoder::decode_with_progress(const std::string& file_path, DecodedMetadata& metadata,
                                            MetadataProgressCallback progress_cb) {
    progress_cb_ = std::move(progress_cb);
    return decode(file_path, metadata);
}

// ── EXIF parsing ──

bool MetadataDecoder::parse_exif(const std::string& file_path, DecodedMetadata& metadata) {
    std::ifstream f(file_path, std::ios::binary | std::ios::ate);
    if (!f) return false;
    size_t sz = f.tellg(); f.seekg(0);
    std::vector<uint8_t> buf(sz);
    f.read(reinterpret_cast<char*>(buf.data()), sz);
    return parse_exif_from_memory(buf.data(), sz, metadata);
}

bool MetadataDecoder::parse_exif_from_memory(const uint8_t* data, size_t size, DecodedMetadata& metadata) {
    if (!data || size < 8) return false;

    // JPEG: find EXIF APP1 marker
    if (data[0] == 0xFF && data[1] == 0xD8) {
        size_t off = 2;
        while (off + 4 < size) {
            if (data[off] == 0xFF) {
                uint8_t m = data[off + 1];
                if (m == 0xE1) {
                    uint16_t slen = (data[off + 2] << 8) | data[off + 3];
                    if (off + 2 + slen <= size && slen > 10) {
                        const uint8_t* exif = data + off + 4;
                        if (exif[0] == 'E' && exif[1] == 'x' && exif[2] == 'i' &&
                            exif[3] == 'f' && exif[4] == 0 && exif[5] == 0) {
                            TiffCtx ctx;
                            if (parse_tiff_header(exif + 6, slen - 8, ctx)) {
                                uint32_t ifd0 = read_u32(exif + 6 + 4, ctx.little_endian);
                                parse_ifd0(ctx, ifd0, metadata);
                                metadata.has_exif = true;
                            }
                        }
                    }
                    break;
                } else if (m == 0xDA) break;
                if (m >= 0xC0 && m != 0xC4 && m != 0xC8 && m != 0xCC)
                    off += 2 + ((data[off + 2] << 8) | data[off + 3]);
                else off += 2;
            } else ++off;
        }
    }
    // TIFF/DNG
    else if ((data[0] == 0x49 || data[0] == 0x4D) && read_u16(data + 2, data[0] == 0x49) == 0x002A) {
        TiffCtx ctx;
        if (parse_tiff_header(data, size, ctx)) {
            parse_ifd0(ctx, read_u32(data + 4, ctx.little_endian), metadata);
            metadata.has_exif = true;
        }
    }
    return metadata.has_exif;
}

void MetadataDecoder::parse_ifd0(const TiffCtx& ctx, uint32_t ifd_offset, DecodedMetadata& meta) {
    if (ifd_offset + 2 > ctx.size) return;
    uint16_t n = read_u16(ctx.data + ifd_offset, ctx.little_endian);

    for (uint16_t i = 0; i < n; ++i) {
        uint16_t tag, type; uint32_t count, voff; const uint8_t* vp;
        if (!read_ifd_entry(ctx, ifd_offset, i, tag, type, count, voff, vp)) continue;

        // Store raw tag
        if (type == 2) meta.exif_tags[tag] = read_string(vp, std::min(count, 256u));
        else {
            char buf[64];
            if (type == 3) snprintf(buf, sizeof(buf), "%u", read_u16(vp, ctx.little_endian));
            else if (type == 4) snprintf(buf, sizeof(buf), "%u", read_u32(vp, ctx.little_endian));
            else if (type == 5) snprintf(buf, sizeof(buf), "%.4f", read_rational(vp, ctx.little_endian));
            else snprintf(buf, sizeof(buf), "type=%u", type);
            meta.exif_tags[tag] = buf;
        }

        switch (tag) {
            case 0x010F: meta.camera.make = read_string(vp, std::min(count, 64u)); break;
            case 0x0110: meta.camera.model = read_string(vp, std::min(count, 64u)); break;
            case 0x0131: meta.image.software = read_string(vp, std::min(count, 64u)); break;
            case 0x0132: meta.image.date_time_modified = read_string(vp, std::min(count, 20u)); break;
            case 0x013B: meta.image.artist = read_string(vp, std::min(count, 64u)); break;
            case 0x8298: meta.image.copyright = read_string(vp, std::min(count, 128u)); break;
            case 0x0100: meta.image.width = (type == 3) ? read_u16(vp, ctx.little_endian) : read_u32(vp, ctx.little_endian); break;
            case 0x0101: meta.image.height = (type == 3) ? read_u16(vp, ctx.little_endian) : read_u32(vp, ctx.little_endian); break;
            case 0x0112: meta.image.orientation = read_u16(vp, ctx.little_endian); break;
            case 0x0102: meta.image.bits_per_sample = read_u16(vp, ctx.little_endian); break;
            case 0x0115: meta.image.samples_per_pixel = read_u16(vp, ctx.little_endian); break;
            case 0x011A: meta.image.x_resolution = read_rational(vp, ctx.little_endian); break;
            case 0x011B: meta.image.y_resolution = read_rational(vp, ctx.little_endian); break;
            case 0x0128: meta.image.resolution_unit = read_u16(vp, ctx.little_endian); break;
            case 0x8769: { // EXIF IFD
                uint32_t exif_off = read_u32(vp, ctx.little_endian);
                parse_exif_ifd(ctx, exif_off, meta);
                break;
            }
            case 0x8825: { // GPS IFD
                uint32_t gps_off = read_u32(vp, ctx.little_endian);
                parse_gps_ifd(ctx, gps_off, meta);
                break;
            }
            case 0x927C: { // MakerNote
                // Save for later parsing
                if (count > 0) {
                    meta.maker_note.raw_data_hex.resize(count * 2);
                    for (uint32_t j = 0; j < std::min(count, 8192u); ++j) {
                        snprintf(&meta.maker_note.raw_data_hex[j * 2], 3, "%02X", vp[j]);
                    }
                    meta.maker_note.camera_maker = meta.camera.make;
                    parse_maker_note_internal(ctx, vp, std::min(count, 8192u), meta);
                }
                break;
            }
            default: break;
        }
    }
}

void MetadataDecoder::parse_exif_ifd(const TiffCtx& ctx, uint32_t ifd_offset, DecodedMetadata& meta) {
    if (ifd_offset + 2 > ctx.size) return;
    uint16_t n = read_u16(ctx.data + ifd_offset, ctx.little_endian);

    for (uint16_t i = 0; i < n; ++i) {
        uint16_t tag, type; uint32_t count, voff; const uint8_t* vp;
        if (!read_ifd_entry(ctx, ifd_offset, i, tag, type, count, voff, vp)) continue;

        switch (tag) {
            case 0x829A: // ExposureTime
                meta.exposure.shutter_speed = read_rational(vp, ctx.little_endian);
                break;
            case 0x829D: // FNumber
                meta.exposure.aperture = read_rational(vp, ctx.little_endian);
                break;
            case 0x8827: // ISO
                if (type == 3) meta.exposure.iso = read_u16(vp, ctx.little_endian);
                else if (type == 4) meta.exposure.iso = static_cast<float>(read_u32(vp, ctx.little_endian));
                break;
            case 0x9204: // ExposureBias
                meta.exposure.exposure_bias = read_srational(vp, ctx.little_endian);
                break;
            case 0x8822: // ExposureProgram
                meta.exposure.exposure_program = get_exposure_program_name(read_u16(vp, ctx.little_endian));
                break;
            case 0x9207: // MeteringMode
                meta.exposure.metering_mode = get_metering_mode_name(read_u16(vp, ctx.little_endian));
                break;
            case 0x9209: // Flash
                {
                    uint16_t fv = read_u16(vp, ctx.little_endian);
                    meta.flash.fired = (fv & 0x01) != 0;
                    meta.flash.mode = get_flash_mode_name(fv);
                }
                break;
            case 0x920A: // FocalLength
                meta.lens.focal_length = read_rational(vp, ctx.little_endian);
                break;
            case 0xA405: // FocalLengthIn35mm
                meta.lens.focal_length_35mm = read_u16(vp, ctx.little_endian);
                break;
            case 0x9205: // MaxAperture
                meta.lens.max_aperture = std::pow(2.0f, read_rational(vp, ctx.little_endian) / 2.0f);
                break;
            case 0xA433: // LensMake
                meta.lens.make = read_string(vp, std::min(count, 64u));
                break;
            case 0xA434: // LensModel
                meta.lens.model = read_string(vp, std::min(count, 64u));
                break;
            case 0xA431: // SerialNumber
                meta.camera.serial_number = read_string(vp, std::min(count, 32u));
                break;
            case 0x9003: // DateTimeOriginal
                meta.image.date_time_original = read_string(vp, std::min(count, 20u));
                break;
            case 0x9004: // DateTimeDigitized
                meta.image.date_time_digitized = read_string(vp, std::min(count, 20u));
                break;
            case 0xA001: // ColorSpace
                meta.image.color_space = get_color_space_name(read_u16(vp, ctx.little_endian));
                break;
            case 0xA403: // WhiteBalance
                meta.white_balance.mode = get_white_balance_name(read_u16(vp, ctx.little_endian));
                break;
            case 0x9203: // BrightnessValue
                meta.exposure.brightness_value = read_srational(vp, ctx.little_endian);
                break;
            default: break;
        }
    }
}

void MetadataDecoder::parse_gps_ifd(const TiffCtx& ctx, uint32_t ifd_offset, DecodedMetadata& meta) {
    if (ifd_offset + 2 > ctx.size) return;
    uint16_t n = read_u16(ctx.data + ifd_offset, ctx.little_endian);
    double lat_d = 0, lat_m = 0, lat_s = 0;
    double lon_d = 0, lon_m = 0, lon_s = 0;

    for (uint16_t i = 0; i < n; ++i) {
        uint16_t tag, type; uint32_t count, voff; const uint8_t* vp;
        if (!read_ifd_entry(ctx, ifd_offset, i, tag, type, count, voff, vp)) continue;

        switch (tag) {
            case 0x0001: meta.gps.latitude_ref = read_string(vp, 2); break;
            case 0x0002:
                if (count >= 3) {
                    lat_d = read_rational(vp, ctx.little_endian);
                    lat_m = read_rational(vp + 8, ctx.little_endian);
                    lat_s = read_rational(vp + 16, ctx.little_endian);
                }
                break;
            case 0x0003: meta.gps.longitude_ref = read_string(vp, 2); break;
            case 0x0004:
                if (count >= 3) {
                    lon_d = read_rational(vp, ctx.little_endian);
                    lon_m = read_rational(vp + 8, ctx.little_endian);
                    lon_s = read_rational(vp + 16, ctx.little_endian);
                }
                break;
            case 0x0005: meta.gps.altitude_ref = std::to_string(vp[0]); break;
            case 0x0006: meta.gps.altitude = read_rational(vp, ctx.little_endian); break;
            case 0x0007: meta.gps.gps_time_stamp = read_string(vp, 11); break;
            case 0x000D: meta.gps.gps_measure_mode = std::to_string(read_string(vp, 2)[0]); break;
            case 0x0012: meta.gps.gps_measure_mode = "3D"; break;
            case 0x001D: meta.gps.gps_date_stamp = read_string(vp, 11); break;
            default: break;
        }
    }

    if (lat_d != 0 || lon_d != 0) {
        meta.gps.has_gps = true;
        meta.gps.latitude = lat_d + lat_m / 60.0 + lat_s / 3600.0;
        meta.gps.longitude = lon_d + lon_m / 60.0 + lon_s / 3600.0;
        if (meta.gps.latitude_ref == "S") meta.gps.latitude = -meta.gps.latitude;
        if (meta.gps.longitude_ref == "W") meta.gps.longitude = -meta.gps.longitude;
    }
}

// ── XMP parsing ──

bool MetadataDecoder::parse_xmp(const std::string& file_path, DecodedMetadata& metadata) {
    std::ifstream f(file_path, std::ios::binary | std::ios::ate);
    if (!f) return false;
    size_t sz = f.tellg(); f.seekg(0);
    std::vector<uint8_t> buf(sz);
    f.read(reinterpret_cast<char*>(buf.data()), sz);
    return parse_xmp_from_memory(buf.data(), sz, metadata);
}

bool MetadataDecoder::parse_xmp_from_memory(const uint8_t* data, size_t size, DecodedMetadata& metadata) {
    if (!data || size < 32) return false;
    std::string content(reinterpret_cast<const char*>(data),
                        std::min(size, static_cast<size_t>(131072)));

    // Look for XMP XML
    std::string xmp_header = "http://ns.adobe.com/xap/1.0/";
    size_t pos = content.find(xmp_header);
    if (pos == std::string::npos) return false;

    size_t xml_start = content.find('\0', pos + xmp_header.length());
    if (xml_start == std::string::npos) return false;
    xml_start++;

    size_t xml_end = content.find("<?xpacket end", xml_start);
    if (xml_end == std::string::npos) xml_end = std::min(xml_start + 65536, content.length());

    metadata.xmp.raw_xml = content.substr(xml_start, xml_end - xml_start);
    parse_xmp_packet(metadata.xmp.raw_xml, metadata);
    metadata.has_xmp = true;
    return true;
}

void MetadataDecoder::parse_xmp_packet(const std::string& xml, DecodedMetadata& meta) {
    auto extract_tag = [&](const std::string& tag) -> std::string {
        std::string open = "<" + tag + ">";
        size_t p = xml.find(open);
        if (p == std::string::npos) {
            p = xml.find("<" + tag + " ");
            if (p != std::string::npos) {
                size_t end = xml.find("/>", p);
                if (end != std::string::npos) {
                    std::string attr = xml.substr(p, end - p + 2);
                    size_t vp = attr.find("rdf:resource=\"");
                    if (vp != std::string::npos) {
                        vp += 14;
                        size_t ve = attr.find('"', vp);
                        return ve != std::string::npos ? attr.substr(vp, ve - vp) : "";
                    }
                }
                return "";
            }
            return "";
        }
        p += open.length();
        std::string close = "</" + tag + ">";
        size_t e = xml.find(close, p);
        return e != std::string::npos ? xml.substr(p, e - p) : "";
    };

    auto extract_attr = [&](const std::string& tag, const std::string& attr_name) -> std::string {
        std::string search = "<" + tag + " ";
        size_t p = xml.find(search);
        if (p == std::string::npos) return "";
        size_t end = xml.find("/>", p);
        if (end == std::string::npos) end = xml.find(">", p);
        if (end == std::string::npos) return "";
        std::string frag = xml.substr(p, end - p);
        std::string attr = attr_name + "=\"";
        size_t ap = frag.find(attr);
        if (ap == std::string::npos) return "";
        ap += attr.length();
        size_t ae = frag.find('"', ap);
        return ae != std::string::npos ? frag.substr(ap, ae - ap) : "";
    };

    meta.xmp.creator_tool = extract_tag("xmp:CreatorTool");
    meta.xmp.rating = extract_tag("xmp:Rating");
    meta.xmp.label = extract_tag("xmp:Label");
    meta.xmp.creator = extract_tag("dc:creator");
    meta.xmp.description = extract_tag("dc:description");
    meta.xmp.keywords = extract_tag("dc:subject");

    // DC fields
    meta.xmp.dc_fields["creator"] = meta.xmp.creator;
    meta.xmp.dc_fields["description"] = meta.xmp.description;
    meta.xmp.dc_fields["subject"] = meta.xmp.keywords;
    meta.xmp.dc_fields["rights"] = extract_tag("dc:rights");
    meta.xmp.dc_fields["title"] = extract_tag("dc:title");

    // XMP fields
    meta.xmp.xmp_fields["CreatorTool"] = meta.xmp.creator_tool;
    meta.xmp.xmp_fields["Rating"] = meta.xmp.rating;
    meta.xmp.xmp_fields["Label"] = meta.xmp.label;
    meta.xmp.xmp_fields["CreateDate"] = extract_tag("xmp:CreateDate");
    meta.xmp.xmp_fields["ModifyDate"] = extract_tag("xmp:ModifyDate");
    meta.xmp.xmp_fields["MetadataDate"] = extract_tag("xmp:MetadataDate");

    // CRS (Camera Raw Settings) fields
    meta.xmp.crs_fields["Version"] = extract_tag("crs:Version");
    meta.xmp.crs_fields["WhiteBalance"] = extract_tag("crs:WhiteBalance");
    meta.xmp.crs_fields["Temperature"] = extract_tag("crs:Temperature");
    meta.xmp.crs_fields["Tint"] = extract_tag("crs:Tint");
    meta.xmp.crs_fields["Exposure"] = extract_tag("crs:Exposure");
    meta.xmp.crs_fields["Contrast"] = extract_tag("crs:Contrast");
    meta.xmp.crs_fields["Highlights"] = extract_tag("crs:Highlights");
    meta.xmp.crs_fields["Shadows"] = extract_tag("crs:Shadows");
    meta.xmp.crs_fields["Saturation"] = extract_tag("crs:Saturation");
    meta.xmp.crs_fields["Sharpness"] = extract_tag("crs:Sharpness");
    meta.xmp.crs_fields["LuminanceSmoothing"] = extract_tag("crs:LuminanceSmoothing");
    meta.xmp.crs_fields["ColorNoiseReduction"] = extract_tag("crs:ColorNoiseReduction");
    meta.xmp.crs_fields["CropLeft"] = extract_tag("crs:CropLeft");
    meta.xmp.crs_fields["CropTop"] = extract_tag("crs:CropTop");
    meta.xmp.crs_fields["CropRight"] = extract_tag("crs:CropRight");
    meta.xmp.crs_fields["CropBottom"] = extract_tag("crs:CropBottom");
    meta.xmp.crs_fields["ProcessVersion"] = extract_tag("crs:ProcessVersion");
}

std::string MetadataDecoder::extract_xmp_field(const std::string& xml, const std::string& ns,
                                                const std::string& name) {
    std::string tag = ns + ":" + name;
    std::string open = "<" + tag + ">";
    size_t p = xml.find(open);
    if (p == std::string::npos) return "";
    p += open.length();
    std::string close = "</" + tag + ">";
    size_t e = xml.find(close, p);
    return e != std::string::npos ? xml.substr(p, e - p) : "";
}

std::string MetadataDecoder::extract_xmp_tag(const std::string& xml, const std::string& tag) {
    return extract_xmp_field(xml, tag.substr(0, tag.find(':')), tag.substr(tag.find(':') + 1));
}

// ── ICC Profile parsing ──

bool MetadataDecoder::parse_icc_profile(const std::string& file_path, DecodedMetadata& metadata) {
    std::ifstream f(file_path, std::ios::binary | std::ios::ate);
    if (!f) return false;
    size_t sz = f.tellg(); f.seekg(0);
    std::vector<uint8_t> buf(sz);
    f.read(reinterpret_cast<char*>(buf.data()), sz);
    return parse_icc_profile_from_memory(buf.data(), sz, metadata);
}

bool MetadataDecoder::parse_icc_profile_from_memory(const uint8_t* data, size_t size, DecodedMetadata& meta) {
    if (!data || size < 4) return false;

    // JPEG: APP2 ICC_PROFILE
    if (data[0] == 0xFF && data[1] == 0xD8) {
        size_t off = 2;
        while (off + 4 < size) {
            if (data[off] == 0xFF) {
                uint8_t m = data[off + 1];
                if (m == 0xE2) {
                    uint16_t slen = (data[off + 2] << 8) | data[off + 3];
                    const uint8_t* icc = data + off + 4;
                    if (off + 2 + slen <= size && slen > 14 &&
                        icc[0] == 'I' && icc[1] == 'C' && icc[2] == 'C') {
                        size_t icc_off = 14;
                        size_t icc_sz = slen - 2 - icc_off;
                        meta.icc_profile.raw_data.assign(icc + icc_off, icc + icc_off + icc_sz);
                        parse_icc_header(meta.icc_profile.raw_data.data(),
                                         meta.icc_profile.raw_data.size(), meta);
                        meta.has_icc = true;
                        return true;
                    }
                }
                if (m == 0xDA) break;
                off += 2 + ((data[off + 2] << 8) | data[off + 3]);
            } else ++off;
        }
    }
    // PNG: iCCP
    if (data[0] == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G') {
        size_t off = 8;
        while (off + 12 < size) {
            uint32_t chunk_sz = (data[off] << 24) | (data[off+1] << 16) |
                                (data[off+2] << 8) | data[off+3];
            if (data[off+4] == 'i' && data[off+5] == 'C' && data[off+6] == 'C' && data[off+7] == 'P') {
                const uint8_t* cd = data + off + 8;
                size_t name_end = 0;
                while (name_end < chunk_sz && cd[name_end] != 0) name_end++;
                if (name_end + 2 < chunk_sz) {
                    meta.icc_profile.raw_data.assign(cd + name_end + 2, cd + chunk_sz);
                    parse_icc_header(meta.icc_profile.raw_data.data(),
                                     meta.icc_profile.raw_data.size(), meta);
                    meta.has_icc = true;
                    return true;
                }
            }
            off += 12 + chunk_sz;
        }
    }
    return false;
}

bool MetadataDecoder::parse_icc_header(const uint8_t* data, size_t size, DecodedMetadata& meta) {
    if (size < 128) return false;

    // Profile size
    meta.icc_profile.version_major = data[8];
    meta.icc_profile.version_minor = (data[9] >> 4) & 0x0F;

    // Device class
    char dclass[5] = {};
    memcpy(dclass, data + 12, 4);
    meta.icc_profile.device_class = dclass;

    // Color space
    char cspace[5] = {};
    memcpy(cspace, data + 16, 4);
    meta.icc_profile.color_space = cspace;

    // PCS
    char pcs[5] = {};
    memcpy(pcs, data + 20, 4);
    meta.icc_profile.pcs = pcs;

    // Manufacturer
    meta.icc_profile.manufacturer = read_string(data + 48, 4);

    // Model
    meta.icc_profile.model = read_string(data + 52, 4);

    // Description (from tag table)
    if (size >= 132) {
        uint32_t tag_count = read_u32(data + 128, true); // big-endian
        for (uint32_t i = 0; i < tag_count && (132 + i * 12 + 12) <= size; ++i) {
            uint32_t toff = 132 + i * 12;
            uint32_t tsig = read_u32(data + toff, true);
            if (tsig == 0x64657363) { // 'desc'
                uint32_t doff = read_u32(data + toff + 4, true);
                uint32_t dsize = read_u32(data + toff + 8, true);
                if (doff + dsize <= size && dsize > 4) {
                    meta.icc_profile.description = read_string(data + doff + 4, dsize - 4);
                }
            }
        }
    }

    meta.icc_profile.has_profile = true;
    return true;
}

// ── MakerNote parsing ──

bool MetadataDecoder::parse_maker_note(const std::string& file_path, DecodedMetadata& metadata) {
    std::ifstream f(file_path, std::ios::binary | std::ios::ate);
    if (!f) return false;
    size_t sz = f.tellg(); f.seekg(0);
    std::vector<uint8_t> buf(sz);
    f.read(reinterpret_cast<char*>(buf.data()), sz);
    return parse_maker_note_from_memory(buf.data(), sz, metadata);
}

bool MetadataDecoder::parse_maker_note_from_memory(const uint8_t* data, size_t size, DecodedMetadata& meta) {
    // MakerNote is already parsed during EXIF parsing
    if (meta.maker_note.camera_maker.empty()) {
        // Force re-parse by calling parse_exif first
        parse_exif_from_memory(data, size, meta);
    }
    return meta.has_maker_note;
}

void MetadataDecoder::parse_maker_note_internal(const TiffCtx& ctx, const uint8_t* maker_data,
                                                 size_t maker_size, DecodedMetadata& meta) {
    if (maker_size < 8) return;
    std::string make = meta.camera.make;
    std::transform(make.begin(), make.end(), make.begin(), ::toupper);

    if (make.find("NIKON") != std::string::npos)
        parse_nikon_maker_note(ctx, maker_data, maker_size, meta);
    else if (make.find("CANON") != std::string::npos)
        parse_canon_maker_note(ctx, maker_data, maker_size, meta);
    else if (make.find("SONY") != std::string::npos)
        parse_sony_maker_note(ctx, maker_data, maker_size, meta);
    else if (make.find("FUJI") != std::string::npos)
        parse_fuji_maker_note(ctx, maker_data, maker_size, meta);

    meta.has_maker_note = true;
}

void MetadataDecoder::parse_nikon_maker_note(const TiffCtx& ctx, const uint8_t* data, size_t size,
                                              DecodedMetadata& meta) {
    // Nikon MakerNote starts with "Nikon\0" + TIFF header
    if (size < 18) return;
    if (data[0] != 'N' || data[1] != 'i' || data[2] != 'k' || data[3] != 'o' || data[4] != 'n') return;

    TiffCtx nctx;
    nctx.little_endian = (data[10] == 0x49);
    nctx.data = data;
    nctx.size = size;

    uint32_t ifd0 = read_u32(data + 12, nctx.little_endian) + 10; // Offset from "Nikon\0" start
    if (ifd0 + 2 > size) return;

    uint16_t n = read_u16(data + ifd0, nctx.little_endian);
    for (uint16_t i = 0; i < n; ++i) {
        uint16_t tag, type; uint32_t count, voff; const uint8_t* vp;
        if (!read_ifd_entry(nctx, ifd0, i, tag, type, count, voff, vp)) continue;

        switch (tag) {
            case 0x0002: meta.maker_note.nikon_iso = read_u16(vp, nctx.little_endian); break;
            case 0x000D: meta.maker_note.nikon_iso_selection = read_string(vp, std::min(count, 16u)); break;
            case 0x0025: meta.maker_note.nikon_color_mode = read_string(vp, std::min(count, 16u)); break;
            case 0x0003: meta.maker_note.nikon_quality = read_string(vp, std::min(count, 16u)); break;
            case 0x0005: meta.maker_note.nikon_white_balance = read_string(vp, std::min(count, 16u)); break;
            case 0x0006: meta.maker_note.nikon_sharpening = read_string(vp, std::min(count, 16u)); break;
            case 0x0007: meta.maker_note.nikon_focus_mode = read_string(vp, std::min(count, 16u)); break;
            case 0x0008: meta.maker_note.nikon_flash_setting = read_string(vp, std::min(count, 16u)); break;
            case 0x0012: meta.maker_note.nikon_flash_compensation = read_srational(vp, nctx.little_endian); break;
            default: break;
        }
    }
}

void MetadataDecoder::parse_canon_maker_note(const TiffCtx& ctx, const uint8_t* data, size_t size,
                                              DecodedMetadata& meta) {
    if (size < 4) return;
    // Canon MakerNote has its own offset system
    uint32_t offset = read_u32(data, ctx.little_endian);
    if (offset + 4 > size) return;

    const uint8_t* mn = data + offset;
    size_t mn_size = size - offset;
    if (mn_size < 4) return;

    uint16_t n = read_u16(mn, ctx.little_endian);
    for (uint16_t i = 0; i < n && (4 + i * 12 + 12) <= mn_size; ++i) {
        uint32_t off = 4 + i * 12;
        uint16_t tag = read_u16(mn + off, ctx.little_endian);
        uint16_t type = read_u16(mn + off + 2, ctx.little_endian);
        uint32_t count = read_u32(mn + off + 4, ctx.little_endian);
        const uint8_t* vp = mn + off + 8;

        switch (tag) {
            case 0x0001: meta.maker_note.canon_iso = read_u16(vp, ctx.little_endian); break;
            case 0x0006: meta.maker_note.canon_serial_number = read_string(vp, std::min(count, 32u)); break;
            case 0x0009: meta.maker_note.canon_owner_name = read_string(vp, std::min(count, 32u)); break;
            case 0x0010: meta.maker_note.canon_sharpness = read_u16(vp, ctx.little_endian); break;
            case 0x0011: meta.maker_note.canon_contrast = read_u16(vp, ctx.little_endian); break;
            case 0x0012: meta.maker_note.canon_saturation = read_u16(vp, ctx.little_endian); break;
            case 0x0013: meta.maker_note.canon_color_tone = read_string(vp, std::min(count, 16u)); break;
            default: break;
        }
    }
}

void MetadataDecoder::parse_sony_maker_note(const TiffCtx& ctx, const uint8_t* data, size_t size,
                                             DecodedMetadata& meta) {
    if (size < 4) return;
    uint16_t n = read_u16(data, ctx.little_endian);
    for (uint16_t i = 0; i < n && (2 + i * 12 + 12) <= size; ++i) {
        uint32_t off = 2 + i * 12;
        uint16_t tag = read_u16(data + off, ctx.little_endian);
        uint16_t type = read_u16(data + off + 2, ctx.little_endian);
        uint32_t count = read_u32(data + off + 4, ctx.little_endian);
        const uint8_t* vp = data + off + 8;

        switch (tag) {
            case 0xB001: meta.maker_note.sony_iso = read_u16(vp, ctx.little_endian); break;
            case 0xB027: meta.maker_note.sony_lens_id = read_string(vp, std::min(count, 32u)); break;
            case 0xB041: meta.maker_note.sony_creative_style = read_string(vp, std::min(count, 32u)); break;
            case 0xB043: meta.maker_note.sony_dynamic_range_optimizer = read_u16(vp, ctx.little_endian); break;
            default: break;
        }
    }
}

void MetadataDecoder::parse_fuji_maker_note(const TiffCtx& ctx, const uint8_t* data, size_t size,
                                             DecodedMetadata& meta) {
    if (size < 12) return;
    // Fuji MakerNote starts with "FUJIFILM"
    if (data[0] != 'F' || data[1] != 'U') return;

    uint32_t ifd_off = 12;
    if (ifd_off + 2 > size) return;
    uint16_t n = read_u16(data + ifd_off, ctx.little_endian);

    for (uint16_t i = 0; i < n && (ifd_off + 2 + i * 12 + 12) <= size; ++i) {
        uint32_t off = ifd_off + 2 + i * 12;
        uint16_t tag = read_u16(data + off, ctx.little_endian);
        uint16_t type = read_u16(data + off + 2, ctx.little_endian);
        uint32_t count = read_u32(data + off + 4, ctx.little_endian);
        const uint8_t* vp = data + off + 8;

        switch (tag) {
            case 0x1001: meta.maker_note.fuji_film_simulation = read_string(vp, std::min(count, 32u)); break;
            case 0x1400: meta.maker_note.fuji_dynamic_range = read_u16(vp, ctx.little_endian); break;
            case 0x1406: meta.maker_note.fuji_grain_effect = read_string(vp, std::min(count, 16u)); break;
            default: break;
        }
    }
}

// ── DNG Color parsing ──

bool MetadataDecoder::parse_dng_color(const std::string& file_path, DecodedMetadata& metadata) {
    std::ifstream f(file_path, std::ios::binary | std::ios::ate);
    if (!f) return false;
    size_t sz = f.tellg(); f.seekg(0);
    std::vector<uint8_t> buf(sz);
    f.read(reinterpret_cast<char*>(buf.data()), sz);
    return parse_dng_color_from_memory(buf.data(), sz, metadata);
}

bool MetadataDecoder::parse_dng_color_from_memory(const uint8_t* data, size_t size, DecodedMetadata& meta) {
    if (!data || size < 8) return false;
    if (data[0] != 0x49 && data[0] != 0x4D) return false;
    bool le = (data[0] == 0x49);
    if (read_u16(data + 2, le) != 0x002A) return false;

    uint32_t ifd0 = read_u32(data + 4, le);
    parse_dng_ifd({le, data, size}, ifd0, meta);
    meta.has_dng_color = meta.dng_color.has_dng_data;
    return meta.has_dng_color;
}

void MetadataDecoder::parse_dng_ifd(const TiffCtx& ctx, uint32_t ifd_offset, DecodedMetadata& meta) {
    if (ifd_offset + 2 > ctx.size) return;
    uint16_t n = read_u16(ctx.data + ifd_offset, ctx.little_endian);

    for (uint16_t i = 0; i < n; ++i) {
        uint16_t tag, type; uint32_t count, voff; const uint8_t* vp;
        if (!read_ifd_entry(ctx, ifd_offset, i, tag, type, count, voff, vp)) continue;

        switch (tag) {
            case 0xC621: // ColorMatrix1
                if (type == 10 && count >= 9) {
                    for (int j = 0; j < 9; ++j)
                        meta.dng_color.color_matrix1[j] = read_srational(vp + j * 8, ctx.little_endian);
                    meta.dng_color.has_dng_data = true;
                }
                break;
            case 0xC622: // ColorMatrix2
                if (type == 10 && count >= 9) {
                    for (int j = 0; j < 9; ++j)
                        meta.dng_color.color_matrix2[j] = read_srational(vp + j * 8, ctx.little_endian);
                }
                break;
            case 0xC623: // CameraCalibration1
                if (type == 10 && count >= 9) {
                    for (int j = 0; j < 9; ++j)
                        meta.dng_color.calibration1[j] = read_srational(vp + j * 8, ctx.little_endian);
                }
                break;
            case 0xC624: // CameraCalibration2
                if (type == 10 && count >= 9) {
                    for (int j = 0; j < 9; ++j)
                        meta.dng_color.calibration2[j] = read_srational(vp + j * 8, ctx.little_endian);
                }
                break;
            case 0xC714: // ForwardMatrix1
                if (type == 10 && count >= 9) {
                    for (int j = 0; j < 9; ++j)
                        meta.dng_color.forward_matrix1[j] = read_srational(vp + j * 8, ctx.little_endian);
                }
                break;
            case 0xC715: // ForwardMatrix2
                if (type == 10 && count >= 9) {
                    for (int j = 0; j < 9; ++j)
                        meta.dng_color.forward_matrix2[j] = read_srational(vp + j * 8, ctx.little_endian);
                }
                break;
            case 0xC65A: // CalibrationIlluminant1
                {
                    int v = read_u16(vp, ctx.little_endian);
                    meta.dng_color.calibration_illuminant1_value = v;
                    meta.dng_color.calibration_illuminant1 = get_illuminant_name(v);
                }
                break;
            case 0xC65B: // CalibrationIlluminant2
                {
                    int v = read_u16(vp, ctx.little_endian);
                    meta.dng_color.calibration_illuminant2_value = v;
                    meta.dng_color.calibration_illuminant2 = get_illuminant_name(v);
                }
                break;
            case 0xC627: // AnalogBalance
                if (type == 5 && count >= 3) {
                    for (int j = 0; j < 3; ++j)
                        meta.dng_color.analog_balance[j] = read_rational(vp + j * 8, ctx.little_endian);
                }
                break;
            case 0xC628: // AsShotNeutral
                if (type == 5 && count >= 3) {
                    for (int j = 0; j < 3; ++j)
                        meta.dng_color.as_shot_neutral[j] = read_rational(vp + j * 8, ctx.little_endian);
                }
                break;
            case 0xC62A: // BaselineExposure
                if (type == 10) meta.dng_color.baseline_exposure = read_srational(vp, ctx.little_endian);
                else if (type == 5) meta.dng_color.baseline_exposure = read_rational(vp, ctx.little_endian);
                break;
            case 0x828E: // CFAPattern
                if (type == 1 && count >= 4) {
                    for (int j = 0; j < 4; ++j) meta.dng_color.cfa_pattern[j] = vp[j];
                }
                break;
            case 0xC61D: // WhiteLevel
                meta.dng_color.white_level = (type == 3) ? read_u16(vp, ctx.little_endian)
                    : static_cast<uint16_t>(read_u32(vp, ctx.little_endian));
                break;
            case 0xC61A: // BlackLevel
                if (type == 3 || type == 4) {
                    int blc = std::min(static_cast<int>(count), 4);
                    for (int j = 0; j < blc; ++j)
                        meta.dng_color.black_levels[j] = (type == 3)
                            ? read_u16(vp + j * 2, ctx.little_endian)
                            : static_cast<int>(read_u32(vp + j * 4, ctx.little_endian));
                    meta.dng_color.black_level = static_cast<uint16_t>(meta.dng_color.black_levels[0]);
                }
                break;
            default: break;
        }
    }
}

// ── Utility name functions ──

std::string MetadataDecoder::get_exposure_program_name(int code) {
    switch (code) {
        case 1: return "Manual";
        case 2: return "Normal";
        case 3: return "Aperture Priority";
        case 4: return "Shutter Priority";
        case 5: return "Creative";
        case 6: return "Action";
        case 7: return "Portrait";
        case 8: return "Landscape";
        default: return "Unknown";
    }
}

std::string MetadataDecoder::get_metering_mode_name(int code) {
    switch (code) {
        case 1: return "Average";
        case 2: return "Center-weighted";
        case 3: return "Spot";
        case 4: return "Multi-spot";
        case 5: return "Pattern";
        case 6: return "Partial";
        default: return "Unknown";
    }
}

std::string MetadataDecoder::get_flash_mode_name(int code) {
    if (code & 0x01) return "Fired";
    return "Did not fire";
}

std::string MetadataDecoder::get_orientation_name(int orientation) {
    switch (orientation) {
        case 1: return "Normal";
        case 3: return "Rotated 180";
        case 6: return "Rotated 90 CW";
        case 8: return "Rotated 90 CCW";
        default: return "Unknown";
    }
}

std::string MetadataDecoder::get_color_space_name(int code) {
    switch (code) {
        case 1: return "sRGB";
        case 2: return "Adobe RGB";
        case 0xFFFF: return "Uncalibrated";
        case 0xFFFE: return "ICC Profile";
        default: return "Unknown";
    }
}

std::string MetadataDecoder::get_white_balance_name(int code) {
    switch (code) {
        case 0: return "Auto";
        case 1: return "Manual";
        default: return "Unknown";
    }
}

std::string MetadataDecoder::get_illuminant_name(int code) {
    switch (code) {
        case 1: return "Daylight";
        case 2: return "Fluorescent";
        case 3: return "Tungsten";
        case 4: return "Flash";
        case 9: return "Fine Weather";
        case 10: return "Cloudy";
        case 11: return "Shade";
        case 12: return "Daylight Fluorescent";
        case 13: return "Day White Fluorescent";
        case 14: return "Cool White Fluorescent";
        case 15: return "White Fluorescent";
        case 16: return "Warm White Fluorescent";
        case 17: return "Standard Light A";
        case 18: return "Standard Light B";
        case 19: return "Standard Light C";
        case 20: return "D55";
        case 21: return "D65";
        case 22: return "D75";
        case 23: return "D50";
        case 24: return "ISO Studio Tungsten";
        default: return "Unknown (" + std::to_string(code) + ")";
    }
}

// ── JSON serialization ──

std::string MetadataDecoder::json_escape(const std::string& s) {
    std::string r;
    for (char c : s) {
        switch (c) {
            case '"': r += "\\\""; break;
            case '\\': r += "\\\\"; break;
            case '\n': r += "\\n"; break;
            case '\r': r += "\\r"; break;
            case '\t': r += "\\t"; break;
            default: r += c;
        }
    }
    return r;
}

std::string MetadataDecoder::json_float_array(const float* data, int count) {
    std::ostringstream ss;
    ss << "[";
    for (int i = 0; i < count; ++i) {
        if (i > 0) ss << ",";
        ss << data[i];
    }
    ss << "]";
    return ss.str();
}

std::string MetadataDecoder::json_int_array(const int* data, int count) {
    std::ostringstream ss;
    ss << "[";
    for (int i = 0; i < count; ++i) {
        if (i > 0) ss << ",";
        ss << data[i];
    }
    ss << "]";
    return ss.str();
}

std::string MetadataDecoder::to_json(const DecodedMetadata& meta) {
    std::ostringstream j;
    j << "{\n";

    // Camera
    j << "  \"camera\": {\n";
    j << "    \"make\": \"" << json_escape(meta.camera.make) << "\",\n";
    j << "    \"model\": \"" << json_escape(meta.camera.model) << "\",\n";
    j << "    \"serial\": \"" << json_escape(meta.camera.serial_number) << "\"\n";
    j << "  },\n";

    // Lens
    j << "  \"lens\": {\n";
    j << "    \"make\": \"" << json_escape(meta.lens.make) << "\",\n";
    j << "    \"model\": \"" << json_escape(meta.lens.model) << "\",\n";
    j << "    \"focal_length\": " << meta.lens.focal_length << ",\n";
    j << "    \"focal_length_35mm\": " << meta.lens.focal_length_35mm << ",\n";
    j << "    \"max_aperture\": " << meta.lens.max_aperture << "\n";
    j << "  },\n";

    // Exposure
    j << "  \"exposure\": {\n";
    j << "    \"aperture\": " << meta.exposure.aperture << ",\n";
    j << "    \"shutter_speed\": " << meta.exposure.shutter_speed << ",\n";
    j << "    \"iso\": " << meta.exposure.iso << ",\n";
    j << "    \"exposure_bias\": " << meta.exposure.exposure_bias << ",\n";
    j << "    \"program\": \"" << json_escape(meta.exposure.exposure_program) << "\",\n";
    j << "    \"metering\": \"" << json_escape(meta.exposure.metering_mode) << "\"\n";
    j << "  },\n";

    // Image
    j << "  \"image\": {\n";
    j << "    \"width\": " << meta.image.width << ",\n";
    j << "    \"height\": " << meta.image.height << ",\n";
    j << "    \"orientation\": " << meta.image.orientation << ",\n";
    j << "    \"datetime\": \"" << json_escape(meta.image.date_time_original) << "\",\n";
    j << "    \"color_space\": \"" << json_escape(meta.image.color_space) << "\"\n";
    j << "  },\n";

    // GPS
    j << "  \"gps\": {\n";
    j << "    \"has_gps\": " << (meta.gps.has_gps ? "true" : "false") << ",\n";
    j << "    \"latitude\": " << meta.gps.latitude << ",\n";
    j << "    \"longitude\": " << meta.gps.longitude << ",\n";
    j << "    \"altitude\": " << meta.gps.altitude << "\n";
    j << "  },\n";

    // DNG color
    j << "  \"dng_color\": {\n";
    j << "    \"has_dng_data\": " << (meta.dng_color.has_dng_data ? "true" : "false") << ",\n";
    j << "    \"color_matrix1\": " << json_float_array(meta.dng_color.color_matrix1, 9) << ",\n";
    j << "    \"forward_matrix1\": " << json_float_array(meta.dng_color.forward_matrix1, 9) << ",\n";
    j << "    \"white_level\": " << meta.dng_color.white_level << ",\n";
    j << "    \"black_level\": " << meta.dng_color.black_level << ",\n";
    j << "    \"cfa_pattern\": " << json_int_array(meta.dng_color.cfa_pattern, 4) << "\n";
    j << "  },\n";

    // File
    j << "  \"file\": {\n";
    j << "    \"type\": \"" << json_escape(meta.file_type) << "\",\n";
    j << "    \"size\": " << meta.file_size << "\n";
    j << "  }\n";

    j << "}";
    return j.str();
}

std::string MetadataDecoder::to_json_compact(const DecodedMetadata& meta) {
    char buf[4096];
    snprintf(buf, sizeof(buf),
        "{\"make\":\"%s\",\"model\":\"%s\",\"iso\":%.0f,\"aperture\":%.1f,"
        "\"shutter\":\"1/%.0f\",\"focal\":%.1f,\"lens\":\"%s\","
        "\"width\":%d,\"height\":%d,\"orientation\":%d,"
        "\"gps_lat\":%.6f,\"gps_lon\":%.6f,\"has_gps\":%s,"
        "\"color_space\":\"%s\",\"file_type\":\"%s\"}",
        meta.camera.make.c_str(), meta.camera.model.c_str(),
        meta.exposure.iso, meta.exposure.aperture,
        1.0f / (meta.exposure.shutter_speed > 0 ? meta.exposure.shutter_speed : 1.0f),
        meta.lens.focal_length, meta.lens.model.c_str(),
        meta.image.width, meta.image.height, meta.image.orientation,
        meta.gps.latitude, meta.gps.longitude,
        meta.gps.has_gps ? "true" : "false",
        meta.image.color_space.c_str(), meta.file_type.c_str());
    return buf;
}

bool MetadataDecoder::from_json(const std::string& json, DecodedMetadata& metadata) {
    // Simple JSON parser for commonly used fields
    auto extract_str = [&](const std::string& key) -> std::string {
        std::string search = "\"" + key + "\":\"";
        size_t p = json.find(search);
        if (p == std::string::npos) return "";
        p += search.length();
        size_t e = json.find('"', p);
        return e != std::string::npos ? json.substr(p, e - p) : "";
    };
    auto extract_float = [&](const std::string& key) -> float {
        std::string search = "\"" + key + "\":";
        size_t p = json.find(search);
        if (p == std::string::npos) return 0.0f;
        p += search.length();
        return std::atof(json.c_str() + p);
    };
    auto extract_int = [&](const std::string& key) -> int {
        std::string search = "\"" + key + "\":";
        size_t p = json.find(search);
        if (p == std::string::npos) return 0;
        p += search.length();
        return std::atoi(json.c_str() + p);
    };

    metadata.camera.make = extract_str("make");
    metadata.camera.model = extract_str("model");
    metadata.lens.model = extract_str("lens");
    metadata.exposure.iso = extract_float("iso");
    metadata.exposure.aperture = extract_float("aperture");
    metadata.exposure.shutter_speed = extract_float("shutter");
    metadata.lens.focal_length = extract_float("focal");
    metadata.image.width = extract_int("width");
    metadata.image.height = extract_int("height");
    metadata.image.orientation = extract_int("orientation");
    metadata.gps.latitude = extract_float("gps_lat");
    metadata.gps.longitude = extract_float("gps_lon");
    metadata.has_metadata = true;

    return true;
}

// ── DNG IFD parse (for metadata) ──
// Already handled in parse_dng_color_from_memory which calls parse_dng_ifd

} // namespace alcedo