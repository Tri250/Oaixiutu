#include "crash_handler.h"

#include <execinfo.h>
#include <unwind.h>
#include <unistd.h>
#include <cstdlib>
#include <mutex>

// Note: In signal handler context, most standard library functions
// (including printf, malloc, snprintf) are NOT async-signal-safe.
// We use write() for critical logging and __android_log_write for
// Android logcat output. __android_log_write is simpler than
// __android_log_print (no format string parsing) but still carries
// a small deadlock risk on some Android versions. This is an
// accepted trade-off for crash diagnostics.

namespace alcedo {
namespace {

// Async-signal-safe write to stderr (fd 2)
void safe_write(const char* msg) {
    size_t len = 0;
    while (msg[len] != '\0') ++len;
    write(STDERR_FILENO, msg, len);
    write(STDERR_FILENO, "\n", 1);
}

// Async-signal-safe integer to string conversion
void safe_itoa(unsigned long long val, char* buf, int base = 10) {
    if (val == 0) {
        buf[0] = '0';
        buf[1] = '\0';
        return;
    }
    char tmp[32];
    int i = 0;
    while (val > 0) {
        int digit = static_cast<int>(val % base);
        tmp[i++] = (digit < 10) ? static_cast<char>('0' + digit)
                                : static_cast<char>('a' + digit - 10);
        val /= base;
    }
    int j = 0;
    while (i > 0) buf[j++] = tmp[--i];
    buf[j] = '\0';
}

// Async-signal-safe log to Android logcat (uses __android_log_write,
// which is simpler than __android_log_print and less likely to deadlock)
void safe_android_log(int prio, const char* tag, const char* msg) {
    __android_log_write(prio, tag, msg);
}

// Signal-safe crash log helper
void safe_log_crash(const char* msg) {
    safe_write(msg);
    safe_android_log(ANDROID_LOG_ERROR, "AlcedoCrash", msg);
}

} // anonymous namespace

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
                __android_log_print(ANDROID_LOG_ERROR, "AlcedoCrash",
                                    "Failed to install handler for %s", SignalName(sig));
            } else {
                __android_log_print(ANDROID_LOG_INFO, "AlcedoCrash",
                                    "Installed handler for %s", SignalName(sig));
            }
        }

        installed_ = true;
        __android_log_print(ANDROID_LOG_INFO, "AlcedoCrash",
                            "Crash handler installed successfully");
    });
}

void CrashHandler::Uninstall() {
    if (!installed_) return;

    for (int i = 0; i < kHandledSignalCount; ++i) {
        int sig = kHandledSignals[i];
        sigaction(sig, &old_actions_[sig], nullptr);
    }

    installed_ = false;
    __android_log_print(ANDROID_LOG_INFO, "AlcedoCrash", "Crash handler uninstalled");
}

void CrashHandler::SetCrashCallback(CrashCallback callback) {
    callback_ = callback;
}

void CrashHandler::SignalHandler(int sig, siginfo_t* info, void* context) {
    // Use signal-safe logging in the signal handler path.
    safe_log_crash("========== NATIVE CRASH DETECTED ==========");

    LogCrashInfo(sig, info, context);
    DumpCallstack();

    // Build crash info string for callback (use signal-safe helpers)
    if (callback_) {
        char crash_info[512];
        const char* name = SignalName(sig);
        size_t pos = 0;
        auto append = [&](const char* s) {
            while (*s && pos < sizeof(crash_info) - 1) crash_info[pos++] = *s++;
        };
        append("Signal: ");
        append(name);
        append(" (");
        safe_itoa(static_cast<unsigned long long>(sig), crash_info + pos);
        while (crash_info[pos]) ++pos;
        append("), si_addr: 0x");
        safe_itoa(reinterpret_cast<unsigned long long>(info->si_addr), crash_info + pos, 16);
        while (crash_info[pos]) ++pos;
        append(", si_code: ");
        safe_itoa(static_cast<unsigned long long>(info->si_code), crash_info + pos);
        while (crash_info[pos]) ++pos;
        crash_info[pos] = '\0';
        callback_(crash_info);
    }

    safe_log_crash("========== END CRASH REPORT ==========");

    // Re-raise with the old/default handler to get proper tombstone/core dump
    if (old_actions_[sig].sa_flags & SA_SIGINFO) {
        old_actions_[sig].sa_sigaction(sig, info, context);
    } else if (old_actions_[sig].sa_handler == SIG_DFL) {
        struct sigaction default_sa;
        memset(&default_sa, 0, sizeof(default_sa));
        default_sa.sa_handler = SIG_DFL;
        sigaction(sig, &default_sa, nullptr);
        raise(sig);
    } else if (old_actions_[sig].sa_handler == SIG_IGN) {
        // Old handler was ignoring - do nothing
    } else {
        old_actions_[sig].sa_handler(sig);
    }
}

void CrashHandler::LogCrashInfo(int sig, siginfo_t* info, void* context) {
    // Use __android_log_write for signal-safety (no format string parsing)
    safe_android_log(ANDROID_LOG_ERROR, "AlcedoCrash", SignalName(sig));

    // Log fault address as hex
    {
        char buf[64];
        buf[0] = '0'; buf[1] = 'x';
        safe_itoa(reinterpret_cast<unsigned long long>(info->si_addr), buf + 2, 16);
        safe_android_log(ANDROID_LOG_ERROR, "AlcedoCrash", buf);
    }

    // Decode si_code for common signals
    if (sig == SIGSEGV) {
        switch (info->si_code) {
            case SEGV_MAPERR: safe_android_log(ANDROID_LOG_ERROR, "AlcedoCrash", "SEGV_MAPERR: Address not mapped"); break;
            case SEGV_ACCERR: safe_android_log(ANDROID_LOG_ERROR, "AlcedoCrash", "SEGV_ACCERR: Invalid permissions"); break;
            default:          safe_android_log(ANDROID_LOG_ERROR, "AlcedoCrash", "SEGV: Unknown code"); break;
        }
    } else if (sig == SIGBUS) {
        switch (info->si_code) {
            case BUS_ADRALN: safe_android_log(ANDROID_LOG_ERROR, "AlcedoCrash", "BUS_ADRALN: Invalid alignment"); break;
            case BUS_ADRERR: safe_android_log(ANDROID_LOG_ERROR, "AlcedoCrash", "BUS_ADRERR: Nonexistent address"); break;
            case BUS_OBJERR: safe_android_log(ANDROID_LOG_ERROR, "AlcedoCrash", "BUS_OBJERR: Hardware error"); break;
            default:         safe_android_log(ANDROID_LOG_ERROR, "AlcedoCrash", "SIGBUS: Unknown code"); break;
        }
    } else if (sig == SIGFPE) {
        switch (info->si_code) {
            case FPE_INTDIV: safe_android_log(ANDROID_LOG_ERROR, "AlcedoCrash", "FPE_INTDIV: Integer divide by zero"); break;
            case FPE_INTOVF: safe_android_log(ANDROID_LOG_ERROR, "AlcedoCrash", "FPE_INTOVF: Integer overflow"); break;
            case FPE_FLTDIV: safe_android_log(ANDROID_LOG_ERROR, "AlcedoCrash", "FPE_FLTDIV: Float divide by zero"); break;
            case FPE_FLTOVF: safe_android_log(ANDROID_LOG_ERROR, "AlcedoCrash", "FPE_FLTOVF: Float overflow"); break;
            case FPE_FLTUND: safe_android_log(ANDROID_LOG_ERROR, "AlcedoCrash", "FPE_FLTUND: Float underflow"); break;
            case FPE_FLTRES: safe_android_log(ANDROID_LOG_ERROR, "AlcedoCrash", "FPE_FLTRES: Inexact result"); break;
            case FPE_FLTINV: safe_android_log(ANDROID_LOG_ERROR, "AlcedoCrash", "FPE_FLTINV: Invalid operation"); break;
            default:         safe_android_log(ANDROID_LOG_ERROR, "AlcedoCrash", "SIGFPE: Unknown code"); break;
        }
    } else if (sig == SIGILL) {
        switch (info->si_code) {
            case ILL_ILLOPC: safe_android_log(ANDROID_LOG_ERROR, "AlcedoCrash", "ILL_ILLOPC: Illegal opcode"); break;
            case ILL_ILLOPN: safe_android_log(ANDROID_LOG_ERROR, "AlcedoCrash", "ILL_ILLOPN: Illegal operand"); break;
            case ILL_ILLADR: safe_android_log(ANDROID_LOG_ERROR, "AlcedoCrash", "ILL_ILLADR: Illegal addressing"); break;
            case ILL_ILLTRP: safe_android_log(ANDROID_LOG_ERROR, "AlcedoCrash", "ILL_ILLTRP: Illegal trap"); break;
            case ILL_PRVOPC: safe_android_log(ANDROID_LOG_ERROR, "AlcedoCrash", "ILL_PRVOPC: Privileged opcode"); break;
            case ILL_PRVREG: safe_android_log(ANDROID_LOG_ERROR, "AlcedoCrash", "ILL_PRVREG: Privileged register"); break;
            case ILL_COPROC: safe_android_log(ANDROID_LOG_ERROR, "AlcedoCrash", "ILL_COPROC: Coprocessor error"); break;
            default:         safe_android_log(ANDROID_LOG_ERROR, "AlcedoCrash", "SIGILL: Unknown code"); break;
        }
    }

    // Log register state from ucontext if available
#if defined(__arm__)
    if (context) {
        ucontext_t* uc = static_cast<ucontext_t*>(context);
        char buf[64];
        buf[0] = '0'; buf[1] = 'x';
        safe_itoa(uc->uc_mcontext.arm_pc, buf + 2, 16);
        safe_android_log(ANDROID_LOG_ERROR, "AlcedoCrash", buf);
        safe_itoa(uc->uc_mcontext.arm_lr, buf + 2, 16);
        safe_android_log(ANDROID_LOG_ERROR, "AlcedoCrash", buf);
        safe_itoa(uc->uc_mcontext.arm_sp, buf + 2, 16);
        safe_android_log(ANDROID_LOG_ERROR, "AlcedoCrash", buf);
    }
#elif defined(__aarch64__)
    if (context) {
        ucontext_t* uc = static_cast<ucontext_t*>(context);
        char buf[64];
        buf[0] = '0'; buf[1] = 'x';
        safe_itoa(static_cast<unsigned long long>(uc->uc_mcontext.pc), buf + 2, 16);
        safe_android_log(ANDROID_LOG_ERROR, "AlcedoCrash", buf);
        safe_itoa(static_cast<unsigned long long>(uc->uc_mcontext.regs[30]), buf + 2, 16);
        safe_android_log(ANDROID_LOG_ERROR, "AlcedoCrash", buf);
        safe_itoa(static_cast<unsigned long long>(uc->uc_mcontext.sp), buf + 2, 16);
        safe_android_log(ANDROID_LOG_ERROR, "AlcedoCrash", buf);
    }
#elif defined(__i386__)
    if (context) {
        ucontext_t* uc = static_cast<ucontext_t*>(context);
        char buf[64];
        buf[0] = '0'; buf[1] = 'x';
        safe_itoa(uc->uc_mcontext.gregs[REG_EIP], buf + 2, 16);
        safe_android_log(ANDROID_LOG_ERROR, "AlcedoCrash", buf);
        safe_itoa(uc->uc_mcontext.gregs[REG_ESP], buf + 2, 16);
        safe_android_log(ANDROID_LOG_ERROR, "AlcedoCrash", buf);
    }
#elif defined(__x86_64__)
    if (context) {
        ucontext_t* uc = static_cast<ucontext_t*>(context);
        char buf[64];
        buf[0] = '0'; buf[1] = 'x';
        safe_itoa(static_cast<unsigned long long>(uc->uc_mcontext.gregs[REG_RIP]), buf + 2, 16);
        safe_android_log(ANDROID_LOG_ERROR, "AlcedoCrash", buf);
        safe_itoa(static_cast<unsigned long long>(uc->uc_mcontext.gregs[REG_RSP]), buf + 2, 16);
        safe_android_log(ANDROID_LOG_ERROR, "AlcedoCrash", buf);
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
    safe_android_log(ANDROID_LOG_ERROR, "AlcedoCrash", "--- Callstack ---");

    void* buffer[64];
    BacktraceState state = { buffer, buffer + 64 };
    _Unwind_Backtrace(unwind_callback, &state);

    int size = static_cast<int>(state.current - buffer);
    if (size <= 0) {
        safe_android_log(ANDROID_LOG_ERROR, "AlcedoCrash", "no frames");
        return;
    }

    for (int i = 0; i < size; ++i) {
        char buf[64];
        buf[0] = '#';
        safe_itoa(static_cast<unsigned long long>(i), buf + 1);
        // Append space and hex address
        size_t pos = 0;
        while (buf[pos]) ++pos;
        buf[pos++] = ' ';
        buf[pos++] = '0';
        buf[pos++] = 'x';
        safe_itoa(reinterpret_cast<unsigned long long>(buffer[i]), buf + pos, 16);
        safe_android_log(ANDROID_LOG_ERROR, "AlcedoCrash", buf);
    }
}

} // namespace alcedo