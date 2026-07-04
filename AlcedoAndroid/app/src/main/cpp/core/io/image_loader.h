// Ported from AlcedoStudio desktop: io/image_loader.hpp
// SPDX-License-Identifier: GPL-3.0-only
//
// Image loader for Android. Uses DecoderScheduler internally.
// No Qt, no OpenCV.

#pragma once

#include <cstdint>
#include <functional>
#include <memory>
#include <string>
#include <vector>

#include "decoders/decoder_scheduler.h"
#include "image/image.h"
#include "image/image_buffer.h"

namespace alcedo {

// ============================================================
// Decode type
// ============================================================
enum class DecodeType {
    SLEEVE_LOADING = 0,  // Lightweight metadata + tiny thumbnail
    THUMB,               // Thumbnail decode
    FULL                 // Full resolution decode
};

// ============================================================
// Image Loader
// ============================================================
class ImageLoader {
public:
    ImageLoader();
    ~ImageLoader();

    // ── Batch loading ──
    // Submit a list of file paths for background loading.
    // Paths are queued in the DecoderScheduler; the caller receives results
    // via the completion callback.
    void StartLoading(const std::vector<std::string>& paths,
                      DecodeType type = DecodeType::SLEEVE_LOADING,
                      DecodeCompleteFn complete_fn = nullptr);

    // ── Single image loading ──
    // Convenience: submit one path and get a task_id back.
    uint64_t StartLoading(const std::string& path,
                          DecodeType type = DecodeType::THUMB,
                          DecodeTask::Callback on_complete = nullptr);

    // ── Synchronous load ──
    // Loads a single image synchronously (blocks the calling thread).
    // Returns nullptr on failure.
    std::shared_ptr<Image> LoadImage(const std::string& path,
                                     DecodeType type = DecodeType::FULL);

    // ── Cancel ──
    bool CancelTask(uint64_t task_id);
    void CancelAll();

    // ── Scheduler access ──
    DecoderScheduler& Scheduler() { return scheduler_; }

private:
    DecoderScheduler scheduler_;

    // Detect ImageType from file extension.
    static ImageType DetectImageType(const std::string& path);
};

// ============================================================
// Byte Buffer Loader
// ============================================================
class ByteBufferLoader {
public:
    // Load raw bytes from a file path. Returns empty vector on failure.
    static std::vector<uint8_t> LoadByteBufferFromPath(const std::string& path);
};

} // namespace alcedo
