// AlcedoAndroid - ModelDownloadService implementation.
// Downloads ML model assets to local storage (delegates actual HTTP to JNI).
// SPDX-License-Identifier: GPL-3.0-only
#include "app/app_services.hpp"

#include <cstdio>
#include <filesystem>
#include <utility>

#include "utils/app_logging.hpp"

namespace alcedo {

ModelDownloadService::ModelDownloadService(std::filesystem::path model_dir)
    : model_dir_(std::move(model_dir)) {
  std::error_code ec;
  std::filesystem::create_directories(model_dir_, ec);
}

auto ModelDownloadService::GetLocalPath(const std::string& model_key) const
    -> std::filesystem::path {
  return model_dir_ / (model_key + ".bin");
}

auto ModelDownloadService::Download(const std::string& url, const std::string& model_key) -> bool {
  // The actual HTTP download is performed by the JNI layer (Android DownloadManager
  // or OkHttp). This service validates the local result and marks availability.
  auto local = GetLocalPath(model_key);
  if (std::filesystem::exists(local)) {
    ALOGI("ModelDownloadService: model %s already present at %s", model_key.c_str(), local.c_str());
    return true;
  }
  ALOGI("ModelDownloadService: requesting download of %s from %s", model_key.c_str(), url.c_str());
  // JNI bridge handles the download; return false to indicate pending.
  return false;
}

}  // namespace alcedo
