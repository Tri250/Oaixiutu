// AlcedoAndroid - JNI scope module.
// Scope analysis (histogram / waveform / vectorscope) submission and retrieval.
// The UI submits a frame by image id; native builds a FinalDisplayFrameView and
// hands it to the IScopeAnalyzer, then returns the latest output as JSON.
// SPDX-License-Identifier: GPL-3.0-only
#include <jni.h>

#include <memory>
#include <mutex>
#include <string>

#include "edit/scope/scope_analyzer.hpp"
#include "image/image.hpp"
#include "jni/jni_context.hpp"
#include "utils/app_logging.hpp"

extern "C" {

// Submit a frame (by image id) for scope analysis with the given scope mask.
// scope_mask is a bitmask of ScopeType values.
JNIEXPORT void JNICALL Java_com_alcedo_studio_ndk_Scope_nativeSubmitFrame(
    JNIEnv* /*env*/, jobject /*thiz*/, jint image_id, jint scope_mask,
    jint histogram_bins, jint waveform_w, jint waveform_h) {
  auto* ctx = alcedo::JniAppContext::Get();
  if (!ctx || !ctx->scope_analyzer || !ctx->image_pool) return;
  std::lock_guard<std::mutex> lk(ctx->mtx);
  auto image = ctx->image_pool->GetImage(static_cast<alcedo::image_id_t>(image_id));
  if (!image) return;

  alcedo::FinalDisplayFrameView frame;
  frame.image = std::make_shared<alcedo::ImageBuffer>(image->image_data_.Clone());
  frame.width = image->image_data_.Width();
  frame.height = image->image_data_.Height();
  frame.channels = image->image_data_.Channels();
  frame.domain = alcedo::AnalysisDomain::DisplayEncoded;
  frame.backend = alcedo::GpuBackendKind::None;
  frame.frame_id = static_cast<uint64_t>(image_id);

  alcedo::ScopeRequest req;
  req.enabled_mask = static_cast<uint32_t>(scope_mask);
  if (histogram_bins > 0) req.histogram_bins = histogram_bins;
  if (waveform_w > 0) req.waveform_width = waveform_w;
  if (waveform_h > 0) req.waveform_height = waveform_h;

  ctx->scope_analyzer->SubmitFrame(frame, req);
}

// Get the latest scope output as a JSON blob (histogram + waveform metadata).
JNIEXPORT jstring JNICALL Java_com_alcedo_studio_ndk_Scope_nativeGetScopeJson(
    JNIEnv* env, jobject /*thiz*/) {
  auto* ctx = alcedo::JniAppContext::Get();
  if (!ctx || !ctx->scope_analyzer) return env->NewStringUTF("{}");
  std::lock_guard<std::mutex> lk(ctx->mtx);
  auto out = ctx->scope_analyzer->GetLatestOutput();
  auto snap = alcedo::ReadScopeRenderSnapshot(out);

  nlohmann::json j;
  j["generation"] = out.generation;
  j["histogram_valid"] = snap.histogram.valid;
  if (snap.histogram.valid) {
    j["histogram_bins"] = snap.histogram.bins;
    j["shadow_clip"] = snap.histogram.shadow_clip_warning;
    j["highlight_clip"] = snap.histogram.highlight_clip_warning;
    j["histogram_rgb"] = snap.histogram.rgb;
  }
  j["waveform_valid"] = snap.waveform.valid;
  if (snap.waveform.valid) {
    j["waveform_width"] = snap.waveform.width;
    j["waveform_height"] = snap.waveform.height;
  }
  return env->NewStringUTF(j.dump().c_str());
}

// Get the raw histogram counts as a flattened int array (R,G,B interleaved).
JNIEXPORT jintArray JNICALL Java_com_alcedo_studio_ndk_Scope_nativeGetHistogram(
    JNIEnv* env, jobject /*thiz*/) {
  auto* ctx = alcedo::JniAppContext::Get();
  if (!ctx || !ctx->scope_analyzer) return env->NewIntArray(0);
  std::lock_guard<std::mutex> lk(ctx->mtx);
  auto out = ctx->scope_analyzer->GetLatestOutput();
  if (!out.histogram_valid || out.histogram_counts.empty()) return env->NewIntArray(0);
  std::vector<jint> counts(out.histogram_counts.size());
  for (size_t i = 0; i < out.histogram_counts.size(); ++i) {
    counts[i] = static_cast<jint>(out.histogram_counts[i]);
  }
  jintArray arr = env->NewIntArray(static_cast<jsize>(counts.size()));
  env->SetIntArrayRegion(arr, 0, static_cast<jsize>(counts.size()), counts.data());
  return arr;
}

// Get the waveform as a flattened float array (RGBA).
JNIEXPORT jfloatArray JNICALL Java_com_alcedo_studio_ndk_Scope_nativeGetWaveform(
    JNIEnv* env, jobject /*thiz*/) {
  auto* ctx = alcedo::JniAppContext::Get();
  if (!ctx || !ctx->scope_analyzer) return env->NewFloatArray(0);
  std::lock_guard<std::mutex> lk(ctx->mtx);
  auto out = ctx->scope_analyzer->GetLatestOutput();
  if (!out.waveform_valid || out.waveform_rgba.empty()) return env->NewFloatArray(0);
  jfloatArray arr = env->NewFloatArray(static_cast<jsize>(out.waveform_rgba.size()));
  env->SetFloatArrayRegion(arr, 0, static_cast<jsize>(out.waveform_rgba.size()),
                           out.waveform_rgba.data());
  return arr;
}

// Get the vectorscope as a flattened float array (RGBA).
JNIEXPORT jfloatArray JNICALL Java_com_alcedo_studio_ndk_Scope_nativeGetVectorscope(
    JNIEnv* env, jobject /*thiz*/) {
  auto* ctx = alcedo::JniAppContext::Get();
  if (!ctx || !ctx->scope_analyzer) return env->NewFloatArray(0);
  std::lock_guard<std::mutex> lk(ctx->mtx);
  auto out = ctx->scope_analyzer->GetLatestOutput();
  if (!out.vectorscope_valid || out.vectorscope_rgba.empty()) return env->NewFloatArray(0);
  jfloatArray arr = env->NewFloatArray(static_cast<jsize>(out.vectorscope_rgba.size()));
  env->SetFloatArrayRegion(arr, 0, static_cast<jsize>(out.vectorscope_rgba.size()),
                           out.vectorscope_rgba.data());
  return arr;
}

// Release scope analyzer GPU/host resources.
JNIEXPORT void JNICALL Java_com_alcedo_studio_ndk_Scope_nativeReleaseResources(
    JNIEnv* /*env*/, jobject /*thiz*/) {
  auto* ctx = alcedo::JniAppContext::Get();
  if (!ctx || !ctx->scope_analyzer) return;
  std::lock_guard<std::mutex> lk(ctx->mtx);
  ctx->scope_analyzer->ReleaseResources();
}

}  // extern "C"
