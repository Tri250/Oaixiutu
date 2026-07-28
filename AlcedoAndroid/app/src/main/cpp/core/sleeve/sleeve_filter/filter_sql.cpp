// AlcedoAndroid - FilterSQLCompiler implementation.
// Compiles a FilterNode tree into a SQL WHERE clause over the image table.
// SPDX-License-Identifier: GPL-3.0-only
#include "sleeve/sleeve_filter/filter_combo.hpp"

#include <sstream>
#include <string>
#include <variant>

namespace alcedo {
namespace {

auto Visit(const FilterValue& v) -> std::string {
  return FilterSQLCompiler::ValueToSQL(v);
}

}  // namespace

auto FilterSQLCompiler::FieldToColumn(FilterField field) -> std::string {
  switch (field) {
    case FilterField::ExifCameraModel: return "exif_camera_model";
    case FilterField::ExifFocalLength: return "exif_focal_length";
    case FilterField::ExifAperture:    return "exif_aperture";
    case FilterField::ExifISO:         return "exif_iso";
    case FilterField::CaptureDate:     return "capture_time";
    case FilterField::ImportDate:      return "added_time";
    case FilterField::FileName:        return "file_name";
    case FilterField::FileExtension:   return "file_extension";
    case FilterField::ImageSize:       return "image_size";
    case FilterField::Rating:          return "rating";
    case FilterField::ImagePath:       return "image_path";
    case FilterField::SemanticTags:    return "semantic_tags";
  }
  return "unknown";
}

auto FilterSQLCompiler::CompareToSQL(CompareOp op) -> std::string {
  switch (op) {
    case CompareOp::EQUALS:        return "=";
    case CompareOp::NOT_EQUALS:    return "<>";
    case CompareOp::CONTAINS:      return "LIKE";
    case CompareOp::NOT_CONTAINS:  return "NOT LIKE";
    case CompareOp::GREATER_THAN:  return ">";
    case CompareOp::LESS_THAN:     return "<";
    case CompareOp::GREATER_EQUAL: return ">=";
    case CompareOp::LESS_EQUAL:    return "<=";
    case CompareOp::STARTS_WITH:   return "LIKE";
    case CompareOp::ENDS_WITH:     return "LIKE";
    case CompareOp::BETWEEN:       return "BETWEEN";
    case CompareOp::REGEX:         return "~";
  }
  return "=";
}

auto FilterSQLCompiler::ValueToSQL(const FilterValue& v) -> std::string {
  // Returns a SQL literal (already quoted/escaped for strings).
  if (std::holds_alternative<std::monostate>(v)) return "NULL";
  if (std::holds_alternative<int64_t>(v)) return std::to_string(std::get<int64_t>(v));
  if (std::holds_alternative<double>(v)) {
    std::ostringstream oss;
    oss << std::get<double>(v);
    return oss.str();
  }
  if (std::holds_alternative<bool>(v)) return std::get<bool>(v) ? "1" : "0";
  if (std::holds_alternative<std::time_t>(v)) return std::to_string(static_cast<int64_t>(v));
  if (std::holds_alternative<std::string>(v)) {
    // Escape single quotes.
    std::string s = std::get<std::string>(v);
    std::string out;
    out.reserve(s.size() + 2);
    out.push_back('\'');
    for (char c : s) {
      if (c == '\'') out += "''";
      else out.push_back(c);
    }
    out.push_back('\'');
    return out;
  }
  return "NULL";
}

auto FilterSQLCompiler::GenerateConditionString(const FieldCondition& cond) -> std::string {
  const std::string col = FieldToColumn(cond.field_);
  const std::string op  = CompareToSQL(cond.op_);
  if (cond.op_ == CompareOp::BETWEEN) {
    return col + " BETWEEN " + ValueToSQL(cond.value_) + " AND " +
           (cond.second_value_ ? ValueToSQL(*cond.second_value_) : "NULL");
  }
  if (cond.op_ == CompareOp::CONTAINS || cond.op_ == CompareOp::NOT_CONTAINS) {
    std::string pat = "%";
    if (std::holds_alternative<std::string>(cond.value_)) pat = "%" + std::get<std::string>(cond.value_) + "%";
    return col + " " + op + " " + ValueToSQL(pat);
  }
  if (cond.op_ == CompareOp::STARTS_WITH) {
    std::string pat = "%";
    if (std::holds_alternative<std::string>(cond.value_)) pat = std::get<std::string>(cond.value_) + "%";
    return col + " LIKE " + ValueToSQL(pat);
  }
  if (cond.op_ == CompareOp::ENDS_WITH) {
    std::string pat = "%";
    if (std::holds_alternative<std::string>(cond.value_)) pat = "%" + std::get<std::string>(cond.value_);
    return col + " LIKE " + ValueToSQL(pat);
  }
  return col + " " + op + " " + ValueToSQL(cond.value_);
}

auto FilterSQLCompiler::CompileNode(const FilterNode& node) -> std::string {
  switch (node.type_) {
    case FilterNode::Type::RawSQL:
      return node.raw_sql_.value_or("1=1");
    case FilterNode::Type::Condition:
      if (node.condition_) return GenerateConditionString(*node.condition_);
      return "1=1";
    case FilterNode::Type::Logical: {
      if (node.children_.empty()) return "1=1";
      std::ostringstream oss;
      const char* sep = "";
      for (const auto& child : node.children_) {
        oss << sep << CompileNode(child);
        sep = node.op_ == FilterOp::AND ? " AND " : " OR ";
      }
      if (node.op_ == FilterOp::NOT) return "NOT (" + oss.str() + ")";
      return "(" + oss.str() + ")";
    }
  }
  return "1=1";
}

auto FilterSQLCompiler::Compile(const FilterNode& node) -> std::string {
  return CompileNode(node);
}

auto FilterSQLCompiler::CompileWithParams(const FilterNode& node) -> Result {
  // For simplicity, params are inlined into the clause; the params vector is
  // left empty. A prepared-statement backend can re-derive params from the tree.
  Result r;
  r.where_clause_ = CompileNode(node);
  return r;
}

}  // namespace alcedo
