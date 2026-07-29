// AlcedoAndroid - Thread pool implementation.
// SPDX-License-Identifier: GPL-3.0-only
#include "concurrency/thread_pool.hpp"

#include <chrono>
#include <utility>

#include "utils/app_logging.hpp"

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
    if (stop_) {
      // Previously this silently dropped the task; log so callers can detect
      // that their work was discarded after shutdown.
      ALOGW("ThreadPool::Submit: task dropped (pool already stopped)");
      return;
    }
    tasks_.push(std::move(task));
  }
  condition_.notify_one();
}

void ThreadPool::Shutdown() {
  {
    std::unique_lock<std::mutex> lock(mtx_);
    if (stop_) return;
    stop_ = true;
  }
  condition_.notify_all();

  // Give queued tasks a bounded chance to run so that any std::promise objects
  // captured by those tasks get their values set instead of throwing
  // std::broken_promise when their futures are waited on. While waiting, the
  // lock is released (wait_for drops it) so workers can drain the queue.
  {
    std::unique_lock<std::mutex> lock(mtx_);
    if (!tasks_.empty()) {
      condition_.wait_for(lock, std::chrono::seconds(2),
                          [this] { return tasks_.empty(); });
    }
    // Discard whatever did not complete in time. std::function<void()> is
    // type-erased so embedded promises cannot be introspected here; their
    // futures will throw std::broken_promise. Log so this is observable.
    if (!tasks_.empty()) {
      ALOGW("ThreadPool: discarding %zu queued task(s) on shutdown; their "
            "futures may throw std::broken_promise",
            tasks_.size());
      while (!tasks_.empty()) tasks_.pop();
    }
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
      if (stop_ && tasks_.empty()) {
        // Notify Shutdown()'s drain wait so it doesn't have to wait the full
        // timeout once the queue is empty.
        condition_.notify_all();
        return;
      }
      task = std::move(tasks_.front());
      tasks_.pop();
      // Wake Shutdown()'s drain wait when the queue becomes empty so it can
      // proceed immediately instead of waiting out the timeout.
      if (tasks_.empty()) condition_.notify_all();
    }
    task();
  }
}

}  // namespace alcedo
