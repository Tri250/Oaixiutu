// AlcedoAndroid - Vulkan shader program registry.
// Maps a logical program name (e.g. "basic", "color", "cst") to its compiled
// SPIR-V bytes. SPIR-V is loaded from the assets at runtime (shaders shipped
// under assets/shaders/*.spv) or, in development, embedded via the
// VulkanProgramRegistry::Register() entry point.
// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <cstdint>
#include <mutex>
#include <optional>
#include <string>
#include <unordered_map>
#include <vector>

namespace alcedo {

class VulkanPipeline;

// Registry of SPIR-V blobs keyed by program name. Thread-safe.
class VulkanProgramRegistry {
 public:
  static VulkanProgramRegistry& Instance();

  // Register SPIR-V bytes for a named program.
  void Register(const std::string& name, std::vector<uint32_t> spirv);

  // Returns a copy of the SPIR-V for the named program, or nullopt if absent.
  std::optional<std::vector<uint32_t>> Get(const std::string& name) const;

  bool Has(const std::string& name) const;
  std::vector<std::string> Names() const;

  // Convenience: load all .spv files from a directory (asset-extracted path).
  void LoadFromDirectory(const std::string& dir);

 private:
  VulkanProgramRegistry() = default;
  mutable std::mutex mtx_;
  std::unordered_map<std::string, std::vector<uint32_t>> programs_;
};

}  // namespace alcedo
