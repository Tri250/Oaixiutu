// AlcedoAndroid - JNI history module.
// Edit-history undo/redo, version creation/switching, and version listing.
// SPDX-License-Identifier: GPL-3.0-only
#include <jni.h>

#include <memory>
#include <mutex>
#include <string>
#include <vector>

#include "app/app_services.hpp"
#include "edit/history/edit_history.hpp"
#include "jni/jni_context.hpp"
#include "utils/app_logging.hpp"

extern "C" {

// Undo the last edit transaction for a file.
JNIEXPORT jboolean JNICALL Java_com_alcedo_studio_ndk_History_nativeUndo(
    JNIEnv* /*env*/, jobject /*thiz*/, jint file_id) {
  auto* ctx = alcedo::JniAppContext::Get();
  if (!ctx || !ctx->project || !ctx->pipeline_svc) return JNI_FALSE;
  std::lock_guard<std::mutex> lk(ctx->mtx);
  alcedo::HistoryMgmtService svc(ctx->project->GetSleeveManager());
  return svc.Undo(static_cast<alcedo::sl_element_id_t>(file_id), *ctx->pipeline_svc)
             ? JNI_TRUE : JNI_FALSE;
}

// Redo the next edit transaction for a file.
JNIEXPORT jboolean JNICALL Java_com_alcedo_studio_ndk_History_nativeRedo(
    JNIEnv* /*env*/, jobject /*thiz*/, jint file_id) {
  auto* ctx = alcedo::JniAppContext::Get();
  if (!ctx || !ctx->project || !ctx->pipeline_svc) return JNI_FALSE;
  std::lock_guard<std::mutex> lk(ctx->mtx);
  alcedo::HistoryMgmtService svc(ctx->project->GetSleeveManager());
  return svc.Redo(static_cast<alcedo::sl_element_id_t>(file_id), *ctx->pipeline_svc)
             ? JNI_TRUE : JNI_FALSE;
}

// Get the version count for a file.
JNIEXPORT jint JNICALL Java_com_alcedo_studio_ndk_History_nativeGetVersionCount(
    JNIEnv* /*env*/, jobject /*thiz*/, jint file_id) {
  auto* ctx = alcedo::JniAppContext::Get();
  if (!ctx || !ctx->project) return 0;
  std::lock_guard<std::mutex> lk(ctx->mtx);
  alcedo::HistoryMgmtService svc(ctx->project->GetSleeveManager());
  return static_cast<jint>(svc.GetVersionCount(static_cast<alcedo::sl_element_id_t>(file_id)));
}

// Create a new version (alternate look) for a file. Returns 0 on failure.
JNIEXPORT jint JNICALL Java_com_alcedo_studio_ndk_History_nativeCreateVersion(
    JNIEnv* env, jobject /*thiz*/, jint file_id, jstring display_name_js) {
  auto* ctx = alcedo::JniAppContext::Get();
  if (!ctx || !ctx->project) return 0;
  std::string display_name = alcedo::JStr(env, display_name_js);
  std::lock_guard<std::mutex> lk(ctx->mtx);

  auto& sleeve = ctx->project->GetSleeveManager();
  auto file = sleeve.GetFile(static_cast<alcedo::sl_element_id_t>(file_id));
  if (!file) return 0;
  auto history = file->GetEditHistory();
  if (!history) return 0;
  auto ver_id = history->CreateVersion(display_name);
  // Encode the 128-bit version id as two 64-bit ints packed into a single
  // jint (low 32 bits) for simplicity; the high bits are available via JSON.
  return static_cast<jint>(ver_id.low64() & 0xFFFFFFFFu);
}

// Switch the active version for a file.
JNIEXPORT jboolean JNICALL Java_com_alcedo_studio_ndk_History_nativeSwitchVersion(
    JNIEnv* env, jobject /*thiz*/, jint file_id, jlong version_lo, jlong version_hi) {
  auto* ctx = alcedo::JniAppContext::Get();
  if (!ctx || !ctx->project) return JNI_FALSE;
  std::lock_guard<std::mutex> lk(ctx->mtx);
  auto& sleeve = ctx->project->GetSleeveManager();
  auto file = sleeve.GetFile(static_cast<alcedo::sl_element_id_t>(file_id));
  if (!file) return JNI_FALSE;
  auto history = file->GetEditHistory();
  if (!history) return JNI_FALSE;
  alcedo::history_id_t ver_id(static_cast<uint64_t>(version_lo),
                              static_cast<uint64_t>(version_hi));
  history->SetActiveVersionID(ver_id);
  return JNI_TRUE;
}

// Serialize the full edit history for a file to JSON.
JNIEXPORT jstring JNICALL Java_com_alcedo_studio_ndk_History_nativeGetHistoryJson(
    JNIEnv* env, jobject /*thiz*/, jint file_id) {
  auto* ctx = alcedo::JniAppContext::Get();
  if (!ctx || !ctx->project) return env->NewStringUTF("{}");
  std::lock_guard<std::mutex> lk(ctx->mtx);
  auto& sleeve = ctx->project->GetSleeveManager();
  auto file = sleeve.GetFile(static_cast<alcedo::sl_element_id_t>(file_id));
  if (!file) return env->NewStringUTF("{}");
  auto history = file->GetEditHistory();
  if (!history) return env->NewStringUTF("{}");
  return env->NewStringUTF(history->ToJSON().dump().c_str());
}

// Rename a version.
JNIEXPORT void JNICALL Java_com_alcedo_studio_ndk_History_nativeRenameVersion(
    JNIEnv* env, jobject /*thiz*/, jint file_id, jlong version_lo, jlong version_hi,
    jstring name_js) {
  auto* ctx = alcedo::JniAppContext::Get();
  if (!ctx || !ctx->project) return;
  std::string name = alcedo::JStr(env, name_js);
  std::lock_guard<std::mutex> lk(ctx->mtx);
  auto& sleeve = ctx->project->GetSleeveManager();
  auto file = sleeve.GetFile(static_cast<alcedo::sl_element_id_t>(file_id));
  if (!file) return;
  auto history = file->GetEditHistory();
  if (!history) return;
  alcedo::history_id_t ver_id(static_cast<uint64_t>(version_lo),
                              static_cast<uint64_t>(version_hi));
  history->RenameVersion(ver_id, name);
}

}  // extern "C"
