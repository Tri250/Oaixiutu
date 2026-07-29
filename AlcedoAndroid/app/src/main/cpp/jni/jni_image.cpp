// AlcedoAndroid - JNI image module.
// Image import, load, and metadata entry points exposed to Java.
// SPDX-License-Identifier: GPL-3.0-only
#include <jni.h>

#include <memory>
#include <mutex>
#include <string>
#include <vector>

#include "app/app_services.hpp"
#include "image/image.hpp"
#include "io/io.hpp"
#include "jni/jni_context.hpp"
#include "storage/controller/image_controller.hpp"
#include "utils/app_logging.hpp"

extern "C" {

// Import a single image file into the open project library. Returns the sleeve
// element id (>0) on success, or 0 on failure.
JNIEXPORT jint JNICALL Java_com_alcedo_studio_ndk_Image_nativeImportImage(
    JNIEnv* env, jobject /*thiz*/, jstring path_js, jstring name_js) {
  auto* ctx = alcedo::JniAppContext::Get();
  if (!ctx || !ctx->project || !ctx->db) return 0;
  std::string path = alcedo::JStr(env, path_js);
  std::string name = alcedo::JStr(env, name_js);
  std::lock_guard<std::mutex> lk(ctx->mtx);

  auto& sleeve = ctx->project->GetSleeveManager();
  alcedo::ImageController img_ctrl(ctx->db->GetConnectionGuard());
  img_ctrl.CaptureImagePool(ctx->image_pool);

  alcedo::ImportService importer(sleeve, img_ctrl);
  auto file = importer.ImportImage(path);
  if (!file) return 0;
  if (!name.empty()) file->element_name_ = name;
  return static_cast<jint>(file->element_id_);
}

// Import a batch of images. Returns a Java int[] of sleeve element ids.
JNIEXPORT jintArray JNICALL Java_com_alcedo_studio_ndk_Image_nativeImportBatch(
    JNIEnv* env, jobject /*thiz*/, jobjectArray paths_js) {
  auto* ctx = alcedo::JniAppContext::Get();
  if (!ctx || !ctx->project || !ctx->db) {
    return env->NewIntArray(0);
  }
  jsize count = env->GetArrayLength(paths_js);
  std::vector<std::filesystem::path> paths;
  paths.reserve(count);
  for (jsize i = 0; i < count; ++i) {
    // GetObjectArrayElement may return null (Java allows null array slots);
    // guard the static_cast<jstring> and the subsequent JStr dereference.
    jobject element = env->GetObjectArrayElement(paths_js, i);
    if (!element) {
      ALOGW("nativeImportBatch: null path at index %d, skipping", i);
      continue;
    }
    auto js = static_cast<jstring>(element);
    paths.emplace_back(alcedo::JStr(env, js));
    env->DeleteLocalRef(js);
  }

  std::lock_guard<std::mutex> lk(ctx->mtx);
  auto& sleeve = ctx->project->GetSleeveManager();
  alcedo::ImageController img_ctrl(ctx->db->GetConnectionGuard());
  img_ctrl.CaptureImagePool(ctx->image_pool);
  alcedo::ImportService importer(sleeve, img_ctrl);
  auto files = importer.ImportBatch(paths);

  std::vector<jint> ids;
  ids.reserve(files.size());
  for (auto& f : files) {
    ids.push_back(static_cast<jint>(f->element_id_));
  }
  jintArray out = env->NewIntArray(static_cast<jsize>(ids.size()));
  env->SetIntArrayRegion(out, 0, static_cast<jsize>(ids.size()), ids.data());
  return out;
}

// Load a raw image file from disk into a native Image and return its image id.
JNIEXPORT jint JNICALL Java_com_alcedo_studio_ndk_Image_nativeLoadImage(
    JNIEnv* env, jobject /*thiz*/, jstring path_js) {
  auto* ctx = alcedo::JniAppContext::Get();
  if (!ctx || !ctx->image_pool) return -1;
  std::string path = alcedo::JStr(env, path_js);
  std::lock_guard<std::mutex> lk(ctx->mtx);

  alcedo::ImageLoader loader;
  auto image = loader.Load(path);
  if (!image) return -1;
  image->SetId(ctx->image_pool->GetCurrentID());
  ctx->image_pool->Insert(image);
  return static_cast<jint>(image->image_id_);
}

// Load only a thumbnail (decoded at reduced resolution).
JNIEXPORT jint JNICALL Java_com_alcedo_studio_ndk_Image_nativeLoadThumbnail(
    JNIEnv* env, jobject /*thiz*/, jstring path_js, jint max_size) {
  auto* ctx = alcedo::JniAppContext::Get();
  if (!ctx || !ctx->image_pool) return -1;
  std::string path = alcedo::JStr(env, path_js);
  std::lock_guard<std::mutex> lk(ctx->mtx);

  alcedo::ImageLoader loader;
  auto image = loader.LoadThumbnail(path, static_cast<uint32_t>(max_size));
  if (!image) return -1;
  image->SetId(ctx->image_pool->GetCurrentID());
  ctx->image_pool->Insert(image);
  return static_cast<jint>(image->image_id_);
}

// Remove an image from the pool (and library if present).
JNIEXPORT void JNICALL Java_com_alcedo_studio_ndk_Image_nativeRemoveImage(
    JNIEnv* /*env*/, jobject /*thiz*/, jint image_id) {
  auto* ctx = alcedo::JniAppContext::Get();
  if (!ctx || !ctx->image_pool) return;
  std::lock_guard<std::mutex> lk(ctx->mtx);
  ctx->image_pool->GetPool().erase(static_cast<alcedo::image_id_t>(image_id));
}

// Return a JSON blob describing an image (EXIF + dimensions).
JNIEXPORT jstring JNICALL Java_com_alcedo_studio_ndk_Image_nativeGetImageInfo(
    JNIEnv* env, jobject /*thiz*/, jint image_id) {
  auto* ctx = alcedo::JniAppContext::Get();
  if (!ctx || !ctx->image_pool) return env->NewStringUTF("{}");
  std::lock_guard<std::mutex> lk(ctx->mtx);
  auto img = ctx->image_pool->GetImage(static_cast<alcedo::image_id_t>(image_id));
  if (!img) return env->NewStringUTF("{}");
  nlohmann::json j;
  j["id"] = img->image_id_;
  j["name"] = img->image_name_;
  j["path"] = img->image_path_.string();
  j["width"] = img->image_data_.Width();
  j["height"] = img->image_data_.Height();
  j["channels"] = img->image_data_.Channels();
  if (img->has_exif_json_.load()) j["exif"] = nlohmann::json::parse(img->ExifToJson(), nullptr, false);
  return env->NewStringUTF(j.dump().c_str());
}

}  // extern "C"
