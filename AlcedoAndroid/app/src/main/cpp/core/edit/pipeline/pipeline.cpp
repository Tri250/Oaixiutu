// AlcedoAndroid - Concrete PipelineExecutor (Vulkan-first, CPU fallback).
// SPDX-License-Identifier: GPL-3.0-only
#include "edit/pipeline/pipeline.hpp"

#include <array>
#include <memory>

#include "edit/operators/operator_factory.hpp"
#include "edit/pipeline/pipeline_stage.hpp"
#include "utils/app_logging.hpp"
#include "vulkan/context/vulkan_context.hpp"

namespace alcedo {

namespace {
// Map a stage enum to its default streamability/caching policy.
struct StagePolicy { bool cache; bool streamable; };
constexpr StagePolicy kStagePolicies[] = {
  {false, false}, // Image_Loading
  {false, false}, // Geometry_Adjustment
  {true,  false}, // To_WorkingSpace
  {true,  true},  // Basic_Adjustment
  {true,  true},  // Color_Adjustment
  {true,  false}, // Detail_Adjustment
  {true,  false}, // Output_Transform
};
}  // namespace

// Concrete executor over the 7-stage pipeline.
class AlcedoPipeline : public PipelineExecutor {
 public:
  AlcedoPipeline() {
    for (int i = 0; i < 7; ++i) {
      const auto& p = kStagePolicies[i];
      stages_[i] = std::make_unique<PipelineStage>(static_cast<PipelineStageName>(i), p.cache, p.streamable);
    }
    for (int i = 0; i < 7; ++i) {
      stages_[i]->SetNeighbors(i > 0 ? stages_[i - 1].get() : nullptr,
                               i < 6 ? stages_[i + 1].get() : nullptr);
    }
    // Default to Vulkan when available.
    bool vulkan_ok = false;
    if (auto* ctx = VulkanContext::Ensure()) vulkan_ok = ctx->Valid();
    SetAccelerator(vulkan_ok ? GpuBackendKind::Vulkan : GpuBackendKind::None);
  }

  void SetAccelerator(GpuBackendKind backend) {
    for (auto& s : stages_) s->SetAcceleratorBackend(backend);
    backend_ = backend;
  }

  void SetBoundFile(sl_element_id_t file_id) override { bound_file_ = file_id; }
  auto GetBoundFile() const -> sl_element_id_t override { return bound_file_; }
  auto GetStage(PipelineStageName stage) -> PipelineStage& override {
    return *stages_[static_cast<int>(stage)];
  }
  auto GetBackend() -> PipelineBackend override {
    return backend_ == GpuBackendKind::Vulkan ? PipelineBackend::GPU : PipelineBackend::CPU;
  }
  auto GetGlobalParams() -> OperatorParams& override { return global_params_; }

  auto Apply(std::shared_ptr<ImageBuffer> input) -> std::shared_ptr<ImageBuffer> override {
    if (!input) return nullptr;
    std::shared_ptr<ImageBuffer> cur = input;
    for (int i = 0; i < 7; ++i) {
      stages_[i]->SetInputImage(cur);
      cur = stages_[i]->ApplyStage(global_params_);
      if (!cur) { ALOGW("AlcedoPipeline: stage %d returned null", i); break; }
    }
    // Final output expected on CPU for display/export.
    if (cur && force_cpu_output_) cur->SyncToCPU();
    return cur;
  }

  auto ExportPipelineParams() const -> nlohmann::json override {
    nlohmann::json j;
    nlohmann::json arr = nlohmann::json::array();
    for (int i = 0; i < 7; ++i) arr.push_back(stages_[i]->ExportStageParams());
    j["stages"] = arr;
    j["bound_file"] = bound_file_;
    return j;
  }

  void ImportPipelineParams(const nlohmann::json& j) override {
    if (j.contains("bound_file")) bound_file_ = j["bound_file"].get<sl_element_id_t>();
    if (!j.contains("stages")) return;
    const auto& arr = j["stages"];
    for (int i = 0; i < 7 && i < (int)arr.size(); ++i) {
      stages_[i]->ImportStageParams(arr[i], global_params_);
    }
  }

  void SetRenderRegion(int x, int y, float sx, float sy = -1.0f,
                       int rw = 0, int rh = 0) override {
    global_params_.render_roi_enabled_ = true;
    global_params_.render_roi_x_ = x;
    global_params_.render_roi_y_ = y;
    global_params_.render_roi_scale_x_ = sx;
    global_params_.render_roi_scale_y_ = sy < 0 ? sx : sy;
    global_params_.render_roi_reference_width_ = rw;
    global_params_.render_roi_reference_height_ = rh;
  }
  void SetRenderRes(bool full_res, int max_side_length = 2048) override {
    (void)full_res;
    global_params_.render_hs_reference_max_long_edge_ = max_side_length;
  }
  void SetResizeDownsampleAlgorithm(ResizeDownsampleAlgorithm algo) override { algo_ = algo; }
  void SetForceCPUOutput(bool force) override {
    force_cpu_output_ = force;
    for (auto& s : stages_) s->SetForceCPUOutput(force);
  }

 private:
  std::array<std::unique_ptr<PipelineStage>, 7> stages_{};
  OperatorParams       global_params_;
  sl_element_id_t      bound_file_ = 0;
  GpuBackendKind       backend_ = GpuBackendKind::None;
  ResizeDownsampleAlgorithm algo_ = ResizeDownsampleAlgorithm::Bilinear;
  bool                 force_cpu_output_ = false;
};

// Public factory used by the app/JNI layer.
std::shared_ptr<PipelineExecutor> CreatePipelineExecutor() {
  return std::make_shared<AlcedoPipeline>();
}

}  // namespace alcedo
