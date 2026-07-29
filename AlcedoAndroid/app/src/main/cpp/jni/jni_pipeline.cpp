// AlcedoAndroid - JNI pipeline module.
// Pipeline execution and parameter import/export entry points.
// SPDX-License-Identifier: GPL-3.0-only
#include <jni.h>

#include <memory>
#include <mutex>
#include <string>

#include "edit/operators/op_base.hpp"
#include "edit/pipeline/pipeline.hpp"
#include "image/image.hpp"
#include "jni/jni_context.hpp"
#include "utils/app_logging.hpp"
#include "vulkan/context/vulkan_context.hpp"

extern "C" {

// Execute the edit pipeline over an image with the given JSON parameters.
// Returns a new image id holding the processed result, or -1 on failure.
JNIEXPORT jint JNICALL Java_com_alcedo_studio_ndk_Pipeline_nativeExecute(
    JNIEnv* env, jobject /*thiz*/, jint image_id, jstring param_json_js) {
  auto* ctx = alcedo::JniAppContext::Get();
  if (!ctx || !ctx->pipeline_svc || !ctx->image_pool) return -1;
  std::string param_json = alcedo::JStr(env, param_json_js);
  std::lock_guard<std::mutex> lk(ctx->mtx);

  auto image = ctx->image_pool->GetImage(static_cast<alcedo::image_id_t>(image_id));
  if (!image) return -1;

  auto result = ctx->pipeline_svc->Execute(image, param_json);
  if (!result) return -1;
  result->SetId(ctx->image_pool->GetCurrentID());
  ctx->image_pool->Insert(result);
  return static_cast<jint>(result->image_id_);
}

// Export the current pipeline parameters as a JSON string.
JNIEXPORT jstring JNICALL Java_com_alcedo_studio_ndk_Pipeline_nativeExportParams(
    JNIEnv* env, jobject /*thiz*/, jint /*image_id*/) {
  auto* ctx = alcedo::JniAppContext::Get();
  if (!ctx || !ctx->pipeline_svc) return env->NewStringUTF("{}");
  std::lock_guard<std::mutex> lk(ctx->mtx);
  // Use the pipeline service's long-lived executor so exported params reflect
  // previously imported/set state instead of a fresh throwaway object.
  auto executor = ctx->pipeline_svc->GetExecutor();
  if (!executor) return env->NewStringUTF("{}");
  return env->NewStringUTF(executor->ExportPipelineParams().dump().c_str());
}

// Import pipeline parameters from a JSON string.
JNIEXPORT void JNICALL Java_com_alcedo_studio_ndk_Pipeline_nativeImportParams(
    JNIEnv* env, jobject /*thiz*/, jint /*image_id*/, jstring param_json_js) {
  auto* ctx = alcedo::JniAppContext::Get();
  if (!ctx || !ctx->pipeline_svc) return;
  std::string param_json = alcedo::JStr(env, param_json_js);
  std::lock_guard<std::mutex> lk(ctx->mtx);
  // Import into the service's persistent executor so params survive the call.
  auto executor = ctx->pipeline_svc->GetExecutor();
  if (!executor) return;
  try {
    auto j = nlohmann::json::parse(param_json);
    executor->ImportPipelineParams(j);
  } catch (const std::exception& e) {
    ALOGW("nativeImportParams: %s", e.what());
  }
}

// Set the render region (for interactive editing of a sub-region).
JNIEXPORT void JNICALL Java_com_alcedo_studio_ndk_Pipeline_nativeSetRenderRegion(
    JNIEnv* /*env*/, jobject /*thiz*/, jint image_id, jint x, jint y,
    jfloat scale_x, jfloat scale_y, jint ref_w, jint ref_h) {
  auto* ctx = alcedo::JniAppContext::Get();
  if (!ctx || !ctx->pipeline_svc) return;
  std::lock_guard<std::mutex> lk(ctx->mtx);
  auto executor = ctx->pipeline_svc->GetExecutor();
  if (!executor) return;
  executor->SetBoundFile(static_cast<alcedo::sl_element_id_t>(image_id));
  executor->SetRenderRegion(x, y, scale_x, scale_y, ref_w, ref_h);
}

// Set render resolution mode (full vs. capped).
JNIEXPORT void JNICALL Java_com_alcedo_studio_ndk_Pipeline_nativeSetRenderRes(
    JNIEnv* /*env*/, jobject /*thiz*/, jboolean full_res, jint max_side) {
  auto* ctx = alcedo::JniAppContext::Get();
  if (!ctx || !ctx->pipeline_svc) return;
  std::lock_guard<std::mutex> lk(ctx->mtx);
  auto executor = ctx->pipeline_svc->GetExecutor();
  if (!executor) return;
  executor->SetRenderRes(full_res == JNI_TRUE, max_side);
}

// Query whether the Vulkan compute backend is available.
JNIEXPORT jboolean JNICALL Java_com_alcedo_studio_ndk_Pipeline_nativeIsVulkanAvailable(
    JNIEnv* /*env*/, jobject /*thiz*/) {
  auto* vk = alcedo::VulkanContext::Get();
  return (vk && vk->Valid()) ? JNI_TRUE : JNI_FALSE;
}

}  // extern "C"
