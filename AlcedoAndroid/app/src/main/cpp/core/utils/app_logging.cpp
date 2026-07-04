#include "app_logging.h"

namespace alcedo {

namespace {
    std::atomic<int> g_log_level{static_cast<int>(LogLevel::INFO)};
}

void set_log_level(LogLevel level) {
    g_log_level.store(static_cast<int>(level), std::memory_order_relaxed);
}

LogLevel get_log_level() {
    return static_cast<LogLevel>(g_log_level.load(std::memory_order_relaxed));
}

namespace internal {

void LogMessage(LogLevel level, const char* tag, const char* fmt, ...) {
    va_list args;
    va_start(args, fmt);
    __android_log_vprint(static_cast<int>(level), tag, fmt, args);
    va_end(args);
}

} // namespace internal

} // namespace alcedo
