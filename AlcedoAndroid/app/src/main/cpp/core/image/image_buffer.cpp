// AlcedoAndroid - ImageBuffer implementation.
// SPDX-License-Identifier: GPL-3.0-only
#include "image/image_buffer.hpp"

#include <cstring>
#include <utility>

#include "image/vulkan_image.hpp"
#include "utils/app_logging.hpp"
#include "vulkan/context/vulkan_context.hpp"

namespace alcedo {

// ---------------- GpuImageWrapper ----------------

GpuImageWrapper::~GpuImageWrapper() { Release(); }

GpuImageWrapper::GpuImageWrapper(GpuImageWrapper&& other) noexcept
    : backend_(other.backend_), width_(other.width_), height_(other.height_),
      channels_(other.channels_),
      vulkan_image_(std::move(other.vulkan_image_)) {
  other.backend_ = GpuBackendKind::None;
  other.width_ = other.height_ = other.channels_ = 0;
}

GpuImageWrapper& GpuImageWrapper::operator=(GpuImageWrapper&& other) noexcept {
  if (this != &other) {
    Release();
    backend_       = other.backend_;
    width_         = other.width_;
    height_        = other.height_;
    channels_      = other.channels_;
    vulkan_image_  = std::move(other.vulkan_image_);
    other.backend_ = GpuBackendKind::None;
    other.width_ = other.height_ = other.channels_ = 0;
  }
  return *this;
}

void GpuImageWrapper::Create(int width, int height, int channels, GpuBackendKind backend) {
  Release();
  width_ = width;
  height_ = height;
  channels_ = channels;
  backend_ = backend;
  if (backend == GpuBackendKind::Vulkan) {
    vulkan_image_ = std::make_unique<VulkanImage>();
    VulkanContext* ctx = VulkanContext::Ensure();
    if (!ctx || !ctx->Valid()) {
      ALOGW("GpuImageWrapper: Vulkan context unavailable, GPU data stays empty");
      vulkan_image_.reset();
      backend_ = GpuBackendKind::None;
      return;
    }
    if (!vulkan_image_->Create(ctx, width, height, channels)) {
      ALOGW("GpuImageWrapper: VulkanImage create failed");
      vulkan_image_.reset();
      backend_ = GpuBackendKind::None;
    }
  }
}

void GpuImageWrapper::Upload(const FloatMat& cpu) {
  if (!vulkan_image_) {
    Create(cpu.Width(), cpu.Height(), cpu.Channels(), GpuBackendKind::Vulkan);
  }
  if (vulkan_image_) vulkan_image_->Upload(cpu.Data());
}

void GpuImageWrapper::Download(FloatMat& cpu) const {
  if (!vulkan_image_) return;
  if (cpu.Width() != vulkan_image_->Width() || cpu.Height() != vulkan_image_->Height() ||
      cpu.Channels() != vulkan_image_->Channels()) {
    cpu = FloatMat(vulkan_image_->Width(), vulkan_image_->Height(), vulkan_image_->Channels());
  }
  vulkan_image_->Download(cpu.Data());
}

void GpuImageWrapper::Release() {
  if (vulkan_image_) {
    vulkan_image_->Destroy();
    vulkan_image_.reset();
  }
  backend_ = GpuBackendKind::None;
  width_ = height_ = channels_ = 0;
}

// ---------------- ImageBuffer ----------------

ImageBuffer::~ImageBuffer() {
  ReleaseCPUData();
  ReleaseGPUData();
  ReleaseBuffer();
}

ImageBuffer::ImageBuffer(ImageBuffer&& other) noexcept : ImageBuffer() { *this = std::move(other); }

ImageBuffer& ImageBuffer::operator=(ImageBuffer&& other) noexcept {
  if (this == &other) return *this;
  cpu_data_       = std::move(other.cpu_data_);
  gpu_data_       = std::move(other.gpu_data_);
  buffer_         = std::move(other.buffer_);
  cpu_data_valid_.store(other.cpu_data_valid_.load());
  gpu_data_valid_.store(other.gpu_data_valid_.load());
  buffer_valid_   = other.buffer_valid_;
  other.cpu_data_valid_.store(false);
  other.gpu_data_valid_.store(false);
  other.buffer_valid_ = false;
  return *this;
}

ImageBuffer::ImageBuffer(FloatMat&& data) : cpu_data_(std::move(data)) {
  cpu_data_valid_ = !cpu_data_.Empty();
}

ImageBuffer::ImageBuffer(int width, int height, int channels)
    : cpu_data_(width, height, channels) {
  cpu_data_valid_ = !cpu_data_.Empty();
}

ImageBuffer::ImageBuffer(std::vector<uint8_t>&& raw_buffer) {
  ReadFromVectorBuffer(std::move(raw_buffer));
}

void ImageBuffer::ReadFromVectorBuffer(std::vector<uint8_t>&& raw) {
  buffer_ = std::make_unique<std::vector<uint8_t>>(std::move(raw));
  buffer_valid_ = !buffer_->empty();
}

void ImageBuffer::SyncToGPU() { SyncToGPU(GpuBackendKind::Vulkan); }

void ImageBuffer::SyncToGPU(GpuBackendKind backend) {
  if (cpu_data_.Empty()) return;
  if (gpu_data_.Empty() || gpu_data_.Width() != cpu_data_.Width() ||
      gpu_data_.Height() != cpu_data_.Height() || gpu_data_.Channels() != cpu_data_.Channels() ||
      gpu_data_.Backend() != backend) {
    gpu_data_.Create(cpu_data_.Width(), cpu_data_.Height(), cpu_data_.Channels(), backend);
  }
  gpu_data_.Upload(cpu_data_);
  gpu_data_valid_ = !gpu_data_.Empty();
}

void ImageBuffer::SyncToCPU() {
  if (gpu_data_.Empty()) return;
  gpu_data_.Download(cpu_data_);
  cpu_data_valid_ = !cpu_data_.Empty();
}

void ImageBuffer::InitGPUData(int width, int height, int channels, GpuBackendKind backend) {
  gpu_data_.Create(width, height, channels, backend);
}

void ImageBuffer::ConvertGPUDataTo(int channels, double alpha, double beta) {
  (void)channels; (void)alpha; (void)beta;
  // Channel/format conversion on GPU is handled by dedicated kernels; this is
  // a no-op fallback that re-syncs to CPU for the host path.
  SyncToCPU();
}

void ImageBuffer::ShareGPUDataFrom(const ImageBuffer& src) {
  gpu_data_ = GpuImageWrapper();  // reset
  // Vulkan images are not shareable across buffers without aliasing; copy instead.
  if (src.gpu_data_.Empty()) return;
  SyncToCPU();
  SyncToGPU(src.gpu_data_.Backend());
}

void ImageBuffer::CopyGPUDataTo(ImageBuffer& dst) const {
  if (gpu_data_.Empty()) {
    dst.SyncToCPU();
    return;
  }
  dst.SyncToCPU();
  dst.SyncToGPU(gpu_data_.Backend());
}

ImageBuffer ImageBuffer::Clone() const {
  ImageBuffer c;
  c.cpu_data_ = cpu_data_.Clone();
  c.cpu_data_valid_.store(cpu_data_valid_.load());
  if (buffer_) {
    c.buffer_ = std::make_unique<std::vector<uint8_t>>(*buffer_);
    c.buffer_valid_ = buffer_valid_;
  }
  return c;
}

void ImageBuffer::ReleaseCPUData() {
  cpu_data_.Release();
  cpu_data_valid_ = false;
}

void ImageBuffer::ReleaseGPUData() {
  gpu_data_.Release();
  gpu_data_valid_ = false;
}

void ImageBuffer::ReleaseBuffer() {
  if (buffer_) buffer_->clear();
  buffer_.reset();
  buffer_valid_ = false;
}

}  // namespace alcedo
