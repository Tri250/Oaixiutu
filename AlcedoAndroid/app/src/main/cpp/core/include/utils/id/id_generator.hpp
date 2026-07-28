// AlcedoAndroid - monotonic incremental ID generator.
// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <atomic>
#include <cstdint>
#include <mutex>

namespace alcedo {
namespace IncrID {

template <typename IdT>
class IDGenerator {
 public:
  explicit IDGenerator(IdT start = 0) : current_(start) {}

  IdT Next() { return current_.fetch_add(1, std::memory_order_relaxed); }
  IdT Peek() const { return current_.load(std::memory_order_relaxed); }
  IdT GetCurrentID() const { return Peek(); }
  void Reset(IdT value = 0) { current_.store(value, std::memory_order_relaxed); }

 private:
  std::atomic<IdT> current_;
};

}  // namespace IncrID
}  // namespace alcedo
