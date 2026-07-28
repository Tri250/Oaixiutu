// AlcedoAndroid - Thread pool implementation.
// SPDX-License-Identifier: GPL-3.0-only
#include "concurrency/thread_pool.hpp"

#include <utility>

namespace alcedo {

ThreadPool::ThreadPool(size_t thread_count) {
  if (thread_count == 0) thread_count = 1;
  workers_.reserve(thread_count);
  for (size_t i = 0; i < thread_count; ++i) {
    workers_.emplace_back([this] { WorkerThread(); });
  }
}

ThreadPool::~ThreadPool() { Shutdown(); }

void ThreadPool::Submit(std::function<void()> task) {
  {
    std::lock_guard<std::mutex> lock(mtx_);
    if (stop_) return;
    tasks_.push(std::move(task));
  }
  condition_.notify_one();
}

void ThreadPool::Shutdown() {
  {
    std::lock_guard<std::mutex> lock(mtx_);
    if (stop_) return;
    stop_ = true;
    // Discard queued tasks that haven't started yet.
    while (!tasks_.empty()) tasks_.pop();
  }
  condition_.notify_all();
  for (auto& w : workers_) {
    if (w.joinable()) w.join();
  }
  workers_.clear();
}

void ThreadPool::WorkerThread() {
  while (true) {
    std::function<void()> task;
    {
      std::unique_lock<std::mutex> lock(mtx_);
      condition_.wait(lock, [this] { return stop_ || !tasks_.empty(); });
      if (stop_ && tasks_.empty()) return;
      task = std::move(tasks_.front());
      tasks_.pop();
    }
    task();
  }
}

}  // namespace alcedo
