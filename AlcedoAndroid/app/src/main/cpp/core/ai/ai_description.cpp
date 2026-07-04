// Ported from AlcedoStudio desktop: ai/ai_description.cpp
// SPDX-License-Identifier: GPL-3.0-only
//
// Tags JSON serialization uses a tiny inline parser (no nlohmann dependency).
// Only supports the `["a","b",...]` array shape the desktop stores; a malformed
// store surfaces as "no tags", matching the desktop's tolerant behavior.

#include "ai_description.h"

#include <cstddef>
#include <cstdio>
#include <stdexcept>
#include <string>
#include <vector>

namespace alcedo {

namespace {

// Escape a string for JSON: ", \, control chars.
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
                    std::snprintf(buf, sizeof(buf), "\\u%04x", c);
                    out += buf;
                } else {
                    out += c;
                }
                break;
        }
    }
    return out;
}

// Skip whitespace.
const char* SkipWs(const char* p, const char* end) {
    while (p < end && (*p == ' ' || *p == '\t' || *p == '\n' || *p == '\r')) ++p;
    return p;
}

// Parse a JSON string literal beginning at *p == '"'. On success returns true
// and advances *p past the closing quote.
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
                        unsigned int cp = std::stoul(buf, nullptr, 16);
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

}  // namespace

std::vector<std::string> AiDescription::Tags() const {
    std::vector<std::string> tags;
    if (tags_json_.empty()) {
        return tags;
    }
    const char* p = tags_json_.data();
    const char* end = p + tags_json_.size();
    p = SkipWs(p, end);
    if (p >= end || *p != '[') {
        return tags;  // malformed
    }
    ++p;
    p = SkipWs(p, end);
    if (p < end && *p == ']') {
        return tags;  // empty array
    }
    while (p < end) {
        std::string s;
        if (!ParseJsonString(p, end, s)) {
            return tags;  // tolerant: treat as no tags
        }
        tags.push_back(std::move(s));
        p = SkipWs(p, end);
        if (p < end && *p == ',') {
            ++p;
            p = SkipWs(p, end);
            continue;
        }
        if (p < end && *p == ']') {
            ++p;
            return tags;
        }
        return tags;  // malformed
    }
    return tags;
}

void AiDescription::SetTags(const std::vector<std::string>& tags) {
    std::string out = "[";
    for (size_t i = 0; i < tags.size(); ++i) {
        if (i) out += ',';
        out += '"';
        out += EscapeJsonString(tags[i]);
        out += '"';
    }
    out += ']';
    tags_json_ = std::move(out);
}

bool AiDescription::IsValid() const {
    return file_id_ != 0 && !task_id_.empty() && !provider_id_.empty() && !model_id_.empty();
}

}  // namespace alcedo
