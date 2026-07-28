// AlcedoAndroid - JNI app context (shared internal header for the JNI bridge).
// Holds the long-lived core service objects that back the JNI entry points.
// Each jni_*.cpp module obtains the singleton context via JniAppContext::Get().
// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <jni.h>

#include <memory>
#include <mutex>
#include <optional>
#include <string>
#include <vector>

#include "ai/ai.hpp"
#include "app/app_services.hpp"
#include "decoders/decoder_scheduler.hpp"
#include "edit/scope/scope_analyzer.hpp"
#include "storage/controller/db_controller.hpp"
#include "storage/image_pool/image_pool_manager.hpp"

namespace alcedo {

// Aggregate of all long-lived native services exposed to Java. Created on
// nativeInit and torn down on nativeShutdown. All access is guarded by a mutex
// because the UI may call into native from multiple threads (render + decode).
struct JniAppContext {
  std::mutex mtx;

  std::unique_ptr<DBController>                 db;
  std::shared_ptr<ImagePoolManager>            image_pool;
  std::unique_ptr<ProjectService>              project;
  std::unique_ptr<DecoderScheduler>            decoder;
  std::unique_ptr<PipelineAppService>          pipeline_svc;
  std::unique_ptr<ExportService>               exporter;
  std::unique_ptr<ThumbnailDiskCacheService>   thumb_cache;
  std::unique_ptr<AiCredentialStore>           ai_credentials;
  std::unique_ptr<AiProviderProfile>           ai_profile;
  std::unique_ptr<ModelAssetCatalog>           model_catalog;
  std::unique_ptr<ImageAnalysisEncoder>        analysis_encoder;
  std::shared_ptr<IScopeAnalyzer>              scope_analyzer;
  std::filesystem::path                        cache_dir;

  bool initialized = false;

  // Singleton accessor (created lazily by the bridge module on nativeInit).
  static JniAppContext* Get();
  static void Create();
  static void Destroy();
};

// ---- JNI helper utilities shared across modules ----

// RAII JNI string. Converts to UTF-8 on construction; releases on destruction.
class JniString {
 public:
  JniString(JNIEnv* env, jstring s) : env_(env), s_(s) {
    if (s_) {
      utf_ = env_->GetStringUTFChars(s, nullptr);
    }
  }
  ~JniString() {
    if (s_ && utf_) env_->ReleaseStringUTFChars(s_, utf_);
  }
  JniString(const JniString&)            = delete;
  JniString& operator=(const JniString&) = delete;
  const char* c_str() const { return utf_ ? utf_ : ""; }
  std::string str() const { return utf_ ? std::string(utf_) : std::string(); }
  bool valid() const { return utf_ != nullptr; }
 private:
  JNIEnv* env_;
  jstring s_;
  const char* utf_ = nullptr;
};

// Convenience: convert a jstring to std::string (null-safe).
inline std::string JStr(JNIEnv* env, jstring s) {
  if (!s) return {};
  JniString js(env, s);
  return js.str();
}

// Create a Java String[] from a vector of strings.
inline jobjectArray ToJStringArray(JNIEnv* env, const std::vector<std::string>& vec) {
  jclass str_class = env->FindClass("java/lang/String");
  jobjectArray arr = env->NewObjectArray(static_cast<jsize>(vec.size()), str_class, nullptr);
  for (jsize i = 0; i < static_cast<jsize>(vec.size()); ++i) {
    jstring js = env->NewStringUTF(vec[i].c_str());
    env->SetObjectArrayElement(arr, i, js);
    env->DeleteLocalRef(js);
  }
  env->DeleteLocalRef(str_class);
  return arr;
}

// Throw a RuntimeException back to Java with the given message.
inline void ThrowJavaRuntime(JNIEnv* env, const std::string& msg) {
  jclass cls = env->FindClass("java/lang/RuntimeException");
  if (cls) {
    env->ThrowNew(cls, msg.c_str());
    env->DeleteLocalRef(cls);
  }
}

}  // namespace alcedo
