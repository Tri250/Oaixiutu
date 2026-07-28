// AlcedoAndroid - ThumbnailDiskCacheService implementation.
// SPDX-License-Identifier: GPL-3.0-only
#include "app/app_services.hpp"

#include <cstdio>
#include <fstream>
#include <utility>

#include "utils/app_logging.hpp"

namespace alcedo {

ThumbnailDiskCacheService::ThumbnailDiskCacheService(std::filesystem::path cache_dir)
    : cache_dir_(std::move(cache_dir)) {
  std::error_code ec;
  std::filesystem::create_directories(cache_dir_, ec);
}

auto ThumbnailDiskCacheService::PathFor(image_id_t id) const -> std::filesystem::path {
  char buf[32];
  std::snprintf(buf, sizeof(buf), "%u.thumb", id);
  return cache_dir_ / buf;
}

auto ThumbnailDiskCacheService::Load(image_id_t id) -> std::optional<std::vector<uint8_t>> {
  auto path = PathFor(id);
  std::ifstream f(path, std::ios::binary | std::ios::ate);
  if (!f.is_open()) return std::nullopt;
  auto size = f.tellg();
  if (size <= 0) return std::nullopt;
  f.seekg(0, std::ios::beg);
  std::vector<uint8_t> data(static_cast<size_t>(size));
  f.read(reinterpret_cast<char*>(data.data()), size);
  return data;
}

void ThumbnailDiskCacheService::Store(image_id_t id, const std::vector<uint8_t>& data) {
  auto path = PathFor(id);
  std::ofstream f(path, std::ios::binary | std::ios::trunc);
  if (!f.is_open()) {
    ALOGW("ThumbnailDiskCache: cannot write %s", path.c_str());
    return;
  }
  f.write(reinterpret_cast<const char*>(data.data()), static_cast<std::streamsize>(data.size()));
}

void ThumbnailDiskCacheService::Evict(image_id_t id) {
  std::error_code ec;
  std::filesystem::remove(PathFor(id), ec);
}

void ThumbnailDiskCacheService::Clear() {
  std::error_code ec;
  std::filesystem::remove_all(cache_dir_, ec);
  std::filesystem::create_directories(cache_dir_, ec);
}

}  // namespace alcedo
