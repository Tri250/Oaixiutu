#pragma once

#include <android/log.h>
#include <atomic>
#include <cstdarg>
#include <cstdio>
#include <cstring>
#include <mutex>
#include <string>
#include <unordered_set>
#include <vector>

#include "time_provider.h"

namespace alcedo {

// ============================================================
// Log levels (mirror Android log priorities + FATAL)
// ============================================================
enum class LogLevel {
    VERBOSE = ANDROID_LOG_VERBOSE,
    DEBUG   = ANDROID_LOG_DEBUG,
    INFO    = ANDROID_LOG_INFO,
    WARN    = ANDROID_LOG_WARN,
    ERROR   = ANDROID_LOG_ERROR,
    FATAL   = ANDROID_LOG_FATAL
};

// Set the global minimum log level.
void set_log_level(LogLevel level);

// Get the current global minimum log level.
LogLevel get_log_level();

// ============================================================
// Tag-based filtering
// ============================================================
// Add a tag to the allowed list. When the allowed list is non-empty,
// only messages from allowed tags are logged.
void add_allowed_tag(const std::string& tag);
void remove_allowed_tag(const std::string& tag);
void clear_allowed_tags();
bool is_tag_allowed(const std::string& tag);

// When true, only tags in the allowed list produce output.
// When false (default), all tags pass through (subject to log level).
void set_tag_filter_enabled(bool enabled);
bool is_tag_filter_enabled();

// ============================================================
// File logging
// ============================================================
// Enable logging to a file in addition to Android logcat.
// Setting an empty path disables file logging.
void set_log_file(const std::string& path);
void close_log_file();
bool is_file_logging_enabled();

// ============================================================
// Core logging
// ============================================================
namespace internal {

inline bool ShouldLog(LogLevel level) {
    return static_cast<int>(level) >= static_cast<int>(get_log_level());
}

inline bool ShouldLogTag(const char* tag) {
    if (!is_tag_filter_enabled()) return true;
    return is_tag_allowed(tag);
}

void LogMessage(LogLevel level, const char* tag, const char* fmt, ...);

// File logging helper (called internally).
void WriteToFile(LogLevel level, const char* tag, const char* message);

} // namespace internal

// ============================================================
// Log macros
// ============================================================
#define ALCEDO_LOGV(tag, ...) \
    do { if (::alcedo::internal::ShouldLog(::alcedo::LogLevel::VERBOSE) && \
             ::alcedo::internal::ShouldLogTag(tag)) \
         ::alcedo::internal::LogMessage(::alcedo::LogLevel::VERBOSE, tag, __VA_ARGS__); } while (0)

#define ALCEDO_LOGI(tag, ...) \
    do { if (::alcedo::internal::ShouldLog(::alcedo::LogLevel::INFO) && \
             ::alcedo::internal::ShouldLogTag(tag)) \
         ::alcedo::internal::LogMessage(::alcedo::LogLevel::INFO, tag, __VA_ARGS__); } while (0)

#define ALCEDO_LOGD(tag, ...) \
    do { if (::alcedo::internal::ShouldLog(::alcedo::LogLevel::DEBUG) && \
             ::alcedo::internal::ShouldLogTag(tag)) \
         ::alcedo::internal::LogMessage(::alcedo::LogLevel::DEBUG, tag, __VA_ARGS__); } while (0)

#define ALCEDO_LOGW(tag, ...) \
    do { if (::alcedo::internal::ShouldLog(::alcedo::LogLevel::WARN) && \
             ::alcedo::internal::ShouldLogTag(tag)) \
         ::alcedo::internal::LogMessage(::alcedo::LogLevel::WARN, tag, __VA_ARGS__); } while (0)

#define ALCEDO_LOGE(tag, ...) \
    do { if (::alcedo::internal::ShouldLog(::alcedo::LogLevel::ERROR) && \
             ::alcedo::internal::ShouldLogTag(tag)) \
         ::alcedo::internal::LogMessage(::alcedo::LogLevel::ERROR, tag, __VA_ARGS__); } while (0)

#define ALCEDO_LOGF(tag, ...) \
    do { if (::alcedo::internal::ShouldLog(::alcedo::LogLevel::FATAL) && \
             ::alcedo::internal::ShouldLogTag(tag)) \
         ::alcedo::internal::LogMessage(::alcedo::LogLevel::FATAL, tag, __VA_ARGS__); } while (0)

// ============================================================
// Performance / Scoped Timer
// ============================================================
class ScopedTimer {
public:
    explicit ScopedTimer(const char* tag, const char* label)
        : tag_(tag), label_(label),
          start_micros_(TimeProvider::SteadyNowMicros()) {}

    ~ScopedTimer() {
        uint64_t elapsed = TimeProvider::SteadyNowMicros() - start_micros_;
        double ms = static_cast<double>(elapsed) / 1000.0;
        ALCEDO_LOGI(tag_, "[PERF] %s: %.3f ms", label_, ms);
    }

    // Non-copyable, non-movable.
    ScopedTimer(const ScopedTimer&) = delete;
    ScopedTimer& operator=(const ScopedTimer&) = delete;

private:
    const char* tag_;
    const char* label_;
    uint64_t start_micros_;
};

// Usage: ALCEDO_SCOPED_TIMER("MyTag", "my_function")
#define ALCEDO_SCOPED_TIMER(tag, label) \
    ::alcedo::ScopedTimer _alcedo_scoped_timer_##__LINE__(tag, label)

// Usage: ALCEDO_SCOPED_TIMER_FUNC("MyTag") — uses the current function name
#define ALCEDO_SCOPED_TIMER_FUNC(tag) \
    ::alcedo::ScopedTimer _alcedo_scoped_timer_##__LINE__(tag, __func__)

} // namespace alcedo
