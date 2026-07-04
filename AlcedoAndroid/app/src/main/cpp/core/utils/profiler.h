// Ported from AlcedoStudio desktop: utils/profiler/profiler.hpp
// SPDX-License-Identifier: GPL-3.0-only
//
// Lightweight wrapper for easy_profiler's EASY_BLOCK.
// On Android there is no easy_profiler available, so EASY_BLOCK degrades to a
// no-op macro. Operators that include this header can sprinkle EASY_BLOCK(...)
// calls without affecting the build, matching the desktop source surface so
// future ports stay source-compatible.

#ifndef ALCEDO_UTILS_PROFILER_WRAPPER_HPP
#define ALCEDO_UTILS_PROFILER_WRAPPER_HPP

#if !defined(EASY_BLOCK)
// easy_profiler is not bundled for Android; fall back to no-op.
#define EASY_BLOCK(...)
#endif

#endif  // ALCEDO_UTILS_PROFILER_WRAPPER_HPP
