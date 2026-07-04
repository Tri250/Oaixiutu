#pragma once

#include <android/log.h>
#include <atomic>
#include <cstdarg>

namespace alcedo {

// ============================================================
// Log levels (mirror Android log priorities)
// ============================================================
enum class LogLevel {
    VERBOSE = ANDROID_LOG_VERBOSE,
    DEBUG   = ANDROID_LOG_DEBUG,
    INFO    = ANDROID_LOG_INFO,
    WARN    = ANDROID_LOG_WARN,
    ERROR   = ANDROID_LOG_ERROR,
};

// Set the global minimum log level.
void set_log_level(LogLevel level);

// Get the current global minimum log level.
LogLevel get_log_level();

namespace internal {

inline bool ShouldLog(LogLevel level) {
    return static_cast<int>(level) >= static_cast<int>(get_log_level());
}

void LogMessage(LogLevel level, const char* tag, const char* fmt, ...);

} // namespace internal

} // namespace alcedo

#define ALCEDO_LOGI(tag, ...) \
    do { if (::alcedo::internal::ShouldLog(::alcedo::LogLevel::INFO)) \
         ::alcedo::internal::LogMessage(::alcedo::LogLevel::INFO, tag, __VA_ARGS__); } while (0)

#define ALCEDO_LOGD(tag, ...) \
    do { if (::alcedo::internal::ShouldLog(::alcedo::LogLevel::DEBUG)) \
         ::alcedo::internal::LogMessage(::alcedo::LogLevel::DEBUG, tag, __VA_ARGS__); } while (0)

#define ALCEDO_LOGW(tag, ...) \
    do { if (::alcedo::internal::ShouldLog(::alcedo::LogLevel::WARN)) \
         ::alcedo::internal::LogMessage(::alcedo::LogLevel::WARN, tag, __VA_ARGS__); } while (0)

#define ALCEDO_LOGE(tag, ...) \
    do { if (::alcedo::internal::ShouldLog(::alcedo::LogLevel::ERROR)) \
         ::alcedo::internal::LogMessage(::alcedo::LogLevel::ERROR, tag, __VA_ARGS__); } while (0)
