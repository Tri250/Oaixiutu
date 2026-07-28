// AlcedoAndroid - ExportICCProfileResolver implementation.
// Resolves ICC colour profiles for export based on the target colour space.
// SPDX-License-Identifier: GPL-3.0-only
#include "io/io.hpp"

#include <utility>

#include "utils/app_logging.hpp"

namespace alcedo {

auto ExportICCProfileResolver::Resolve(const std::string& color_space)
    -> std::optional<std::vector<uint8_t>> {
  auto it = profiles_.find(color_space);
  if (it != profiles_.end()) return it->second;
  // Built-in fallbacks: sRGB is the default. The actual ICC binary blobs are
  // registered by the JNI layer from Android resources.
  if (color_space == "sRGB" || color_space == "srgb") {
    // Return empty; the JNI export path embeds the system sRGB profile.
    ALOGI("ExportICCResolver: using system sRGB profile");
    return std::nullopt;
  }
  if (color_space == "DisplayP3" || color_space == "display_p3") {
    ALOGI("ExportICCResolver: using system DisplayP3 profile");
    return std::nullopt;
  }
  if (color_space == "AdobeRGB" || color_space == "adobe_rgb") {
    ALOGI("ExportICCResolver: using system AdobeRGB profile");
    return std::nullopt;
  }
  ALOGW("ExportICCResolver: unknown colour space %s", color_space.c_str());
  return std::nullopt;
}

void ExportICCProfileResolver::RegisterProfile(const std::string& color_space,
                                               std::vector<uint8_t> profile) {
  profiles_[color_space] = std::move(profile);
}

}  // namespace alcedo
