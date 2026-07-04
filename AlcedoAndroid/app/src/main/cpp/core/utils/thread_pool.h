#pragma once

#include <condition_variable>
#include <cstddef>
#include <functional>
#include <future>
#include <mutex>
#include <queue>
#include <thread>
#include <type_traits>
#include <vector>

namespace alcedo {

// ============================================================
// Generic Thread Pool
// ============================================================
class ThreadPool {
public:
    explicit ThreadPool(size_t thread_count = 0);
    ~ThreadPool();

    // Start worker threads. If thread_count is 0, uses hardware_concurrency.
    void start();

    // Stop all workers gracefully. Waits for currently running tasks to finish.
    void stop();

    // Submit a task to the pool and receive a future for its result.
    template <typename F, typename... Args>
    auto enqueue(F&& f, Args&&... args)
        -> std::future<std::invoke_result_t<F, Args...>> {
        using return_type = std::invoke_result_t<F, Args...>;

        auto task = std::make_shared<std::packaged_task<return_type()>>(
            std::bind(std::forward<F>(f), std::forward<Args>(args)...));

        std::future<return_type> result = task->get_future();
        {
            std::unique_lock<std::mutex> lock(queue_mutex_);
            if (stop_) {
                throw std::runtime_error("Cannot enqueue on stopped ThreadPool");
            }
            tasks_.emplace([task]() { (*task)(); });
        }
        condition_.notify_one();
        return result;
    }

    // Resize the thread pool. Increasing count while running is supported;
    // decreasing requires a stop/start cycle internally.
    void set_thread_count(size_t count);

    size_t thread_count() const;
    size_t queue_size() const;

    // Block until all queued tasks have finished executing.
    void wait_idle();

    // Check whether the pool has been started.
    bool is_started() const;

private:
    void worker_loop();

    size_t thread_count_;
    std::vector<std::thread> workers_;
    std::queue<std::function<void()>> tasks_;

    mutable std::mutex queue_mutex_;
    std::condition_variable condition_;
    std::condition_variable idle_condition_;
    int active_count_ = 0;
    bool stop_ = false;
    bool started_ = false;
};

} // namespace alcedo
