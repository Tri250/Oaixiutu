#include "time_provider.h"

#include <chrono>

namespace alcedo {

uint64_t TimeProvider::NowMillis() {
    return static_cast<uint64_t>(
        std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::system_clock::now().time_since_epoch())
            .count());
}

uint64_t TimeProvider::NowMicros() {
    return static_cast<uint64_t>(
        std::chrono::duration_cast<std::chrono::microseconds>(
            std::chrono::system_clock::now().time_since_epoch())
            .count());
}

void TimeProvider::Refresh() {
    // No-op on Android.
}

} // namespace alcedo
