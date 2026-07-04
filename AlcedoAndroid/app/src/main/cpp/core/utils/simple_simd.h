// Ported from AlcedoStudio desktop: utils/simd/simple_simd.hpp
// SPDX-License-Identifier: GPL-3.0-only
// Minimal cross-arch SIMD abstraction. On Android the primary path is ARM NEON
// (arm64-v8a / armeabi-v7a with NEON); x86/x86_64 ABIs use SSE/AVX when available.

#pragma once

#include <cstddef>
#include <cstdint>

#if defined(_MSC_VER)
#include <intrin.h>
#endif

// --- arch detection ---
#if defined(__i386__) || defined(__x86_64__) || defined(_M_IX86) || defined(_M_X64)
#define SIMPLE_SIMD_X86 1
#else
#define SIMPLE_SIMD_X86 0
#endif

#if defined(__aarch64__) || defined(__ARM_NEON) || defined(_M_ARM64)
#define SIMPLE_SIMD_ARM 1
#else
#define SIMPLE_SIMD_ARM 0
#endif

#if SIMPLE_SIMD_X86
#include <immintrin.h>
#if !defined(_MSC_VER)
#include <cpuid.h>
#endif
#elif SIMPLE_SIMD_ARM
#include <arm_neon.h>
#endif

namespace simple_simd {

// -------- feature detection (x86 only; NEON is baseline on AArch64) ----------
struct CPUFeatures {
    bool sse41_ = false;
    bool sse42_ = false;
    bool avx2_  = false;
    bool avx_   = false;
    bool neon_  = false;
};

inline CPUFeatures detect_cpu() {
    CPUFeatures f;
#if SIMPLE_SIMD_ARM
    f.neon_ = true;  // baseline on AArch64 / NEON-capable ARM
#elif SIMPLE_SIMD_X86
#if defined(_MSC_VER) && (defined(_M_X64) || defined(_M_IX86))
    int info[4];
    __cpuid(info, 0);
    int nIds = info[0];
    if (nIds >= 1) {
        __cpuid(info, 1);
        int ecx = info[2];
        f.sse42_ = (ecx & (1 << 20)) != 0;
        f.sse41_ = (ecx & (1 << 19)) != 0;
        f.avx_   = (ecx & (1 << 28)) != 0;
    }
    if (nIds >= 7) {
        __cpuidex(info, 7, 0);
        int ebx = info[1];
        f.avx2_  = (ebx & (1 << 5)) != 0;
    }
#elif defined(__GNUC__) || defined(__clang__)
    unsigned int eax, ebx, ecx, edx;
    if (__get_cpuid_max(0, nullptr) >= 1) {
        __get_cpuid(1, &eax, &ebx, &ecx, &edx);
        f.sse41_ = (ecx & (1 << 19)) != 0;
        f.sse42_ = (ecx & (1 << 20)) != 0;
        f.avx_   = (ecx & (1 << 28)) != 0;
    }
    if (__get_cpuid_max(0, nullptr) >= 7) {
        __get_cpuid_count(7, 0, &eax, &ebx, &ecx, &edx);
        f.avx2_ = (ebx & (1 << 5)) != 0;
    }
#endif
#endif
    return f;
}

// -------- types ----------
#if SIMPLE_SIMD_ARM
using f32x4 = float32x4_t;  // ARM NEON 128-bit vector
#else
using f32x4 = __m128;       // consistent 128-bit API on x86
#endif

// -------- SSE4.1 (x86) ----------
#if SIMPLE_SIMD_X86 && \
    (defined(__SSE4_1__) || (defined(_MSC_VER) && defined(_M_IX86_FP) && _M_IX86_FP >= 2) || defined(_M_X64))
namespace impl_sse {
inline f32x4 load(const float* p) { return _mm_loadu_ps(p); }
inline void  store(float* dst, const f32x4& v) { _mm_storeu_ps(dst, v); }
inline f32x4 set1(float x) { return _mm_set1_ps(x); }
inline f32x4 mul(const f32x4& a, const f32x4& b) { return _mm_mul_ps(a, b); }
inline f32x4 add(const f32x4& a, const f32x4& b) { return _mm_add_ps(a, b); }
inline f32x4 sub(const f32x4& a, const f32x4& b) { return _mm_sub_ps(a, b); }
inline f32x4 div(const f32x4& a, const f32x4& b) { return _mm_div_ps(a, b); }
inline f32x4 mul_add(const f32x4& a, const f32x4& b, const f32x4& c) {
#if defined(__FMA__)
    return _mm_fmadd_ps(a, b, c);
#else
    return _mm_add_ps(_mm_mul_ps(a, b), c);
#endif
}
}  // namespace impl_sse
#endif

// -------- AVX2 (x86, still 128-bit lanes for API uniformity) ----------
#if SIMPLE_SIMD_X86 && defined(__AVX2__)
namespace impl_avx2 {
inline f32x4 load(const float* p) { return _mm_loadu_ps(p); }
inline void  store(float* dst, const f32x4& v) { _mm_storeu_ps(dst, v); }
inline f32x4 set1(float x) { return _mm_set1_ps(x); }
inline f32x4 mul(const f32x4& a, const f32x4& b) { return _mm_mul_ps(a, b); }
inline f32x4 add(const f32x4& a, const f32x4& b) { return _mm_add_ps(a, b); }
inline f32x4 sub(const f32x4& a, const f32x4& b) { return _mm_sub_ps(a, b); }
inline f32x4 div(const f32x4& a, const f32x4& b) { return _mm_div_ps(a, b); }
inline f32x4 mul_add(const f32x4& a, const f32x4& b, const f32x4& c) {
#if defined(__FMA__)
    return _mm_fmadd_ps(a, b, c);
#else
    return _mm_add_ps(_mm_mul_ps(a, b), c);
#endif
}
}  // namespace impl_avx2
#endif

// -------- NEON (ARM64 / ARM with NEON) ----------
#if SIMPLE_SIMD_ARM
namespace impl_neon {
inline f32x4 load(const float* p) { return vld1q_f32(p); }
inline void  store(float* dst, const f32x4& v) { vst1q_f32(dst, v); }
inline f32x4 set1(float x) { return vdupq_n_f32(x); }
inline f32x4 mul(const f32x4& a, const f32x4& b) { return vmulq_f32(a, b); }
inline f32x4 add(const f32x4& a, const f32x4& b) { return vaddq_f32(a, b); }
inline f32x4 sub(const f32x4& a, const f32x4& b) { return vsubq_f32(a, b); }
inline f32x4 div(const f32x4& a, const f32x4& b) {
    // NEON has no native division; use reciprocal with one Newton refinement.
    f32x4 reciprocal = vrecpeq_f32(b);
    reciprocal       = vmulq_f32(vrecpsq_f32(b, reciprocal), reciprocal);
    return vmulq_f32(a, reciprocal);
}
inline f32x4 mul_add(const f32x4& a, const f32x4& b, const f32x4& c) {
    return vmlaq_f32(c, a, b);  // c + a * b
}
}  // namespace impl_neon
#endif

// -------- dispatcher ----------
using load_fn_t    = f32x4 (*)(const float*);
using store_fn_t   = void (*)(float*, const f32x4&);
using set1_fn_t    = f32x4 (*)(float);
using mul_fn_t     = f32x4 (*)(const f32x4&, const f32x4&);
using add_fn_t     = f32x4 (*)(const f32x4&, const f32x4&);
using sub_fn_t     = f32x4 (*)(const f32x4&, const f32x4&);
using div_fn_t     = f32x4 (*)(const f32x4&, const f32x4&);
using mul_add_fn_t = f32x4 (*)(const f32x4&, const f32x4&, const f32x4&);

struct Dispatch {
#if SIMPLE_SIMD_ARM
    load_fn_t    load    = impl_neon::load;
    store_fn_t   store   = impl_neon::store;
    set1_fn_t    set1    = impl_neon::set1;
    mul_fn_t     mul     = impl_neon::mul;
    add_fn_t     add     = impl_neon::add;
    sub_fn_t     sub     = impl_neon::sub;
    div_fn_t     div     = impl_neon::div;
    mul_add_fn_t mul_add = impl_neon::mul_add;
#elif SIMPLE_SIMD_X86
    load_fn_t    load    = nullptr;
    store_fn_t   store   = nullptr;
    set1_fn_t    set1    = nullptr;
    mul_fn_t     mul     = nullptr;
    add_fn_t     add     = nullptr;
    sub_fn_t     sub     = nullptr;
    div_fn_t     div     = nullptr;
    mul_add_fn_t mul_add = nullptr;
#endif

    void init() {
#if SIMPLE_SIMD_X86
        CPUFeatures f = detect_cpu();
#if defined(__AVX2__)
        if (f.avx2_) {
            load    = impl_avx2::load;
            store   = impl_avx2::store;
            set1    = impl_avx2::set1;
            mul     = impl_avx2::mul;
            add     = impl_avx2::add;
            sub     = impl_avx2::sub;
            div     = impl_avx2::div;
            mul_add = impl_avx2::mul_add;
            return;
        }
#endif
#if defined(__SSE4_1__)
        if (f.sse41_) {
            load    = impl_sse::load;
            store   = impl_sse::store;
            set1    = impl_sse::set1;
            mul     = impl_sse::mul;
            add     = impl_sse::add;
            sub     = impl_sse::sub;
            div     = impl_sse::div;
            mul_add = impl_sse::mul_add;
            return;
        }
#endif
#elif SIMPLE_SIMD_ARM
        // NEON is baseline on AArch64; defaults already set.
        return;
#endif
    }
};

inline Dispatch& global_dispatch() {
    static Dispatch d;
    static bool     initialized = false;
    if (!initialized) {
        d.init();
        initialized = true;
    }
    return d;
}

// -------- public API ----------
inline f32x4 load_f32x4(const float* p) { return global_dispatch().load(p); }
inline void  store_f32x4(float* dst, const f32x4& v) { return global_dispatch().store(dst, v); }
inline f32x4 set1_f32(float x) { return global_dispatch().set1(x); }
inline f32x4 mul_f32x4(const f32x4& a, const f32x4& b) { return global_dispatch().mul(a, b); }
inline f32x4 add_f32x4(const f32x4& a, const f32x4& b) { return global_dispatch().add(a, b); }
inline f32x4 sub_f32x4(const f32x4& a, const f32x4& b) { return global_dispatch().sub(a, b); }
inline f32x4 div_f32x4(const f32x4& a, const f32x4& b) { return global_dispatch().div(a, b); }
inline f32x4 mul_add_f32x4(const f32x4& a, const f32x4& b, const f32x4& c) {
    return global_dispatch().mul_add(a, b, c);
}

}  // namespace simple_simd
