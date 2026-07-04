#include "image_pool.h"
#include <cstdlib>
#include <cstring>
#include <algorithm>

namespace alcedo {

ImagePoolManager::ImagePoolManager(size_t max_memory_bytes)
    : max_memory_bytes_(max_memory_bytes),
      used_memory_(0),
      total_allocated_(0) {}

ImagePoolManager::~ImagePoolManager() {
    ReleaseAll();
}

size_t ImagePoolManager::BufferSize(int w, int h, int ch) const {
    return static_cast<size_t>(w) * static_cast<size_t>(h) * static_cast<size_t>(ch) * sizeof(float);
}

float* ImagePoolManager::Acquire(int width, int height, int channels) {
    std::lock_guard<std::mutex> lock(mutex_);

    size_t required = BufferSize(width, height, channels);

    // Try to find a matching unused buffer in the pool.
    for (auto it = pool_.begin(); it != pool_.end(); ++it) {
        if (!it->in_use && it->width == width && it->height == height &&
            it->channels == channels && it->capacity >= required) {
            it->in_use = true;
            active_[it->data] = it;
            used_memory_ += required;
            return it->data;
        }
    }

    // No matching buffer found. Evict unused buffers if we are over budget.
    while (total_allocated_ + required > max_memory_bytes_ && EvictOne()) {
        // Keep evicting until we have room.
    }

    // Allocate a new buffer.
    float* data = static_cast<float*>(std::malloc(required));
    if (!data) {
        return nullptr;
    }
    std::memset(data, 0, required);

    PooledBuffer buf;
    buf.data = data;
    buf.width = width;
    buf.height = height;
    buf.channels = channels;
    buf.capacity = required;
    buf.in_use = true;

    pool_.push_front(buf);
    auto it = pool_.begin();
    active_[data] = it;
    used_memory_ += required;
    total_allocated_ += required;

    return data;
}

void ImagePoolManager::Release(float* buffer) {
    if (!buffer) return;

    std::lock_guard<std::mutex> lock(mutex_);

    auto it = active_.find(buffer);
    if (it == active_.end()) {
        return;
    }

    auto pool_it = it->second;
    size_t buf_size = BufferSize(pool_it->width, pool_it->height, pool_it->channels);

    pool_it->in_use = false;
    used_memory_ -= buf_size;
    active_.erase(it);

    // If total memory is still over budget, evict unused buffers.
    while (total_allocated_ > max_memory_bytes_ && EvictOne()) {
        // Keep evicting until under budget.
    }
}

void ImagePoolManager::GarbageCollect() {
    std::lock_guard<std::mutex> lock(mutex_);

    auto it = pool_.begin();
    while (it != pool_.end()) {
        if (!it->in_use) {
            std::free(it->data);
            total_allocated_ -= it->capacity;
            it = pool_.erase(it);
        } else {
            ++it;
        }
    }
}

void ImagePoolManager::ReleaseAll() {
    std::lock_guard<std::mutex> lock(mutex_);

    for (auto& buf : pool_) {
        if (buf.data) {
            std::free(buf.data);
        }
    }
    pool_.clear();
    active_.clear();
    used_memory_ = 0;
    total_allocated_ = 0;
}

size_t ImagePoolManager::GetUsedMemory() const {
    std::lock_guard<std::mutex> lock(mutex_);
    return used_memory_;
}

size_t ImagePoolManager::GetTotalAllocated() const {
    std::lock_guard<std::mutex> lock(mutex_);
    return total_allocated_;
}

size_t ImagePoolManager::GetPooledCount() const {
    std::lock_guard<std::mutex> lock(mutex_);
    size_t count = 0;
    for (const auto& buf : pool_) {
        if (!buf.in_use) ++count;
    }
    return count;
}

size_t ImagePoolManager::GetActiveCount() const {
    std::lock_guard<std::mutex> lock(mutex_);
    return active_.size();
}

bool ImagePoolManager::EvictOne() {
    // Find the last unused buffer (LRU eviction)
    std::list<PooledBuffer>::iterator target = pool_.end();
    for (auto it = pool_.begin(); it != pool_.end(); ++it) {
        if (!it->in_use) {
            target = it;  // keep updating to get the last one (LRU)
        }
    }
    if (target != pool_.end()) {
        if (target->data) {
            std::free(target->data);
        }
        total_allocated_ -= target->capacity;
        pool_.erase(target);
        return true;
    }
    return false;
}

} // namespace alcedo
