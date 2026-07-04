#include "app_logging.h"

#include <algorithm>
#include <ctime>

namespace alcedo {

namespace {
    std::atomic<int> g_log_level{static_cast<int>(LogLevel::INFO)};
    std::atomic<bool> g_tag_filter_enabled{false};

    std::mutex g_tag_mutex;
    std::unordered_set<std::string> g_allowed_tags;

    std::mutex g_file_mutex;
    FILE* g_log_file = nullptr;

    const char* LevelToString(LogLevel level) {
        switch (level) {
            case LogLevel::VERBOSE: return "V";
            case LogLevel::DEBUG:   return "D";
            case LogLevel::INFO:    return "I";
            case LogLevel::WARN:    return "W";
            case LogLevel::ERROR:   return "E";
            case LogLevel::FATAL:   return "F";
            default:                return "?";
        }
    }
} // namespace

void set_log_level(LogLevel level) {
    g_log_level.store(static_cast<int>(level), std::memory_order_relaxed);
}

LogLevel get_log_level() {
    return static_cast<LogLevel>(g_log_level.load(std::memory_order_relaxed));
}

// ============================================================
// Tag-based filtering
// ============================================================
void add_allowed_tag(const std::string& tag) {
    std::lock_guard<std::mutex> lock(g_tag_mutex);
    g_allowed_tags.insert(tag);
}

void remove_allowed_tag(const std::string& tag) {
    std::lock_guard<std::mutex> lock(g_tag_mutex);
    g_allowed_tags.erase(tag);
}

void clear_allowed_tags() {
    std::lock_guard<std::mutex> lock(g_tag_mutex);
    g_allowed_tags.clear();
}

bool is_tag_allowed(const std::string& tag) {
    std::lock_guard<std::mutex> lock(g_tag_mutex);
    return g_allowed_tags.find(tag) != g_allowed_tags.end();
}

void set_tag_filter_enabled(bool enabled) {
    g_tag_filter_enabled.store(enabled, std::memory_order_relaxed);
}

bool is_tag_filter_enabled() {
    return g_tag_filter_enabled.load(std::memory_order_relaxed);
}

// ============================================================
// File logging
// ============================================================
void set_log_file(const std::string& path) {
    std::lock_guard<std::mutex> lock(g_file_mutex);
    if (g_log_file) {
        std::fclose(g_log_file);
        g_log_file = nullptr;
    }
    if (!path.empty()) {
        g_log_file = std::fopen(path.c_str(), "a");
    }
}

void close_log_file() {
    std::lock_guard<std::mutex> lock(g_file_mutex);
    if (g_log_file) {
        std::fclose(g_log_file);
        g_log_file = nullptr;
    }
}

bool is_file_logging_enabled() {
    std::lock_guard<std::mutex> lock(g_file_mutex);
    return g_log_file != nullptr;
}

namespace internal {

void WriteToFile(LogLevel level, const char* tag, const char* message) {
    std::lock_guard<std::mutex> lock(g_file_mutex);
    if (!g_log_file) return;

    // Format: [YYYY-MM-DD HH:MM:SS] [LEVEL] [tag] message
    std::time_t now = std::time(nullptr);
    char time_buf[32];
    std::strftime(time_buf, sizeof(time_buf), "%Y-%m-%d %H:%M:%S", std::localtime(&now));

    std::fprintf(g_log_file, "[%s] [%s] [%s] %s\n", time_buf, LevelToString(level), tag, message);
    std::fflush(g_log_file);
}

void LogMessage(LogLevel level, const char* tag, const char* fmt, ...) {
    // Format the message first for both logcat and file output.
    char buf[1024];
    va_list args;
    va_start(args, fmt);
    vsnprintf(buf, sizeof(buf), fmt, args);
    va_end(args);

    // Output to Android logcat.
    __android_log_print(static_cast<int>(level), tag, "%s", buf);

    // Output to file if enabled.
    WriteToFile(level, tag, buf);

    // FATAL: abort after logging.
    if (level == LogLevel::FATAL) {
        __builtin_trap();
    }
}

} // namespace internal

} // namespace alcedo
