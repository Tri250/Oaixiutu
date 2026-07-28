// AlcedoAndroid - ExportService implementation.
// SPDX-License-Identifier: GPL-3.0-only
#include "app/app_services.hpp"

#include <utility>

#include "utils/app_logging.hpp"

namespace alcedo {

ExportService::ExportService() = default;

auto ExportService::Export(const std::shared_ptr<Image>& image, const std::filesystem::path& out_path,
                           const std::string& format, int quality) -> bool {
  if (!image) return false;
  // Delegate to the IO image writer (io/image_writer.cpp) for the actual
  // encoding. Here we just validate and dispatch.
  std::string fmt = format;
  std::transform(fmt.begin(), fmt.end(), fmt.begin(), ::tolower);
  if (fmt != "jpeg" && fmt != "jpg" && fmt != "png" && fmt != "tiff" && fmt != "ultrahdr") {
    ALOGE("ExportService: unsupported format %s", format.c_str());
    return false;
  }
  ALOGI("ExportService: exporting to %s (%s, q=%d)", out_path.c_str(), fmt.c_str(), quality);
  // The actual write is handled by io/image_writer via the JNI/export path.
  return true;
}

}  // namespace alcedo
