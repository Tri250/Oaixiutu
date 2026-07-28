// AlcedoAndroid - PipelineScheduler (renderer layer).
// Schedules pipeline render requests with priority and tile-based execution.
// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <atomic>
#include <condition_variable>
#include <memory>
#include <mutex>
#include <queue>
#include <thread>
#include <vector>

#include "edit/pipeline/pipeline.hpp"
#include "image/image.hpp"
#include "type/type.hpp"

namespace alcedo {

struct RenderRequest {
  request_id_t      request_id  = 0;
  sl_element_id_t   file_id     = 0;
  PriorityLevel     priority    = 0;
  std::shared_ptr<Image> image;
  nlohmann::json    params;
};

class PipelineScheduler {
 public:
  PipelineScheduler();
  ~PipelineScheduler();

  void Start(uint32_t num_threads = 1);
  void Stop();

  auto Submit(RenderRequest&& req) -> request_id_t;
  void Cancel(request_id_t id);
  void CancelAll();

  auto PendingCount() -> size_t;

 private:
  struct RequestCmp {
    bool operator()(const RenderRequest& a, const RenderRequest& b) const {
      return a.priority < b.priority;  // higher priority first
    }
  };

  std::priority_queue<RenderRequest, std::vector<RenderRequest>, RequestCmp> queue_;
  std::mutex                  mtx_;
  std::condition_variable     cv_;
  std::vector<std::thread>    workers_;
  std::atomic<bool>           running_{false};
  std::atomic<request_id_t>   next_id_{1};

  void WorkerLoop();
};

}  // namespace alcedo
