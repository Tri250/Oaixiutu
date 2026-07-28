// AlcedoAndroid - SleeveElementFactory.
// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <memory>

#include "sleeve_element.hpp"
#include "type/type.hpp"

namespace alcedo {

class SleeveElementFactory {
 public:
  static auto CreateElement(ElementType type, sl_element_id_t id, file_name_t element_name)
      -> std::shared_ptr<SleeveElement>;
};

}  // namespace alcedo
