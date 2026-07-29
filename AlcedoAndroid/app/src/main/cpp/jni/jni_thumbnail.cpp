// AlcedoAndroid - JNI thumbnail module.
// Thumbnail generation, retrieval, and disk-cache operations.
// SPDX-License-Identifier: GPL-3.0-only
#include <jni.h>

#include <memory>
#include <mutex>
#include <string>
#include <vector>

#include "app/app_services.hpp"
#include "image/image.hpp"
#include "jni/jni_context.hpp"
#include "storage/controller/image_controller.hpp"
#include "utils/app_logging.hpp"

extern "C" {

// Generate a thumbnail of the given target size for an image.
JNIEXPORT jboolean JNICALL Java_com_alcedo_studio_ndk_Thumbnail_nativeGenerateThumbnail(
    JNIEnv* /*env*/, jobject /*thiz*/, jint image_id, jint target_size) {
  auto* ctx = alcedo::JniAppContext::Get();
  if (!ctx || !ctx->image_pool || !ctx->db) return JNI_FALSE;
  std::lock_guard<std::mutex> lk(ctx->mtx);

  auto image = ctx->image_pool->GetImage(static_cast<alcedo::image_id_t>(image_id));
  if (!image) return JNI_FALSE;

  alcedo::ImageController img_ctrl(ctx->db->GetConnectionGuard());
  img_ctrl.CaptureImagePool(ctx->image_pool);
  alcedo::ThumbnailService svc(img_ctrl);
  svc.GenerateThumbnail(image, static_cast<uint32_t>(target_size));
  return JNI_TRUE;
}

// Retrieve the cached thumbnail for an image as a packed RGB byte array.
// Returns nullptr if no thumbnail is available.
JNIEXPORT jbyteArray JNICALL Java_com_alcedo_studio_ndk_Thumbnail_nativeGetThumbnailBytes(
    JNIEnv* env, jobject /*thiz*/, jint image_id) {
  auto* ctx = alcedo::JniAppContext::Get();
  if (!ctx || !ctx->image_pool) return nullptr;
  std::lock_guard<std::mutex> lk(ctx->mtx);

  // Prefer disk cache.
  if (ctx->thumb_cache) {
    auto cached = ctx->thumb_cache->Load(static_cast<alcedo::image_id_t>(image_id));
    if (cached) {
      jbyteArray arr = env->NewByteArray(static_cast<jsize>(cached->size()));
      env->SetByteArrayRegion(arr, 0, static_cast<jsize>(cached->size()),
                              reinterpret_cast<const jbyte*>(cached->data()));
      return arr;
    }
  }

  auto image = ctx->image_pool->GetImage(static_cast<alcedo::image_id_t>(image_id));
  if (!image || !image->has_thumbnail_.load()) return nullptr;

  // Reuse the already-resolved image handle; the previous code called
  // GetImage() a second time with the same id, which was redundant (and
  // incurred an extra lookup / shared_ptr atomic bump).
  auto& mat = image->GetThumbnailMat();
  if (mat.Empty()) return nullptr;
  // Pack the float mat as RGBA8 for the UI (clamped to [0,1]).
  std::vector<uint8_t> bytes;
  bytes.reserve(static_cast<size_t>(mat.Width()) * mat.Height() * 4);
  for (int y = 0; y < mat.Height(); ++y) {
    for (int x = 0; x < mat.Width(); ++x) {
      const float* p = mat.Ptr(y, x);
      for (int c = 0; c < 3; ++c) {
        float v = p[c];
        if (v < 0.0f) v = 0.0f;
        if (v > 1.0f) v = 1.0f;
        bytes.push_back(static_cast<uint8_t>(v * 255.0f + 0.5f));
      }
      bytes.push_back(255);  // alpha
    }
  }
  jbyteArray arr = env->NewByteArray(static_cast<jsize>(bytes.size()));
  env->SetByteArrayRegion(arr, 0, static_cast<jsize>(bytes.size()),
                          reinterpret_cast<const jbyte*>(bytes.data()));
  return arr;
}

// Store a thumbnail byte blob in the disk cache.
JNIEXPORT void JNICALL Java_com_alcedo_studio_ndk_Thumbnail_nativeCacheThumbnail(
    JNIEnv* env, jobject /*thiz*/, jint image_id, jbyteArray bytes_js) {
  auto* ctx = alcedo::JniAppContext::Get();
  if (!ctx || !ctx->thumb_cache) return;
  jsize len = env->GetArrayLength(bytes_js);
  std::vector<uint8_t> data(static_cast<size_t>(len));
  env->GetByteArrayRegion(bytes_js, 0, len, reinterpret_cast<jbyte*>(data.data()));
  std::lock_guard<std::mutex> lk(ctx->mtx);
  ctx->thumb_cache->Store(static_cast<alcedo::image_id_t>(image_id), data);
}

// Evict a thumbnail from the disk cache.
JNIEXPORT void JNICALL Java_com_alcedo_studio_ndk_Thumbnail_nativeEvictThumbnail(
    JNIEnv* /*env*/, jobject /*thiz*/, jint image_id) {
  auto* ctx = alcedo::JniAppContext::Get();
  if (!ctx || !ctx->thumb_cache) return;
  std::lock_guard<std::mutex> lk(ctx->mtx);
  ctx->thumb_cache->Evict(static_cast<alcedo::image_id_t>(image_id));
}

// Clear the entire thumbnail disk cache.
JNIEXPORT void JNICALL Java_com_alcedo_studio_ndk_Thumbnail_nativeClearThumbnailCache(
    JNIEnv* /*env*/, jobject /*thiz*/) {
  auto* ctx = alcedo::JniAppContext::Get();
  if (!ctx || !ctx->thumb_cache) return;
  std::lock_guard<std::mutex> lk(ctx->mtx);
  ctx->thumb_cache->Clear();
}

}  // extern "C"
