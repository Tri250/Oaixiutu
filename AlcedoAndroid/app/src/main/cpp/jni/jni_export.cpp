// AlcedoAndroid - JNI export module.
// Image export to JPEG/PNG/TIFF and UltraHDR (gain-mapped JPEG for Android 14+).
// SPDX-License-Identifier: GPL-3.0-only
#include <jni.h>

#include <memory>
#include <mutex>
#include <string>

#include "app/app_services.hpp"
#include "image/image.hpp"
#include "io/io.hpp"
#include "jni/jni_context.hpp"
#include "utils/app_logging.hpp"

extern "C" {

// Export an image buffer (by image id) to a file in the given format.
// format: "jpeg"/"png"/"tiff". quality is 1-100 (ignored for PNG).
JNIEXPORT jboolean JNICALL Java_com_alcedo_studio_ndk_Export_nativeExportImage(
    JNIEnv* env, jobject /*thiz*/, jint image_id, jstring out_path_js,
    jstring format_js, jint quality) {
  auto* ctx = alcedo::JniAppContext::Get();
  if (!ctx || !ctx->exporter || !ctx->image_pool) return JNI_FALSE;
  std::string out_path = alcedo::JStr(env, out_path_js);
  std::string format = alcedo::JStr(env, format_js);
  std::lock_guard<std::mutex> lk(ctx->mtx);

  auto image = ctx->image_pool->GetImage(static_cast<alcedo::image_id_t>(image_id));
  if (!image) return JNI_FALSE;
  return ctx->exporter->Export(image, out_path, format, quality) ? JNI_TRUE : JNI_FALSE;
}

// Export to JPEG.
JNIEXPORT jboolean JNICALL Java_com_alcedo_studio_ndk_Export_nativeExportJpeg(
    JNIEnv* env, jobject /*thiz*/, jint image_id, jstring out_path_js, jint quality) {
  auto* ctx = alcedo::JniAppContext::Get();
  if (!ctx || !ctx->image_pool) return JNI_FALSE;
  std::string out_path = alcedo::JStr(env, out_path_js);
  std::lock_guard<std::mutex> lk(ctx->mtx);

  auto image = ctx->image_pool->GetImage(static_cast<alcedo::image_id_t>(image_id));
  if (!image) return JNI_FALSE;
  alcedo::ImageWriter writer;
  return writer.WriteJPEG(image->image_data_, out_path, quality) ? JNI_TRUE : JNI_FALSE;
}

// Export to PNG.
JNIEXPORT jboolean JNICALL Java_com_alcedo_studio_ndk_Export_nativeExportPng(
    JNIEnv* env, jobject /*thiz*/, jint image_id, jstring out_path_js) {
  auto* ctx = alcedo::JniAppContext::Get();
  if (!ctx || !ctx->image_pool) return JNI_FALSE;
  std::string out_path = alcedo::JStr(env, out_path_js);
  std::lock_guard<std::mutex> lk(ctx->mtx);

  auto image = ctx->image_pool->GetImage(static_cast<alcedo::image_id_t>(image_id));
  if (!image) return JNI_FALSE;
  alcedo::ImageWriter writer;
  return writer.WritePNG(image->image_data_, out_path) ? JNI_TRUE : JNI_FALSE;
}

// Export to TIFF.
JNIEXPORT jboolean JNICALL Java_com_alcedo_studio_ndk_Export_nativeExportTiff(
    JNIEnv* env, jobject /*thiz*/, jint image_id, jstring out_path_js) {
  auto* ctx = alcedo::JniAppContext::Get();
  if (!ctx || !ctx->image_pool) return JNI_FALSE;
  std::string out_path = alcedo::JStr(env, out_path_js);
  std::lock_guard<std::mutex> lk(ctx->mtx);

  auto image = ctx->image_pool->GetImage(static_cast<alcedo::image_id_t>(image_id));
  if (!image) return JNI_FALSE;
  alcedo::ImageWriter writer;
  return writer.WriteTIFF(image->image_data_, out_path) ? JNI_TRUE : JNI_FALSE;
}

// Export an UltraHDR (gain-mapped) JPEG from SDR + HDR buffers.
JNIEXPORT jboolean JNICALL Java_com_alcedo_studio_ndk_Export_nativeExportUltraHdr(
    JNIEnv* env, jobject /*thiz*/, jint sdr_id, jint hdr_id, jstring out_path_js, jint quality) {
  auto* ctx = alcedo::JniAppContext::Get();
  if (!ctx || !ctx->image_pool) return JNI_FALSE;
  std::string out_path = alcedo::JStr(env, out_path_js);
  std::lock_guard<std::mutex> lk(ctx->mtx);

  auto sdr = ctx->image_pool->GetImage(static_cast<alcedo::image_id_t>(sdr_id));
  auto hdr = ctx->image_pool->GetImage(static_cast<alcedo::image_id_t>(hdr_id));
  if (!sdr || !hdr) return JNI_FALSE;
  alcedo::UltraHDRWriter writer;
  return writer.Write(sdr->image_data_, hdr->image_data_, out_path, quality) ? JNI_TRUE : JNI_FALSE;
}

// Package the whole project to an archive path.
JNIEXPORT jboolean JNICALL Java_com_alcedo_studio_ndk_Export_nativePackageProject(
    JNIEnv* env, jobject /*thiz*/, jstring out_archive_js) {
  auto* ctx = alcedo::JniAppContext::Get();
  if (!ctx || !ctx->project) return JNI_FALSE;
  std::string out_archive = alcedo::JStr(env, out_archive_js);
  std::lock_guard<std::mutex> lk(ctx->mtx);
  alcedo::ProjectPackageService svc(ctx->project->GetSleeveManager());
  return svc.Package(out_archive) ? JNI_TRUE : JNI_FALSE;
}

}  // extern "C"
