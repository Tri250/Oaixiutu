// AlcedoAndroid - PipelineExecutor abstract interface.
// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <memory>

#include "edit/operators/geometry/resize_algorithm.hpp"
#include "edit/operators/op_base.hpp"
#include "image/image_buffer.hpp"
#include "json.hpp"
#include "pipeline_accelerator.hpp"
#include "type/type.hpp"

namespace alcedo {

class PipelineStage;

enum class PipelineBackend { CPU, GPU };

// Abstract executor over the 7-stage pipeline. Concrete implementations live in
// pipeline.cpp (CPU) and pipeline_vulkan_impl.cpp (Vulkan).
class PipelineExecutor {
 public:
  virtual ~PipelineExecutor() = default;

  virtual void SetBoundFile(sl_element_id_t file_id) = 0;
  virtual auto GetBoundFile() const -> sl_element_id_t = 0;
  virtual auto GetStage(PipelineStageName stage) -> PipelineStage& = 0;
  virtual auto Apply(std::shared_ptr<ImageBuffer> input) -> std::shared_ptr<ImageBuffer> = 0;
  virtual auto GetBackend() -> PipelineBackend = 0;
  virtual auto ExportPipelineParams() const -> nlohmann::json = 0;
  virtual void ImportPipelineParams(const nlohmann::json& j) = 0;
  virtual auto GetGlobalParams() -> OperatorParams& = 0;

  virtual void SetRenderRegion(int x, int y, float scale_factor_x,
                               float scale_factor_y = -1.0f,
                               int reference_width = 0,
                               int reference_height = 0) = 0;
  virtual void SetRenderRes(bool full_res, int max_side_length = 2048) = 0;
  virtual void SetResizeDownsampleAlgorithm(ResizeDownsampleAlgorithm algorithm) = 0;
  virtual void SetForceCPUOutput(bool /*force*/) {}
};

// Factory: creates a Vulkan-first PipelineExecutor (with CPU fallback).
std::shared_ptr<PipelineExecutor> CreatePipelineExecutor();

}  // namespace alcedo
