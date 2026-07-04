#include "filter_combo.h"

#include <sstream>
#include <cstdio>
#include <android/log.h>

#define LOG_TAG "AlcedoFilterCombo"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace alcedo {

// ============================================================
// FilterSQLCompiler - FieldToColumn
// Maps FilterField enum to SQLite/Room column references.
// Metadata fields use json_extract on the metadata_json column
// of the sleeve_files table.
// ============================================================
std::string FilterSQLCompiler::FieldToColumn(FilterField field) {
    switch (field) {
    case FilterField::ExifCameraModel:
        return R"(json_extract(sf.metadata_json, '$.cameraModel'))";
    case FilterField::ExifFocalLength:
        return R"(json_extract(sf.metadata_json, '$.focalLength'))";
    case FilterField::ExifAperture:
        return R"(json_extract(sf.metadata_json, '$.aperture'))";
    case FilterField::ExifISO:
        return R"(json_extract(sf.metadata_json, '$.iso'))";
    case FilterField::CaptureDate:
        return R"(json_extract(sf.metadata_json, '$.captureDate'))";
    case FilterField::ImportDate:
        return "se.added_time";
    case FilterField::FileName:
        return "se.element_name";
    case FilterField::FileExtension:
        return "sf.file_extension";
    case FilterField::ImageSize:
        return "(sf.width * sf.height)";
    case FilterField::Rating:
        return "se.ref_count";
    case FilterField::ImagePath:
        return "sf.file_path";
    case FilterField::SemanticTags:
        return R"(json_extract(sf.metadata_json, '$.semanticTags'))";
    default:
        LOGW("FieldToColumn: unknown field %u", static_cast<uint32_t>(field));
        return "\"unknown\"";
    }
}

// ============================================================
// FilterSQLCompiler - CompareToSQL
// ============================================================
std::string FilterSQLCompiler::CompareToSQL(CompareOp op) {
    switch (op) {
    case CompareOp::EQUALS:         return "=";
    case CompareOp::NOT_EQUALS:     return "!=";
    case CompareOp::CONTAINS:       return "LIKE";
    case CompareOp::NOT_CONTAINS:   return "NOT LIKE";
    case CompareOp::GREATER_THAN:   return ">";
    case CompareOp::LESS_THAN:      return "<";
    case CompareOp::GREATER_EQUAL:  return ">=";
    case CompareOp::LESS_EQUAL:     return "<=";
    case CompareOp::STARTS_WITH:    return "LIKE";
    case CompareOp::ENDS_WITH:      return "LIKE";
    case CompareOp::BETWEEN:        return "BETWEEN";
    case CompareOp::REGEX:          return "REGEXP";
    default:
        LOGW("CompareToSQL: unknown op %u", static_cast<uint32_t>(op));
        return "=";
    }
}

// ============================================================
// FilterSQLCompiler - GenerateConditionString
// Produces a single SQL condition fragment from a FieldCondition.
// Uses string concatenation and parameterised value quoting.
// ============================================================
std::string FilterSQLCompiler::GenerateConditionString(const FieldCondition& cond) {
    std::string column = FieldToColumn(cond.field_);
    std::string op_sql = CompareToSQL(cond.op_);

    // Helper: quote a string value for SQL, escaping single quotes
    auto quote_string = [](const std::string& s) -> std::string {
        std::string escaped;
        escaped.reserve(s.size() + 2);
        escaped += '\'';
        for (char c : s) {
            if (c == '\'') {
                escaped += '\'';
            }
            escaped += c;
        }
        escaped += '\'';
        return escaped;
    };

    // Helper: format a FilterValue as SQL literal
    auto format_value = [&quote_string](const FilterValue& val) -> std::string {
        return std::visit([&quote_string](auto&& arg) -> std::string {
            using T = std::decay_t<decltype(arg)>;
            if constexpr (std::is_same_v<T, std::monostate>) {
                return "NULL";
            } else if constexpr (std::is_same_v<T, int64_t>) {
                return std::to_string(arg);
            } else if constexpr (std::is_same_v<T, double>) {
                {
                    char buf[64];
                    std::snprintf(buf, sizeof(buf), "%g", arg);
                    return std::string(buf);
                }
            } else if constexpr (std::is_same_v<T, bool>) {
                return arg ? "1" : "0";
            } else if constexpr (std::is_same_v<T, std::string>) {
                return quote_string(arg);
            } else {
                return "NULL";
            }
        }, val);
    };

    switch (cond.op_) {
    case CompareOp::CONTAINS:
    case CompareOp::NOT_CONTAINS: {
        // value becomes LIKE pattern: '%value%'
        std::string pattern;
        if (std::holds_alternative<std::string>(cond.value_)) {
            pattern = "'" + std::string("%") + std::get<std::string>(cond.value_) + std::string("%") + "'";
        } else {
            pattern = format_value(cond.value_);
        }
        return column + " " + op_sql + " " + pattern;
    }
    case CompareOp::STARTS_WITH: {
        std::string pattern;
        if (std::holds_alternative<std::string>(cond.value_)) {
            pattern = "'" + std::get<std::string>(cond.value_) + std::string("%") + "'";
        } else {
            pattern = format_value(cond.value_);
        }
        return column + " LIKE " + pattern;
    }
    case CompareOp::ENDS_WITH: {
        std::string pattern;
        if (std::holds_alternative<std::string>(cond.value_)) {
            pattern = "'" + std::string("%") + std::get<std::string>(cond.value_) + "'";
        } else {
            pattern = format_value(cond.value_);
        }
        return column + " LIKE " + pattern;
    }
    case CompareOp::BETWEEN: {
        std::string low = format_value(cond.value_);
        std::string high = cond.second_value_.has_value()
                               ? format_value(cond.second_value_.value())
                               : low;
        return column + " BETWEEN " + low + " AND " + high;
    }
    case CompareOp::REGEX: {
        std::string val = format_value(cond.value_);
        return column + " REGEXP " + val;
    }
    default:
        return column + " " + op_sql + " " + format_value(cond.value_);
    }
}

// ============================================================
// FilterSQLCompiler - CompileNode (recursive)
// ============================================================
std::string FilterSQLCompiler::CompileNode(const FilterNode& node) {
    switch (node.type_) {
    case FilterNode::Type::Condition: {
        if (!node.condition_.has_value()) {
            LOGW("CompileNode: Condition node has no condition");
            return "1=1";
        }
        return GenerateConditionString(node.condition_.value());
    }
    case FilterNode::Type::Logical: {
        if (node.children_.empty()) {
            return "1=1";
        }
        std::string joiner = (node.op_ == FilterOp::OR) ? " OR " : " AND ";
        std::string result;
        for (size_t i = 0; i < node.children_.size(); ++i) {
            if (i > 0) {
                result += joiner;
            }
            result += "(" + CompileNode(node.children_[i]) + ")";
        }
        return result;
    }
    case FilterNode::Type::RawSQL: {
        if (!node.raw_sql_.has_value() || node.raw_sql_->empty()) {
            LOGW("CompileNode: RawSQL node has no SQL");
            return "1=1";
        }
        return node.raw_sql_.value();
    }
    default:
        LOGE("CompileNode: unknown node type");
        return "1=1";
    }
}

// ============================================================
// FilterSQLCompiler - Compile (public entry point)
// ============================================================
std::string FilterSQLCompiler::Compile(const FilterNode& node) {
    auto sql = CompileNode(node);
    LOGI("Compiled filter SQL: %s", sql.c_str());
    return sql;
}

// ============================================================
// FilterCombo - GenerateSQLOn
// Generates a full SELECT query with the filter applied under
// a given parent folder.
// ============================================================
std::string FilterCombo::GenerateSQLOn(uint32_t parent_id) const {
    std::string where_clause = FilterSQLCompiler::Compile(root_);
    return std::string(
        "SELECT se.element_id, se.element_name, se.element_type, "
        "se.parent_id, se.added_time, se.last_modified_time, "
        "se.ref_count, se.pinned, se.sync_flag, "
        "sf.image_id, sf.file_path, sf.file_extension, sf.file_size, "
        "sf.width, sf.height, sf.has_thumbnail, sf.has_full_image "
        "FROM sleeve_elements se "
        "LEFT JOIN sleeve_files sf ON se.element_id = sf.element_id "
        "WHERE se.parent_id = ") + std::to_string(parent_id) + " AND (" + where_clause + ")";
}

// ============================================================
// FilterCombo - GenerateIdSQLOn
// Generates a query that returns only element IDs.
// ============================================================
std::string FilterCombo::GenerateIdSQLOn(uint32_t parent_id) const {
    std::string where_clause = FilterSQLCompiler::Compile(root_);
    return std::string(
        "SELECT se.element_id FROM sleeve_elements se "
        "LEFT JOIN sleeve_files sf ON se.element_id = sf.element_id "
        "WHERE se.parent_id = ") + std::to_string(parent_id) + " AND (" + where_clause + ")";
}

} // namespace alcedo
