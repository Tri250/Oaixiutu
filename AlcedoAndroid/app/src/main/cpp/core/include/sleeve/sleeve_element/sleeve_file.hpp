// AlcedoAndroid - SleeveFile: an image file + its edit history.
// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <memory>

#include "edit/history/edit_history.hpp"
#include "image/image.hpp"
#include "sleeve_element.hpp"
#include "type/type.hpp"

namespace alcedo {

class SleeveFile : public SleeveElement {
 public:
  image_id_t image_id_ = 0;

  SleeveFile(sl_element_id_t id, file_name_t element_name);
  SleeveFile(sl_element_id_t id, file_name_t element_name, std::shared_ptr<Image> image);

  auto Clear() -> bool override;
  auto Copy(sl_element_id_t new_id) const -> std::shared_ptr<SleeveElement> override;

  auto GetImage() -> std::shared_ptr<Image>;
  void SetImage(std::shared_ptr<Image> img);

  auto GetEditHistory() -> std::shared_ptr<EditHistory>;
  void SetEditHistory(std::shared_ptr<EditHistory> history);
  ~SleeveFile() override;

 private:
  std::shared_ptr<Image>       image_;
  std::shared_ptr<EditHistory> edit_history_;
};

}  // namespace alcedo
