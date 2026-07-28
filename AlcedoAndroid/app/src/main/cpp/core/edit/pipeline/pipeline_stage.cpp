// AlcedoAndroid - PipelineStage implementation.
// SPDX-License-Identifier: GPL-3.0-only
#include "edit/pipeline/pipeline_stage.hpp"

#include <algorithm>

#include "edit/operators/operator_factory.hpp"
#include "utils/app_logging.hpp"

namespace alcedo {

// Forward: Vulkan stage runner (defined in pipeline_vulkan_impl.cpp).
std::shared_ptr<ImageBuffer> RunVulkanStage(PipelineStage& stage,
                                            std::shared_ptr<ImageBuffer> input,
                                            OperatorParams& global_params);

PipelineStage::PipelineStage(PipelineStageName stage, bool enable_cache, bool is_streamable)
    : stage_(stage), operators_(std::make_unique<std::map<OperatorType, OperatorEntry>>()),
      is_streamable_(is_streamable), enable_cache_(enable_cache) {
  accelerator_backend_ = GpuBackendKind::None;
  stage_role_ = DetermineStageRole(stage, is_streamable_, accelerator_backend_);
}

auto PipelineStage::DetermineStageRole(PipelineStageName stage, bool is_streamable,
                                       GpuBackendKind backend) -> StageRole {
  if (backend == GpuBackendKind::None) {
    return is_streamable ? StageRole::CpuOperators : StageRole::DescriptorOnly;
  }
  // Vulkan backend.
  if (stage == PipelineStageName::Image_Loading ||
      stage == PipelineStageName::Geometry_Adjustment) {
    return StageRole::GpuOperators;
  }
  return is_streamable ? StageRole::GpuStreamable : StageRole::GpuOperators;
}

void PipelineStage::SetAcceleratorBackend(GpuBackendKind backend) {
  if (accelerator_backend_ == backend) return;
  accelerator_backend_ = backend;
  stage_role_ = DetermineStageRole(stage_, is_streamable_, backend);
  gpu_setup_done_ = false;
}

void PipelineStage::SetInputImage(std::shared_ptr<ImageBuffer> img) {
  input_img_ = std::move(img);
  input_set_ = true;
  input_cache_valid_ = false;
  output_cache_valid_ = false;
}

void PipelineStage::ResetRuntimeResources(RuntimeResetMode mode) {
  (void)mode;
  output_cache_.reset();
  input_cache_valid_ = false;
  output_cache_valid_ = false;
  gpu_setup_done_ = false;
}

void PipelineStage::SetOperator(OperatorType type, nlohmann::json param) {
  auto& ops = *operators_;
  auto it = ops.find(type);
  if (it == ops.end()) {
    auto op = OperatorFactory::Instance().Create(type);
    if (!op) { ALOGW("PipelineStage: cannot create operator type %d", (int)type); return; }
    op->SetParams(param);
    OperatorEntry e; e.op_ = op;
    ops[type] = std::move(e);
  } else {
    it->second.op_->SetParams(param);
  }
}

void PipelineStage::SetOperator(OperatorType type, nlohmann::json param,
                                OperatorParams& global_params) {
  SetOperator(type, std::move(param));
  auto it = operators_->find(type);
  if (it != operators_->end() && it->second.enable_ && it->second.op_) {
    it->second.op_->SetGlobalParams(global_params);
  }
}

auto PipelineStage::GetOperator(OperatorType type) const -> std::optional<OperatorEntry*> {
  auto it = operators_->find(type);
  if (it == operators_->end()) return std::nullopt;
  return &it->second;
}

void PipelineStage::EnableOperator(OperatorType type, bool enable) {
  auto it = operators_->find(type);
  if (it != operators_->end()) it->second.enable_ = enable;
}

void PipelineStage::EnableOperator(OperatorType type, bool enable, OperatorParams& global_params) {
  EnableOperator(type, enable);
  auto it = operators_->find(type);
  if (it != operators_->end() && it->second.op_) {
    it->second.op_->EnableGlobalParams(global_params, enable);
  }
}

auto PipelineStage::GetStageNameString() const -> std::string {
  switch (stage_) {
    case PipelineStageName::Image_Loading:       return "Image_Loading";
    case PipelineStageName::Geometry_Adjustment: return "Geometry_Adjustment";
    case PipelineStageName::To_WorkingSpace:     return "To_WorkingSpace";
    case PipelineStageName::Basic_Adjustment:    return "Basic_Adjustment";
    case PipelineStageName::Color_Adjustment:    return "Color_Adjustment";
    case PipelineStageName::Detail_Adjustment:   return "Detail_Adjustment";
    case PipelineStageName::Output_Transform:    return "Output_Transform";
    default: return "Unknown";
  }
}

auto PipelineStage::HasInput() -> bool { return input_set_ && input_img_ != nullptr; }

bool PipelineStage::HasEnabledOperator() const {
  for (const auto& kv : *operators_) if (kv.second.enable_ && kv.second.op_) return true;
  return false;
}

void PipelineStage::RefreshGlobalParams(OperatorParams& global_params) {
  for (auto& kv : *operators_) {
    if (kv.second.enable_ && kv.second.op_) kv.second.op_->SetGlobalParams(global_params);
  }
}

auto PipelineStage::ApplyStage(OperatorParams& global_params) -> std::shared_ptr<ImageBuffer> {
  if (!HasInput()) return nullptr;
  if (CacheValid() && output_cache_) return output_cache_;
  switch (stage_role_) {
    case StageRole::DescriptorOnly: return ApplyDescriptorOnly();
    case StageRole::CpuOperators:   return ApplyCpuOperators(global_params);
    case StageRole::GpuStreamable:
    case StageRole::GpuOperators:
      if (force_cpu_output_) return ApplyCpuOperators(global_params);
      return ApplyGpuOperators(global_params);
  }
  return nullptr;
}

std::shared_ptr<ImageBuffer> PipelineStage::ApplyDescriptorOnly() {
  // No per-pixel work; pass input through.
  return input_img_;
}

std::shared_ptr<ImageBuffer> PipelineStage::ApplyCpuOperators(OperatorParams& global_params) {
  std::shared_ptr<ImageBuffer> out = input_img_;
  for (auto& kv : *operators_) {
    if (!kv.second.enable_ || !kv.second.op_) continue;
    kv.second.op_->SetGlobalParams(global_params);
    kv.second.op_->Apply(out);
  }
  output_cache_ = out;
  output_cache_valid_ = true;
  return out;
}

std::shared_ptr<ImageBuffer> PipelineStage::ApplyGpuOperators(OperatorParams& global_params) {
  // Geometry / loading stages run individual operators on the GPU (or CPU
  // fallback). Streamable stages route through the fused Vulkan kernel stream.
  if (is_streamable_ && stage_role_ == StageRole::GpuStreamable) {
    return ApplyGpuStream(global_params);
  }
  std::shared_ptr<ImageBuffer> out = input_img_;
  for (auto& kv : *operators_) {
    if (!kv.second.enable_ || !kv.second.op_) continue;
    kv.second.op_->SetGlobalParams(global_params);
    kv.second.op_->ApplyGPU(out);
  }
  // Ensure CPU output is valid for downstream/export.
  if (force_cpu_output_) out->SyncToCPU();
  output_cache_ = out;
  output_cache_valid_ = true;
  return out;
}

std::shared_ptr<ImageBuffer> PipelineStage::ApplyGpuStream(OperatorParams& global_params) {
  auto out = RunVulkanStage(*this, input_img_, global_params);
  if (!out) out = ApplyCpuOperators(global_params);
  output_cache_ = out;
  output_cache_valid_ = true;
  return out;
}

void PipelineStage::ResetAll() {
  operators_->clear();
  output_cache_.reset();
  input_cache_valid_ = output_cache_valid_ = false;
  gpu_setup_done_ = false;
}

auto PipelineStage::ExportStageParams() const -> nlohmann::json {
  nlohmann::json j;
  j["stage"] = GetStageNameString();
  j["enable_cache"] = enable_cache_;
  nlohmann::json arr = nlohmann::json::array();
  for (const auto& kv : *operators_) arr.push_back(kv.second.ExportOperatorParams());
  j["operators"] = arr;
  return j;
}

void PipelineStage::ImportStageParams(const nlohmann::json& j) {
  operators_->clear();
  if (j.contains("operators")) {
    for (const auto& ej : j["operators"]) {
      OperatorType type = static_cast<OperatorType>(ej.value("type", 0));
      auto op = OperatorFactory::Instance().Create(type);
      if (!op) continue;
      OperatorEntry e;
      e.op_ = op;
      e.ImportOperatorParams(ej);
      (*operators_)[type] = std::move(e);
    }
  }
}

void PipelineStage::ImportStageParams(const nlohmann::json& j, OperatorParams& global_params) {
  ImportStageParams(j);
  RefreshGlobalParams(global_params);
}

void PipelineStage::MergeStageParams(const nlohmann::json& j, OperatorParams& global_params) {
  if (!j.contains("operators")) return;
  for (const auto& ej : j["operators"]) {
    OperatorType type = static_cast<OperatorType>(ej.value("type", 0));
    auto it = operators_->find(type);
    if (it == operators_->end()) {
      auto op = OperatorFactory::Instance().Create(type);
      if (!op) continue;
      OperatorEntry e; e.op_ = op;
      e.ImportOperatorParams(ej);
      (*operators_)[type] = std::move(e);
    } else {
      it->second.ImportOperatorParams(ej);
    }
  }
  RefreshGlobalParams(global_params);
}

}  // namespace alcedo
