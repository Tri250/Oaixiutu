// AlcedoAndroid - FinalDisplayFrameTap implementation.
// SPDX-License-Identifier: GPL-3.0-only
#include "edit/scope/final_display_frame_tap.hpp"

#include <utility>

namespace alcedo {

FinalDisplayFrameTap::FinalDisplayFrameTap(std::shared_ptr<IScopeAnalyzer> analyzer)
    : analyzer_(std::move(analyzer)) {}

void FinalDisplayFrameTap::SetScopeAnalyzer(std::shared_ptr<IScopeAnalyzer> analyzer) {
  std::lock_guard<std::mutex> lock(mutex_);
  analyzer_ = std::move(analyzer);
}

auto FinalDisplayFrameTap::GetScopeAnalyzer() const -> std::shared_ptr<IScopeAnalyzer> {
  std::lock_guard<std::mutex> lock(mutex_);
  return analyzer_;
}

void FinalDisplayFrameTap::SetScopeRequest(const ScopeRequest& request) {
  std::lock_guard<std::mutex> lock(mutex_);
  scope_request_ = request;
  if (analyzer_) analyzer_->ResizeResources(request);
}

auto FinalDisplayFrameTap::GetScopeRequest() const -> ScopeRequest {
  std::lock_guard<std::mutex> lock(mutex_);
  return scope_request_;
}

void FinalDisplayFrameTap::SubmitFrame(std::shared_ptr<ImageBuffer> image, int width, int height,
                                       int channels, AnalysisDomain domain,
                                       GpuBackendKind backend, uint64_t frame_id) {
  FinalDisplayFrameView view;
  view.image    = std::move(image);
  view.width    = width;
  view.height   = height;
  view.channels = channels;
  view.domain   = domain;
  view.backend  = backend;
  view.frame_id = frame_id;

  std::shared_ptr<IScopeAnalyzer> analyzer;
  ScopeRequest request;
  {
    std::lock_guard<std::mutex> lock(mutex_);
    current_     = view;
    generation_++;
    analyzer     = analyzer_;
    request      = scope_request_;
  }
  if (analyzer) {
    analyzer->SubmitFrame(view, request);
  }
}

auto FinalDisplayFrameTap::GetCurrentDisplayFrameView() const -> FinalDisplayFrameView {
  std::lock_guard<std::mutex> lock(mutex_);
  return current_;
}

void FinalDisplayFrameTap::Reset() {
  std::lock_guard<std::mutex> lock(mutex_);
  if (analyzer_) analyzer_->ReleaseResources();
  current_ = FinalDisplayFrameView{};
  generation_ = 0;
}

}  // namespace alcedo
