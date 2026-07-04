#pragma once

#include <cstdint>
#include <string>
#include <vector>
#include <variant>
#include <optional>
#include <memory>

namespace alcedo {

enum class FilterField : uint32_t {
    ExifCameraModel = 0,
    ExifFocalLength = 1,
    ExifAperture = 2,
    ExifISO = 3,
    CaptureDate = 4,
    ImportDate = 5,
    FileName = 6,
    FileExtension = 7,
    ImageSize = 8,
    Rating = 9,
    ImagePath = 10,
    SemanticTags = 11
};

enum class CompareOp : uint32_t {
    EQUALS = 0, NOT_EQUALS = 1, CONTAINS = 2, NOT_CONTAINS = 3,
    GREATER_THAN = 4, LESS_THAN = 5, GREATER_EQUAL = 6, LESS_EQUAL = 7,
    STARTS_WITH = 8, ENDS_WITH = 9, BETWEEN = 10, REGEX = 11
};

enum class FilterOp { AND, OR };

using FilterValue = std::variant<std::monostate, int64_t, double, bool, std::string>;

struct FieldCondition {
    FilterField field_;
    CompareOp op_;
    FilterValue value_;
    std::optional<FilterValue> second_value_;
};

struct FilterNode {
    enum class Type { Condition, Logical, RawSQL };
    Type type_;
    FilterOp op_ = FilterOp::AND;
    std::optional<FieldCondition> condition_;
    std::vector<FilterNode> children_;
    std::optional<std::string> raw_sql_;
};

class FilterSQLCompiler {
public:
    static std::string Compile(const FilterNode& node);
private:
    static std::string FieldToColumn(FilterField field);
    static std::string CompareToSQL(CompareOp op);
    static std::string GenerateConditionString(const FieldCondition& cond);
    static std::string CompileNode(const FilterNode& node);
};

class FilterCombo {
public:
    FilterCombo() = default;
    explicit FilterCombo(FilterNode root) : root_(std::move(root)) {}

    std::string GenerateSQLOn(uint32_t parent_id) const;
    std::string GenerateIdSQLOn(uint32_t parent_id) const;

    const FilterNode& Root() const { return root_; }
    void SetRoot(FilterNode root) { root_ = std::move(root); }

private:
    FilterNode root_;
};

} // namespace alcedo
