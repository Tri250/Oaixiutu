// AlcedoAndroid - string / path conversion utilities (UTF-8 focused for Android).
// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <cstdint>
#include <filesystem>
#include <string>
#include <string_view>
#include <vector>

namespace alcedo {
namespace converter {

// Convert a filesystem path to a UTF-8 string.
std::string PathToUtf8(const std::filesystem::path& p);
std::filesystem::path Utf8ToPath(std::string_view s);

// Split a sleeve path (slash-delimited) into segments.
std::vector<std::string> SplitPath(std::string_view path, char delim = '/');
std::string JoinPath(const std::vector<std::string>& segments);

// Lower-case ASCII compare helper.
std::string ToLower(std::string_view s);
bool        IEquals(std::string_view a, std::string_view b);

// Trim whitespace.
std::string Trim(std::string_view s);

// Hex encode / decode (used by hash serialization).
std::string ToHex(const uint8_t* data, size_t len);
std::vector<uint8_t> FromHex(std::string_view hex);

}  // namespace converter
}  // namespace alcedo
