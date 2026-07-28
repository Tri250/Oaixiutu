// AlcedoAndroid - DecoderScheduler.
// Schedules decode jobs (RAW / thumbnail / metadata) onto a thread pool and
// delivers results via futures. Replaces the desktop BufferQueue-based
// scheduler with a simpler future-based design suited to the Android JNI layer.
// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <future>
#include <memory>
#include <string>
#include <vector>

#include "concurrency/thread_pool.hpp"
#include "decoders/data_decoder.hpp"
#include "type/type.hpp"

namespace alcedo {

class RawDecoder;
class ThumbnailDecoder;
class MetadataDecoder;

class DecoderScheduler {
 public:
  explicit DecoderScheduler(size_t thread_count = 2);
  ~DecoderScheduler();

  // Schedule a full RAW decode for a file. The returned future yields the
  // decode result once the worker finishes.
  auto ScheduleRawDecode(image_id_t id, const image_path_t& path)
      -> std::future<DecodeResult>;

  // Schedule a thumbnail decode for a file.
  auto ScheduleThumbnailDecode(image_id_t id, const image_path_t& path)
      -> std::future<DecodeResult>;

  // Schedule a metadata-only decode for a file.
  auto ScheduleMetadataDecode(image_id_t id, const image_path_t& path)
      -> std::future<DecodeResult>;

  // Run a decode synchronously on the calling thread (useful for tests / JNI).
  auto DecodeNow(image_id_t id, const image_path_t& path, DecodeType type) -> DecodeResult;

  void Shutdown();

 private:
  ThreadPool                 pool_;
  std::unique_ptr<RawDecoder>       raw_decoder_;
  std::unique_ptr<ThumbnailDecoder> thumb_decoder_;
  std::unique_ptr<MetadataDecoder>  meta_decoder_;
};

}  // namespace alcedo
