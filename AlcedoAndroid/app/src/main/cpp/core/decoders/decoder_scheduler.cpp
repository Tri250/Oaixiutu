#include "decoder_scheduler.h"
#include "core/image/raw_decoder.h"
#include "core/image/metadata_decoder.h"
#include "core/image/thumbnail_decoder.h"
#include <algorithm>
#include <android/log.h>

#define LOG_TAG "AlcedoScheduler"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace alcedo {

// ============================================================
// Constructor / Destructor
// ============================================================

DecoderScheduler::DecoderScheduler() = default;

DecoderScheduler::~DecoderScheduler() {
    stop();
}

// ── Configuration ──

void DecoderScheduler::set_thread_count(int count) {
    thread_count_ = std::max(1, std::min(count, 32));
}

int DecoderScheduler::thread_count() const {
    return thread_count_;
}

void DecoderScheduler::set_max_queue_size(size_t size) {
    max_queue_size_ = std::max(size, static_cast<size_t>(1));
}

size_t DecoderScheduler::max_queue_size() const {
    return max_queue_size_;
}

void DecoderScheduler::set_progress_callback(DecodeProgressFn callback) {
    std::lock_guard<std::mutex> lock(callback_mutex_);
    progress_callback_ = std::move(callback);
}

void DecoderScheduler::set_complete_callback(DecodeCompleteFn callback) {
    std::lock_guard<std::mutex> lock(callback_mutex_);
    complete_callback_ = std::move(callback);
}

// ── Lifecycle ──

void DecoderScheduler::start() {
    if (running_.exchange(true)) return;
    LOGI("DecoderScheduler starting with %d threads", thread_count_);

    workers_.reserve(thread_count_);
    for (int i = 0; i < thread_count_; ++i) {
        workers_.emplace_back(&DecoderScheduler::worker_thread, this);
    }
}

void DecoderScheduler::stop() {
    if (!running_.exchange(false)) return;

    // Wake all workers
    {
        std::lock_guard<std::mutex> lock(cv_mutex_);
        paused_ = false;
    }
    queue_cv_.notify_all();

    // Join all workers
    for (auto& w : workers_) {
        if (w.joinable()) w.join();
    }
    workers_.clear();

    // Cancel all remaining tasks
    cancel_all();

    LOGI("DecoderScheduler stopped");
}

void DecoderScheduler::pause() {
    paused_ = true;
    LOGI("DecoderScheduler paused");
}

void DecoderScheduler::resume() {
    paused_ = false;
    queue_cv_.notify_all();
    LOGI("DecoderScheduler resumed");
}

bool DecoderScheduler::is_running() const { return running_; }
bool DecoderScheduler::is_paused() const { return paused_; }

// ── Task ID generation ──

uint64_t DecoderScheduler::generate_task_id() {
    return next_task_id_.fetch_add(1);
}

// ── Task submission ──

uint64_t DecoderScheduler::submit_metadata(const std::string& file_path,
                                            DecodeTask::Callback on_complete,
                                            DecodeTaskPriority priority) {
    DecodeTask task;
    task.task_id = generate_task_id();
    task.type = DecodeTaskType::METADATA;
    task.priority = priority;
    task.file_path = file_path;
    task.on_complete = std::move(on_complete);
    task.enqueue_time = std::chrono::steady_clock::now();

    {
        std::lock_guard<std::mutex> lock(queue_mutex_);
        if (metadata_queue_.size() < max_queue_size_) {
            metadata_queue_.push(task);
        } else {
            LOGE("Metadata queue full, dropping task %llu", (unsigned long long)task.task_id);
            return 0;
        }
    }

    total_submitted_++;
    queue_cv_.notify_one();
    return task.task_id;
}

uint64_t DecoderScheduler::submit_thumbnail(const std::string& file_path,
                                             DecodeTask::Callback on_complete,
                                             DecodeTaskPriority priority) {
    DecodeTask task;
    task.task_id = generate_task_id();
    task.type = DecodeTaskType::THUMBNAIL;
    task.priority = priority;
    task.file_path = file_path;
    task.on_complete = std::move(on_complete);
    task.enqueue_time = std::chrono::steady_clock::now();

    {
        std::lock_guard<std::mutex> lock(queue_mutex_);
        if (thumbnail_queue_.size() < max_queue_size_) {
            thumbnail_queue_.push(task);
        } else {
            LOGE("Thumbnail queue full, dropping task %llu", (unsigned long long)task.task_id);
            return 0;
        }
    }

    total_submitted_++;
    queue_cv_.notify_one();
    return task.task_id;
}

uint64_t DecoderScheduler::submit_full_decode(const std::string& file_path,
                                               DecodeTask::Callback on_complete,
                                               DecodeTaskPriority priority) {
    DecodeTask task;
    task.task_id = generate_task_id();
    task.type = DecodeTaskType::FULL_DECODE;
    task.priority = priority;
    task.file_path = file_path;
    task.on_complete = std::move(on_complete);
    task.enqueue_time = std::chrono::steady_clock::now();

    {
        std::lock_guard<std::mutex> lock(queue_mutex_);
        if (full_decode_queue_.size() < max_queue_size_) {
            full_decode_queue_.push(task);
        } else {
            LOGE("Full decode queue full, dropping task %llu", (unsigned long long)task.task_id);
            return 0;
        }
    }

    total_submitted_++;
    queue_cv_.notify_one();
    return task.task_id;
}

uint64_t DecoderScheduler::submit_raw_preview(const std::string& file_path,
                                               DecodeTask::Callback on_complete,
                                               DecodeTaskPriority priority) {
    DecodeTask task;
    task.task_id = generate_task_id();
    task.type = DecodeTaskType::RAW_PREVIEW;
    task.priority = priority;
    task.file_path = file_path;
    task.on_complete = std::move(on_complete);
    task.enqueue_time = std::chrono::steady_clock::now();

    {
        std::lock_guard<std::mutex> lock(queue_mutex_);
        if (thumbnail_queue_.size() < max_queue_size_) {
            thumbnail_queue_.push(task);
        } else {
            return 0;
        }
    }

    total_submitted_++;
    queue_cv_.notify_one();
    return task.task_id;
}

uint64_t DecoderScheduler::submit_metadata_from_memory(const uint8_t* data, size_t size,
                                                        const std::string& source_id,
                                                        DecodeTask::Callback on_complete) {
    DecodeTask task;
    task.task_id = generate_task_id();
    task.type = DecodeTaskType::METADATA;
    task.priority = DecodeTaskPriority::HIGH;
    task.file_path = source_id;
    task.file_data.assign(data, data + size);
    task.use_memory_data = true;
    task.on_complete = std::move(on_complete);
    task.enqueue_time = std::chrono::steady_clock::now();

    {
        std::lock_guard<std::mutex> lock(queue_mutex_);
        metadata_queue_.push(task);
    }

    total_submitted_++;
    queue_cv_.notify_one();
    return task.task_id;
}

uint64_t DecoderScheduler::submit_thumbnail_from_memory(const uint8_t* data, size_t size,
                                                         const std::string& source_id,
                                                         DecodeTask::Callback on_complete) {
    DecodeTask task;
    task.task_id = generate_task_id();
    task.type = DecodeTaskType::THUMBNAIL;
    task.priority = DecodeTaskPriority::MEDIUM;
    task.file_path = source_id;
    task.file_data.assign(data, data + size);
    task.use_memory_data = true;
    task.on_complete = std::move(on_complete);
    task.enqueue_time = std::chrono::steady_clock::now();

    {
        std::lock_guard<std::mutex> lock(queue_mutex_);
        thumbnail_queue_.push(task);
    }

    total_submitted_++;
    queue_cv_.notify_one();
    return task.task_id;
}

uint64_t DecoderScheduler::submit_full_decode_from_memory(const uint8_t* data, size_t size,
                                                           const std::string& source_id,
                                                           DecodeTask::Callback on_complete) {
    DecodeTask task;
    task.task_id = generate_task_id();
    task.type = DecodeTaskType::FULL_DECODE;
    task.priority = DecodeTaskPriority::LOW;
    task.file_path = source_id;
    task.file_data.assign(data, data + size);
    task.use_memory_data = true;
    task.on_complete = std::move(on_complete);
    task.enqueue_time = std::chrono::steady_clock::now();

    {
        std::lock_guard<std::mutex> lock(queue_mutex_);
        full_decode_queue_.push(task);
    }

    total_submitted_++;
    queue_cv_.notify_one();
    return task.task_id;
}

// ── Cancellation ──

bool DecoderScheduler::cancel_task(uint64_t task_id) {
    // Check active tasks first
    {
        std::lock_guard<std::mutex> lock(active_mutex_);
        auto it = active_tasks_.find(task_id);
        if (it != active_tasks_.end()) {
            it->second.task.cancelled = true;
            total_cancelled_++;
            return true;
        }
    }

    // Check queues (need to mark as cancelled; we can't remove from priority_queue easily)
    // For simplicity, we mark the task as cancelled and it will be skipped when dequeued
    // Full implementation would rebuild the queue
    LOGW("cancel_task: full queue removal not implemented for task %llu", (unsigned long long)task_id);
    return false;
}

void DecoderScheduler::cancel_all() {
    {
        std::lock_guard<std::mutex> lock(active_mutex_);
        for (auto& [id, entry] : active_tasks_) {
            entry.task.cancelled = true;
        }
    }

    // Clear queues
    {
        std::lock_guard<std::mutex> lock(queue_mutex_);
        total_cancelled_ += metadata_queue_.size() + thumbnail_queue_.size() + full_decode_queue_.size();
        while (!metadata_queue_.empty()) metadata_queue_.pop();
        while (!thumbnail_queue_.empty()) thumbnail_queue_.pop();
        while (!full_decode_queue_.empty()) full_decode_queue_.pop();
    }
}

void DecoderScheduler::cancel_all_of_type(DecodeTaskType type) {
    switch (type) {
        case DecodeTaskType::METADATA: {
            std::lock_guard<std::mutex> lock(queue_mutex_);
            total_cancelled_ += metadata_queue_.size();
            while (!metadata_queue_.empty()) metadata_queue_.pop();
            break;
        }
        case DecodeTaskType::THUMBNAIL: {
            std::lock_guard<std::mutex> lock(queue_mutex_);
            total_cancelled_ += thumbnail_queue_.size();
            while (!thumbnail_queue_.empty()) thumbnail_queue_.pop();
            break;
        }
        case DecodeTaskType::FULL_DECODE: {
            std::lock_guard<std::mutex> lock(queue_mutex_);
            total_cancelled_ += full_decode_queue_.size();
            while (!full_decode_queue_.empty()) full_decode_queue_.pop();
            break;
        }
        default: break;
    }
}

// ── Status ──

size_t DecoderScheduler::queue_size() const {
    std::lock_guard<std::mutex> lock(queue_mutex_);
    return metadata_queue_.size() + thumbnail_queue_.size() + full_decode_queue_.size();
}

size_t DecoderScheduler::active_task_count() const {
    std::lock_guard<std::mutex> lock(active_mutex_);
    return active_tasks_.size();
}

uint64_t DecoderScheduler::total_tasks_submitted() const { return total_submitted_; }
uint64_t DecoderScheduler::total_tasks_completed() const { return total_completed_; }
uint64_t DecoderScheduler::total_tasks_failed() const { return total_failed_; }
uint64_t DecoderScheduler::total_tasks_cancelled() const { return total_cancelled_; }

DecoderScheduler::QueueStats DecoderScheduler::get_queue_stats() const {
    QueueStats stats;
    std::lock_guard<std::mutex> lock(queue_mutex_);
    stats.metadata_queue = metadata_queue_.size();
    stats.thumbnail_queue = thumbnail_queue_.size();
    stats.full_decode_queue = full_decode_queue_.size();
    {
        std::lock_guard<std::mutex> alock(active_mutex_);
        stats.active_count = active_tasks_.size();
    }
    stats.total_submitted = total_submitted_;
    stats.total_completed = total_completed_;
    return stats;
}

std::vector<DecoderScheduler::TaskInfo> DecoderScheduler::get_active_tasks() const {
    std::vector<TaskInfo> result;
    std::lock_guard<std::mutex> lock(active_mutex_);
    for (const auto& [id, entry] : active_tasks_) {
        TaskInfo info;
        info.task_id = id;
        info.type = entry.task.type;
        info.priority = entry.task.priority;
        info.file_path = entry.task.file_path;
        info.progress = entry.task.progress;
        info.cancelled = entry.task.cancelled;
        info.enqueue_time = entry.task.enqueue_time;
        result.push_back(info);
    }
    return result;
}

std::vector<DecoderScheduler::TaskInfo> DecoderScheduler::get_queued_tasks() const {
    std::vector<TaskInfo> result;
    std::lock_guard<std::mutex> lock(queue_mutex_);

    // Note: priority_queue doesn't support iteration; we copy what we can
    // In a production implementation, we'd store a separate list
    auto add_from = [&](const std::priority_queue<DecodeTask>& q) {
        auto copy = q;
        while (!copy.empty()) {
            const auto& t = copy.top();
            TaskInfo info;
            info.task_id = t.task_id;
            info.type = t.type;
            info.priority = t.priority;
            info.file_path = t.file_path;
            info.progress = t.progress;
            info.cancelled = t.cancelled;
            info.enqueue_time = t.enqueue_time;
            result.push_back(info);
            copy.pop();
        }
    };

    add_from(metadata_queue_);
    add_from(thumbnail_queue_);
    add_from(full_decode_queue_);
    return result;
}

// ── Wait for completion ──

bool DecoderScheduler::wait_for_task(uint64_t task_id, int timeout_ms) {
    std::unique_lock<std::mutex> lock(cv_mutex_);
    auto pred = [&]() {
        std::lock_guard<std::mutex> rlock(results_mutex_);
        return completed_results_.find(task_id) != completed_results_.end();
    };

    if (timeout_ms < 0) {
        complete_cv_.wait(lock, pred);
        return true;
    }

    return complete_cv_.wait_for(lock, std::chrono::milliseconds(timeout_ms), pred);
}

void DecoderScheduler::wait_for_all(int timeout_ms) {
    auto start = std::chrono::steady_clock::now();
    while (queue_size() > 0 || active_task_count() > 0) {
        if (timeout_ms > 0) {
            auto elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(
                std::chrono::steady_clock::now() - start).count();
            if (elapsed >= timeout_ms) break;
        }
        std::this_thread::sleep_for(std::chrono::milliseconds(10));
    }
}

// ── Result retrieval ──

bool DecoderScheduler::get_result(uint64_t task_id, DecodeResult& result) {
    std::lock_guard<std::mutex> lock(results_mutex_);
    auto it = completed_results_.find(task_id);
    if (it == completed_results_.end()) return false;
    result = it->second;
    return true;
}

bool DecoderScheduler::has_result(uint64_t task_id) const {
    std::lock_guard<std::mutex> lock(results_mutex_);
    return completed_results_.find(task_id) != completed_results_.end();
}

void DecoderScheduler::clear_result(uint64_t task_id) {
    std::lock_guard<std::mutex> lock(results_mutex_);
    completed_results_.erase(task_id);
}

// ── Thread pool info ──

int DecoderScheduler::active_threads() const {
    std::lock_guard<std::mutex> lock(active_mutex_);
    return static_cast<int>(active_tasks_.size());
}

int DecoderScheduler::idle_threads() const {
    return thread_count_ - active_threads();
}

// ── Worker thread ──

void DecoderScheduler::worker_thread() {
    LOGI("Worker thread started");

    while (running_) {
        DecodeTask task;
        if (!get_next_task(task)) {
            // Wait for tasks
            std::unique_lock<std::mutex> lock(cv_mutex_);
            if (queue_size() == 0) {
                queue_cv_.wait_for(lock, std::chrono::milliseconds(100));
            }
            continue;
        }

        if (task.cancelled) continue;

        // Register as active
        {
            std::lock_guard<std::mutex> lock(active_mutex_);
            active_tasks_[task.task_id] = {task, std::make_shared<DecodeResult>()};
        }

        task.start_time = std::chrono::steady_clock::now();

        // Process task
        DecodeResult result;
        result.task_id = task.task_id;
        result.file_path = task.file_path;

        try {
            process_task(task, result);
        } catch (const std::exception& e) {
            result.success = false;
            result.error_message = std::string("Exception: ") + e.what();
            LOGE("Task %llu failed: %s", (unsigned long long)task.task_id, e.what());
        }

        task.end_time = std::chrono::steady_clock::now();
        result.elapsed_ms = std::chrono::duration<double, std::milli>(
            task.end_time - task.start_time).count();

        // Store result
        {
            std::lock_guard<std::mutex> lock(results_mutex_);
            completed_results_[task.task_id] = result;
        }

        // Remove from active
        {
            std::lock_guard<std::mutex> lock(active_mutex_);
            active_tasks_.erase(task.task_id);
        }

        if (result.success) total_completed_++;
        else total_failed_++;

        // Notify
        notify_complete(result);

        if (task.on_complete) {
            task.on_complete(result.success, result.error_message);
        }

        // Wake waiters
        complete_cv_.notify_all();
    }

    LOGI("Worker thread exiting");
}

bool DecoderScheduler::get_next_task(DecodeTask& task) {
    std::lock_guard<std::mutex> lock(queue_mutex_);

    // Priority order: metadata > thumbnail > full_decode
    if (!metadata_queue_.empty()) {
        task = metadata_queue_.top();
        metadata_queue_.pop();
        return true;
    }
    if (!thumbnail_queue_.empty()) {
        task = thumbnail_queue_.top();
        thumbnail_queue_.pop();
        return true;
    }
    if (!full_decode_queue_.empty()) {
        task = full_decode_queue_.top();
        full_decode_queue_.pop();
        return true;
    }
    return false;
}

void DecoderScheduler::process_task(const DecodeTask& task, DecodeResult& result) {
    notify_progress(task.task_id, 0.0f, "Starting");

    switch (task.type) {
        case DecodeTaskType::METADATA: {
            notify_progress(task.task_id, 0.1f, "Extracting metadata");
            MetadataDecoder meta_dec;
            DecodedMetadata meta;
            bool ok = task.use_memory_data
                ? meta_dec.decode_from_memory(task.file_data.data(), task.file_data.size(), meta)
                : meta_dec.decode(task.file_path, meta);
            if (ok) {
                result.metadata_json = MetadataDecoder::to_json_compact(meta);
                result.success = true;
            } else {
                result.error_message = "Failed to extract metadata";
            }
            notify_progress(task.task_id, 1.0f, "Metadata done");
            break;
        }
        case DecodeTaskType::THUMBNAIL: {
            notify_progress(task.task_id, 0.1f, "Generating thumbnail");
            ThumbnailDecoder thumb_dec;
            ThumbnailOptions opts;
            opts.size = ThumbnailSize::MEDIUM;
            opts.max_dimension = 256;
            ThumbnailResult thumb;
            if (task.use_memory_data) {
                thumb = thumb_dec.generate_from_memory(task.file_data.data(),
                                                         task.file_data.size(),
                                                         task.file_path, opts);
            } else {
                thumb = thumb_dec.generate(task.file_path, opts);
            }
            if (thumb.success) {
                result.thumbnail_data = std::move(thumb.data);
                result.thumbnail_width = thumb.width;
                result.thumbnail_height = thumb.height;
                result.success = true;
            } else {
                result.error_message = thumb.error_message;
            }
            notify_progress(task.task_id, 1.0f, "Thumbnail done");
            break;
        }
        case DecodeTaskType::FULL_DECODE:
        case DecodeTaskType::RAW_PREVIEW: {
            notify_progress(task.task_id, 0.05f, "Decoding RAW");
            RawDecoder raw_dec;
            RawDecodeOptions raw_opts;
            raw_opts.demosaic = DemosaicMethod::RCD;
            raw_opts.output_float = true;
            raw_opts.extract_thumbnail = (task.type == DecodeTaskType::RAW_PREVIEW);
            raw_opts.half_resolution = (task.type == DecodeTaskType::RAW_PREVIEW);

            RawDecodeResult raw_result;
            bool ok = task.use_memory_data
                ? raw_dec.decode_from_memory(task.file_data.data(), task.file_data.size(),
                                              raw_result, raw_opts)
                : raw_dec.decode(task.file_path, raw_result, raw_opts);

            if (ok) {
                result.raw_width = raw_result.width;
                result.raw_height = raw_result.height;
                result.raw_float_data = std::move(raw_result.float_rgb_data);
                result.raw_cfa_data = std::move(raw_result.raw_cfa_data);
                result.thumbnail_data = std::move(raw_result.jpeg_thumbnail);
                result.thumbnail_width = raw_result.thumbnail_width;
                result.thumbnail_height = raw_result.thumbnail_height;
                result.preview_data = std::move(raw_result.jpeg_preview);
                result.preview_width = raw_result.preview_width;
                result.preview_height = raw_result.preview_height;
                result.success = true;
            } else {
                result.error_message = raw_result.error_message;
            }
            notify_progress(task.task_id, 1.0f, "Decode done");
            break;
        }
        default:
            result.error_message = "Unknown task type";
            break;
    }
}

// ── Notification helpers ──

void DecoderScheduler::notify_progress(uint64_t id, float progress, const std::string& stage) {
    std::lock_guard<std::mutex> lock(callback_mutex_);
    if (progress_callback_) {
        progress_callback_(id, progress, stage);
    }
}

void DecoderScheduler::notify_complete(const DecodeResult& result) {
    std::lock_guard<std::mutex> lock(callback_mutex_);
    if (complete_callback_) {
        complete_callback_(result);
    }
}

} // namespace alcedo