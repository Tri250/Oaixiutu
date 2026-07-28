// AlcedoAndroid - Pipeline stage: StaticKernelStream, PointChain, PipelineStage.
// Adapted from the desktop project; GPU executor now backed by Vulkan.
// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <map>
#include <memory>
#include <optional>
#include <string>
#include <tuple>

#include "edit/operators/op_base.hpp"
#include "edit/operators/op_kernel.hpp"
#include "edit/operators/operator_factory.hpp"
#include "edit/pipeline/pipeline_accelerator.hpp"
#include "edit/pipeline/tile_scheduler.hpp"
#include "image/gpu_backend.hpp"
#include "image/image_buffer.hpp"
#include "json.hpp"
#include "type/type.hpp"

namespace alcedo {

// A compile-time chain of point operators applied to a single pixel.
template <typename... Ops>
struct PointChain {
  std::tuple<Ops...> ops_;
  PointChain(Ops... ops) : ops_(std::move(ops)...) {}
  template <size_t I = 0>
  inline void ApplyOps(Pixel& p, OperatorParams& params) {
    if constexpr (I < sizeof...(Ops)) {
      std::get<I>(ops_)(p, params);
      ApplyOps<I + 1>(p, params);
    }
  }
  void operator()(Tile& tile, OperatorParams& params) {
    for (int y = 0; y < tile.height_; ++y)
      for (int x = 0; x < tile.width_; ++x)
        ApplyOps(tile.at(y, x), params);
  }
};

// A compile-time stream of stages (point chains + neighbor ops) over a tile.
template <typename... Stages>
class StaticKernelStream {
 public:
  StaticKernelStream(Stages... stages) : stages_(std::move(stages)...) {}
  template <size_t I = 0>
  inline void Dispatch(Tile& tile, OperatorParams& params) {
    if constexpr (I < sizeof...(Stages)) {
      auto& s = std::get<I>(stages_);
      if constexpr (std::is_base_of_v<PointOpTag, std::decay_t<decltype(s)>>) {
        s(tile, params);
      } else {
        s(tile, params);
      }
      Dispatch<I + 1>(tile, params);
    }
  }
  void ProcessTile(Tile& tile, OperatorParams& params) { Dispatch(tile, params); }
 private:
  std::tuple<Stages...> stages_;
};

// Serialized operator entry held by a stage.
struct OperatorEntry {
  bool                           enable_ = true;
  std::shared_ptr<IOperatorBase> op_;
  bool operator<(const OperatorEntry& other) const {
    return op_->GetPriorityLevel() < other.op_->GetPriorityLevel();
  }
  auto ExportOperatorParams() const -> nlohmann::json {
    nlohmann::json j;
    j["type"]   = static_cast<int>(op_->GetOperatorType());
    j["enable"] = enable_;
    j["params"] = op_->GetParams();
    return j;
  }
  void ImportOperatorParams(const nlohmann::json& j) {
    if (j.contains("enable")) enable_ = j["enable"].get<bool>();
    if (j.contains("params")) op_->SetParams(j["params"]);
  }
};

// A single stage in the pipeline graph. Holds its operators, input/output
// caches, and dispatches to either the CPU static kernel stream or the Vulkan
// kernel stream depending on the configured accelerator backend.
class PipelineStage {
 public:
  enum class StageRole { DescriptorOnly, CpuOperators, GpuStreamable, GpuOperators };
  enum class RuntimeResetMode {
    InvalidateCache,
    ClearIntermediateBuffers,
    ReleaseGpuScratch,
    ReleaseGpuResources,
    ClearIntermediateBuffersAndGpu,
  };

  PipelineStage() = delete;
  PipelineStage(const PipelineStage&) = delete;
  PipelineStage(PipelineStageName stage, bool enable_cache, bool is_streamable);

  auto IsStreamable() const { return is_streamable_; }
  void SetAcceleratorBackend(GpuBackendKind backend);
  auto GetAcceleratorBackend() const { return accelerator_backend_; }
  void SetInputImage(std::shared_ptr<ImageBuffer> img);
  void SetForceCPUOutput(bool force) { force_cpu_output_ = force; }
  void ResetRuntimeResources(RuntimeResetMode mode);

  void SetOperator(OperatorType type, nlohmann::json param);
  void SetOperator(OperatorType type, nlohmann::json param, OperatorParams& global_params);
  auto GetOperator(OperatorType type) const -> std::optional<OperatorEntry*>;
  auto GetAllOperators() const -> std::map<OperatorType, OperatorEntry>& { return *operators_; }
  void EnableOperator(OperatorType type, bool enable);
  void EnableOperator(OperatorType type, bool enable, OperatorParams& global_params);

  void SetNeighbors(PipelineStage* prev, PipelineStage* next) { prev_stage_ = prev; next_stage_ = next; }
  void ResetNeighbors() { prev_stage_ = next_stage_ = nullptr; }
  void SetInputCacheValid(bool valid) { input_cache_valid_ = valid; }
  void SetOutputCacheValid(bool valid) { output_cache_valid_ = valid; }
  auto CacheValid() const -> bool {
    if (!enable_cache_) return false;
    if (!prev_stage_) return output_cache_valid_;
    return input_cache_valid_ && output_cache_valid_;
  }
  auto GetOutputCache() const -> std::shared_ptr<ImageBuffer> { return output_cache_; }
  void AddDependent(PipelineStage* d) { dependents_ = d; }
  void ResetDependents() { dependents_ = nullptr; }
  auto GetStageNameString() const -> std::string;
  auto HasInput() -> bool;
  auto ApplyStage(OperatorParams& global_params) -> std::shared_ptr<ImageBuffer>;
  void RefreshGlobalParams(OperatorParams& global_params);
  void SetEnableCache(bool enable) {
    if (enable_cache_ == enable) return;
    enable_cache_ = enable;
    ResetRuntimeResources(RuntimeResetMode::InvalidateCache);
  }
  void ResetAll();
  auto ExportStageParams() const -> nlohmann::json;
  void ImportStageParams(const nlohmann::json& j);
  void ImportStageParams(const nlohmann::json& j, OperatorParams& global_params);
  void MergeStageParams(const nlohmann::json& j, OperatorParams& global_params);

  PipelineStageName stage_;

 private:
  static StageRole DetermineStageRole(PipelineStageName stage, bool is_streamable,
                                      GpuBackendKind backend);
  bool HasEnabledOperator() const;
  std::shared_ptr<ImageBuffer> ApplyDescriptorOnly();
  std::shared_ptr<ImageBuffer> ApplyCpuOperators(OperatorParams& global_params);
  std::shared_ptr<ImageBuffer> ApplyGpuOperators(OperatorParams& global_params);
  std::shared_ptr<ImageBuffer> ApplyGpuStream(OperatorParams& global_params);

  std::unique_ptr<std::map<OperatorType, OperatorEntry>> operators_;
  bool                                                   is_streamable_ = true;
  StageRole                                              stage_role_    = StageRole::DescriptorOnly;
  PipelineStage*                                         prev_stage_    = nullptr;
  PipelineStage*                                         next_stage_    = nullptr;
  PipelineStage*                                         dependents_    = nullptr;
  std::shared_ptr<ImageBuffer>                           input_img_     = nullptr;
  std::shared_ptr<ImageBuffer>                           output_cache_  = nullptr;
  bool                                                   enable_cache_  = false;
  bool                                                   input_cache_valid_  = false;
  bool                                                   output_cache_valid_ = false;
  bool                                                   input_set_          = false;
  GpuBackendKind                                         accelerator_backend_ = GpuBackendKind::None;
  bool                                                   gpu_setup_done_  = false;
  bool                                                   force_cpu_output_ = false;
  std::string                                            last_profile_summary_;
};

}  // namespace alcedo
