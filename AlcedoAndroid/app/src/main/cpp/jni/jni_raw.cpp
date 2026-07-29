// AlcedoAndroid - JNI RAW module.
// RAW decode (synchronous and scheduled), RAW parameter configuration, and
// runtime color context metadata access.
// SPDX-License-Identifier: GPL-3.0-only
#include <jni.h>

#include <future>
#include <memory>
#include <mutex>
#include <string>

#include "decoders/data_decoder.hpp"
#include "decoders/decoder_scheduler.hpp"
#include "decoders/raw_processor.hpp"
#include "image/image.hpp"
#include "jni/jni_context.hpp"
#include "utils/app_logging.hpp"
#include "vulkan/context/vulkan_context.hpp"

extern "C" {

// Decode a RAW file synchronously. Returns the image id of the decoded RGB
// buffer, or -1 on failure.
JNIEXPORT jint JNICALL Java_com_alcedo_studio_ndk_Raw_nativeDecodeRaw(
    JNIEnv* env, jobject /*thiz*/, jstring path_js, jint decode_res) {
  auto* ctx = alcedo::JniAppContext::Get();
  if (!ctx || !ctx->decoder || !ctx->image_pool) return -1;
  std::string path = alcedo::JStr(env, path_js);
  std::lock_guard<std::mutex> lk(ctx->mtx);

  auto result = ctx->decoder->DecodeNow(0, path, alcedo::DecodeType::RAW);
  if (!result.success || !result.buffer) return -1;

  auto image = std::make_shared<alcedo::Image>(path, alcedo::ImageType::DNG);
  image->image_data_ = std::move(*result.buffer);
  image->SetId(ctx->image_pool->GetCurrentID());
  ctx->image_pool->Insert(image);
  return static_cast<jint>(image->image_id_);
}

// Schedule a RAW decode; returns a request token (monotonic) the caller can use
// to poll for completion. Here we return the image id once the future resolves
// (blocking) for simplicity.
JNIEXPORT jint JNICALL Java_com_alcedo_studio_ndk_Raw_nativeDecodeRawAsync(
    JNIEnv* env, jobject /*thiz*/, jstring path_js) {
  auto* ctx = alcedo::JniAppContext::Get();
  if (!ctx) return -1;
  std::string path = alcedo::JStr(env, path_js);
  // Capture shared ownership of the decoder under the lock, then release the
  // lock while waiting on the future. This keeps the scheduler (and the
  // decoders its tasks reference) alive for the duration of the wait even if a
  // concurrent nativeShutdown drops the context's reference.
  std::shared_ptr<alcedo::DecoderScheduler> decoder;
  {
    std::lock_guard<std::mutex> lk(ctx->mtx);
    decoder = ctx->decoder;
  }
  if (!decoder) return -1;

  auto fut = decoder->ScheduleRawDecode(0, path);
  alcedo::DecodeResult result;
  try {
    result = fut.get();
  } catch (const std::exception& e) {
    ALOGW("nativeDecodeRawAsync: decode future failed for %s: %s", path.c_str(), e.what());
    return -1;
  }
  if (!result.success || !result.buffer) return -1;

  // Re-check validity before accessing the image pool: it may have been torn
  // down by a concurrent shutdown while we were waiting.
  std::lock_guard<std::mutex> lk(ctx->mtx);
  if (!ctx->image_pool) {
    ALOGW("nativeDecodeRawAsync: image pool unavailable after decode for %s", path.c_str());
    return -1;
  }
  auto image = std::make_shared<alcedo::Image>(path, alcedo::ImageType::DNG);
  image->image_data_ = std::move(*result.buffer);
  image->SetId(ctx->image_pool->GetCurrentID());
  ctx->image_pool->Insert(image);
  return static_cast<jint>(image->image_id_);
}

// Select the RAW GPU backend (0 = CPU, 1 = Vulkan).
JNIEXPORT void JNICALL Java_com_alcedo_studio_ndk_Raw_nativeSetRawBackend(
    JNIEnv* /*env*/, jobject /*thiz*/, jint backend) {
  auto* ctx = alcedo::JniAppContext::Get();
  if (!ctx) return;
  std::lock_guard<std::mutex> lk(ctx->mtx);
  // The backend choice is applied per RawProcessor construction; record the
  // preference by ensuring Vulkan is initialised when Vulkan is requested.
  if (backend == 1) {
    alcedo::VulkanContext::Ensure();
  }
}

// Get RAW runtime color context (cam mul, color matrices, lens metadata) as JSON.
JNIEXPORT jstring JNICALL Java_com_alcedo_studio_ndk_Raw_nativeGetRawMetadata(
    JNIEnv* env, jobject /*thiz*/, jint image_id) {
  auto* ctx = alcedo::JniAppContext::Get();
  if (!ctx || !ctx->image_pool) return env->NewStringUTF("{}");
  std::lock_guard<std::mutex> lk(ctx->mtx);
  auto img = ctx->image_pool->GetImage(static_cast<alcedo::image_id_t>(image_id));
  if (!img || !img->HasRawColorContext()) return env->NewStringUTF("{}");

  const auto& c = img->GetRawColorContext();
  nlohmann::json j;
  j["valid"] = c.valid_;
  j["camera_make"] = c.camera_make_;
  j["camera_model"] = c.camera_model_;
  j["output_in_camera_space"] = c.output_in_camera_space_;
  nlohmann::json cam_mul = nlohmann::json::array();
  nlohmann::json cam_xyz = nlohmann::json::array();
  for (int i = 0; i < 3; ++i) cam_mul.push_back(c.cam_mul_[i]);
  for (int i = 0; i < 9; ++i) cam_xyz.push_back(c.cam_xyz_[i]);
  j["cam_mul"] = cam_mul;
  j["cam_xyz"] = cam_xyz;
  j["lens_make"] = c.lens_make_;
  j["lens_model"] = c.lens_model_;
  j["focal_mm"] = c.focal_length_mm_;
  j["aperture_f"] = c.aperture_f_number_;
  return env->NewStringUTF(j.dump().c_str());
}

// Set a custom white balance (CCT + tint) for a decoded image.
JNIEXPORT void JNICALL Java_com_alcedo_studio_ndk_Raw_nativeSetWhiteBalance(
    JNIEnv* /*env*/, jobject /*thiz*/, jint /*image_id*/, jfloat cct, jfloat tint) {
  auto* ctx = alcedo::JniAppContext::Get();
  if (!ctx) return;
  std::lock_guard<std::mutex> lk(ctx->mtx);
  auto executor = alcedo::CreatePipelineExecutor();
  if (!executor) return;
  auto& params = executor->GetGlobalParams();
  params.color_temp_enabled_ = true;
  params.color_temp_mode_ = alcedo::ColorTempMode::CUSTOM;
  params.color_temp_custom_cct_ = cct;
  params.color_temp_custom_tint_ = tint;
  params.color_temp_runtime_dirty_ = true;
}

}  // extern "C"
