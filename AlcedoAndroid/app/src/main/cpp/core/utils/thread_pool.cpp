#include "thread_pool.h"

#include <algorithm>

namespace alcedo {

ThreadPool::ThreadPool(size_t thread_count) : thread_count_(thread_count) {}

ThreadPool::~ThreadPool() {
    stop();
}

void ThreadPool::start() {
    std::unique_lock<std::mutex> lock(queue_mutex_);
    if (started_) return;

    size_t count = thread_count_;
    if (count == 0) {
        count = std::max<size_t>(1, std::thread::hardware_concurrency());
    }
    thread_count_ = count;
    started_ = true;
    stop_ = false;
    lock.unlock();

    workers_.reserve(count);
    for (size_t i = 0; i < count; ++i) {
        workers_.emplace_back(&ThreadPool::worker_loop, this);
    }
}

void ThreadPool::stop() {
    {
        std::unique_lock<std::mutex> lock(queue_mutex_);
        if (!started_) return;
        stop_ = true;
        started_ = false;
    }
    condition_.notify_all();

    for (auto& worker : workers_) {
        if (worker.joinable()) {
            worker.join();
        }
    }
    workers_.clear();

    // Drain remaining tasks.
    std::unique_lock<std::mutex> lock(queue_mutex_);
    std::queue<std::function<void()>> empty;
    std::swap(tasks_, empty);
}

void ThreadPool::set_thread_count(size_t count) {
    std::unique_lock<std::mutex> lock(queue_mutex_);
    if (!started_) {
        thread_count_ = count;
        return;
    }

    if (count == thread_count_) return;

    if (count > thread_count_) {
        // Expand: add new workers.
        size_t add = count - thread_count_;
        thread_count_ = count;
        lock.unlock();
        for (size_t i = 0; i < add; ++i) {
            workers_.emplace_back(&ThreadPool::worker_loop, this);
        }
        return;
    }

    // Shrink: stop all workers and restart with the smaller count.
    // Save pending tasks so they are not lost.
    std::queue<std::function<void()>> saved_tasks = std::move(tasks_);
    size_t new_count = count;
    stop_ = true;
    started_ = false;
    thread_count_ = new_count;
    lock.unlock();
    condition_.notify_all();

    for (auto& worker : workers_) {
        if (worker.joinable()) worker.join();
    }
    workers_.clear();

    // Restart with the new count.
    stop_ = false;
    started_ = true;
    tasks_ = std::move(saved_tasks);
    for (size_t i = 0; i < new_count; ++i) {
        workers_.emplace_back(&ThreadPool::worker_loop, this);
    }
    condition_.notify_all();
}

size_t ThreadPool::thread_count() const {
    std::lock_guard<std::mutex> lock(queue_mutex_);
    return thread_count_;
}

size_t ThreadPool::queue_size() const {
    std::lock_guard<std::mutex> lock(queue_mutex_);
    return tasks_.size();
}

void ThreadPool::worker_loop() {
    while (true) {
        std::function<void()> task;
        {
            std::unique_lock<std::mutex> lock(queue_mutex_);
            condition_.wait(lock, [this] { return stop_ || !tasks_.empty(); });
            if (stop_ && tasks_.empty()) {
                return;
            }
            task = std::move(tasks_.front());
            tasks_.pop();
        }
        if (task) {
            task();
        }
    }
}

} // namespace alcedo
