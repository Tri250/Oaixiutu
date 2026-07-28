// AlcedoAndroid - Final display frame tap.
// A lightweight tap that captures the most recent final display frame (as an
// ImageBuffer) and forwards it to a scope analyzer. Replaces the desktop
// IFrameSink chain with a minimal Android-friendly interface.
// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <memory>
#include <mutex>

#include "edit/scope/scope_analyzer.hpp"
#include "image/image_buffer.hpp"

namespace alcedo {

class FinalDisplayFrameTap final : public IFinalDisplayFrameProvider {
 public:
  FinalDisplayFrameTap() = default;
  explicit FinalDisplayFrameTap(std::shared_ptr<IScopeAnalyzer> analyzer);

  // Install/replace the scope analyzer.
  void SetScopeAnalyzer(std::shared_ptr<IScopeAnalyzer> analyzer);
  auto GetScopeAnalyzer() const -> std::shared_ptr<IScopeAnalyzer>;

  // Configure the scope request applied to every submitted frame.
  void SetScopeRequest(const ScopeRequest& request);
  auto GetScopeRequest() const -> ScopeRequest;

  // Submit a final display frame. The frame is captured and forwarded to the
  // analyzer (if any). Safe to call from the render thread.
  void SubmitFrame(std::shared_ptr<ImageBuffer> image, int width, int height, int channels,
                   AnalysisDomain domain = AnalysisDomain::DisplayEncoded,
                   GpuBackendKind backend = GpuBackendKind::None, uint64_t frame_id = 0);

  // IFinalDisplayFrameProvider
  auto GetCurrentDisplayFrameView() const -> FinalDisplayFrameView override;

  // Release the captured frame and analyzer resources.
  void Reset();

 private:
  mutable std::mutex                  mutex_;
  std::shared_ptr<IScopeAnalyzer>     analyzer_;
  ScopeRequest                        scope_request_{};
  FinalDisplayFrameView               current_{};
  uint64_t                            generation_ = 0;
};

}  // namespace alcedo
