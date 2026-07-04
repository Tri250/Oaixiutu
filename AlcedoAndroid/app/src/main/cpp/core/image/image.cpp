// Ported from AlcedoStudio desktop: image/image.cpp
// SPDX-License-Identifier: GPL-3.0-only
//
// Core Image model implementation for Android.
// JSON serialization uses a tiny inline parser (no nlohmann dependency),
// following the same self-contained approach as ai_description.cpp.
// Checksum uses FNV-1a hash.

#include "image/image.h"

#include <android/log.h>
#include <cstdio>
#include <cstring>
#include <string>
#include <string_view>
#include <utility>

namespace alcedo {

namespace {

constexpr const char* kTag = "AlcedoImage";

// ── JSON helpers (self-contained, no nlohmann) ──

std::string EscapeJsonString(const std::string& in) {
    std::string out;
    out.reserve(in.size() + 2);
    for (char c : in) {
        switch (c) {
            case '"':  out += "\\\""; break;
            case '\\': out += "\\\\"; break;
            case '\b': out += "\\b";  break;
            case '\f': out += "\\f";  break;
            case '\n': out += "\\n";  break;
            case '\r': out += "\\r";  break;
            case '\t': out += "\\t";  break;
            default:
                if (static_cast<unsigned char>(c) < 0x20) {
                    char buf[8];
                    std::snprintf(buf, sizeof(buf), "\\u%04x", static_cast<unsigned char>(c));
                    out += buf;
                } else {
                    out += c;
                }
                break;
        }
    }
    return out;
}

// Skip whitespace
const char* SkipWs(const char* p, const char* end) {
    while (p < end && (*p == ' ' || *p == '\t' || *p == '\n' || *p == '\r')) ++p;
    return p;
}

// Parse a JSON string literal beginning at *p == '"'.
bool ParseJsonString(const char*& p, const char* end, std::string& out) {
    if (p >= end || *p != '"') return false;
    ++p;
    out.clear();
    while (p < end && *p != '"') {
        if (*p == '\\' && p + 1 < end) {
            char esc = *(p + 1);
            switch (esc) {
                case '"':  out += '"';  break;
                case '\\': out += '\\'; break;
                case '/':  out += '/';  break;
                case 'b':  out += '\b'; break;
                case 'f':  out += '\f'; break;
                case 'n':  out += '\n'; break;
                case 'r':  out += '\r'; break;
                case 't':  out += '\t'; break;
                case 'u': {
                    if (p + 5 < end) {
                        char buf[5] = {p[2], p[3], p[4], p[5], 0};
                        unsigned int cp = 0;
                        std::sscanf(buf, "%x", &cp);
                        if (cp <= 0x7F) {
                            out += static_cast<char>(cp);
                        } else if (cp <= 0x7FF) {
                            out += static_cast<char>(0xC0 | (cp >> 6));
                            out += static_cast<char>(0x80 | (cp & 0x3F));
                        } else {
                            out += static_cast<char>(0xE0 | (cp >> 12));
                            out += static_cast<char>(0x80 | ((cp >> 6) & 0x3F));
                            out += static_cast<char>(0x80 | (cp & 0x3F));
                        }
                        p += 4;
                    }
                    break;
                }
                default: out += esc; break;
            }
            p += 2;
        } else {
            out += *p;
            ++p;
        }
    }
    if (p >= end || *p != '"') return false;
    ++p;
    return true;
}

// Parse a JSON number (int or float) from the current position.
bool ParseJsonNumber(const char*& p, const char* end, double& out) {
    if (p >= end) return false;
    char* num_end = nullptr;
    out = std::strtod(p, &num_end);
    if (num_end == p) return false;
    p = num_end;
    return true;
}

// Find the position after a JSON value (string, number, bool, null, object, array).
bool SkipJsonValue(const char*& p, const char* end) {
    p = SkipWs(p, end);
    if (p >= end) return false;
    if (*p == '"') {
        std::string dummy;
        return ParseJsonString(p, end, dummy);
    }
    if (*p == '{') {
        ++p;
        p = SkipWs(p, end);
        if (p < end && *p == '}') { ++p; return true; }
        while (p < end) {
            std::string key;
            if (!ParseJsonString(p, end, key)) return false;
            p = SkipWs(p, end);
            if (p >= end || *p != ':') return false;
            ++p;
            if (!SkipJsonValue(p, end)) return false;
            p = SkipWs(p, end);
            if (p < end && *p == ',') { ++p; p = SkipWs(p, end); continue; }
            if (p < end && *p == '}') { ++p; return true; }
        }
        return false;
    }
    if (*p == '[') {
        ++p;
        p = SkipWs(p, end);
        if (p < end && *p == ']') { ++p; return true; }
        while (p < end) {
            if (!SkipJsonValue(p, end)) return false;
            p = SkipWs(p, end);
            if (p < end && *p == ',') { ++p; p = SkipWs(p, end); continue; }
            if (p < end && *p == ']') { ++p; return true; }
        }
        return false;
    }
    // true, false, null, number
    if (std::strncmp(p, "true", 4) == 0) { p += 4; return true; }
    if (std::strncmp(p, "false", 5) == 0) { p += 5; return true; }
    if (std::strncmp(p, "null", 4) == 0) { p += 4; return true; }
    double dummy;
    return ParseJsonNumber(p, end, dummy);
}

// Look up a string value in a JSON object by key. Returns "" if not found.
std::string JsonGetString(const std::string& json, const std::string& key) {
    const char* p = json.data();
    const char* end = p + json.size();
    p = SkipWs(p, end);
    if (p >= end || *p != '{') return {};
    ++p;
    p = SkipWs(p, end);
    while (p < end && *p != '}') {
        std::string k;
        if (!ParseJsonString(p, end, k)) return {};
        p = SkipWs(p, end);
        if (p >= end || *p != ':') return {};
        ++p;
        p = SkipWs(p, end);
        if (k == key) {
            std::string val;
            if (p < end && *p == '"') {
                ParseJsonString(p, end, val);
            }
            return val;
        }
        if (!SkipJsonValue(p, end)) return {};
        p = SkipWs(p, end);
        if (p < end && *p == ',') { ++p; p = SkipWs(p, end); }
    }
    return {};
}

// Look up a numeric value. Returns default_val if not found.
double JsonGetNumber(const std::string& json, const std::string& key, double default_val = 0.0) {
    const char* p = json.data();
    const char* end = p + json.size();
    p = SkipWs(p, end);
    if (p >= end || *p != '{') return default_val;
    ++p;
    p = SkipWs(p, end);
    while (p < end && *p != '}') {
        std::string k;
        if (!ParseJsonString(p, end, k)) return default_val;
        p = SkipWs(p, end);
        if (p >= end || *p != ':') return default_val;
        ++p;
        p = SkipWs(p, end);
        if (k == key) {
            double val;
            if (ParseJsonNumber(p, end, val)) return val;
            return default_val;
        }
        if (!SkipJsonValue(p, end)) return default_val;
        p = SkipWs(p, end);
        if (p < end && *p == ',') { ++p; p = SkipWs(p, end); }
    }
    return default_val;
}

// Look up a boolean value.
bool JsonGetBool(const std::string& json, const std::string& key, bool default_val = false) {
    const char* p = json.data();
    const char* end = p + json.size();
    p = SkipWs(p, end);
    if (p >= end || *p != '{') return default_val;
    ++p;
    p = SkipWs(p, end);
    while (p < end && *p != '}') {
        std::string k;
        if (!ParseJsonString(p, end, k)) return default_val;
        p = SkipWs(p, end);
        if (p >= end || *p != ':') return default_val;
        ++p;
        p = SkipWs(p, end);
        if (k == key) {
            if (std::strncmp(p, "true", 4) == 0) return true;
            if (std::strncmp(p, "false", 5) == 0) return false;
            return default_val;
        }
        if (!SkipJsonValue(p, end)) return default_val;
        p = SkipWs(p, end);
        if (p < end && *p == ',') { ++p; p = SkipWs(p, end); }
    }
    return default_val;
}

// ── FNV-1a constants (64-bit) ──
constexpr uint64_t kFnv1aOffsetBasis = 0xcbf29ce484222325ULL;
constexpr uint64_t kFnv1aPrime       = 0x100000001b3ULL;

uint64_t Fnv1aHash(const uint8_t* data, size_t len) {
    uint64_t h = kFnv1aOffsetBasis;
    for (size_t i = 0; i < len; ++i) {
        h ^= data[i];
        h *= kFnv1aPrime;
    }
    return h;
}

} // anonymous namespace

// ============================================================
// ExifDisplayMetaData JSON
// ============================================================

std::string ExifDisplayMetaData::ToJson() const {
    std::string out;
    out.reserve(256);
    out += '{';

    auto add_str = [&](const char* key, const std::string& val) {
        if (!out.empty() && out.back() != '{') out += ',';
        out += '"';
        out += key;
        out += "\":\"";
        out += EscapeJsonString(val);
        out += '"';
    };
    auto add_num = [&](const char* key, double val) {
        if (!out.empty() && out.back() != '{') out += ',';
        out += '"';
        out += key;
        out += "\":";
        char buf[64];
        std::snprintf(buf, sizeof(buf), "%.6g", val);
        out += buf;
    };
    auto add_int = [&](const char* key, int val) {
        if (!out.empty() && out.back() != '{') out += ',';
        out += '"';
        out += key;
        out += "\":";
        out += std::to_string(val);
    };
    auto add_bool = [&](const char* key, bool val) {
        if (!out.empty() && out.back() != '{') out += ',';
        out += '"';
        out += key;
        out += "\":";
        out += val ? "true" : "false";
    };

    add_str("make", make);
    add_str("model", model);
    add_str("lens", lens);
    add_str("lens_make", lens_make);
    add_str("date_time_str", date_time_str);
    add_num("aperture", static_cast<double>(aperture));
    add_num("focal", static_cast<double>(focal));
    add_num("focal_35mm", static_cast<double>(focal_35mm));
    add_num("focus_distance_m", static_cast<double>(focus_distance_m));
    add_num("iso", static_cast<double>(iso));
    add_int("shutter_num", shutter_speed.first);
    add_int("shutter_den", shutter_speed.second);
    add_int("rating", rating);
    add_bool("is_hdr", is_hdr);
    add_int("width", width);
    add_int("height", height);

    out += '}';
    return out;
}

ExifDisplayMetaData ExifDisplayMetaData::FromJson(const std::string& json) {
    ExifDisplayMetaData meta;
    meta.make           = JsonGetString(json, "make");
    meta.model          = JsonGetString(json, "model");
    meta.lens           = JsonGetString(json, "lens");
    meta.lens_make      = JsonGetString(json, "lens_make");
    meta.date_time_str  = JsonGetString(json, "date_time_str");
    meta.aperture       = static_cast<float>(JsonGetNumber(json, "aperture"));
    meta.focal          = static_cast<float>(JsonGetNumber(json, "focal"));
    meta.focal_35mm     = static_cast<float>(JsonGetNumber(json, "focal_35mm"));
    meta.focus_distance_m = static_cast<float>(JsonGetNumber(json, "focus_distance_m"));
    meta.iso            = static_cast<float>(JsonGetNumber(json, "iso"));
    meta.shutter_speed.first  = static_cast<int>(JsonGetNumber(json, "shutter_num"));
    meta.shutter_speed.second = static_cast<int>(JsonGetNumber(json, "shutter_den"));
    if (meta.shutter_speed.second == 0) meta.shutter_speed.second = 1;
    meta.rating         = NormalizeRating(static_cast<int>(JsonGetNumber(json, "rating")));
    meta.is_hdr         = JsonGetBool(json, "is_hdr");
    meta.width          = static_cast<int>(JsonGetNumber(json, "width"));
    meta.height         = static_cast<int>(JsonGetNumber(json, "height"));
    return meta;
}

// ============================================================
// Image class
// ============================================================

Image::Image() = default;

Image::Image(image_id_t id) : image_id_(id) {}

Image::Image(image_id_t id, const image_path_t& path)
    : image_id_(id), image_path_(path) {
    SetImagePath(path);
}

void Image::LoadOriginalData(ImageBuffer&& buf) {
    original_buffer_ = std::move(buf);
    has_full_img_ = original_buffer_.is_valid();
    if (has_full_img_) {
        __android_log_print(ANDROID_LOG_DEBUG, kTag,
                            "LoadOriginalData: %s (%dx%d)",
                            image_path_.c_str(),
                            original_buffer_.width,
                            original_buffer_.height);
    }
}

void Image::LoadThumbnailData(ImageBuffer&& buf) {
    thumbnail_buffer_ = std::move(buf);
    has_thumbnail_ = thumbnail_buffer_.is_valid();
}

void Image::ClearData() {
    original_buffer_.release();
    has_full_img_ = false;
    checksum_ = 0;
}

void Image::ClearThumbnail() {
    thumbnail_buffer_.release();
    has_thumbnail_ = false;
}

void Image::ComputeChecksum() {
    if (!has_full_img_ || !original_buffer_.is_valid()) {
        checksum_ = 0;
        return;
    }
    const auto& data = original_buffer_.cpu_data;
    if (!data.empty()) {
        checksum_ = Fnv1aHash(data.data(), data.size());
    } else {
        // If there's no cpu_data, hash from the first plane if available
        if (!original_buffer_.planes.empty() && !original_buffer_.planes[0].data.empty()) {
            checksum_ = Fnv1aHash(original_buffer_.planes[0].data.data(),
                                  original_buffer_.planes[0].data.size());
        } else {
            checksum_ = 0;
        }
    }
}

std::string Image::ExifToJson() const {
    return exif_data_.ToJson();
}

ExifDisplayMetaData Image::JsonToExif(const std::string& json) {
    return ExifDisplayMetaData::FromJson(json);
}

void Image::SetExifDisplayMetaData(const ExifDisplayMetaData& data) {
    exif_data_ = data;
    exif_data_.rating = ExifDisplayMetaData::NormalizeRating(exif_data_.rating);
    has_exif_display_ = true;
}

void Image::SetHdrDisplayMetadata(bool is_hdr) {
    is_hdr_ = is_hdr;
    exif_data_.is_hdr = is_hdr;
}

void Image::SetRawColorContext(const RawRuntimeColorContext& ctx) {
    raw_color_context_ = ctx;
    has_raw_color_context_ = ctx.valid;
}

void Image::MarkSyncState(ImageSyncState state) {
    sync_state_.store(state, std::memory_order_release);
}

ImageSyncState Image::GetSyncState() const {
    return sync_state_.load(std::memory_order_acquire);
}

void Image::SetImagePath(const image_path_t& path) {
    image_path_ = path;
    // Extract the filename portion as image_name_
    if (!path.empty()) {
        auto sep = path.find_last_of("/\\");
        image_name_ = (sep != std::string::npos) ? path.substr(sep + 1) : path;
    } else {
        image_name_.clear();
    }
}

} // namespace alcedo
