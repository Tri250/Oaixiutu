// AlcedoAndroid - concurrent queue primitives.
// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <condition_variable>
#include <cstddef>
#include <deque>
#include <mutex>
#include <optional>

namespace alcedo {

// A simple blocking multi-producer/multi-consumer queue.
template <typename T>
class ConcurrentBlockingQueue {
 public:
  ConcurrentBlockingQueue() = default;
  explicit ConcurrentBlockingQueue(size_t max_size) : max_size_(max_size) {}

  void Push(T value) {
    std::unique_lock<std::mutex> lock(mtx_);
    not_full_.wait(lock, [this] { return max_size_ == 0 || queue_.size() < max_size_; });
    queue_.push_back(std::move(value));
    not_empty_.notify_one();
  }

  std::optional<T> Pop() {
    std::unique_lock<std::mutex> lock(mtx_);
    not_empty_.wait(lock, [this] { return !queue_.empty() || closed_; });
    if (queue_.empty()) return std::nullopt;
    T value = std::move(queue_.front());
    queue_.pop_front();
    not_full_.notify_one();
    return value;
  }

  bool TryPop(T& out) {
    std::lock_guard<std::mutex> lock(mtx_);
    if (queue_.empty()) return false;
    out = std::move(queue_.front());
    queue_.pop_front();
    not_full_.notify_one();
    return true;
  }

  void Close() {
    std::lock_guard<std::mutex> lock(mtx_);
    closed_ = true;
    not_empty_.notify_all();
    not_full_.notify_all();
  }

  size_t Size() {
    std::lock_guard<std::mutex> lock(mtx_);
    return queue_.size();
  }

  void Clear() {
    std::lock_guard<std::mutex> lock(mtx_);
    queue_.clear();
    not_full_.notify_all();
  }

 private:
  std::deque<T>            queue_;
  std::mutex               mtx_;
  std::condition_variable  not_empty_;
  std::condition_variable  not_full_;
  size_t                   max_size_ = 0;
  bool                     closed_   = false;
};

}  // namespace alcedo
