#pragma once

#include <cstdint>

namespace alcedo {

// ============================================================
// Cross-platform time provider (Android NDK implementation)
// ============================================================
class TimeProvider {
public:
    // Current time in milliseconds since epoch.
    static uint64_t NowMillis();

    // Current time in microseconds since epoch.
    static uint64_t NowMicros();

    // Refresh internal state. No-op on Android; provided for desktop compatibility.
    static void Refresh();
};

} // namespace alcedo
