// AlcedoAndroid - ExifFilter.
// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <cstdint>
#include <string>

#include "sleeve_filter.hpp"
#include "type/type.hpp"
#include "value_filter.hpp"

namespace alcedo {

// Filterable subset of EXIF metadata used by the ExifFilter.
struct FilterableMetadata {
  std::string    make_      = {};
  std::string    model_     = {};
  unsigned short height_    = 0;
  unsigned short width_     = 0;
  std::string    lens_      = {};
  std::string    lens_make_ = {};
  float          aperture_  = 0.0f;
  float          focal_     = 0.0f;
  bool           has_attachment_ = false;
};

class ExifFilter : public SleeveFilter, public ValueFilter<FilterableMetadata> {
 public:
  FilterType   type_ = FilterType::EXIF;
  ElementOrder order_ = ElementOrder::ASC;

  void SetFilter(FilterableMetadata metadata, ElementOrder order);
  void SetValue(FilterableMetadata value) override;
  void ResetFilter() override;
  auto GetPredicate() -> std::string override;
  auto ToJSON() -> std::string override;
  void FromJSON(const std::string& j_str) override;

 private:
  FilterableMetadata metadata_;
};

}  // namespace alcedo
