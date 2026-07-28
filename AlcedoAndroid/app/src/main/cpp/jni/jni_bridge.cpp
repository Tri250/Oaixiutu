// AlcedoAndroid - JNI bridge entry point.
// Owns the JniAppContext singleton (created/destroyed here), JNI_OnLoad, and the
// project lifecycle + version entry points. Other jni_*.cpp modules share the
// context via jni_context.hpp.
// SPDX-License-Identifier: GPL-3.0-only
#include <jni.h>

#include <memory>
#include <mutex>

#include "app/app_services.hpp"
#include "edit/scope/scope_analyzer.hpp"
#include "jni/jni_context.hpp"
#include "storage/image_pool/image_pool_manager.hpp"
#include "utils/app_logging.hpp"
#include "vulkan/context/vulkan_context.hpp"
#include "vulkan/pipeline/vulkan_program_registry.hpp"

namespace alcedo {

namespace {
JniAppContext* g_ctx = nullptr;
std::mutex     g_ctx_mtx;
}  // namespace

JniAppContext* JniAppContext::Get() {
  std::lock_guard<std::mutex> lk(g_ctx_mtx);
  return g_ctx;
}

void JniAppContext::Create() {
  std::lock_guard<std::mutex> lk(g_ctx_mtx);
  if (!g_ctx) g_ctx = new JniAppContext();
}

void JniAppContext::Destroy() {
  std::lock_guard<std::mutex> lk(g_ctx_mtx);
  delete g_ctx;
  g_ctx = nullptr;
}

}  // namespace alcedo

extern "C" {

// ------------------------------------------------------------------------------
// JNI lifecycle
// ------------------------------------------------------------------------------

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* /*reserved*/) {
  JNIEnv* env = nullptr;
  if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
    return JNI_ERR;
  }
  alcedo::JniAppContext::Create();
  ALOGI("AlcedoNative JNI_OnLoad: context created");
  return JNI_VERSION_1_6;
}

JNIEXPORT void JNICALL JNI_OnUnload(JavaVM* /*vm*/, void* /*reserved*/) {
  alcedo::JniAppContext::Destroy();
  ALOGI("AlcedoNative JNI_OnUnload: context destroyed");
}

// ------------------------------------------------------------------------------
// Bridge: init / shutdown / version
// ------------------------------------------------------------------------------

#define JNI_CLASS com_alcedo_studio_ndk_Bridge

JNIEXPORT jboolean JNICALL Java_com_alcedo_studio_ndk_Bridge_nativeInit(
    JNIEnv* env, jobject /*thiz*/, jstring cache_dir_js) {
  auto* ctx = alcedo::JniAppContext::Get();
  if (!ctx) {
    alcedo::JniAppContext::Create();
    ctx = alcedo::JniAppContext::Get();
  }
  std::lock_guard<std::mutex> lk(ctx->mtx);

  // Initialise the Vulkan backend (compute-first). Failure is non-fatal: the
  // pipeline falls back to the CPU path.
  if (alcedo::VulkanContext::Ensure()) {
    ALOGI("nativeInit: Vulkan backend ready");
  } else {
    ALOGW("nativeInit: Vulkan backend unavailable, CPU fallback in use");
  }

  ctx->cache_dir = alcedo::JStr(env, cache_dir_js);
  ctx->image_pool = std::make_shared<alcedo::ImagePoolManager>();
  ctx->decoder = std::make_unique<alcedo::DecoderScheduler>(2);
  ctx->pipeline_svc = std::make_unique<alcedo::PipelineAppService>();
  ctx->exporter = std::make_unique<alcedo::ExportService>();
  ctx->thumb_cache = std::make_unique<alcedo::ThumbnailDiskCacheService>(ctx->cache_dir / "thumbs");
  ctx->ai_credentials = std::make_unique<alcedo::AiCredentialStore>();
  ctx->ai_profile = std::make_unique<alcedo::AiProviderProfile>();
  ctx->model_catalog = std::make_unique<alcedo::ModelAssetCatalog>();
  ctx->analysis_encoder = std::make_unique<alcedo::ImageAnalysisEncoder>();
  ctx->scope_analyzer = alcedo::CreateDefaultScopeAnalyzer();
  ctx->initialized = true;

  ALOGI("nativeInit complete (cache_dir=%s)", ctx->cache_dir.c_str());
  return JNI_TRUE;
}

JNIEXPORT void JNICALL Java_com_alcedo_studio_ndk_Bridge_nativeShutdown(
    JNIEnv* /*env*/, jobject /*thiz*/) {
  auto* ctx = alcedo::JniAppContext::Get();
  if (!ctx) return;
  std::lock_guard<std::mutex> lk(ctx->mtx);
  ctx->project.reset();
  ctx->db.reset();
  ctx->decoder.reset();
  ctx->pipeline_svc.reset();
  ctx->exporter.reset();
  ctx->thumb_cache.reset();
  ctx->ai_credentials.reset();
  ctx->ai_profile.reset();
  ctx->model_catalog.reset();
  ctx->analysis_encoder.reset();
  ctx->scope_analyzer.reset();
  ctx->image_pool.reset();
  ctx->initialized = false;

  if (auto* vk = alcedo::VulkanContext::Get()) {
    vk->Shutdown();
  }
  ALOGI("nativeShutdown complete");
}

JNIEXPORT jstring JNICALL Java_com_alcedo_studio_ndk_Bridge_nativeGetVersion(
    JNIEnv* env, jobject /*thiz*/) {
  return env->NewStringUTF("AlcedoNative 1.0.0 (Vulkan Compute, ACES 2.0/OpenDRT)");
}

// ------------------------------------------------------------------------------
// Project lifecycle
// ------------------------------------------------------------------------------

JNIEXPORT jboolean JNICALL Java_com_alcedo_studio_ndk_Bridge_nativeOpenProject(
    JNIEnv* env, jobject /*thiz*/, jstring db_path_js) {
  auto* ctx = alcedo::JniAppContext::Get();
  if (!ctx || !ctx->initialized) {
    alcedo::ThrowJavaRuntime(env, "native core not initialized");
    return JNI_FALSE;
  }
  std::string db_path = alcedo::JStr(env, db_path_js);
  std::lock_guard<std::mutex> lk(ctx->mtx);

  ctx->db = std::make_unique<alcedo::DBController>(db_path);
  ctx->db->InitializeDB();

  ctx->project = std::make_unique<alcedo::ProjectService>();
  if (!ctx->project->Open(db_path)) {
    ctx->project.reset();
    ctx->db.reset();
    return JNI_FALSE;
  }
  return JNI_TRUE;
}

JNIEXPORT void JNICALL Java_com_alcedo_studio_ndk_Bridge_nativeCloseProject(
    JNIEnv* /*env*/, jobject /*thiz*/) {
  auto* ctx = alcedo::JniAppContext::Get();
  if (!ctx) return;
  std::lock_guard<std::mutex> lk(ctx->mtx);
  if (ctx->project) {
    ctx->project->Close();
    ctx->project.reset();
  }
  ctx->db.reset();
}

JNIEXPORT jboolean JNICALL Java_com_alcedo_studio_ndk_Bridge_nativeSaveAll(
    JNIEnv* env, jobject /*thiz*/) {
  auto* ctx = alcedo::JniAppContext::Get();
  if (!ctx || !ctx->project) return JNI_FALSE;
  std::lock_guard<std::mutex> lk(ctx->mtx);
  return ctx->project->SaveAll() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL Java_com_alcedo_studio_ndk_Bridge_nativeIsProjectOpen(
    JNIEnv* /*env*/, jobject /*thiz*/) {
  auto* ctx = alcedo::JniAppContext::Get();
  if (!ctx || !ctx->project) return JNI_FALSE;
  std::lock_guard<std::mutex> lk(ctx->mtx);
  return ctx->project->IsOpen() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL Java_com_alcedo_studio_ndk_Bridge_nativeGetProjectPath(
    JNIEnv* env, jobject /*thiz*/) {
  auto* ctx = alcedo::JniAppContext::Get();
  if (!ctx || !ctx->project) return env->NewStringUTF("");
  std::lock_guard<std::mutex> lk(ctx->mtx);
  return env->NewStringUTF(ctx->project->GetProjectPath().c_str());
}

}  // extern "C"
