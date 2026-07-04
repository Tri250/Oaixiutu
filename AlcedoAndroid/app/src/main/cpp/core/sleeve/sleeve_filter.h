#pragma once

#include "sleeve_element.h"
#include "sleeve_file.h"
#include <string>
#include <vector>
#include <functional>
#include <memory>

namespace alcedo {

enum class FilterType {
    RATING,
    COLOR_LABEL,
    DATE_RANGE,
    FILE_TYPE,
    EXIF_CAMERA,
    EXIF_LENS,
    ISO_RANGE,
    TEXT_SEARCH
};

enum class LogicOp { AND, OR };

class FilterCriteria {
public:
    FilterType type;
    LogicOp logic = LogicOp::AND;
    bool negated = false;

    virtual ~FilterCriteria() = default;
    virtual bool Matches(const SleeveElement& element) const = 0;
};

class RatingFilter : public FilterCriteria {
public:
    int min_rating;
    int max_rating;
    bool Matches(const SleeveElement& element) const override;
};

class ColorLabelFilter : public FilterCriteria {
public:
    int color_label;
    bool Matches(const SleeveElement& element) const override;
};

class DateRangeFilter : public FilterCriteria {
public:
    uint64_t start_time;
    uint64_t end_time;
    bool Matches(const SleeveElement& element) const override;
};

class FileTypeFilter : public FilterCriteria {
public:
    bool raw_only;
    bool Matches(const SleeveElement& element) const override;
};

class ExifCameraFilter : public FilterCriteria {
public:
    std::string camera_make;
    std::string camera_model;
    bool Matches(const SleeveElement& element) const override;
};

class ExifLensFilter : public FilterCriteria {
public:
    std::string lens_name;
    bool Matches(const SleeveElement& element) const override;
};

class IsoRangeFilter : public FilterCriteria {
public:
    int min_iso;
    int max_iso;
    bool Matches(const SleeveElement& element) const override;
};

class TextSearchFilter : public FilterCriteria {
public:
    std::string query;
    bool case_sensitive;
    bool Matches(const SleeveElement& element) const override;
};

class SleeveFilterChain {
public:
    void AddCriteria(std::shared_ptr<FilterCriteria> criteria);
    void SetLogic(LogicOp logic);
    bool Matches(const SleeveElement& element) const;
    void Clear();

private:
    std::vector<std::shared_ptr<FilterCriteria>> criteria_;
    LogicOp logic_ = LogicOp::AND;
};

} // namespace alcedo
