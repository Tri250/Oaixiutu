// AlcedoAndroid - PipelineScheduler implementation.
// Priority-queue based render request scheduler with worker threads.
// SPDX-License-Identifier: GPL-3.0-only
#include "renderer/pipeline_scheduler.hpp"

#include <utility>

#include "utils/app_logging.hpp"

namespace alcedo {

PipelineScheduler::PipelineScheduler() = default;

PipelineScheduler::~PipelineScheduler() { Stop(); }

void PipelineScheduler::Start(uint32_t num_threads) {
  if (running_.load()) return;
  running_.store(true);
  workers_.reserve(num_threads);
  for (uint32_t i = 0; i < num_threads; ++i) {
    workers_.emplace_back(&PipelineScheduler::WorkerLoop, this);
  }
  ALOGI("PipelineScheduler: started with %u threads", num_threads);
}

void PipelineScheduler::Stop() {
  if (!running_.load()) return;
  running_.store(false);
  cv_.notify_all();
  for (auto& t : workers_) {
    if (t.joinable()) t.join();
  }
  workers_.clear();
}

auto PipelineScheduler::Submit(RenderRequest&& req) -> request_id_t {
  req.request_id = next_id_.fetch_add(1, std::memory_order_relaxed);
  {
    std::lock_guard<std::mutex> lock(mtx_);
    queue_.push(std::move(req));
  }
  cv_.notify_one();
  return req.request_id;
}

void PipelineScheduler::Cancel(request_id_t id) {
  // The priority_queue doesn't support removal by id; cancelled requests are
  // skipped when dequeued. A full cancel rebuilds the queue.
  std::lock_guard<std::mutex> lock(mtx_);
  std::priority_queue<RenderRequest, std::vector<RenderRequest>, RequestCmp> new_queue;
  while (!queue_.empty()) {
    auto top = std::move(const_cast<RenderRequest&>(queue_.top()));
    queue_.pop();
    if (top.request_id != id) new_queue.push(std::move(top));
  }
  queue_ = std::move(new_queue);
}

void PipelineScheduler::CancelAll() {
  std::lock_guard<std::mutex> lock(mtx_);
  while (!queue_.empty()) queue_.pop();
}

auto PipelineScheduler::PendingCount() -> size_t {
  std::lock_guard<std::mutex> lock(mtx_);
  return queue_.size();
}

void PipelineScheduler::WorkerLoop() {
  while (running_.load()) {
    RenderRequest req;
    {
      std::unique_lock<std::mutex> lock(mtx_);
      cv_.wait(lock, [this] { return !queue_.empty() || !running_.load(); });
      if (!running_.load() && queue_.empty()) return;
      if (queue_.empty()) continue;
      req = std::move(const_cast<RenderRequest&>(queue_.top()));
      queue_.pop();
    }
    // Execute the pipeline for this request.
    if (req.image) {
      auto executor = CreatePipelineExecutor();
      if (executor) {
        executor->SetBoundFile(req.file_id);
        if (!req.params.is_null()) executor->ImportPipelineParams(req.params);
        auto input = std::make_shared<ImageBuffer>(req.image->GetImageData().Clone());
        auto output = executor->Apply(input);
        if (output) {
          req.image->LoadOriginalData(std::move(*output));
        }
      }
    }
  }
}

}  // namespace alcedo
