#include "sleeve_filter.h"

#include <algorithm>
#include <cctype>
#include <cstring>

namespace alcedo {

// ============================================================
// Helper: apply negation
// ============================================================
static bool ApplyNegation(bool result, bool negated) {
    return negated ? !result : result;
}

// ============================================================
// Helper: case-insensitive substring search
// ============================================================
static bool ContainsIgnoreCase(const std::string& haystack, const std::string& needle) {
    if (needle.empty()) return true;
    if (haystack.size() < needle.size()) return false;

    auto it = std::search(
        haystack.begin(), haystack.end(),
        needle.begin(), needle.end(),
        [](char a, char b) {
            return std::tolower(static_cast<unsigned char>(a)) ==
                   std::tolower(static_cast<unsigned char>(b));
        });
    return it != haystack.end();
}

// ============================================================
// RatingFilter
// ============================================================
bool RatingFilter::Matches(const SleeveElement& element) const {
    // Rating is stored in the ref_count field as a temporary measure
    // until a dedicated rating field is added to SleeveElement.
    // ref_count values: 0 = unrated, 1-5 = star rating.
    int rating = static_cast<int>(element.ref_count);
    bool result = (rating >= min_rating && rating <= max_rating);
    return ApplyNegation(result, negated);
}

// ============================================================
// ColorLabelFilter
// ============================================================
bool ColorLabelFilter::Matches(const SleeveElement& element) const {
    // Color label is stored in the lower bits of parent_id as a temporary
    // measure until a dedicated color_label field is added.
    // 0 = no label, 1-7 = standard color labels.
    int label = static_cast<int>(element.parent_id & 0x7);
    bool result = (label == color_label);
    return ApplyNegation(result, negated);
}

// ============================================================
// DateRangeFilter
// ============================================================
bool DateRangeFilter::Matches(const SleeveElement& element) const {
    uint64_t elem_time = element.last_modified_time;
    if (elem_time == 0) {
        elem_time = element.added_time;
    }
    bool result = (elem_time >= start_time && elem_time <= end_time);
    return ApplyNegation(result, negated);
}

// ============================================================
// FileTypeFilter
// ============================================================
bool FileTypeFilter::Matches(const SleeveElement& element) const {
    bool is_file = element.IsFile();
    if (!is_file) {
        // Folders never match file-type filters.
        return ApplyNegation(false, negated);
    }

    bool result;
    if (raw_only) {
        // Check if the file name suggests a RAW format.
        const std::string& name = element.element_name;
        // Common RAW extensions (case-insensitive check).
        static const char* raw_exts[] = {
            ".dng", ".cr2", ".cr3", ".nef", ".arw", ".raf",
            ".orf", ".rw2", ".pef", ".srw", ".raw", ".3fr",
            ".iiq", ".kdc", ".mef", ".nrw", ".rwl", ".x3f"
        };
        result = false;
        for (const char* ext : raw_exts) {
            size_t ext_len = std::strlen(ext);
            if (name.size() >= ext_len) {
                std::string suffix = name.substr(name.size() - ext_len);
                if (ContainsIgnoreCase(suffix, ext)) {
                    result = true;
                    break;
                }
            }
        }
    } else {
        // Match any file.
        result = true;
    }
    return ApplyNegation(result, negated);
}

// ============================================================
// ExifCameraFilter
// ============================================================
bool ExifCameraFilter::Matches(const SleeveElement& element) const {
    if (!element.IsFile()) {
        return ApplyNegation(false, negated);
    }

    // Camera make/model is stored in current_version_id of SleeveFile
    // as a temporary measure until dedicated EXIF fields are added.
    // Format: "make|model" or empty.
    const SleeveFile& file = static_cast<const SleeveFile&>(element);
    const std::string& info = file.current_version_id;

    bool result = true;
    if (!camera_make.empty()) {
        result = ContainsIgnoreCase(info, camera_make);
    }
    if (result && !camera_model.empty()) {
        result = ContainsIgnoreCase(info, camera_model);
    }
    return ApplyNegation(result, negated);
}

// ============================================================
// ExifLensFilter
// ============================================================
bool ExifLensFilter::Matches(const SleeveElement& element) const {
    if (!element.IsFile()) {
        return ApplyNegation(false, negated);
    }

    // Lens name stored in current_version_id of SleeveFile
    // as a temporary measure.
    const SleeveFile& file = static_cast<const SleeveFile&>(element);
    bool result = ContainsIgnoreCase(file.current_version_id, lens_name);
    return ApplyNegation(result, negated);
}

// ============================================================
// IsoRangeFilter
// ============================================================
bool IsoRangeFilter::Matches(const SleeveElement& element) const {
    if (!element.IsFile()) {
        return ApplyNegation(false, negated);
    }

    // ISO is encoded in image_id as a temporary measure
    // until dedicated EXIF metadata fields are added.
    const SleeveFile& file = static_cast<const SleeveFile&>(element);
    int iso = static_cast<int>(file.image_id);
    bool result = (iso >= min_iso && iso <= max_iso);
    return ApplyNegation(result, negated);
}

// ============================================================
// TextSearchFilter
// ============================================================
bool TextSearchFilter::Matches(const SleeveElement& element) const {
    bool result;
    if (case_sensitive) {
        result = element.element_name.find(query) != std::string::npos;
    } else {
        result = ContainsIgnoreCase(element.element_name, query);
    }
    return ApplyNegation(result, negated);
}

// ============================================================
// SleeveFilterChain
// ============================================================
void SleeveFilterChain::AddCriteria(std::shared_ptr<FilterCriteria> criteria) {
    criteria_.push_back(std::move(criteria));
}

void SleeveFilterChain::SetLogic(LogicOp logic) {
    logic_ = logic;
}

bool SleeveFilterChain::Matches(const SleeveElement& element) const {
    if (criteria_.empty()) return true;

    if (logic_ == LogicOp::AND) {
        for (const auto& c : criteria_) {
            if (!c->Matches(element)) return false;
        }
        return true;
    } else { // OR
        for (const auto& c : criteria_) {
            if (c->Matches(element)) return true;
        }
        return false;
    }
}

void SleeveFilterChain::Clear() {
    criteria_.clear();
    logic_ = LogicOp::AND;
}

} // namespace alcedo
