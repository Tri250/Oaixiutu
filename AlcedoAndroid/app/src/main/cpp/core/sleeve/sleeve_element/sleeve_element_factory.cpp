// AlcedoAndroid - SleeveElementFactory implementation.
// SPDX-License-Identifier: GPL-3.0-only
#include "sleeve/sleeve_element/sleeve_element_factory.hpp"

#include "sleeve/sleeve_element/sleeve_file.hpp"
#include "sleeve/sleeve_element/sleeve_folder.hpp"

namespace alcedo {

auto SleeveElementFactory::CreateElement(ElementType type, sl_element_id_t id,
                                         file_name_t element_name)
    -> std::shared_ptr<SleeveElement> {
  switch (type) {
    case ElementType::FILE:
      return std::make_shared<SleeveFile>(id, std::move(element_name));
    case ElementType::FOLDER:
      return std::make_shared<SleeveFolder>(id, std::move(element_name));
  }
  return nullptr;
}

}  // namespace alcedo
