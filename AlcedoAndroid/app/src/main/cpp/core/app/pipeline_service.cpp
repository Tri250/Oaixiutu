// AlcedoAndroid - PipelineAppService implementation.
// SPDX-License-Identifier: GPL-3.0-only
#include "app/app_services.hpp"

#include "edit/pipeline/pipeline.hpp"
#include "utils/app_logging.hpp"

namespace alcedo {

PipelineAppService::PipelineAppService() = default;

auto PipelineAppService::Execute(const std::shared_ptr<Image>& image, const std::string& param_json)
    -> std::shared_ptr<Image> {
  if (!image) return nullptr;
  auto executor = CreatePipelineExecutor();
  if (!executor) {
    ALOGE("PipelineAppService: failed to create executor");
    return image;
  }
  nlohmann::json params = nlohmann::json::parse(param_json, nullptr, false);
  if (!params.is_discarded()) {
    executor->ImportPipelineParams(params);
  }
  // Apply the pipeline to the image's working buffer.
  auto input = std::make_shared<ImageBuffer>(image->GetImageData());
  auto output = executor->Apply(input);
  if (output) {
    image->LoadOriginalData(std::move(*output));
  }
  return image;
}

}  // namespace alcedo
