// AlcedoAndroid - RangeFilter template.
// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include "sleeve_filter.hpp"

namespace alcedo {

template <typename T>
class RangeFilter : public SleeveFilter {
 public:
  virtual void SetRange(T range_low, T range_high) = 0;
};

}  // namespace alcedo
