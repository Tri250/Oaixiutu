// AlcedoAndroid - DecoderScheduler implementation.
// SPDX-License-Identifier: GPL-3.0-only
#include "decoders/decoder_scheduler.hpp"

#include <future>
#include <memory>
#include <utility>

#include "decoders/metadata_decoder.hpp"
#include "decoders/raw_decoder.hpp"
#include "decoders/thumbnail_decoder.hpp"
#include "utils/app_logging.hpp"

namespace alcedo {

DecoderScheduler::DecoderScheduler(size_t thread_count)
    : pool_(thread_count),
      raw_decoder_(std::make_unique<RawDecoder>()),
      thumb_decoder_(std::make_unique<ThumbnailDecoder>()),
      meta_decoder_(std::make_unique<MetadataDecoder>()) {}

DecoderScheduler::~DecoderScheduler() { Shutdown(); }

auto DecoderScheduler::ScheduleRawDecode(image_id_t id, const image_path_t& path)
    -> std::future<DecodeResult> {
  auto promise = std::make_shared<std::promise<DecodeResult>>();
  auto fut = promise->get_future();
  // Capture copies of the path and a raw pointer to the decoder (pool lifetime
  // owns the decoder; the scheduler outlives its tasks via Shutdown()).
  pool_.Submit([this, id, path, promise]() {
    DecodeResult result;
    try {
      result = raw_decoder_->Decode(path, id, DecodeType::RAW);
    } catch (const std::exception& e) {
      result.success = false;
      result.error = e.what();
      ALOGW("DecoderScheduler: RAW decode failed for id=%u: %s", id, e.what());
    }
    promise->set_value(std::move(result));
  });
  return fut;
}

auto DecoderScheduler::ScheduleThumbnailDecode(image_id_t id, const image_path_t& path)
    -> std::future<DecodeResult> {
  auto promise = std::make_shared<std::promise<DecodeResult>>();
  auto fut = promise->get_future();
  pool_.Submit([this, id, path, promise]() {
    DecodeResult result;
    try {
      result = thumb_decoder_->Decode(path, id, DecodeType::THUMB);
    } catch (const std::exception& e) {
      result.success = false;
      result.error = e.what();
      ALOGW("DecoderScheduler: thumbnail decode failed for id=%u: %s", id, e.what());
    }
    promise->set_value(std::move(result));
  });
  return fut;
}

auto DecoderScheduler::ScheduleMetadataDecode(image_id_t id, const image_path_t& path)
    -> std::future<DecodeResult> {
  auto promise = std::make_shared<std::promise<DecodeResult>>();
  auto fut = promise->get_future();
  pool_.Submit([this, id, path, promise]() {
    DecodeResult result;
    try {
      result = meta_decoder_->Decode(path, id, DecodeType::REGULAR);
    } catch (const std::exception& e) {
      result.success = false;
      result.error = e.what();
      ALOGW("DecoderScheduler: metadata decode failed for id=%u: %s", id, e.what());
    }
    promise->set_value(std::move(result));
  });
  return fut;
}

auto DecoderScheduler::DecodeNow(image_id_t id, const image_path_t& path, DecodeType type)
    -> DecodeResult {
  switch (type) {
    case DecodeType::THUMB:          return thumb_decoder_->Decode(path, id, type);
    case DecodeType::RAW:            return raw_decoder_->Decode(path, id, type);
    case DecodeType::SLEEVE_LOADING:
    case DecodeType::REGULAR:        return meta_decoder_->Decode(path, id, type);
  }
  return meta_decoder_->Decode(path, id, type);
}

void DecoderScheduler::Shutdown() { pool_.Shutdown(); }

}  // namespace alcedo
