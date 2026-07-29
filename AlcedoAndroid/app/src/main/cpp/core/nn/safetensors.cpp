// AlcedoAndroid - SafetensorsReader implementation.
// Reads the safetensors binary format (8-byte header length + JSON header +
// raw tensor data). Used to load on-device ML model weights.
// SPDX-License-Identifier: GPL-3.0-only
#include "nn/safetensors.hpp"

#include <cstring>
#include <sstream>
#include <utility>

#include "utils/app_logging.hpp"
#include "json.hpp"

namespace alcedo {

SafetensorsReader::~SafetensorsReader() { Close(); }

bool SafetensorsReader::Open(const std::string& path) {
  Close();
  file_.open(path, std::ios::binary);
  if (!file_.is_open()) {
    ALOGE("SafetensorsReader: cannot open %s", path.c_str());
    return false;
  }
  // Record total file size for bounds checking on tensor reads.
  file_.seekg(0, std::ios::end);
  file_size_ = file_.tellg();
  file_.seekg(0, std::ios::beg);
  if (file_size_ < 0) file_size_ = 0;
  // Read 8-byte little-endian header length.
  uint64_t header_len = 0;
  file_.read(reinterpret_cast<char*>(&header_len), 8);
  if (!file_ || header_len == 0 || header_len > 100 * 1024 * 1024) {
    ALOGE("SafetensorsReader: invalid header length %llu", static_cast<unsigned long long>(header_len));
    Close();
    return false;
  }
  // Read JSON header.
  std::string header_json(header_len, '\0');
  file_.read(header_json.data(), header_len);
  if (!file_) {
    ALOGE("SafetensorsReader: failed to read header");
    Close();
    return false;
  }
  data_start_ = 8 + static_cast<int64_t>(header_len);

  // Parse JSON.
  nlohmann::json j;
  try {
    j = nlohmann::json::parse(header_json);
  } catch (...) {
    ALOGE("SafetensorsReader: invalid JSON header");
    Close();
    return false;
  }
  for (auto& [key, val] : j.items()) {
    if (key == "__metadata__") continue;
    if (!val.is_object()) continue;
    SafetensorInfo info;
    info.name = key;
    if (val.contains("dtype") && val["dtype"].is_string())
      info.dtype = val["dtype"].get<std::string>();
    if (val.contains("shape") && val["shape"].is_array()) {
      for (auto& s : val["shape"]) info.shape.push_back(s.get<int64_t>());
    }
    if (val.contains("data_offsets") && val["data_offsets"].is_array()) {
      auto& off = val["data_offsets"];
      if (off.size() >= 2) {
        info.data_offset = off[0].get<int64_t>();
        info.data_length = off[1].get<int64_t>() - info.data_offset;
      }
    }
    tensors_[key] = std::move(info);
  }
  opened_ = true;
  ALOGI("SafetensorsReader: opened %s with %zu tensors", path.c_str(), tensors_.size());
  return true;
}

void SafetensorsReader::Close() {
  if (file_.is_open()) file_.close();
  tensors_.clear();
  opened_ = false;
  data_start_ = 0;
}

auto SafetensorsReader::GetTensorInfo(const std::string& name) const -> const SafetensorInfo* {
  auto it = tensors_.find(name);
  if (it == tensors_.end()) return nullptr;
  return &it->second;
}

auto SafetensorsReader::ListTensors() const -> std::vector<std::string> {
  std::vector<std::string> names;
  names.reserve(tensors_.size());
  for (const auto& [name, _] : tensors_) names.push_back(name);
  return names;
}

auto SafetensorsReader::ReadTensor(const std::string& name, std::vector<uint8_t>& out_data) -> bool {
  if (!opened_) return false;
  auto info = GetTensorInfo(name);
  if (!info) return false;
  if (info->data_offset < 0 || info->data_length < 0) return false;
  // Bounds check: the tensor byte range must lie within the file.
  int64_t abs_start = data_start_ + info->data_offset;
  if (file_size_ > 0 && (abs_start > file_size_ || info->data_length > file_size_ - abs_start)) {
    ALOGE("SafetensorsReader: tensor '%s' range [%lld, %lld) exceeds file size %lld",
          name.c_str(), static_cast<long long>(abs_start),
          static_cast<long long>(abs_start + info->data_length),
          static_cast<long long>(file_size_));
    return false;
  }
  out_data.resize(static_cast<size_t>(info->data_length));
  file_.clear();
  file_.seekg(data_start_ + info->data_offset, std::ios::beg);
  file_.read(reinterpret_cast<char*>(out_data.data()), info->data_length);
  return static_cast<bool>(file_);
}

}  // namespace alcedo
