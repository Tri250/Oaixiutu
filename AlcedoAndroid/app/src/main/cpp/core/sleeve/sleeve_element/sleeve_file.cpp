// AlcedoAndroid - SleeveFile implementation.
// SPDX-License-Identifier: GPL-3.0-only
#include "sleeve/sleeve_element/sleeve_file.hpp"

#include <memory>
#include <utility>

namespace alcedo {

SleeveFile::SleeveFile(sl_element_id_t id, file_name_t element_name)
    : SleeveElement(id, std::move(element_name), ElementType::FILE) {}

SleeveFile::SleeveFile(sl_element_id_t id, file_name_t element_name, std::shared_ptr<Image> image)
    : SleeveElement(id, std::move(element_name), ElementType::FILE), image_(std::move(image)) {
  if (image_) image_id_ = image_->image_id_;
}

SleeveFile::~SleeveFile() = default;

auto SleeveFile::Clear() -> bool {
  image_.reset();
  edit_history_.reset();
  return true;
}

auto SleeveFile::Copy(sl_element_id_t new_id) const -> std::shared_ptr<SleeveElement> {
  auto copy = std::make_shared<SleeveFile>(new_id, element_name_, image_);
  copy->image_id_           = image_id_;
  copy->added_time_         = added_time_;
  copy->last_modified_time_ = last_modified_time_;
  copy->pinned_             = pinned_;
  copy->sync_flag_          = sync_flag_;
  // Clone the edit history if present so the copy is an independent look.
  if (edit_history_) {
    copy->edit_history_ = edit_history_->CloneForFile(new_id);
  }
  return copy;
}

auto SleeveFile::GetImage() -> std::shared_ptr<Image> { return image_; }
void SleeveFile::SetImage(std::shared_ptr<Image> img) {
  image_ = std::move(img);
  if (image_) image_id_ = image_->image_id_;
  SetLastModifiedTime();
}

auto SleeveFile::GetEditHistory() -> std::shared_ptr<EditHistory> { return edit_history_; }
void SleeveFile::SetEditHistory(std::shared_ptr<EditHistory> history) {
  edit_history_ = std::move(history);
  SetLastModifiedTime();
}

}  // namespace alcedo
