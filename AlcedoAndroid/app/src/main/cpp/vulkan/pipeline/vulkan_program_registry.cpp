// AlcedoAndroid - Vulkan program registry implementation.
// Thread-safe SPIR-V blob store keyed by logical program name. Supports runtime
// registration (embedded shaders) and bulk load from a directory of .spv files
// (asset-extracted path).
// SPDX-License-Identifier: GPL-3.0-only
#include "vulkan/pipeline/vulkan_program_registry.hpp"

#include <cstdint>
#include <cstring>
#include <filesystem>
#include <fstream>
#include <iterator>
#include <mutex>
#include <sstream>

#include "utils/app_logging.hpp"

namespace alcedo {

VulkanProgramRegistry& VulkanProgramRegistry::Instance() {
  static VulkanProgramRegistry instance;
  return instance;
}

void VulkanProgramRegistry::Register(const std::string& name, std::vector<uint32_t> spirv) {
  std::lock_guard<std::mutex> lk(mtx_);
  programs_[name] = std::move(spirv);
  ALOGD("VulkanProgramRegistry: registered '%s' (%zu words)", name.c_str(),
        programs_[name].size());
}

std::optional<std::vector<uint32_t>> VulkanProgramRegistry::Get(const std::string& name) const {
  std::lock_guard<std::mutex> lk(mtx_);
  auto it = programs_.find(name);
  if (it == programs_.end()) return std::nullopt;
  return it->second;
}

bool VulkanProgramRegistry::Has(const std::string& name) const {
  std::lock_guard<std::mutex> lk(mtx_);
  return programs_.find(name) != programs_.end();
}

std::vector<std::string> VulkanProgramRegistry::Names() const {
  std::lock_guard<std::mutex> lk(mtx_);
  std::vector<std::string> out;
  out.reserve(programs_.size());
  for (const auto& kv : programs_) out.push_back(kv.first);
  return out;
}

void VulkanProgramRegistry::LoadFromDirectory(const std::string& dir) {
  // Best-effort: load every <dir>/<name>.spv as a SPIR-V blob. SPIR-V is a
  // stream of 32-bit words; files are read in binary and reinterpreted.
  namespace fs = std::filesystem;
  std::error_code ec;
  if (!fs::is_directory(dir, ec)) {
    ALOGW("VulkanProgramRegistry: '%s' is not a directory", dir.c_str());
    return;
  }

  for (auto& entry : fs::directory_iterator(dir, ec)) {
    if (!entry.is_regular_file()) continue;
    auto path = entry.path();
    if (path.extension() != ".spv") continue;

    std::ifstream f(path, std::ios::binary | std::ios::ate);
    if (!f) {
      ALOGW("VulkanProgramRegistry: cannot open '%s'", path.string().c_str());
      continue;
    }
    auto end = f.tellg();
    f.seekg(0, std::ios::beg);
    std::vector<uint8_t> bytes(static_cast<size_t>(end));
    if (!f.read(reinterpret_cast<char*>(bytes.data()), bytes.size())) {
      ALOGW("VulkanProgramRegistry: read failed for '%s'", path.string().c_str());
      continue;
    }
    // SPIR-V must be a multiple of 4 bytes.
    if (bytes.size() % sizeof(uint32_t) != 0) {
      ALOGW("VulkanProgramRegistry: '%s' not word-aligned", path.string().c_str());
      continue;
    }
    std::vector<uint32_t> words(bytes.size() / sizeof(uint32_t));
    std::memcpy(words.data(), bytes.data(), bytes.size());

    std::string name = path.stem().string();
    Register(name, std::move(words));
  }
}

}  // namespace alcedo
