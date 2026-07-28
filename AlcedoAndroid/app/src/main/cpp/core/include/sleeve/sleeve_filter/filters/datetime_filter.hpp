// AlcedoAndroid - DatetimeFilter.
// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <ctime>
#include <string>

#include "range_filter.hpp"
#include "sleeve_filter.hpp"
#include "type/type.hpp"

namespace alcedo {

class DatetimeFilter : public RangeFilter<std::time_t> {
 public:
  FilterType type_ = FilterType::DATETIME;

  void SetFilter(std::time_t start_time, std::time_t end_time);
  void SetRange(std::time_t range_low, std::time_t range_high) override;
  void ResetFilter() override;
  auto GetPredicate() -> std::string override;
  auto ToJSON() -> std::string override;
  void FromJSON(const std::string& j_str) override;

 private:
  std::time_t start_time_ = 0;
  std::time_t end_time_   = 0;
};

}  // namespace alcedo
