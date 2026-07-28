// AlcedoAndroid - SleeveElementController (sleeve element CRUD over SleeveMapper).
// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <memory>
#include <optional>
#include <vector>

#include "storage/controller/controller_types.hpp"
#include "storage/mapper/sleeve_mapper.hpp"
#include "sleeve/sleeve_element/sleeve_element.hpp"
#include "type/type.hpp"

namespace alcedo {

class SleeveElementController {
 public:
  explicit SleeveElementController(ConnectionGuard&& guard);

  void PersistElement(const SleeveElement& elem);
  void DeleteElement(sl_element_id_t id);
  auto LoadElement(sl_element_id_t id) -> std::optional<SleeveElementRecord>;
  auto LoadAllElements() -> std::vector<SleeveElementRecord>;

  void LinkChild(sl_element_id_t folder_id, sl_element_id_t element_id);
  void UnlinkChild(sl_element_id_t folder_id, sl_element_id_t element_id);
  auto LoadChildren(sl_element_id_t folder_id) -> std::vector<sl_element_id_t>;

  void SaveEditHistory(sl_element_id_t file_id, const std::string& history_json);
  auto LoadEditHistory(sl_element_id_t file_id) -> std::optional<std::string>;

  void SaveFileImage(sl_element_id_t file_id, image_id_t image_id);
  auto LoadFileImage(sl_element_id_t file_id) -> std::optional<image_id_t>;

 private:
  ConnectionGuard guard_;
  SleeveMapper    mapper_;
};

}  // namespace alcedo
