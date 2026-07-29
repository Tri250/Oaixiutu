// AlcedoAndroid - JNI sleeve module.
// Sleeve filesystem operations: list/create/move/delete folders and files,
// filter folder contents, and text search.
// SPDX-License-Identifier: GPL-3.0-only
#include <jni.h>

#include <memory>
#include <mutex>
#include <string>
#include <vector>

#include "app/app_services.hpp"
#include "jni/jni_context.hpp"
#include "sleeve/sleeve_element/sleeve_element.hpp"
#include "sleeve/sleeve_element/sleeve_file.hpp"
#include "utils/app_logging.hpp"

extern "C" {

// List the contents (element ids) of a folder path.
JNIEXPORT jintArray JNICALL Java_com_alcedo_studio_ndk_Sleeve_nativeListFolder(
    JNIEnv* env, jobject /*thiz*/, jstring folder_js) {
  auto* ctx = alcedo::JniAppContext::Get();
  if (!ctx || !ctx->project) return env->NewIntArray(0);
  std::string folder = alcedo::JStr(env, folder_js);
  // Copy element ids out of the SleeveManager under the lock, then release
  // before any JNI construction so a concurrent manager mutation cannot
  // use-after-free the referenced storage.
  std::vector<alcedo::sl_element_id_t> ids;
  {
    std::lock_guard<std::mutex> lk(ctx->mtx);
    auto& fs = ctx->project->GetSleeveManager().GetFileSystem();
    ids = fs.ListFolderContent(folder);
  }
  std::vector<jint> out(ids.size());
  for (size_t i = 0; i < ids.size(); ++i) out[i] = static_cast<jint>(ids[i]);
  jintArray arr = env->NewIntArray(static_cast<jsize>(out.size()));
  env->SetIntArrayRegion(arr, 0, static_cast<jsize>(out.size()), out.data());
  return arr;
}

// Create a folder under a parent path. Returns the element id, or 0 on failure.
JNIEXPORT jint JNICALL Java_com_alcedo_studio_ndk_Sleeve_nativeCreateFolder(
    JNIEnv* env, jobject /*thiz*/, jstring parent_js, jstring name_js) {
  auto* ctx = alcedo::JniAppContext::Get();
  if (!ctx || !ctx->project) return 0;
  std::string parent = alcedo::JStr(env, parent_js);
  std::string name = alcedo::JStr(env, name_js);
  std::lock_guard<std::mutex> lk(ctx->mtx);

  alcedo::SleeveAppService svc(ctx->project->GetSleeveManager());
  return svc.CreateFolder(parent, name) ? 1 : 0;
}

// Move an element from src to dest.
JNIEXPORT jboolean JNICALL Java_com_alcedo_studio_ndk_Sleeve_nativeMoveElement(
    JNIEnv* env, jobject /*thiz*/, jstring src_js, jstring dest_js) {
  auto* ctx = alcedo::JniAppContext::Get();
  if (!ctx || !ctx->project) return JNI_FALSE;
  std::string src = alcedo::JStr(env, src_js);
  std::string dest = alcedo::JStr(env, dest_js);
  std::lock_guard<std::mutex> lk(ctx->mtx);
  alcedo::SleeveAppService svc(ctx->project->GetSleeveManager());
  return svc.MoveElement(src, dest) ? JNI_TRUE : JNI_FALSE;
}

// Delete an element by path.
JNIEXPORT jboolean JNICALL Java_com_alcedo_studio_ndk_Sleeve_nativeDeleteElement(
    JNIEnv* env, jobject /*thiz*/, jstring path_js) {
  auto* ctx = alcedo::JniAppContext::Get();
  if (!ctx || !ctx->project) return JNI_FALSE;
  std::string path = alcedo::JStr(env, path_js);
  std::lock_guard<std::mutex> lk(ctx->mtx);
  alcedo::SleeveAppService svc(ctx->project->GetSleeveManager());
  return svc.DeleteElement(path) ? JNI_TRUE : JNI_FALSE;
}

// Filter a folder's contents with a SQL predicate. Returns matching element ids.
JNIEXPORT jintArray JNICALL Java_com_alcedo_studio_ndk_Sleeve_nativeFilterFolder(
    JNIEnv* env, jobject /*thiz*/, jstring folder_js, jstring sql_js) {
  auto* ctx = alcedo::JniAppContext::Get();
  if (!ctx || !ctx->project) return env->NewIntArray(0);
  std::string folder = alcedo::JStr(env, folder_js);
  std::string sql = alcedo::JStr(env, sql_js);
  // Copy only the element ids we need out of the SleeveManager under the lock;
  // do not hold shared_ptrs into its storage past the lock (the manager may be
  // mutated concurrently, invalidating them).
  std::vector<jint> ids;
  {
    std::lock_guard<std::mutex> lk(ctx->mtx);
    alcedo::SleeveFilterService svc(ctx->project->GetSleeveManager());
    auto elems = svc.FilterFolder(folder, sql);
    ids.reserve(elems.size());
    for (auto& e : elems) ids.push_back(static_cast<jint>(e->element_id_));
  }
  jintArray arr = env->NewIntArray(static_cast<jsize>(ids.size()));
  env->SetIntArrayRegion(arr, 0, static_cast<jsize>(ids.size()), ids.data());
  return arr;
}

// Full-text search across the library. Returns matching element ids.
JNIEXPORT jintArray JNICALL Java_com_alcedo_studio_ndk_Sleeve_nativeSearchByText(
    JNIEnv* env, jobject /*thiz*/, jstring query_js) {
  auto* ctx = alcedo::JniAppContext::Get();
  if (!ctx || !ctx->project) return env->NewIntArray(0);
  std::string query = alcedo::JStr(env, query_js);
  std::vector<alcedo::sl_element_id_t> ids;
  {
    std::lock_guard<std::mutex> lk(ctx->mtx);
    alcedo::SleeveFilterService svc(ctx->project->GetSleeveManager());
    ids = svc.SearchByText(query);
  }
  std::vector<jint> out(ids.size());
  for (size_t i = 0; i < ids.size(); ++i) out[i] = static_cast<jint>(ids[i]);
  jintArray arr = env->NewIntArray(static_cast<jsize>(out.size()));
  env->SetIntArrayRegion(arr, 0, static_cast<jsize>(out.size()), out.data());
  return arr;
}

// Get element metadata (name/type) as JSON.
JNIEXPORT jstring JNICALL Java_com_alcedo_studio_ndk_Sleeve_nativeGetElementInfo(
    JNIEnv* env, jobject /*thiz*/, jint element_id) {
  auto* ctx = alcedo::JniAppContext::Get();
  if (!ctx || !ctx->project) return env->NewStringUTF("{}");
  // Copy the element's scalar fields under the lock; build the JSON and the
  // Java string afterwards so no reference into the SleeveManager is held past
  // the lock (the manager may be mutated concurrently).
  bool found = false;
  alcedo::sl_element_id_t id = 0;
  std::string name;
  bool is_folder = false;
  bool pinned = false;
  {
    std::lock_guard<std::mutex> lk(ctx->mtx);
    auto& fs = ctx->project->GetSleeveManager().GetFileSystem();
    auto elem = fs.Get(static_cast<alcedo::sl_element_id_t>(element_id));
    if (elem) {
      found     = true;
      id        = elem->element_id_;
      name      = elem->element_name_;
      is_folder = (elem->type_ == alcedo::ElementType::FOLDER);
      pinned    = elem->pinned_;
    }
  }
  if (!found) return env->NewStringUTF("{}");
  nlohmann::json j;
  j["id"] = id;
  j["name"] = name;
  j["type"] = is_folder ? "folder" : "file";
  j["pinned"] = pinned;
  return env->NewStringUTF(j.dump().c_str());
}

}  // extern "C"
