// AlcedoAndroid - monotonic / wall clock time provider.
// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <chrono>
#include <cstdint>
#include <ctime>
#include <string>

namespace alcedo {

// Provides wall-clock and monotonic timestamps. Used by history, sleeve and
// logging subsystems so they can be mocked in tests.
class TimeProvider {
 public:
  static std::time_t Now();
  static int64_t NowMillis();
  static int64_t NowMicros();
  static std::string NowIso8601();
};

}  // namespace alcedo
