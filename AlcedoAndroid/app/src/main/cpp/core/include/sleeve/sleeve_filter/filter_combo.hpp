// AlcedoAndroid - FilterCombo + FilterNode + FilterSQLCompiler.
// Self-contained Android port (std::string instead of std::wstring). Compiles
// a FilterNode tree into a SQL WHERE clause over the image table.
// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <cstdint>
#include <optional>
#include <string>
#include <variant>
#include <vector>

#include "type/type.hpp"

namespace alcedo {

enum class FilterOp { AND, OR, NOT };

enum class FilterField {
  ExifCameraModel,
  ExifFocalLength,
  ExifAperture,
  ExifISO,
  CaptureDate,
  ImportDate,
  FileName,
  FileExtension,
  ImageSize,
  Rating,
  ImagePath,
  SemanticTags,
};

enum class CompareOp {
  EQUALS, NOT_EQUALS, CONTAINS, NOT_CONTAINS,
  GREATER_THAN, LESS_THAN, GREATER_EQUAL, LESS_EQUAL,
  STARTS_WITH, ENDS_WITH, BETWEEN, REGEX,
};

// NOTE: std::time_t is intentionally omitted — on 64-bit Linux/Android it is
// the same type as int64_t (both are `long`), so including both in the variant
// causes a "type occurs more than once" compile error. Time values are stored
// as int64_t and converted at the call site.
using FilterValue = std::variant<std::monostate, int64_t, double, bool, std::string>;

struct FieldCondition {
  FilterField                field_;
  CompareOp                  op_;
  FilterValue                value_;
  std::optional<FilterValue> second_value_ = std::nullopt;
};

struct FilterNode {
  enum class Type { Logical, Condition, RawSQL } type_ = Type::Condition;
  FilterOp                      op_         = FilterOp::AND;
  std::vector<FilterNode>       children_;
  std::optional<FieldCondition> condition_  = std::nullopt;
  std::optional<std::string>    raw_sql_    = std::nullopt;
};

class FilterSQLCompiler {
 public:
  struct Result {
    std::string             where_clause_;
    std::vector<FilterValue> params_;
  };
  static auto Compile(const FilterNode& node) -> std::string;
  static auto CompileWithParams(const FilterNode& node) -> Result;
  static auto ValueToSQL(const FilterValue& v) -> std::string;
 private:
  static auto CompileNode(const FilterNode& node) -> std::string;
  static auto GenerateConditionString(const FieldCondition& cond) -> std::string;
  static auto FieldToColumn(FilterField field) -> std::string;
  static auto CompareToSQL(CompareOp op) -> std::string;
};

class FilterCombo {
 public:
  filter_id_t filter_id_ = 0;
  FilterCombo() = default;
  FilterCombo(filter_id_t id, const FilterNode& root) : filter_id_(id), root_(root) {}
  const FilterNode& GetRoot() const { return root_; }
  void              SetRoot(const FilterNode& root) { root_ = root; }
  auto GenerateSQLOn(sl_element_id_t parent_id) const -> std::string;
  auto GenerateIdSQLOn(sl_element_id_t parent_id) const -> std::string;
 private:
  FilterNode root_;
};

}  // namespace alcedo
