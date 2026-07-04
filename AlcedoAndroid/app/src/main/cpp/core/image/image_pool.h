#pragma once

#include "image_buffer.h"
#include <unordered_map>
#include <list>
#include <mutex>
#include <cstddef>

namespace alcedo {

struct PooledBuffer {
    float* data;
    int width;
    int height;
    int channels;
    size_t capacity;
    bool in_use;
};

class ImagePoolManager {
public:
    explicit ImagePoolManager(size_t max_memory_bytes = 512 * 1024 * 1024); // 512MB default
    ~ImagePoolManager();

    float* Acquire(int width, int height, int channels);
    void Release(float* buffer);

    void GarbageCollect();
    void ReleaseAll();

    size_t GetUsedMemory() const;
    size_t GetTotalAllocated() const;
    size_t GetPooledCount() const;
    size_t GetActiveCount() const;

private:
    size_t max_memory_bytes_;
    size_t used_memory_;
    size_t total_allocated_;
    std::list<PooledBuffer> pool_;
    std::unordered_map<float*, std::list<PooledBuffer>::iterator> active_;
    mutable std::mutex mutex_;

    size_t BufferSize(int w, int h, int ch) const;
    bool EvictOne();
};

} // namespace alcedo
