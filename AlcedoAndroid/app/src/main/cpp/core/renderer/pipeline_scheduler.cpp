#include "renderer/pipeline_scheduler.h"
#include "edit/pipeline_service.h"
#include "utils/app_logging.h"

#include <algorithm>
#include <utility>

#define PS_TAG "PipelineScheduler"

namespace alcedo {

// ============================================================
// PipelineTask implementation
// ============================================================

void PipelineTask::SetExecutorRenderParams() {
    auto& desc = options.render_desc;

    switch (desc.render_type) {
        case RenderType::FAST_PREVIEW:
            // Fast interactive preview: capped at 2560 edge, bilinear downscale
            desc.max_edge = 2560;
            desc.decode_res = 1;  // half-res decode
            desc.frame_metadata.frame_role = 0;  // interactive
            break;

        case RenderType::QUALITY_BASE_PREVIEW:
            // Quality base: up to 4096, area downscale for better quality
            desc.max_edge = 4096;
            desc.decode_res = 0;  // full decode
            desc.frame_metadata.frame_role = 1;  // quality_base
            break;

        case RenderType::DETAIL_ROI_PREVIEW:
            // Detail patch for ROI region — keep high quality in ROI
            desc.max_edge = 4096;
            desc.decode_res = 0;
            desc.frame_metadata.frame_role = 2;  // detail_patch
            break;

        case RenderType::THUMBNAIL:
            // Thumbnail: force CPU output, small edge
            desc.max_edge = 512;
            desc.decode_res = 2;  // thumbnail decode
            desc.frame_metadata.frame_role = 0;
            break;

        case RenderType::FULL_RES_PREVIEW:
            // Full resolution preview
            desc.max_edge = 0;  // no cap
            desc.decode_res = 0;
            desc.frame_metadata.frame_role = 1;
            break;

        case RenderType::FULL_RES_EXPORT:
            // Full resolution export: force CPU output, no cap
            desc.max_edge = 0;  // no cap — render at native resolution
            desc.decode_res = 0;
            desc.frame_metadata.frame_role = 1;
            break;
    }

    ALCEDO_LOGD(PS_TAG, "SetExecutorRenderParams: type=%d max_edge=%d decode_res=%d frame_role=%d",
                static_cast<int>(desc.render_type), desc.max_edge, desc.decode_res,
                desc.frame_metadata.frame_role);
}

void PipelineTask::ResetPreviewRenderParams() {
    auto& desc = options.render_desc;
    desc.render_type = RenderType::FAST_PREVIEW;
    desc.max_edge = 2560;
    desc.decode_res = 1;
    desc.scale_factor_x = 1.0f;
    desc.scale_factor_y = 1.0f;
    desc.use_viewport_region = false;
    desc.frame_metadata = FramePreviewMetadata{};
    ALCEDO_LOGD(PS_TAG, "ResetPreviewRenderParams");
}

void PipelineTask::ResetThumbnailRenderParams() {
    auto& desc = options.render_desc;
    desc.render_type = RenderType::THUMBNAIL;
    desc.max_edge = 512;
    desc.decode_res = 2;
    desc.scale_factor_x = 1.0f;
    desc.scale_factor_y = 1.0f;
    desc.use_viewport_region = false;
    desc.frame_metadata = FramePreviewMetadata{};
    ALCEDO_LOGD(PS_TAG, "ResetThumbnailRenderParams");
}

// ============================================================
// PipelineScheduler implementation
// ============================================================

PipelineScheduler::PipelineScheduler()
    : PipelineScheduler(std::max(1u, std::thread::hardware_concurrency())) {}

PipelineScheduler::PipelineScheduler(size_t thread_count) {
    ALCEDO_LOGI(PS_TAG, "Creating PipelineScheduler with %zu worker threads", thread_count);

    for (size_t i = 0; i < thread_count; ++i) {
        workers_.emplace_back(&PipelineScheduler::WorkerLoop, this);
    }
}

PipelineScheduler::~PipelineScheduler() {
    Shutdown();
}

void PipelineScheduler::ScheduleTask(PipelineTask&& task) {
    auto& desc = task.options.render_desc;

    ALCEDO_LOGI(PS_TAG, "ScheduleTask: id=%llu type=%d blocking=%d path=%s",
                static_cast<unsigned long long>(task.task_id),
                static_cast<int>(desc.render_type),
                static_cast<int>(task.options.is_blocking),
                task.input_path.c_str());

    // Prepare the render params if the task has a prepare hook
    if (task.prepare) {
        if (!task.prepare(task)) {
            ALCEDO_LOGW(PS_TAG, "Prepare hook returned false for task %llu, skipping",
                        static_cast<unsigned long long>(task.task_id));
            if (task.result) {
                task.result->set_value(nullptr);
            }
            return;
        }
    }

    // Capture the task by moving into the lambda
    bool is_blocking = task.options.is_blocking;
    std::shared_ptr<std::promise<std::shared_ptr<ImageBuffer>>> promise = task.result;

    {
        std::unique_lock<std::mutex> lock(queue_mutex_);
        if (stop_.load(std::memory_order_relaxed)) {
            ALCEDO_LOGW(PS_TAG, "Scheduler is stopped, rejecting task %llu",
                        static_cast<unsigned long long>(task.task_id));
            if (promise) {
                promise->set_value(nullptr);
            }
            return;
        }

        task_queue_.emplace([t = std::move(task)]() mutable {
            // Check for cancellation
            if (t.cancel_requested && t.cancel_requested()) {
                ALCEDO_LOGI(PS_TAG, "Task %llu cancelled before execution",
                            static_cast<unsigned long long>(t.task_id));
                if (t.result) {
                    t.result->set_value(nullptr);
                }
                return;
            }

            // Execute the pipeline via PipelineService
            auto& svc = PipelineService::Instance();
            auto& desc = t.options.render_desc;

            std::shared_ptr<ImageBuffer> output;

            if (t.input && t.input->is_valid()) {
                // Process from an existing ImageBuffer
                output = std::make_shared<ImageBuffer>();
                output->copy_from(*t.input);

                int w = output->width;
                int h = output->height;

                // Apply max_edge cap
                if (desc.max_edge > 0) {
                    int max_dim = std::max(w, h);
                    if (max_dim > desc.max_edge) {
                        float scale = static_cast<float>(desc.max_edge) / static_cast<float>(max_dim);
                        int new_w = static_cast<int>(w * scale);
                        int new_h = static_cast<int>(h * scale);
                        // For simplicity, store the target dimensions in metadata
                        // Actual resize would go through resize_op
                        ALCEDO_LOGD(PS_TAG, "Task %llu: capping %dx%d -> %dx%d",
                                    static_cast<unsigned long long>(t.task_id), w, h, new_w, new_h);
                    }
                }

                PipelineParams params;
                bool ok = svc.process(output->float_data(), output->width, output->height,
                                      output->channels, params);

                if (!ok) {
                    ALCEDO_LOGE(PS_TAG, "Pipeline process failed for task %llu",
                                static_cast<unsigned long long>(t.task_id));
                    output.reset();
                }
            } else if (!t.input_path.empty()) {
                // No pre-loaded buffer and no ImageLoader at this level;
                // signal to the caller that input must be provided upstream.
                ALCEDO_LOGW(PS_TAG, "Task %llu: no input buffer, path-only not supported at scheduler level",
                            static_cast<unsigned long long>(t.task_id));
                output = nullptr;
            }

            // Deliver result
            if (t.result) {
                t.result->set_value(output);
            }

            // Fire callbacks
            if (output) {
                if (t.options.is_callback && t.callback) {
                    t.callback(*output);
                }
                if (t.options.is_seq_callback && t.seq_callback) {
                    t.seq_callback(*output, t.input_image_id);
                }
            }

            ALCEDO_LOGI(PS_TAG, "Task %llu completed, output valid=%d",
                        static_cast<unsigned long long>(t.task_id),
                        output ? static_cast<int>(output->is_valid()) : 0);
        });
    }

    condition_.notify_one();

    ALCEDO_LOGD(PS_TAG, "Task %llu enqueued, queue size=%zu",
                static_cast<unsigned long long>(task.task_id), task_queue_.size());
}

void PipelineScheduler::WorkerLoop() {
    ALCEDO_LOGD(PS_TAG, "Worker thread started");

    while (true) {
        std::function<void()> task;

        {
            std::unique_lock<std::mutex> lock(queue_mutex_);
            condition_.wait(lock, [this] {
                return stop_.load(std::memory_order_relaxed) || !task_queue_.empty();
            });

            if (stop_.load(std::memory_order_relaxed) && task_queue_.empty()) {
                ALCEDO_LOGD(PS_TAG, "Worker thread exiting");
                return;
            }

            if (!task_queue_.empty()) {
                task = std::move(task_queue_.front());
                task_queue_.pop();
            }
        }

        if (task) {
            task();
        }
    }
}

void PipelineScheduler::Shutdown() {
    if (stop_.load(std::memory_order_relaxed)) {
        return;  // Already shut down
    }

    ALCEDO_LOGI(PS_TAG, "Shutting down PipelineScheduler");

    {
        std::unique_lock<std::mutex> lock(queue_mutex_);
        stop_.store(true, std::memory_order_release);
    }
    condition_.notify_all();

    for (auto& worker : workers_) {
        if (worker.joinable()) {
            worker.join();
        }
    }
    workers_.clear();

    ALCEDO_LOGI(PS_TAG, "PipelineScheduler shut down complete");
}

size_t PipelineScheduler::queue_size() const {
    std::unique_lock<std::mutex> lock(queue_mutex_);
    return task_queue_.size();
}

} // namespace alcedo
