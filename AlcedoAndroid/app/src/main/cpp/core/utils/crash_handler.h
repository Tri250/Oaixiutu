#pragma once

#include <csignal>
#include <cstring>
#include <android/log.h>

namespace alcedo {

class CrashHandler {
public:
    static void Install();
    static void Uninstall();

    using CrashCallback = void(*)(const char* crash_info);
    static void SetCrashCallback(CrashCallback callback);

private:
    static void SignalHandler(int sig, siginfo_t* info, void* context);
    static void LogCrashInfo(int sig, siginfo_t* info, void* context);
    static void DumpCallstack();

    static struct sigaction old_actions_[32];
    static CrashCallback callback_;
    static bool installed_;
};

} // namespace alcedo
