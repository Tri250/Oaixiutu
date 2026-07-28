// AlcedoAndroid - JNI AI module.
// AI image description, rating, credential management, and provider profile.
// SPDX-License-Identifier: GPL-3.0-only
#include <jni.h>

#include <memory>
#include <mutex>
#include <string>
#include <vector>

#include "ai/ai.hpp"
#include "app/app_services.hpp"
#include "image/image.hpp"
#include "jni/jni_context.hpp"
#include "utils/app_logging.hpp"

extern "C" {

// Generate a natural-language description of an image. Returns JSON.
JNIEXPORT jstring JNICALL Java_com_alcedo_studio_ndk_Ai_nativeDescribeImage(
    JNIEnv* env, jobject /*thiz*/, jint image_id) {
  auto* ctx = alcedo::JniAppContext::Get();
  if (!ctx || !ctx->image_pool) return env->NewStringUTF("{}");
  std::lock_guard<std::mutex> lk(ctx->mtx);
  auto img = ctx->image_pool->GetImage(static_cast<alcedo::image_id_t>(image_id));
  if (!img) return env->NewStringUTF("{}");

  alcedo::AiDescriptionInference inference;
  auto result = inference.Infer(img);
  nlohmann::json j;
  j["caption"] = result.caption;
  j["scene"] = result.scene;
  j["confidence"] = result.confidence;
  j["tags"] = result.tags;
  return env->NewStringUTF(j.dump().c_str());
}

// Generate a quality rating for an image. Returns JSON.
JNIEXPORT jstring JNICALL Java_com_alcedo_studio_ndk_Ai_nativeRateImage(
    JNIEnv* env, jobject /*thiz*/, jint image_id) {
  auto* ctx = alcedo::JniAppContext::Get();
  if (!ctx || !ctx->image_pool) return env->NewStringUTF("{}");
  std::lock_guard<std::mutex> lk(ctx->mtx);
  auto img = ctx->image_pool->GetImage(static_cast<alcedo::image_id_t>(image_id));
  if (!img) return env->NewStringUTF("{}");

  alcedo::AiRatingInference inference;
  auto result = inference.Infer(img);
  nlohmann::json j;
  j["rating"] = result.rating;
  j["rubric_id"] = result.rubric_id;
  j["reasons"] = result.reasons;
  return env->NewStringUTF(j.dump().c_str());
}

// Set an AI provider API key.
JNIEXPORT void JNICALL Java_com_alcedo_studio_ndk_Ai_nativeSetCredential(
    JNIEnv* env, jobject /*thiz*/, jstring provider_js, jstring api_key_js) {
  auto* ctx = alcedo::JniAppContext::Get();
  if (!ctx || !ctx->ai_credentials) return;
  std::string provider = alcedo::JStr(env, provider_js);
  std::string api_key = alcedo::JStr(env, api_key_js);
  std::lock_guard<std::mutex> lk(ctx->mtx);
  ctx->ai_credentials->SetCredential(provider, api_key);
}

// Configure an AI provider profile (base URL + model id).
JNIEXPORT void JNICALL Java_com_alcedo_studio_ndk_Ai_nativeSetProviderProfile(
    JNIEnv* env, jobject /*thiz*/, jstring provider_js, jstring base_url_js, jstring model_js) {
  auto* ctx = alcedo::JniAppContext::Get();
  if (!ctx || !ctx->ai_profile) return;
  std::string provider = alcedo::JStr(env, provider_js);
  std::string base_url = alcedo::JStr(env, base_url_js);
  std::string model = alcedo::JStr(env, model_js);
  std::lock_guard<std::mutex> lk(ctx->mtx);
  ctx->ai_profile->SetProfile(provider, base_url, model);
}

// Register a local ML model asset.
JNIEXPORT void JNICALL Java_com_alcedo_studio_ndk_Ai_nativeRegisterModel(
    JNIEnv* env, jobject /*thiz*/, jstring key_js, jstring path_js, jlong size_bytes) {
  auto* ctx = alcedo::JniAppContext::Get();
  if (!ctx || !ctx->model_catalog) return;
  std::string key = alcedo::JStr(env, key_js);
  std::string path = alcedo::JStr(env, path_js);
  std::lock_guard<std::mutex> lk(ctx->mtx);
  ctx->model_catalog->RegisterModel(key, path, static_cast<int64_t>(size_bytes));
}

// List registered ML model keys.
JNIEXPORT jobjectArray JNICALL Java_com_alcedo_studio_ndk_Ai_nativeListModels(
    JNIEnv* env, jobject /*thiz*/) {
  auto* ctx = alcedo::JniAppContext::Get();
  if (!ctx || !ctx->model_catalog) return alcedo::ToJStringArray(env, {});
  std::lock_guard<std::mutex> lk(ctx->mtx);
  return alcedo::ToJStringArray(env, ctx->model_catalog->ListModels());
}

}  // extern "C"
