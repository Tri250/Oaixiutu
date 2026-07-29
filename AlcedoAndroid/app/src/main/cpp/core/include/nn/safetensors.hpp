// AlcedoAndroid - Safetensors reader (NN layer).
// Minimal reader for the safetensors tensor container format used by ML models.
// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <cstdint>
#include <fstream>
#include <memory>
#include <string>
#include <unordered_map>
#include <vector>

namespace alcedo {

struct SafetensorInfo {
  std::string  name;
  std::string  dtype;
  std::vector<int64_t> shape;
  int64_t      data_offset = 0;
  int64_t      data_length = 0;
};

class SafetensorsReader {
 public:
  SafetensorsReader() = default;
  ~SafetensorsReader();

  bool Open(const std::string& path);
  void Close();

  auto GetTensorInfo(const std::string& name) const -> const SafetensorInfo*;
  auto ListTensors() const -> std::vector<std::string>;
  auto ReadTensor(const std::string& name, std::vector<uint8_t>& out_data) -> bool;

 private:
  std::ifstream                              file_;
  int64_t                                    header_size_ = 0;
  int64_t                                    data_start_  = 0;
  int64_t                                    file_size_   = 0;
  std::unordered_map<std::string, SafetensorInfo> tensors_;
  bool                                       opened_ = false;
};

}  // namespace alcedo
