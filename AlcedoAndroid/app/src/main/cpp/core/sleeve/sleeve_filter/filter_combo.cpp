// AlcedoAndroid - FilterCombo implementation.
// SPDX-License-Identifier: GPL-3.0-only
#include "sleeve/sleeve_filter/filter_combo.hpp"

#include <sstream>
#include <string>

namespace alcedo {

auto FilterCombo::GenerateSQLOn(sl_element_id_t parent_id) const -> std::string {
  // Restrict the image-table query to images that live under parent_id, then
  // AND in the compiled filter tree.
  std::ostringstream oss;
  oss << "parent_folder_id = " << static_cast<int64_t>(parent_id);
  const std::string clause = FilterSQLCompiler::Compile(root_);
  if (!clause.empty() && clause != "1=1") {
    oss << " AND (" << clause << ")";
  }
  return oss.str();
}

auto FilterCombo::GenerateIdSQLOn(sl_element_id_t parent_id) const -> std::string {
  std::ostringstream oss;
  oss << "SELECT element_id FROM sleeve_elements WHERE " << GenerateSQLOn(parent_id);
  return oss.str();
}

}  // namespace alcedo
