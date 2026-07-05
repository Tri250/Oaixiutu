#include "crash_handler.h"

#include <execinfo.h>
#include <unwind.h>
#include <unistd.h>
#include <cstdlib>
#include <mutex>

#define ALOGE(tag, fmt, ...) __android_log_print(ANDROID_LOG_ERROR, tag, fmt, ##__VA_ARGS__)
#define ALOGI(tag, fmt, ...) __android_log_print(ANDROID_LOG_INFO, tag, fmt, ##__VA_ARGS__)

namespace alcedo {

struct sigaction CrashHandler::old_actions_[32];
CrashHandler::CrashCallback CrashHandler::callback_ = nullptr;
bool CrashHandler::installed_ = false;

static std::once_flag install_once_;

static const int kHandledSignals[] = {
    SIGSEGV, SIGABRT, SIGBUS, SIGFPE, SIGILL, SIGTRAP
};
static const int kHandledSignalCount = sizeof(kHandledSignals) / sizeof(kHandledSignals[0]);

static const char* SignalName(int sig) {
    switch (sig) {
        case SIGSEGV: return "SIGSEGV";
        case SIGABRT: return "SIGABRT";
        case SIGBUS:  return "SIGBUS";
        case SIGFPE:  return "SIGFPE";
        case SIGILL:  return "SIGILL";
        case SIGTRAP: return "SIGTRAP";
        default:      return "UNKNOWN";
    }
}

void CrashHandler::Install() {
    std::call_once(install_once_, []() {
        struct sigaction sa;
        memset(&sa, 0, sizeof(sa));
        sa.sa_sigaction = SignalHandler;
        sa.sa_flags = SA_SIGINFO | SA_RESTART;

        for (int i = 0; i < kHandledSignalCount; ++i) {
            int sig = kHandledSignals[i];
            if (sigaction(sig, &sa, &old_actions_[sig]) != 0) {
                ALOGE("AlcedoCrash", "Failed to install handler for %s", SignalName(sig));
            } else {
                ALOGI("AlcedoCrash", "Installed handler for %s", SignalName(sig));
            }
        }

        installed_ = true;
        ALOGI("AlcedoCrash", "Crash handler installed successfully");
    });
}

void CrashHandler::Uninstall() {
    if (!installed_) return;

    for (int i = 0; i < kHandledSignalCount; ++i) {
        int sig = kHandledSignals[i];
        sigaction(sig, &old_actions_[sig], nullptr);
    }

    installed_ = false;
    ALOGI("AlcedoCrash", "Crash handler uninstalled");
}

void CrashHandler::SetCrashCallback(CrashCallback callback) {
    callback_ = callback;
}

void CrashHandler::SignalHandler(int sig, siginfo_t* info, void* context) {
    ALOGE("AlcedoCrash", "========== NATIVE CRASH DETECTED ==========");

    LogCrashInfo(sig, info, context);
    DumpCallstack();

    // Build crash info string for callback
    if (callback_) {
        char crash_info[512];
        snprintf(crash_info, sizeof(crash_info),
                 "Signal: %s (%d), si_addr: %p, si_code: %d",
                 SignalName(sig), sig, info->si_addr, info->si_code);
        callback_(crash_info);
    }

    ALOGE("AlcedoCrash", "========== END CRASH REPORT ==========");

    // Re-raise with the old/default handler to get proper tombstone/core dump
    // Restore old action first to avoid recursion
    if (old_actions_[sig].sa_flags & SA_SIGINFO) {
        // Old handler was a siginfo handler
        old_actions_[sig].sa_sigaction(sig, info, context);
    } else if (old_actions_[sig].sa_handler == SIG_DFL) {
        // Default handler - restore and re-raise
        struct sigaction default_sa;
        memset(&default_sa, 0, sizeof(default_sa));
        default_sa.sa_handler = SIG_DFL;
        sigaction(sig, &default_sa, nullptr);
        raise(sig);
    } else if (old_actions_[sig].sa_handler == SIG_IGN) {
        // Old handler was ignoring - do nothing
    } else {
        // Old handler was a regular handler
        old_actions_[sig].sa_handler(sig);
    }
}

void CrashHandler::LogCrashInfo(int sig, siginfo_t* info, void* context) {
    ALOGE("AlcedoCrash", "Signal: %s (%d)", SignalName(sig), sig);
    ALOGE("AlcedoCrash", "Fault address: %p", info->si_addr);
    ALOGE("AlcedoCrash", "Signal code: %d", info->si_code);

    // Decode si_code for common signals
    if (sig == SIGSEGV) {
        switch (info->si_code) {
            case SEGV_MAPERR:  ALOGE("AlcedoCrash", "Reason: Address not mapped to object"); break;
            case SEGV_ACCERR:  ALOGE("AlcedoCrash", "Reason: Invalid permissions for mapped object"); break;
            default:           ALOGE("AlcedoCrash", "Reason: Unknown (si_code=%d)", info->si_code); break;
        }
    } else if (sig == SIGBUS) {
        switch (info->si_code) {
            case BUS_ADRALN:   ALOGE("AlcedoCrash", "Reason: Invalid address alignment"); break;
            case BUS_ADRERR:   ALOGE("AlcedoCrash", "Reason: Nonexistent physical address"); break;
            case BUS_OBJERR:   ALOGE("AlcedoCrash", "Reason: Object-specific hardware error"); break;
            default:           ALOGE("AlcedoCrash", "Reason: Unknown (si_code=%d)", info->si_code); break;
        }
    } else if (sig == SIGFPE) {
        switch (info->si_code) {
            case FPE_INTDIV:   ALOGE("AlcedoCrash", "Reason: Integer divide by zero"); break;
            case FPE_INTOVF:   ALOGE("AlcedoCrash", "Reason: Integer overflow"); break;
            case FPE_FLTDIV:   ALOGE("AlcedoCrash", "Reason: Floating point divide by zero"); break;
            case FPE_FLTOVF:   ALOGE("AlcedoCrash", "Reason: Floating point overflow"); break;
            case FPE_FLTUND:   ALOGE("AlcedoCrash", "Reason: Floating point underflow"); break;
            case FPE_FLTRES:   ALOGE("AlcedoCrash", "Reason: Floating point inexact result"); break;
            case FPE_FLTINV:   ALOGE("AlcedoCrash", "Reason: Floating point invalid operation"); break;
            default:           ALOGE("AlcedoCrash", "Reason: Unknown (si_code=%d)", info->si_code); break;
        }
    } else if (sig == SIGILL) {
        switch (info->si_code) {
            case ILL_ILLOPC:   ALOGE("AlcedoCrash", "Reason: Illegal opcode"); break;
            case ILL_ILLOPN:   ALOGE("AlcedoCrash", "Reason: Illegal operand"); break;
            case ILL_ILLADR:   ALOGE("AlcedoCrash", "Reason: Illegal addressing mode"); break;
            case ILL_ILLTRP:   ALOGE("AlcedoCrash", "Reason: Illegal trap"); break;
            case ILL_PRVOPC:   ALOGE("AlcedoCrash", "Reason: Privileged opcode"); break;
            case ILL_PRVREG:   ALOGE("AlcedoCrash", "Reason: Privileged register"); break;
            case ILL_COPROC:   ALOGE("AlcedoCrash", "Reason: Coprocessor error"); break;
            default:           ALOGE("AlcedoCrash", "Reason: Unknown (si_code=%d)", info->si_code); break;
        }
    }

    // Log register state from ucontext if available
#if defined(__arm__)
    if (context) {
        ucontext_t* uc = static_cast<ucontext_t*>(context);
        ALOGE("AlcedoCrash", "PC: 0x%08x", uc->uc_mcontext.arm_pc);
        ALOGE("AlcedoCrash", "LR: 0x%08x", uc->uc_mcontext.arm_lr);
        ALOGE("AlcedoCrash", "SP: 0x%08x", uc->uc_mcontext.arm_sp);
        ALOGE("AlcedoCrash", "Faulting PC: 0x%08x", uc->uc_mcontext.fault_address);
    }
#elif defined(__aarch64__)
    if (context) {
        ucontext_t* uc = static_cast<ucontext_t*>(context);
        ALOGE("AlcedoCrash", "PC: 0x%016llx", (unsigned long long)uc->uc_mcontext.pc);
        ALOGE("AlcedoCrash", "LR: 0x%016llx", (unsigned long long)uc->uc_mcontext.regs[30]);
        ALOGE("AlcedoCrash", "SP: 0x%016llx", (unsigned long long)uc->uc_mcontext.sp);
        ALOGE("AlcedoCrash", "Fault address: 0x%016llx", (unsigned long long)uc->uc_mcontext.fault_address);
    }
#elif defined(__i386__)
    if (context) {
        ucontext_t* uc = static_cast<ucontext_t*>(context);
        ALOGE("AlcedoCrash", "EIP: 0x%08x", uc->uc_mcontext.gregs[REG_EIP]);
        ALOGE("AlcedoCrash", "ESP: 0x%08x", uc->uc_mcontext.gregs[REG_ESP]);
    }
#elif defined(__x86_64__)
    if (context) {
        ucontext_t* uc = static_cast<ucontext_t*>(context);
        ALOGE("AlcedoCrash", "RIP: 0x%016llx", (unsigned long long)uc->uc_mcontext.gregs[REG_RIP]);
        ALOGE("AlcedoCrash", "RSP: 0x%016llx", (unsigned long long)uc->uc_mcontext.gregs[REG_RSP]);
    }
#endif
}

struct BacktraceState {
    void** current;
    void** end;
};

static _Unwind_Reason_Code unwind_callback(struct _Unwind_Context* context, void* arg) {
    BacktraceState* state = static_cast<BacktraceState*>(arg);
    uintptr_t pc = _Unwind_GetIP(context);
    if (pc) {
        if (state->current == state->end) {
            return _URC_END_OF_STACK;
        } else {
            *state->current++ = reinterpret_cast<void*>(pc);
        }
    }
    return _URC_NO_REASON;
}

void CrashHandler::DumpCallstack() {
    ALOGE("AlcedoCrash", "--- Callstack ---");

    void* buffer[64];
    BacktraceState state = { buffer, buffer + 64 };
    _Unwind_Backtrace(unwind_callback, &state);

    int size = static_cast<int>(state.current - buffer);
    if (size <= 0) {
        ALOGE("AlcedoCrash", "no frames");
        return;
    }

    for (int i = 0; i < size; ++i) {
        ALOGE("AlcedoCrash", "  #%02d %p", i, buffer[i]);
    }
}

} // namespace alcedo
