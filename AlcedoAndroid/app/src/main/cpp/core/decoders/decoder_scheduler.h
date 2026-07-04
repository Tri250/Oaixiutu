#pragma once

#include <cstdint>
#include <string>
#include <vector>
#include <queue>
#include <thread>
#include <mutex>
#include <condition_variable>
#include <functional>
#include <atomic>
#include <memory>
#include <map>
#include <chrono>

namespace alcedo {

// ============================================================
// Decode task type / priority
// ============================================================
enum class DecodeTaskType {
    METADATA = 0,      // Highest priority - fast, needed for UI
    THUMBNAIL = 1,     // Medium priority - visual feedback
    FULL_DECODE = 2,   // Low priority - computationally expensive
    RAW_PREVIEW = 3,   // Medium priority
    CANCEL = 99,
};

enum class DecodeTaskPriority {
    HIGH = 0,
    MEDIUM = 1,
    LOW = 2,
};

// ============================================================
// Decode task
// ============================================================
struct DecodeTask {
    uint64_t task_id = 0;
    DecodeTaskType type = DecodeTaskType::METADATA;
    DecodeTaskPriority priority = DecodeTaskPriority::MEDIUM;
    std::string file_path;
    std::vector<uint8_t> file_data;  // Optional: in-memory data
    bool use_memory_data = false;

    // Progress tracking
    std::atomic<float> progress{0.0f};
    std::atomic<bool> cancelled{false};
    std::string current_stage;

    // Callback on completion
    using Callback = std::function<void(bool success, const std::string& error)>;
    Callback on_complete;

    // Timestamps
    std::chrono::steady_clock::time_point enqueue_time;
    std::chrono::steady_clock::time_point start_time;
    std::chrono::steady_clock::time_point end_time;

    // Custom data
    void* user_data = nullptr;
    std::map<std::string, std::string> options;

    // Default constructor
    DecodeTask() = default;

    // Move constructor
    DecodeTask(DecodeTask&& other) noexcept
        : task_id(other.task_id), type(other.type), priority(other.priority),
          file_path(std::move(other.file_path)), file_data(std::move(other.file_data)),
          use_memory_data(other.use_memory_data),
          progress(other.progress.load()), cancelled(other.cancelled.load()),
          current_stage(std::move(other.current_stage)),
          on_complete(std::move(other.on_complete)),
          enqueue_time(other.enqueue_time), start_time(other.start_time), end_time(other.end_time),
          user_data(other.user_data), options(std::move(other.options)) {}

    // Move assignment
    DecodeTask& operator=(DecodeTask&& other) noexcept {
        if (this != &other) {
            task_id = other.task_id;
            type = other.type;
            priority = other.priority;
            file_path = std::move(other.file_path);
            file_data = std::move(other.file_data);
            use_memory_data = other.use_memory_data;
            progress.store(other.progress.load());
            cancelled.store(other.cancelled.load());
            current_stage = std::move(other.current_stage);
            on_complete = std::move(other.on_complete);
            enqueue_time = other.enqueue_time;
            start_time = other.start_time;
            end_time = other.end_time;
            user_data = other.user_data;
            options = std::move(other.options);
        }
        return *this;
    }

    // Delete copy operations (atomics are not copyable)
    DecodeTask(const DecodeTask&) = delete;
    DecodeTask& operator=(const DecodeTask&) = delete;

    // Comparison for priority queue (higher priority = lower value)
    bool operator<(const DecodeTask& other) const {
        if (priority != other.priority)
            return static_cast<int>(priority) > static_cast<int>(other.priority);
        return task_id > other.task_id; // FIFO within same priority
    }
};

// ============================================================
// Decode result
// ============================================================
struct DecodeResult {
    uint64_t task_id = 0;
    bool success = false;
    std::string error_message;
    std::string file_path;

    // RAW decode result
    std::vector<uint16_t> raw_rgb_data;
    std::vector<float> raw_float_data;
    std::vector<uint16_t> raw_cfa_data;
    int raw_width = 0;
    int raw_height = 0;

    // Thumbnail
    std::vector<uint8_t> thumbnail_data;
    int thumbnail_width = 0;
    int thumbnail_height = 0;

    // Preview
    std::vector<uint8_t> preview_data;
    int preview_width = 0;
    int preview_height = 0;

    // Metadata
    std::string metadata_json;

    // Timing
    double elapsed_ms = 0.0;
};

// ============================================================
// Progress callback
// ============================================================
using DecodeProgressFn = std::function<void(uint64_t task_id, float progress, const std::string& stage)>;
using DecodeCompleteFn = std::function<void(const DecodeResult& result)>;

// ============================================================
// Decoder Scheduler
// ============================================================
class DecoderScheduler {
public:
    DecoderScheduler();
    ~DecoderScheduler();

    // ── Configuration ──
    void set_thread_count(int count);
    int thread_count() const;

    void set_max_queue_size(size_t size);
    size_t max_queue_size() const;

    void set_progress_callback(DecodeProgressFn callback);
    void set_complete_callback(DecodeCompleteFn callback);

    // ── Lifecycle ──
    void start();
    void stop();
    void pause();
    void resume();
    bool is_running() const;
    bool is_paused() const;

    // ── Task submission ──
    uint64_t submit_metadata(const std::string& file_path,
                              DecodeTask::Callback on_complete = nullptr,
                              DecodeTaskPriority priority = DecodeTaskPriority::HIGH);
    uint64_t submit_thumbnail(const std::string& file_path,
                               DecodeTask::Callback on_complete = nullptr,
                               DecodeTaskPriority priority = DecodeTaskPriority::MEDIUM);
    uint64_t submit_full_decode(const std::string& file_path,
                                 DecodeTask::Callback on_complete = nullptr,
                                 DecodeTaskPriority priority = DecodeTaskPriority::LOW);
    uint64_t submit_raw_preview(const std::string& file_path,
                                 DecodeTask::Callback on_complete = nullptr,
                                 DecodeTaskPriority priority = DecodeTaskPriority::MEDIUM);

    // Submit from memory
    uint64_t submit_metadata_from_memory(const uint8_t* data, size_t size,
                                          const std::string& source_id,
                                          DecodeTask::Callback on_complete = nullptr);
    uint64_t submit_thumbnail_from_memory(const uint8_t* data, size_t size,
                                           const std::string& source_id,
                                           DecodeTask::Callback on_complete = nullptr);
    uint64_t submit_full_decode_from_memory(const uint8_t* data, size_t size,
                                             const std::string& source_id,
                                             DecodeTask::Callback on_complete = nullptr);

    // ── Cancellation ──
    bool cancel_task(uint64_t task_id);
    void cancel_all();
    void cancel_all_of_type(DecodeTaskType type);

    // ── Status ──
    size_t queue_size() const;
    size_t active_task_count() const;
    uint64_t total_tasks_submitted() const;
    uint64_t total_tasks_completed() const;
    uint64_t total_tasks_failed() const;
    uint64_t total_tasks_cancelled() const;

    struct QueueStats {
        size_t metadata_queue = 0;
        size_t thumbnail_queue = 0;
        size_t full_decode_queue = 0;
        size_t active_count = 0;
        size_t total_submitted = 0;
        size_t total_completed = 0;
    };
    QueueStats get_queue_stats() const;

    struct TaskInfo {
        uint64_t task_id;
        DecodeTaskType type;
        DecodeTaskPriority priority;
        std::string file_path;
        float progress;
        bool cancelled;
        std::chrono::steady_clock::time_point enqueue_time;
    };
    std::vector<TaskInfo> get_active_tasks() const;
    std::vector<TaskInfo> get_queued_tasks() const;

    // ── Wait for completion ──
    bool wait_for_task(uint64_t task_id, int timeout_ms = -1);
    void wait_for_all(int timeout_ms = -1);

    // ── Result retrieval ──
    bool get_result(uint64_t task_id, DecodeResult& result);
    bool has_result(uint64_t task_id) const;
    void clear_result(uint64_t task_id);

    // ── Thread pool info ──
    int active_threads() const;
    int idle_threads() const;

private:
    // ── Internal structures ──
    struct TaskEntry {
        DecodeTask task;
        std::shared_ptr<DecodeResult> result;
    };

    // ── Thread pool ──
    void worker_thread();
    void process_task(const DecodeTask& task, DecodeResult& result);

    // ── State ──
    std::atomic<bool> running_{false};
    std::atomic<bool> paused_{false};
    int thread_count_ = 4;
    size_t max_queue_size_ = 512;

    // ── Task ID counter ──
    std::atomic<uint64_t> next_task_id_{1};

    // ── Priority queues per type ──
    std::priority_queue<DecodeTask> metadata_queue_;
    std::priority_queue<DecodeTask> thumbnail_queue_;
    std::priority_queue<DecodeTask> full_decode_queue_;
    mutable std::mutex queue_mutex_;

    // ── Active tasks ──
    std::map<uint64_t, TaskEntry> active_tasks_;
    mutable std::mutex active_mutex_;

    // ── Completed results ──
    std::map<uint64_t, DecodeResult> completed_results_;
    mutable std::mutex results_mutex_;

    // ── Thread pool ──
    std::vector<std::thread> workers_;
    std::condition_variable queue_cv_;
    std::condition_variable complete_cv_;
    std::mutex cv_mutex_;

    // ── Callbacks ──
    DecodeProgressFn progress_callback_;
    DecodeCompleteFn complete_callback_;
    mutable std::mutex callback_mutex_;

    // ── Stats ──
    std::atomic<uint64_t> total_submitted_{0};
    std::atomic<uint64_t> total_completed_{0};
    std::atomic<uint64_t> total_failed_{0};
    std::atomic<uint64_t> total_cancelled_{0};

    // ── Helpers ──
    bool get_next_task(DecodeTask& task);
    void notify_progress(uint64_t id, float progress, const std::string& stage);
    void notify_complete(const DecodeResult& result);
    uint64_t generate_task_id();
};

} // namespace alcedo