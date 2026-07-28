// AlcedoAndroid - Android log wrapper.
// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <android/log.h>

#include <cstdio>
#include <string>
#include <string_view>

namespace alcedo {
namespace log {

constexpr const char* kTag = "AlcedoNative";

enum class Level : int {
  kVerbose = ANDROID_LOG_VERBOSE,
  kDebug   = ANDROID_LOG_DEBUG,
  kInfo    = ANDROID_LOG_INFO,
  kWarn    = ANDROID_LOG_WARN,
  kError   = ANDROID_LOG_ERROR,
  kFatal   = ANDROID_LOG_FATAL,
};

inline void Write(Level level, std::string_view tag, std::string_view msg) {
  __android_log_print(static_cast<int>(level), tag.data(), "%.*s",
                      static_cast<int>(msg.size()), msg.data());
}

inline void V(std::string_view msg) { Write(Level::kVerbose, kTag, msg); }
inline void D(std::string_view msg) { Write(Level::kDebug, kTag, msg); }
inline void I(std::string_view msg) { Write(Level::kInfo, kTag, msg); }
inline void W(std::string_view msg) { Write(Level::kWarn, kTag, msg); }
inline void E(std::string_view msg) { Write(Level::kError, kTag, msg); }
inline void F(std::string_view msg) { Write(Level::kFatal, kTag, msg); }

}  // namespace log

// Convenience macros for formatted logging (PRINTF-style, evaluated lazily).
#define ALOGV(fmt, ...) __android_log_print(ANDROID_LOG_VERBOSE, ::alcedo::log::kTag, (fmt), ##__VA_ARGS__)
#define ALOGD(fmt, ...) __android_log_print(ANDROID_LOG_DEBUG,   ::alcedo::log::kTag, (fmt), ##__VA_ARGS__)
#define ALOGI(fmt, ...) __android_log_print(ANDROID_LOG_INFO,    ::alcedo::log::kTag, (fmt), ##__VA_ARGS__)
#define ALOGW(fmt, ...) __android_log_print(ANDROID_LOG_WARN,    ::alcedo::log::kTag, (fmt), ##__VA_ARGS__)
#define ALOGE(fmt, ...) __android_log_print(ANDROID_LOG_ERROR,   ::alcedo::log::kTag, (fmt), ##__VA_ARGS__)
#define ALOGF(fmt, ...) __android_log_print(ANDROID_LOG_FATAL,   ::alcedo::log::kTag, (fmt), ##__VA_ARGS__)

}  // namespace alcedo
