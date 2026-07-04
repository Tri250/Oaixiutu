// Ported from AlcedoStudio desktop: utils/string/convert.hpp + string/convert.cpp
// SPDX-License-Identifier: GPL-3.0-only
//
// Desktop uses utfcpp to bridge std::wstring (Windows WCHAR) <-> std::string (UTF-8).
// Android is UTF-8 native and has no WCHAR UI surface, so:
//   - ToBytes(wstring)  : decodes UTF-16/UTF-32 wstring -> UTF-8 string (lossless)
//   - FromBytes(string) : encodes UTF-8 string -> wstring (lossless)
// Both are implemented inline here without external deps (codecvt is deprecated
// in C++20, so we do a small manual UTF-8 <-> UTF-32 round trip).

#pragma once

#include <cstdint>
#include <string>

namespace conv {

// wstring (UTF-32 on Linux/Android) -> UTF-8 string
inline std::string ToBytes(const std::wstring& wstr) {
    std::string out;
    out.reserve(wstr.size() * 2);
    for (wchar_t wc : wstr) {
        uint32_t cp = static_cast<uint32_t>(wc);
        if (cp <= 0x7F) {
            out.push_back(static_cast<char>(cp));
        } else if (cp <= 0x7FF) {
            out.push_back(static_cast<char>(0xC0 | (cp >> 6)));
            out.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
        } else if (cp <= 0xFFFF) {
            out.push_back(static_cast<char>(0xE0 | (cp >> 12)));
            out.push_back(static_cast<char>(0x80 | ((cp >> 6) & 0x3F)));
            out.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
        } else {
            out.push_back(static_cast<char>(0xF0 | (cp >> 18)));
            out.push_back(static_cast<char>(0x80 | ((cp >> 12) & 0x3F)));
            out.push_back(static_cast<char>(0x80 | ((cp >> 6) & 0x3F)));
            out.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
        }
    }
    return out;
}

inline std::string ToBytes(std::wstring&& wstr) {
    return ToBytes(wstr);
}

// UTF-8 string -> wstring (UTF-32 on Linux/Android)
inline std::wstring FromBytes(const std::string& str) {
    std::wstring out;
    out.reserve(str.size());
    size_t i = 0;
    while (i < str.size()) {
        uint32_t cp = 0;
        unsigned char c = static_cast<unsigned char>(str[i]);
        int extra = 0;
        if (c <= 0x7F) {
            cp = c;
            extra = 0;
        } else if ((c & 0xE0) == 0xC0) {
            cp = c & 0x1F;
            extra = 1;
        } else if ((c & 0xF0) == 0xE0) {
            cp = c & 0x0F;
            extra = 2;
        } else if ((c & 0xF8) == 0xF0) {
            cp = c & 0x07;
            extra = 3;
        } else {
            // Invalid leading byte; emit replacement and advance.
            out.push_back(static_cast<wchar_t>(0xFFFD));
            ++i;
            continue;
        }
        if (i + extra >= str.size()) {
            break;  // truncated
        }
        bool valid = true;
        for (int k = 1; k <= extra; ++k) {
            unsigned char cc = static_cast<unsigned char>(str[i + k]);
            if ((cc & 0xC0) != 0x80) {
                valid = false;
                break;
            }
            cp = (cp << 6) | (cc & 0x3F);
        }
        if (!valid) {
            out.push_back(static_cast<wchar_t>(0xFFFD));
            ++i;
            continue;
        }
        out.push_back(static_cast<wchar_t>(cp));
        i += 1 + extra;
    }
    return out;
}

inline std::wstring FromBytes(std::string&& str) {
    return FromBytes(str);
}

}  // namespace conv
