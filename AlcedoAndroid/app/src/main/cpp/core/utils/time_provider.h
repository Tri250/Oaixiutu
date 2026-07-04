#pragma once

#include <cstdint>

namespace alcedo {

// ============================================================
// Cross-platform time provider (Android NDK implementation)
// ============================================================
class TimeProvider {
public:
    // Current time in milliseconds since epoch (wall clock).
    static uint64_t NowMillis();

    // Current time in microseconds since epoch (wall clock).
    static uint64_t NowMicros();

    // Current time in nanoseconds since epoch (wall clock).
    static uint64_t NowNanos();

    // Current time in seconds since epoch (wall clock).
    static uint64_t NowSeconds();

    // Monotonic time in milliseconds (steady clock, not affected by system time changes).
    static uint64_t SteadyNowMillis();

    // Monotonic time in microseconds (steady clock).
    static uint64_t SteadyNowMicros();

    // Refresh internal state. No-op on Android; provided for desktop compatibility.
    static void Refresh();
};

} // namespace alcedo
