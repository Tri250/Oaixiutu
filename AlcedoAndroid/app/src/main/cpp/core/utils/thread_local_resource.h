// Ported from AlcedoStudio desktop: concurrency/thread_local_resource.hpp
// SPDX-License-Identifier: GPL-3.0-only
//
// Generic per-thread lazy-initialized resource. An Initializer factory produces
// one unique_ptr<T> per worker thread; Get() returns the thread-local instance.

#pragma once

#include <functional>
#include <memory>

template <typename T>
class ThreadLocalResource {
public:
    using Initializer = std::function<std::unique_ptr<T>()>;

    static void SetInitializer(Initializer init) { GetInitFunc() = std::move(init); }

    static T& Get() {
        thread_local std::unique_ptr<T> instance = GetInitFunc()();
        return *instance;
    }

private:
    static Initializer& GetInitFunc() {
        static Initializer init_func;
        return init_func;
    }
};
