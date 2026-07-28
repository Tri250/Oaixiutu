// AlcedoAndroid - SleeveFilter base.
// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <string>

#include "type/type.hpp"

namespace alcedo {

enum class ElementOrder { ASC, DESC };
enum class FilterType { DATETIME, EXIF, DEFAULT };

class SleeveFilter {
 public:
  FilterType type_ = FilterType::DEFAULT;
  virtual ~SleeveFilter() = default;
  virtual void ResetFilter() = 0;
  virtual auto GetPredicate() -> std::string = 0;
  virtual auto ToJSON() -> std::string = 0;
  virtual void FromJSON(const std::string& j_str) = 0;
};

}  // namespace alcedo
