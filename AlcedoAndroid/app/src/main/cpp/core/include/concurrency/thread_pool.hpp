// AlcedoAndroid - Thread pool.
// Fixed-size worker pool running copyable callables. Adapted from the desktop
// project; the MPMS-queue optimization is deferred (TODO).
// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <cstddef>
#include <cstdint>
#include <functional>
#include <future>
#include <memory>
#include <queue>
#include <thread>
#include <type_traits>
#include <vector>

#include <condition_variable>
#include <mutex>

namespace alcedo {

class ThreadPool {
 public:
  explicit ThreadPool(size_t thread_count);
  ~ThreadPool();

  void Submit(std::function<void()> task);

  // Stop the pool, discarding any queued tasks that have not started yet, and
  // join all worker threads. In-flight tasks run to completion.
  void Shutdown();

  template <typename F>
  void Submit(F&& task) {
    using TaskT = std::decay_t<F>;
    static_assert(std::is_copy_constructible_v<TaskT>,
                  "ThreadPool::Submit requires a copy-constructible task when using std::function."
                  " Wrap move-only state in std::shared_ptr or provide a copyable callable.");
    Submit(std::function<void()>(std::forward<F>(task)));
  }

 private:
  std::queue<std::function<void()>> tasks_;
  std::mutex                        mtx_;
  std::condition_variable           condition_;
  std::vector<std::thread>          workers_;
  bool                              stop_ = false;

  void WorkerThread();
};

}  // namespace alcedo
