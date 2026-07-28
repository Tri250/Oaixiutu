// AlcedoAndroid - string/path conversion utilities implementation.
// SPDX-License-Identifier: GPL-3.0-only
#include "utils/converter.hpp"

#include <algorithm>
#include <cctype>

namespace alcedo {
namespace converter {

std::string PathToUtf8(const std::filesystem::path& p) {
  return p.generic_string();
}

std::filesystem::path Utf8ToPath(std::string_view s) {
  return std::filesystem::path(std::string(s));
}

std::vector<std::string> SplitPath(std::string_view path, char delim) {
  std::vector<std::string> out;
  size_t start = 0;
  while (start <= path.size()) {
    size_t pos = path.find(delim, start);
    if (pos == std::string_view::npos) {
      out.emplace_back(path.substr(start));
      break;
    }
    out.emplace_back(path.substr(start, pos - start));
    start = pos + 1;
  }
  return out;
}

std::string JoinPath(const std::vector<std::string>& segments) {
  std::string out;
  for (size_t i = 0; i < segments.size(); ++i) {
    if (!out.empty() && out.back() != '/') out.push_back('/');
    out += segments[i];
  }
  return out;
}

std::string ToLower(std::string_view s) {
  std::string out(s);
  std::transform(out.begin(), out.end(), out.begin(),
                 [](unsigned char c) { return static_cast<char>(std::tolower(c)); });
  return out;
}

bool IEquals(std::string_view a, std::string_view b) {
  if (a.size() != b.size()) return false;
  for (size_t i = 0; i < a.size(); ++i) {
    if (std::tolower(static_cast<unsigned char>(a[i])) !=
        std::tolower(static_cast<unsigned char>(b[i]))) {
      return false;
    }
  }
  return true;
}

std::string Trim(std::string_view s) {
  size_t b = 0;
  while (b < s.size() && std::isspace(static_cast<unsigned char>(s[b]))) ++b;
  size_t e = s.size();
  while (e > b && std::isspace(static_cast<unsigned char>(s[e - 1]))) --e;
  return std::string(s.substr(b, e - b));
}

static const char* kHexDigits = "0123456789abcdef";

std::string ToHex(const uint8_t* data, size_t len) {
  std::string out;
  out.reserve(len * 2);
  for (size_t i = 0; i < len; ++i) {
    out.push_back(kHexDigits[(data[i] >> 4) & 0x0F]);
    out.push_back(kHexDigits[data[i] & 0x0F]);
  }
  return out;
}

static int HexVal(char c) {
  if (c >= '0' && c <= '9') return c - '0';
  if (c >= 'a' && c <= 'f') return c - 'a' + 10;
  if (c >= 'A' && c <= 'F') return c - 'A' + 10;
  return -1;
}

std::vector<uint8_t> FromHex(std::string_view hex) {
  std::vector<uint8_t> out;
  out.reserve(hex.size() / 2);
  for (size_t i = 0; i + 1 < hex.size(); i += 2) {
    int hi = HexVal(hex[i]);
    int lo = HexVal(hex[i + 1]);
    if (hi < 0 || lo < 0) break;
    out.push_back(static_cast<uint8_t>((hi << 4) | lo));
  }
  return out;
}

}  // namespace converter
}  // namespace alcedo
