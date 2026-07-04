#include "id_generator.h"

#include <chrono>
#include <random>

namespace alcedo {

std::string IDGenerator::GenerateUUID() {
    // Use a thread-local RNG to avoid contention and re-seeding on every call.
    thread_local std::mt19937_64 rng([]() {
        uint64_t seed = static_cast<uint64_t>(
            std::chrono::steady_clock::now().time_since_epoch().count());
        std::random_device rd;
        // Mix in extra entropy if random_device is non-deterministic.
        seed ^= (static_cast<uint64_t>(rd()) << 32) | static_cast<uint64_t>(rd());
        return seed;
    }());

    std::uniform_int_distribution<int> dist(0, 15);
    std::uniform_int_distribution<int> dist_y(8, 11);

    // UUID v4 template: xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx
    std::string uuid = "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx";
    for (char& c : uuid) {
        if (c == 'x') {
            c = "0123456789abcdef"[dist(rng)];
        } else if (c == 'y') {
            c = "0123456789abcdef"[dist_y(rng)];
        }
    }
    return uuid;
}

} // namespace alcedo
