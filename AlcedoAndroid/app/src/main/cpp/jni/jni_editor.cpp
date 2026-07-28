// AlcedoAndroid - JNI editor module.
// Editing operator application, parameter set/get, operator listing.
// SPDX-License-Identifier: GPL-3.0-only
#include <jni.h>

#include <memory>
#include <mutex>
#include <string>
#include <vector>

#include "edit/operators/op_base.hpp"
#include "edit/operators/operator_factory.hpp"
#include "edit/pipeline/pipeline.hpp"
#include "image/image.hpp"
#include "jni/jni_context.hpp"
#include "utils/app_logging.hpp"

extern "C" {

// List all registered operator script names.
JNIEXPORT jobjectArray JNICALL Java_com_alcedo_studio_ndk_Editor_nativeListOperators(
    JNIEnv* env, jobject /*thiz*/) {
  auto* ctx = alcedo::JniAppContext::Get();
  if (!ctx) return alcedo::ToJStringArray(env, {});
  std::lock_guard<std::mutex> lk(ctx->mtx);
  std::vector<std::string> names;
  for (auto type : alcedo::OperatorFactory::Instance().RegisteredTypes()) {
    names.push_back(alcedo::OperatorFactory::Instance().ScriptNameFromType(type));
  }
  return alcedo::ToJStringArray(env, names);
}

// Apply a single operator (by script name) to an image with JSON params.
// Returns the processed image id, or -1 on failure.
JNIEXPORT jint JNICALL Java_com_alcedo_studio_ndk_Editor_nativeApplyOperator(
    JNIEnv* env, jobject /*thiz*/, jint image_id, jstring op_name_js, jstring param_json_js) {
  auto* ctx = alcedo::JniAppContext::Get();
  if (!ctx || !ctx->image_pool) return -1;
  std::string op_name = alcedo::JStr(env, op_name_js);
  std::string param_json = alcedo::JStr(env, param_json_js);
  std::lock_guard<std::mutex> lk(ctx->mtx);

  auto op = alcedo::OperatorFactory::Instance().CreateByScriptName(op_name);
  if (!op) {
    ALOGW("nativeApplyOperator: unknown operator '%s'", op_name.c_str());
    return -1;
  }
  auto image = ctx->image_pool->GetImage(static_cast<alcedo::image_id_t>(image_id));
  if (!image) return -1;

  try {
    auto j = nlohmann::json::parse(param_json.empty() ? "{}" : param_json);
    op->SetParams(j);
  } catch (const std::exception& e) {
    ALOGW("nativeApplyOperator: bad params: %s", e.what());
  }

  auto buf = std::make_shared<alcedo::ImageBuffer>(image->image_data_.Clone());
  op->Apply(buf);
  auto result = std::make_shared<alcedo::Image>(image->image_id_, image->image_path_,
                                                image->image_name_, image->image_type_);
  result->image_data_ = std::move(*buf);
  result->SetId(ctx->image_pool->GetCurrentID());
  ctx->image_pool->Insert(result);
  return static_cast<jint>(result->image_id_);
}

// Set an operator's parameters on the bound pipeline (by script name).
JNIEXPORT void JNICALL Java_com_alcedo_studio_ndk_Editor_nativeSetOperatorParam(
    JNIEnv* env, jobject /*thiz*/, jint /*image_id*/, jstring op_name_js, jstring param_json_js) {
  auto* ctx = alcedo::JniAppContext::Get();
  if (!ctx) return;
  std::string op_name = alcedo::JStr(env, op_name_js);
  std::string param_json = alcedo::JStr(env, param_json_js);
  std::lock_guard<std::mutex> lk(ctx->mtx);

  auto executor = alcedo::CreatePipelineExecutor();
  if (!executor) return;
  auto op = alcedo::OperatorFactory::Instance().CreateByScriptName(op_name);
  if (!op) return;
  try {
    op->SetParams(nlohmann::json::parse(param_json.empty() ? "{}" : param_json));
  } catch (const std::exception& e) {
    ALOGW("nativeSetOperatorParam: %s", e.what());
  }
  op->SetGlobalParams(executor->GetGlobalParams());
}

// Get an operator's parameters as JSON.
JNIEXPORT jstring JNICALL Java_com_alcedo_studio_ndk_Editor_nativeGetOperatorParam(
    JNIEnv* env, jobject /*thiz*/, jstring op_name_js) {
  std::string op_name = alcedo::JStr(env, op_name_js);
  auto op = alcedo::OperatorFactory::Instance().CreateByScriptName(op_name);
  if (!op) return env->NewStringUTF("{}");
  return env->NewStringUTF(op->GetParams().dump().c_str());
}

// Reset (disable) an operator by script name on the bound pipeline.
JNIEXPORT void JNICALL Java_com_alcedo_studio_ndk_Editor_nativeResetOperator(
    JNIEnv* env, jobject /*thiz*/, jint /*image_id*/, jstring op_name_js) {
  auto* ctx = alcedo::JniAppContext::Get();
  if (!ctx) return;
  std::string op_name = alcedo::JStr(env, op_name_js);
  std::lock_guard<std::mutex> lk(ctx->mtx);
  auto executor = alcedo::CreatePipelineExecutor();
  if (!executor) return;
  auto op = alcedo::OperatorFactory::Instance().CreateByScriptName(op_name);
  if (!op) return;
  op->EnableGlobalParams(executor->GetGlobalParams(), false);
}

// Copy adjustment settings from one image to another (or many).
JNIEXPORT jboolean JNICALL Java_com_alcedo_studio_ndk_Editor_nativeCopyAdjustments(
    JNIEnv* /*env*/, jobject /*thiz*/, jint src_file_id, jint dest_file_id) {
  auto* ctx = alcedo::JniAppContext::Get();
  if (!ctx || !ctx->project) return JNI_FALSE;
  std::lock_guard<std::mutex> lk(ctx->mtx);
  alcedo::AdjustmentTransferService svc(ctx->project->GetSleeveManager());
  return svc.CopyAdjustments(static_cast<alcedo::sl_element_id_t>(src_file_id),
                             static_cast<alcedo::sl_element_id_t>(dest_file_id))
             ? JNI_TRUE
             : JNI_FALSE;
}

}  // extern "C"
