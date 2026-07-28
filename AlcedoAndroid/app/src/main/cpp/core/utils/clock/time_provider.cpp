// AlcedoAndroid - TimeProvider implementation.
// SPDX-License-Identifier: GPL-3.0-only
#include "utils/time_provider.hpp"

#include <chrono>
#include <cstdio>
#include <ctime>

namespace alcedo {

std::time_t TimeProvider::Now() {
  return std::chrono::system_clock::to_time_t(std::chrono::system_clock::now());
}

int64_t TimeProvider::NowMillis() {
  return std::chrono::duration_cast<std::chrono::milliseconds>(
             std::chrono::steady_clock::now().time_since_epoch())
      .count();
}

int64_t TimeProvider::NowMicros() {
  return std::chrono::duration_cast<std::chrono::microseconds>(
             std::chrono::steady_clock::now().time_since_epoch())
      .count();
}

std::string TimeProvider::NowIso8601() {
  std::time_t now = Now();
  std::tm tm{};
  gmtime_r(&now, &tm);
  char buf[32];
  std::strftime(buf, sizeof(buf), "%Y-%m-%dT%H:%M:%SZ", &tm);
  return std::string(buf);
}

}  // namespace alcedo
