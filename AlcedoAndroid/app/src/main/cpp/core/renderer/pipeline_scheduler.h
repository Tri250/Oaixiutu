#pragma once

#include <atomic>
#include <condition_variable>
#include <functional>
#include <future>
#include <memory>
#include <mutex>
#include <queue>
#include <string>
#include <thread>
#include <vector>

#include "image/image_buffer.h"

namespace alcedo {

// ============================================================
// Render types
// ============================================================

enum class RenderType {
    FAST_PREVIEW = 0,
    QUALITY_BASE_PREVIEW = 1,
    DETAIL_ROI_PREVIEW = 2,
    THUMBNAIL = 3,
    FULL_RES_PREVIEW = 4,
    FULL_RES_EXPORT = 5
};

// ============================================================
// Frame metadata
// ============================================================

struct FrameRoiRect {
    float x = 0.0f;
    float y = 0.0f;
    float width = 1.0f;
    float height = 1.0f;
};

struct ViewportRenderRegion {
    int x = 0;
    int y = 0;
    float scale_x = 1.0f;
    float scale_y = 1.0f;
    int reference_width = 0;
    int reference_height = 0;
    int target_width = 0;
    int target_height = 0;
};

struct FramePreviewMetadata {
    FrameRoiRect source_roi_norm;
    int frame_role = 0;  // 0=interactive, 1=quality_base, 2=detail_patch
};

// ============================================================
// Render descriptor
// ============================================================

struct RenderDescriptor {
    RenderType render_type = RenderType::FAST_PREVIEW;
    int x = 0;
    int y = 0;
    float scale_factor_x = 1.0f;
    float scale_factor_y = 1.0f;
    bool use_viewport_region = false;
    int max_edge = 512;
    int decode_res = 0;  // 0=full, 1=half, 2=thumbnail
    FramePreviewMetadata frame_metadata;
};

// ============================================================
// Pipeline task options
// ============================================================

struct PipelineTaskOptions {
    RenderDescriptor render_desc;
    bool is_blocking = false;
    bool is_callback = false;
    bool is_seq_callback = false;
};

// ============================================================
// Pipeline task
// ============================================================

class PipelineTask {
public:
    PipelineTask() = default;

    // Input
    std::shared_ptr<ImageBuffer> input;
    std::string input_path;
    uint64_t input_image_id = 0;

    // Options
    PipelineTaskOptions options;

    // Result (for blocking tasks)
    std::shared_ptr<std::promise<std::shared_ptr<ImageBuffer>>> result;

    // Callbacks
    std::function<void(ImageBuffer&)> callback;
    std::function<void(ImageBuffer&, uint64_t)> seq_callback;

    // Cancel support
    std::function<bool()> cancel_requested;

    // Prepare hook
    std::function<bool(PipelineTask&)> prepare;

    // Task ID
    uint64_t task_id = 0;

    void SetExecutorRenderParams();
    void ResetPreviewRenderParams();
    void ResetThumbnailRenderParams();
};

// ============================================================
// Pipeline scheduler
// ============================================================

class PipelineScheduler {
public:
    PipelineScheduler();
    explicit PipelineScheduler(size_t thread_count);
    ~PipelineScheduler();

    PipelineScheduler(const PipelineScheduler&) = delete;
    PipelineScheduler& operator=(const PipelineScheduler&) = delete;

    // Enqueue a rendering task for async execution.
    void ScheduleTask(PipelineTask&& task);

    // Gracefully shut down the scheduler. Blocks until all queued tasks finish.
    void Shutdown();

    size_t queue_size() const;

private:
    // Thread pool
    std::vector<std::thread> workers_;
    std::queue<std::function<void()>> task_queue_;
    mutable std::mutex queue_mutex_;
    std::condition_variable condition_;
    std::atomic<bool> stop_{false};

    void WorkerLoop();
};

} // namespace alcedo
