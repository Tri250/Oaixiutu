// AlcedoAndroid - ImageLoader implementation.
// Loads images from disk. RAW files are decoded via the raw_decoder; JPEG/PNG
// use Android's BitmapFactory through the JNI bridge.
// SPDX-License-Identifier: GPL-3.0-only
#include "io/io.hpp"

#include <algorithm>
#include <cctype>
#include <cstdint>
#include <cstring>
#include <filesystem>
#include <fstream>
#include <utility>

#include "decoders/raw_decoder.hpp"
#include "utils/app_logging.hpp"

namespace alcedo {

static auto to_lower(const std::string& s) -> std::string {
  std::string out = s;
  std::transform(out.begin(), out.end(), out.begin(),
                 [](unsigned char c) { return std::tolower(c); });
  return out;
}

auto ImageLoader::DetectType(const std::filesystem::path& path) -> ImageType {
  auto ext = to_lower(path.extension().string());
  if (ext == ".arw")  return ImageType::ARW;
  if (ext == ".cr2")  return ImageType::CR2;
  if (ext == ".cr3")  return ImageType::CR3;
  if (ext == ".nef")  return ImageType::NEF;
  if (ext == ".dng")  return ImageType::DNG;
  if (ext == ".jpg" || ext == ".jpeg") return ImageType::JPEG;
  if (ext == ".png")  return ImageType::PNG;
  if (ext == ".tif" || ext == ".tiff") return ImageType::TIFF;
  return ImageType::DEFAULT;
}

auto ImageLoader::Load(const std::filesystem::path& path) -> std::shared_ptr<Image> {
  auto type = DetectType(path);
  auto image = std::make_shared<Image>(path, type);
  image->image_name_ = path.filename().string();

  if (type == ImageType::ARW || type == ImageType::CR2 || type == ImageType::CR3 ||
      type == ImageType::NEF || type == ImageType::DNG) {
    // Decode RAW via the raw decoder.
    RawDecoder decoder;
    auto buffer = decoder.Decode(path);
    if (buffer && !buffer->Empty()) {
      image->LoadOriginalData(std::move(*buffer));
      image->has_full_img_.store(true);
    }
  } else {
    // JPEG/PNG/TIFF: read raw bytes; actual decode via Android Bitmap through JNI.
    std::ifstream f(path, std::ios::binary | std::ios::ate);
    if (f.is_open()) {
      auto size = f.tellg();
      // Bounds-check tellg(): a negative (-1 on failure) or absurdly large
      // value would drive a bogus allocation. Cap at 500 MiB.
      constexpr int64_t kMaxReadBytes = 500LL * 1024 * 1024;
      if (size < 0 || static_cast<int64_t>(size) > kMaxReadBytes) {
        ALOGW("ImageLoader: refusing to read %s (tellg=%lld)", path.c_str(),
              static_cast<long long>(size));
      } else {
        f.seekg(0, std::ios::beg);
        std::vector<uint8_t> data(static_cast<size_t>(size));
        f.read(reinterpret_cast<char*>(data.data()), size);
        image->GetImageData().ReadFromVectorBuffer(std::move(data));
        image->has_full_img_.store(true);
      }
    }
  }
  return image;
}

auto ImageLoader::LoadThumbnail(const std::filesystem::path& path, uint32_t max_size)
    -> std::shared_ptr<Image> {
  auto image = Load(path);
  if (!image) return nullptr;
  if (image->has_full_img_.load()) {
    // Downscale to max_size using nearest-neighbour on CPU.
    auto& src = image->GetImageData().GetCPUData();
    if (src.Width() > 0 && src.Height() > 0) {
      float scale = std::min(static_cast<float>(max_size) / src.Width(),
                             static_cast<float>(max_size) / src.Height());
      if (scale < 1.0f) {
        int tw = std::max(1, static_cast<int>(src.Width() * scale));
        int th = std::max(1, static_cast<int>(src.Height() * scale));
        FloatMat thumb(tw, th, src.Channels());
        for (int y = 0; y < th; ++y) {
          for (int x = 0; x < tw; ++x) {
            int sx = static_cast<int>(x / scale);
            int sy = static_cast<int>(y / scale);
            for (int c = 0; c < src.Channels(); ++c) {
              thumb.Ptr(y, x)[c] = src.Ptr(sy, sx)[c];
            }
          }
        }
        image->GetThumbnailBuffer() = FloatMat(std::move(thumb));
        image->has_thumbnail_.store(true);
        image->thumb_state_.store(ThumbState::READY);
      }
    }
  }
  return image;
}

}  // namespace alcedo
