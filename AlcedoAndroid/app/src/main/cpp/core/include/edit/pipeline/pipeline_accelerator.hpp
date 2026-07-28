// AlcedoAndroid - Accelerator backend preference (CPU / Vulkan / Auto).
// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include "image/gpu_backend.hpp"

namespace alcedo {

enum class AcceleratorBackendPreference {
  CPU    = 0,
  Vulkan = 1,
  Auto   = 2,  // pick Vulkan when available, else CPU
};

// Resolve a preference to a concrete backend kind given device availability.
inline GpuBackendKind ResolveBackend(AcceleratorBackendPreference pref, bool vulkan_available) {
  if (pref == AcceleratorBackendPreference::CPU) return GpuBackendKind::None;
  if (pref == AcceleratorBackendPreference::Vulkan) return GpuBackendKind::Vulkan;
  // Auto
  return vulkan_available ? GpuBackendKind::Vulkan : GpuBackendKind::None;
}

}  // namespace alcedo
