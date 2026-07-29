// AlcedoAndroid - ImageBuffer with Vulkan backend support.
// Self-contained 32-bit float image buffer replacing the desktop OpenCV-backed
// buffer. All pixel processing operates in linear 32-bit float. The optional
// Vulkan image holds a device-local copy of the same data for compute kernels.
// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <atomic>
#include <cstdint>
#include <cstring>
#include <functional>
#include <memory>
#include <vector>

#include "image/gpu_backend.hpp"
#include "image/vulkan_image.hpp"

namespace alcedo {

// A packed 3-channel (RGB) float pixel used by point operators.
struct alignas(16) Pixel {
  float r = 0.0f;
  float g = 0.0f;
  float b = 0.0f;
  float& operator[](int i) { return (&r)[i]; }
  float  operator[](int i) const { return (&r)[i]; }
};

// Internal CPU float buffer. Channels is typically 3 (RGB) for the working
// space; raw buffers may use 1 (Bayer) or 4 (RGBA).
class FloatMat {
 public:
  FloatMat() = default;
  FloatMat(int width, int height, int channels)
      : width_(width), height_(height), channels_(channels),
        data_(static_cast<size_t>(width) * height * channels, 0.0f) {}

  int   Width() const { return width_; }
  int   Height() const { return height_; }
  int   Channels() const { return channels_; }
  bool  Empty() const { return data_.empty(); }
  size_t Total() const { return static_cast<size_t>(width_) * height_ * channels_; }

  float*       Data() { return data_.data(); }
  const float* Data() const { return data_.data(); }

  float*       Ptr(int y, int x) {
    return data_.data() + (static_cast<size_t>(y) * width_ + x) * channels_;
  }
  const float* Ptr(int y, int x) const {
    return data_.data() + (static_cast<size_t>(y) * width_ + x) * channels_;
  }

  Pixel& PixelAt(int y, int x) { return *reinterpret_cast<Pixel*>(Ptr(y, x)); }
  const Pixel& PixelAt(int y, int x) const { return *reinterpret_cast<const Pixel*>(Ptr(y, x)); }

  void ForEachPixel(const std::function<void(Pixel&, int, int)>& fn) {
    for (int y = 0; y < height_; ++y) {
      for (int x = 0; x < width_; ++x) {
        fn(PixelAt(y, x), x, y);
      }
    }
  }

  void Fill(float v) { std::fill(data_.begin(), data_.end(), v); }

  FloatMat Clone() const {
    FloatMat m(width_, height_, channels_);
    m.data_ = data_;
    return m;
  }

  void Release() {
    data_.clear();
    data_.shrink_to_fit();
    width_ = height_ = channels_ = 0;
  }

 private:
  int                 width_    = 0;
  int                 height_   = 0;
  int                 channels_ = 0;
  std::vector<float>  data_;
};

// A GPU image wrapper holding a handle to a Vulkan device image + its layout.
class GpuImageWrapper {
 public:
  GpuImageWrapper() = default;
  ~GpuImageWrapper();
  GpuImageWrapper(const GpuImageWrapper&)            = delete;
  GpuImageWrapper& operator=(const GpuImageWrapper&) = delete;
  GpuImageWrapper(GpuImageWrapper&&) noexcept;
  GpuImageWrapper& operator=(GpuImageWrapper&&) noexcept;

  GpuBackendKind Backend() const { return backend_; }
  bool           Empty() const { return width_ == 0 || height_ == 0; }
  int            Width() const { return width_; }
  int            Height() const { return height_; }
  int            Channels() const { return channels_; }

  void Create(int width, int height, int channels, GpuBackendKind backend = GpuBackendKind::None);
  void Upload(const FloatMat& cpu);
  void Download(FloatMat& cpu) const;
  void Release();

  // Access the underlying Vulkan image (may be nullptr when CPU-only).
  VulkanImage* GetVulkanImage() { return vulkan_image_.get(); }
  const VulkanImage* GetVulkanImage() const { return vulkan_image_.get(); }

 private:
  GpuBackendKind                 backend_   = GpuBackendKind::None;
  int                            width_     = 0;
  int                            height_    = 0;
  int                            channels_  = 0;
  std::unique_ptr<VulkanImage>   vulkan_image_;
};

// The primary image buffer used by the pipeline. Holds a CPU float mat and an
// optional GPU wrapper; callers keep them in sync via SyncToGPU/SyncToCPU.
class ImageBuffer {
 public:
  ImageBuffer() = default;
  ~ImageBuffer();
  ImageBuffer(const ImageBuffer&)            = delete;
  ImageBuffer& operator=(const ImageBuffer&) = delete;
  ImageBuffer(ImageBuffer&& other) noexcept;
  ImageBuffer& operator=(ImageBuffer&& other) noexcept;

  explicit ImageBuffer(FloatMat&& data);
  ImageBuffer(int width, int height, int channels);
  explicit ImageBuffer(std::vector<uint8_t>&& raw_buffer);

  void ReadFromVectorBuffer(std::vector<uint8_t>&& buffer);

  // CPU access.
  FloatMat&       GetCPUData() { return cpu_data_; }
  const FloatMat& GetCPUData() const { return cpu_data_; }
  std::vector<uint8_t>& GetBuffer() {
    if (!buffer_) buffer_ = std::make_unique<std::vector<uint8_t>>();
    return *buffer_;
  }

  int  Width() const { return cpu_data_.Width(); }
  int  Height() const { return cpu_data_.Height(); }
  int  Channels() const { return cpu_data_.Channels(); }
  bool Empty() const { return cpu_data_.Empty() && (!buffer_ || buffer_->empty()); }

  // GPU access.
  GpuImageWrapper&       GetGPUData() { return gpu_data_; }
  const GpuImageWrapper& GetGPUData() const { return gpu_data_; }
  GpuBackendKind         GetGPUBackend() const { return gpu_data_.Backend(); }
  int                    GetGPUWidth() const { return gpu_data_.Width(); }
  int                    GetGPUHeight() const { return gpu_data_.Height(); }

  VulkanImage* GetVulkanImage() { return gpu_data_.GetVulkanImage(); }

  // Sync.
  void SyncToGPU();
  void SyncToGPU(GpuBackendKind backend);
  void SyncToCPU();
  void InitGPUData(int width, int height, int channels,
                   GpuBackendKind backend = GpuBackendKind::None);
  void SetGPUDataValid(bool valid) { gpu_data_valid_ = valid; }
  void ConvertGPUDataTo(int channels, double alpha = 1.0, double beta = 0.0);
  void ShareGPUDataFrom(const ImageBuffer& src);
  void CopyGPUDataTo(ImageBuffer& dst) const;

  ImageBuffer Clone() const;

  void ReleaseCPUData();
  void ReleaseGPUData();
  void ReleaseBuffer();

  // Accessed from multiple threads (decode workers, render thread, pipeline);
  // kept atomic so readers observe a consistent validity state without locking.
  std::atomic<bool> cpu_data_valid_{false};
  std::atomic<bool> gpu_data_valid_{false};
  bool              buffer_valid_   = false;

 private:
  FloatMat                      cpu_data_;
  GpuImageWrapper               gpu_data_;
  std::unique_ptr<std::vector<uint8_t>> buffer_;
};

}  // namespace alcedo
