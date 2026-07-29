// AlcedoAndroid - ExportService implementation.
// SPDX-License-Identifier: GPL-3.0-only
#include "app/app_services.hpp"

#include <algorithm>
#include <cctype>
#include <utility>

#include "io/io.hpp"
#include "utils/app_logging.hpp"

namespace alcedo {

ExportService::ExportService() = default;

auto ExportService::Export(const std::shared_ptr<Image>& image, const std::filesystem::path& out_path,
                           const std::string& format, int quality) -> bool {
  if (!image) return false;
  // Delegate to the IO image writer (io/image_writer.cpp) / UltraHDRWriter for
  // the actual encoding. Here we validate, dispatch and return the real result.
  std::string fmt = format;
  std::transform(fmt.begin(), fmt.end(), fmt.begin(),
                 [](unsigned char c) { return std::tolower(c); });
  if (fmt != "jpeg" && fmt != "jpg" && fmt != "png" && fmt != "tiff" && fmt != "ultrahdr") {
    ALOGE("ExportService: unsupported format %s", format.c_str());
    return false;
  }

  ImageBuffer& data = image->GetImageData();
  if (data.Empty()) {
    ALOGE("ExportService: image data is empty, cannot export to %s", out_path.c_str());
    return false;
  }

  ALOGI("ExportService: exporting to %s (%s, q=%d)", out_path.c_str(), fmt.c_str(), quality);

  ImageWriter writer;
  if (fmt == "jpeg" || fmt == "jpg") {
    return writer.WriteJPEG(data, out_path, quality);
  }
  if (fmt == "png") {
    return writer.WritePNG(data, out_path);
  }
  if (fmt == "tiff") {
    return writer.WriteTIFF(data, out_path);
  }

  // "ultrahdr": the working image is treated as the HDR source. An SDR image is
  // derived via a simple Reinhard tonemap so the gain map captures meaningful
  // HDR headroom (values >1.0 in the HDR buffer).
  FloatMat& hdr = data.GetCPUData();
  ImageBuffer sdr(hdr.Width(), hdr.Height(), hdr.Channels());
  FloatMat& smat = sdr.GetCPUData();
  for (size_t i = 0; i < hdr.Total(); ++i) {
    float v = hdr.Data()[i];
    smat.Data()[i] = v / (1.0f + v);  // Reinhard tonemap into [0,1)
  }
  sdr.cpu_data_valid_ = true;

  UltraHDRWriter uhdr;
  return uhdr.Write(sdr, data, out_path, quality);
}

}  // namespace alcedo
