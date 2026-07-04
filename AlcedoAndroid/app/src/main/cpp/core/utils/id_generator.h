#pragma once

#include <atomic>
#include <cstdint>
#include <string>

namespace alcedo {

// ============================================================
// Thread-safe ID Generator
// ============================================================
class IDGenerator {
public:
    IDGenerator() : current_id_(1) {}

    // Atomically generate the next incremental ID.
    uint32_t GenerateID() {
        return current_id_.fetch_add(1, std::memory_order_relaxed);
    }

    // Reset the generator to a specific starting ID.
    void SetStartID(uint32_t id) {
        current_id_.store(id, std::memory_order_relaxed);
    }

    // Get the current ID without incrementing.
    uint32_t GetCurrentID() const {
        return current_id_.load(std::memory_order_relaxed);
    }

    // Generate a random UUID v4 string.
    static std::string GenerateUUID();

private:
    std::atomic<uint32_t> current_id_;
};

} // namespace alcedo
