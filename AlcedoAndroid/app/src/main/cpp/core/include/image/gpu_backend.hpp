// AlcedoAndroid - GPU backend kind enumeration.
// SPDX-License-Identifier: GPL-3.0-only
#pragma once

namespace alcedo {

// Identifies which accelerator backs a GPU image buffer. The Android port
// replaces the desktop CUDA/Metal/OpenCL trio with a single Vulkan backend.
enum class GpuBackendKind {
  None   = 0,
  Vulkan = 1,
};

}  // namespace alcedo
