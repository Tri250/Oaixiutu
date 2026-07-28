// AlcedoAndroid - ValueFilter template.
// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include "sleeve_filter.hpp"

namespace alcedo {

template <typename T>
class ValueFilter {
 public:
  virtual ~ValueFilter() = default;
  virtual void SetValue(T value) = 0;
};

}  // namespace alcedo
