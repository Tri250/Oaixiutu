// AlcedoAndroid - Pipeline accelerator selection / initialization.
// SPDX-License-Identifier: GPL-3.0-only
#include "edit/pipeline/pipeline_accelerator.hpp"

#include "utils/app_logging.hpp"
#include "vulkan/context/vulkan_context.hpp"

namespace alcedo {

namespace {
// Reports whether a Vulkan compute device is available on this host.
bool VulkanAvailable() {
  if (auto* ctx = VulkanContext::Get()) return ctx->Valid();
  return false;
}
}  // namespace

// Helper used by the app layer to pick a default preference at startup.
AcceleratorBackendPreference DefaultAcceleratorPreference() {
  return VulkanAvailable() ? AcceleratorBackendPreference::Vulkan
                           : AcceleratorBackendPreference::CPU;
}

// Eagerly warm up the Vulkan context (called from JNI_OnLoad).
void WarmUpAccelerator() {
  if (VulkanContext::Ensure() && VulkanContext::Get()->Valid()) {
    ALOGI("Accelerator: Vulkan compute ready (subgroup=%u)",
          VulkanContext::Get()->PhysicalProps().limits.maxComputeWorkGroupSize[0]);
  } else {
    ALOGI("Accelerator: Vulkan unavailable, using CPU backend");
  }
}

}  // namespace alcedo
