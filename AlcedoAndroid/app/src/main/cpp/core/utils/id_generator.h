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

    // Atomically generate the next 64-bit incremental ID.
    uint64_t GenerateID64() {
        return current_id_64_.fetch_add(1, std::memory_order_relaxed);
    }

    // Reset the 64-bit generator to a specific starting ID.
    void SetStartID64(uint64_t id) {
        current_id_64_.store(id, std::memory_order_relaxed);
    }

    // Get the current 64-bit ID without incrementing.
    uint64_t GetCurrentID64() const {
        return current_id_64_.load(std::memory_order_relaxed);
    }

    // Generate a random UUID v4 string.
    static std::string GenerateUUID();

private:
    std::atomic<uint32_t> current_id_;
    std::atomic<uint64_t> current_id_64_{1};
};

} // namespace alcedo
