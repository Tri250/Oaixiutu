// AlcedoAndroid - SleeveElementController implementation.
// SPDX-License-Identifier: GPL-3.0-only
#include "storage/controller/sleeve_element_controller.hpp"

#include <utility>

namespace alcedo {

SleeveElementController::SleeveElementController(ConnectionGuard&& guard)
    : guard_(std::move(guard)), mapper_(guard_) {}

void SleeveElementController::PersistElement(const SleeveElement& elem) {
  mapper_.UpsertElement(elem);
}

void SleeveElementController::DeleteElement(sl_element_id_t id) {
  mapper_.RemoveElement(id);
}

auto SleeveElementController::LoadElement(sl_element_id_t id) -> std::optional<SleeveElementRecord> {
  return mapper_.SelectElement(id);
}

auto SleeveElementController::LoadAllElements() -> std::vector<SleeveElementRecord> {
  return mapper_.SelectAllElements();
}

void SleeveElementController::LinkChild(sl_element_id_t folder_id, sl_element_id_t element_id) {
  mapper_.InsertFolderContent(folder_id, element_id);
}

void SleeveElementController::UnlinkChild(sl_element_id_t folder_id, sl_element_id_t element_id) {
  mapper_.RemoveFolderContent(folder_id, element_id);
}

auto SleeveElementController::LoadChildren(sl_element_id_t folder_id) -> std::vector<sl_element_id_t> {
  return mapper_.SelectFolderContent(folder_id);
}

void SleeveElementController::SaveEditHistory(sl_element_id_t file_id,
                                              const std::string& history_json) {
  mapper_.UpsertEditHistory(file_id, history_json);
}

auto SleeveElementController::LoadEditHistory(sl_element_id_t file_id) -> std::optional<std::string> {
  return mapper_.SelectEditHistory(file_id);
}

void SleeveElementController::SaveFileImage(sl_element_id_t file_id, image_id_t image_id) {
  mapper_.UpsertFileImage(file_id, image_id);
}

auto SleeveElementController::LoadFileImage(sl_element_id_t file_id) -> std::optional<image_id_t> {
  return mapper_.SelectFileImage(file_id);
}

}  // namespace alcedo
