// AlcedoAndroid - 128-bit hash type (self-contained, no xxhash dependency).
// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <array>
#include <cstdint>
#include <functional>
#include <iomanip>
#include <sstream>
#include <string>

namespace alcedo {

// A 128-bit hash value used for version/commit identity. Implemented with a
// self-contained FNV-1a / splitmix64 mix so the Android build does not need to
// link xxhash.
class Hash128 {
 public:
  Hash128() : low_(0), high_(0) {}
  Hash128(uint64_t low, uint64_t high) : low_(low), high_(high) {}

  uint64_t low64() const { return low_; }
  uint64_t high64() const { return high_; }

  std::array<uint8_t, 16> ToBytes() const {
    std::array<uint8_t, 16> arr{};
    uint64_t lo = low_;
    uint64_t hi = high_;
    for (int i = 0; i < 8; ++i) {
      arr[i]     = static_cast<uint8_t>(lo & 0xFF);
      arr[i + 8] = static_cast<uint8_t>(hi & 0xFF);
      lo >>= 8;
      hi >>= 8;
    }
    return arr;
  }

  std::string ToString() const {
    std::ostringstream oss;
    oss << std::hex << std::setfill('0');
    oss << std::setw(16) << high_;
    oss << std::setw(16) << low_;
    return oss.str();
  }

  static Hash128 FromString(const std::string& str) {
    if (str.length() != 32) {
      return Hash128{};
    }
    uint64_t high = std::stoull(str.substr(0, 16), nullptr, 16);
    uint64_t low  = std::stoull(str.substr(16, 16), nullptr, 16);
    return Hash128(low, high);
  }

  std::size_t ToSizeT() const noexcept {
    return static_cast<std::size_t>(low_ ^ high_);
  }

  bool operator==(const Hash128& other) const noexcept {
    return low_ == other.low_ && high_ == other.high_;
  }
  bool operator!=(const Hash128& other) const noexcept { return !(*this == other); }
  bool operator<(const Hash128& other) const noexcept {
    return (high_ < other.high_) || (high_ == other.high_ && low_ < other.low_);
  }

  static Hash128 Compute(const void* data, size_t length, uint64_t seed = 0) {
    // FNV-1a 128-bit approximation using two splitmix64 streams.
    const uint8_t* bytes = static_cast<const uint8_t*>(data);
    uint64_t h1 = 0xcbf29ce484222325ULL ^ seed;
    uint64_t h2 = 0x9e3779b97f4a7c15ULL ^ seed;
    for (size_t i = 0; i < length; ++i) {
      h1 ^= bytes[i];
      h1 *= 0x100000001b3ULL;
      h2 ^= bytes[i];
      h2 ^= h2 >> 33;
      h2 *= 0xff51afd7ed558ccdULL;
      h2 ^= h2 >> 33;
      h2 *= 0xc4ceb9fe1a85ec53ULL;
      h2 ^= h2 >> 33;
    }
    return Hash128(h1, h2);
  }

  static Hash128 Blend(const Hash128& h1, const Hash128& h2) {
    uint64_t lo = h1.low64() ^ (h2.high64() + 0x9e3779b97f4a7c15ULL);
    uint64_t hi = h1.high64() ^ (h2.low64() + 0x85ebca77c2b2ae63ULL);
    return Hash128(lo, hi);
  }

 private:
  uint64_t low_;
  uint64_t high_;
};

}  // namespace alcedo

namespace std {
template <>
struct hash<alcedo::Hash128> {
  std::size_t operator()(const alcedo::Hash128& h) const noexcept {
    auto h1 = std::hash<uint64_t>{}(h.low64());
    auto h2 = std::hash<uint64_t>{}(h.high64());
    return h1 ^ (h2 + 0x9e3779b97f4a7c15ULL + (h1 << 6) + (h1 >> 2));
  }
};
}  // namespace std
