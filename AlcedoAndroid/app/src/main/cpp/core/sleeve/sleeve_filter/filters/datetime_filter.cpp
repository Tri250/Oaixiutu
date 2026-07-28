// AlcedoAndroid - DatetimeFilter implementation.
// SPDX-License-Identifier: GPL-3.0-only
#include "datetime_filter.hpp"

#include <cstdio>
#include <ctime>
#include <string>

namespace alcedo {

void DatetimeFilter::SetFilter(std::time_t start_time, std::time_t end_time) {
  start_time_ = start_time;
  end_time_   = end_time;
}

void DatetimeFilter::SetRange(std::time_t range_low, std::time_t range_high) {
  SetFilter(range_low, range_high);
}

void DatetimeFilter::ResetFilter() {
  start_time_ = 0;
  end_time_   = 0;
}

auto DatetimeFilter::GetPredicate() -> std::string {
  if (start_time_ == 0 && end_time_ == 0) return "1=1";
  char buf[128];
  if (start_time_ > 0 && end_time_ > 0) {
    std::snprintf(buf, sizeof(buf),
                  "capture_time BETWEEN %ld AND %ld",
                  static_cast<long>(start_time_), static_cast<long>(end_time_));
  } else if (start_time_ > 0) {
    std::snprintf(buf, sizeof(buf),
                  "capture_time >= %ld", static_cast<long>(start_time_));
  } else {
    std::snprintf(buf, sizeof(buf),
                  "capture_time <= %ld", static_cast<long>(end_time_));
  }
  return std::string(buf);
}

auto DatetimeFilter::ToJSON() -> std::string {
  char buf[160];
  std::snprintf(buf, sizeof(buf),
                "{\"type\":\"datetime\",\"start\":%ld,\"end\":%ld}",
                static_cast<long>(start_time_), static_cast<long>(end_time_));
  return std::string(buf);
}

void DatetimeFilter::FromJSON(const std::string& j_str) {
  // Minimal hand-rolled parse: find "start" and "end" keys.
  auto extract = [&](const char* key) -> std::time_t {
    auto pos = j_str.find(key);
    if (pos == std::string::npos) return 0;
    pos = j_str.find(':', pos);
    if (pos == std::string::npos) return 0;
    return static_cast<std::time_t>(std::strtoll(j_str.c_str() + pos + 1, nullptr, 10));
  };
  start_time_ = extract("\"start\"");
  end_time_   = extract("\"end\"");
}

}  // namespace alcedo
