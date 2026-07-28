// AlcedoAndroid - SleeveService implementation.
// SPDX-License-Identifier: GPL-3.0-only
#include "storage/service/sleeve_service.hpp"

namespace alcedo {

SleeveService::SleeveService(ConnectionGuard& guard) : mapper_(guard) {}

void SleeveService::PersistElement(const SleeveElement& elem) { mapper_.UpsertElement(elem); }

void SleeveService::DeleteElement(sl_element_id_t id) { mapper_.RemoveElement(id); }

auto SleeveService::LoadElement(sl_element_id_t id) -> std::optional<SleeveElementRecord> {
  return mapper_.SelectElement(id);
}

auto SleeveService::LoadAllElements() -> std::vector<SleeveElementRecord> {
  return mapper_.SelectAllElements();
}

void SleeveService::LinkChild(sl_element_id_t folder_id, sl_element_id_t element_id) {
  mapper_.InsertFolderContent(folder_id, element_id);
}

void SleeveService::UnlinkChild(sl_element_id_t folder_id, sl_element_id_t element_id) {
  mapper_.RemoveFolderContent(folder_id, element_id);
}

auto SleeveService::LoadChildren(sl_element_id_t folder_id) -> std::vector<sl_element_id_t> {
  return mapper_.SelectFolderContent(folder_id);
}

void SleeveService::SaveEditHistory(sl_element_id_t file_id, const std::string& history_json) {
  mapper_.UpsertEditHistory(file_id, history_json);
}

auto SleeveService::LoadEditHistory(sl_element_id_t file_id) -> std::optional<std::string> {
  return mapper_.SelectEditHistory(file_id);
}

}  // namespace alcedo
